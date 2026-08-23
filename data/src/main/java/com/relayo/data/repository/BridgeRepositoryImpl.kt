package com.relayo.data.repository

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.wire.BridgeRequestWire
import com.relayo.data.wire.BridgeResponseWire
import com.relayo.data.wire.BridgeWireCodec
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.BridgeRequest
import com.relayo.domain.model.BridgeRequestType
import com.relayo.domain.model.BridgeResponse
import com.relayo.domain.repository.BridgeRepository
import com.relayo.domain.repository.IdentityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE_REQUEST = "bridge_request"
private const val PAYLOAD_TYPE_RESPONSE = "bridge_response"

@OptIn(InternalSerializationApi::class)
@Singleton
class BridgeRepositoryImpl @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val identityRepository:IdentityRepository,
    private val contentFilter:ContentFilter,
    @ApplicationContext private val context:Context
):BridgeRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _myRequests = MutableStateFlow<List<BridgeRequest>>(emptyList())
    private val _responses = MutableStateFlow<List<BridgeResponse>>(emptyList())
    private val seenRequestIds = mutableSetOf<String>()

    @Volatile
    private var currentSessionId:String? = null

    init {
        repositoryScope.launch {
            identityRepository.observeIdentity().collect { identity ->
                currentSessionId = identity?.sessionId
            }
        }
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                when(received.payloadType) {
                    PAYLOAD_TYPE_REQUEST -> handleIncomingRequest(received.payloadBytes)
                    PAYLOAD_TYPE_RESPONSE -> handleIncomingResponse(received.payloadBytes)
                }
            }
        }
    }

    private fun mySenderId():String {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.address?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: currentSessionId ?: "unknown"
        } catch(e:SecurityException) {
            currentSessionId ?: "unknown"
        }
    }

    override fun observeMyRequests():StateFlow<List<BridgeRequest>> = _myRequests.asStateFlow()
    override fun observeResponses():StateFlow<List<BridgeResponse>> = _responses.asStateFlow()
    override fun observeResponsesForRequest(requestId:String): Flow<BridgeResponse?> =
        _responses.map { list -> list.find { it.requestId == requestId } }

    override suspend fun hasInternet():Boolean = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return@withContext false
            val caps = cm.getNetworkCapabilities(network) ?: return@withContext false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch(e:Exception) { false }
    }

    override suspend fun sendRequest(type:BridgeRequestType, query:String):String {
        if(!contentFilter.isAllowed(query)) throw IllegalArgumentException("Query contains blocked content")
        val requestId = "bridge-${System.nanoTime()}"
        val request = BridgeRequest(
            id = requestId,
            requesterId = mySenderId(),
            type = type,
            query = query,
            timestampEpochMillis = System.currentTimeMillis()
        )
        _myRequests.value = _myRequests.value + request
        seenRequestIds.add(requestId)
        val wire = BridgeRequestWire(
            id = requestId,
            requesterId = request.requesterId,
            type = type.name,
            query = query,
            timestampEpochMillis = request.timestampEpochMillis
        )
        floodRouter.broadcast(PAYLOAD_TYPE_REQUEST, BridgeWireCodec.encodeRequest(wire))
        return requestId
    }

    private suspend fun handleIncomingRequest(payloadBytes:ByteArray) {
        val wire = BridgeWireCodec.decodeRequest(payloadBytes) ?: return
        if(seenRequestIds.contains(wire.id)) return
        seenRequestIds.add(wire.id)
        if(wire.requesterId == mySenderId()) return
        if(!contentFilter.isAllowed(wire.query)) return
        val type = try { BridgeRequestType.valueOf(wire.type) } catch(e:Exception) { return }
        val request = BridgeRequest(
            id = wire.id,
            requesterId = wire.requesterId,
            type = type,
            query = wire.query,
            timestampEpochMillis = wire.timestampEpochMillis
        )
        // Keep a local list of seen requests for UI (even if we are not the bridge)
        // Only the bridge with internet will actually respond
        if(hasInternet()) {
            repositoryScope.launch(Dispatchers.IO) {
                val result = handleBridgeLookup(type, wire.query)
                val response = BridgeResponse(
                    requestId = wire.id,
                    responderId = mySenderId(),
                    result = result.getOrElse { it.message ?: "Unknown error" },
                    error = if(result.isSuccess) null else result.exceptionOrNull()?.message,
                    timestampEpochMillis = System.currentTimeMillis()
                )
                if(result.isSuccess && !contentFilter.isAllowed(response.result)) {
                    // Don't forward filtered results
                    return@launch
                }
                val responseWire = BridgeResponseWire(
                    requestId = response.requestId,
                    responderId = response.responderId,
                    result = response.result,
                    error = response.error,
                    timestampEpochMillis = response.timestampEpochMillis
                )
                floodRouter.broadcast(PAYLOAD_TYPE_RESPONSE, BridgeWireCodec.encodeResponse(responseWire))
                // Also add to our own responses flow so requester can see via mesh loopback
                _responses.value = _responses.value + response
            }
        }
    }

    private suspend fun handleIncomingResponse(payloadBytes:ByteArray) {
        val wire = BridgeWireCodec.decodeResponse(payloadBytes) ?: return
        if(!contentFilter.isAllowed(wire.result)) return
        val response = BridgeResponse(
            requestId = wire.requestId,
            responderId = wire.responderId,
            result = wire.result,
            error = wire.error,
            timestampEpochMillis = wire.timestampEpochMillis
        )
        // Deduplicate
        if(_responses.value.any { it.requestId == response.requestId && it.responderId == response.responderId && it.result == response.result }) return
        _responses.value = _responses.value + response
    }

    private suspend fun handleBridgeLookup(type:BridgeRequestType, query:String):Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = when(type) {
                BridgeRequestType.EXCHANGE_RATE -> fetchExchangeRate(query)
                BridgeRequestType.WEATHER -> fetchWeather(query)
                BridgeRequestType.WEB_FETCH -> fetchWebPage(query)
            }
            Result.success(result)
        } catch(e:Exception) {
            Log.w("Bridge", "Lookup failed for $type query=$query", e)
            Result.failure(e)
        }
    }

    private fun fetchExchangeRate(query:String):String {
        // Parse query like "USD/EUR", "USD to EUR", "USD EUR"
        val cleaned = query.trim().uppercase()
        val parts = cleaned.split(Regex("[^A-Z]+")).filter { it.length == 3 }
        val base = parts.getOrNull(0) ?: "USD"
        val target = parts.getOrNull(1) ?: "EUR"
        val urlStr = "https://api.frankfurter.app/latest?from=${URLEncoder.encode(base, "UTF-8")}&to=${URLEncoder.encode(target, "UTF-8")}"
        val json = httpGet(urlStr, 5000)
        // Frankfurter returns {"rates":{"EUR":0.92}, "base":"USD", ...}
        // Simple regex extract
        val rateRegex = Regex("\"$target\"\\s*:\\s*([0-9.]+)")
        val match = rateRegex.find(json)
        val rate = match?.groupValues?.get(1)
        return if(rate != null) "1 $base = $rate $target (via frankfurter.app)" else "Rate $base->$target: $json".take(800)
    }

    private fun fetchWeather(query:String):String {
        val city = query.trim().takeIf { it.isNotBlank() } ?: "London"
        val encodedCity = URLEncoder.encode(city, "UTF-8")
        // wttr.in returns plain text; use format 3 for compact
        val urlStr = "https://wttr.in/${encodedCity}?format=3"
        val text = httpGet(urlStr, 5000)
        return text.take(800).ifBlank { "Weather for $city: $text" }
    }

    private fun fetchWebPage(query:String):String {
        var urlStr = query.trim()
        if(!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://$urlStr"
        }
        val text = httpGet(urlStr, 7000)
        // Strip tags roughly and limit
        val stripped = text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        return stripped.take(1200).ifBlank { text.take(1200) }
    }

    private fun httpGet(urlStr:String, timeoutMs:Int):String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "Relayo/0.1 (Bridge)")
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        val stream = if(code in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line:String?
        while(reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
            if(sb.length > 8000) break
        }
        reader.close()
        conn.disconnect()
        if(code !in 200..299) throw RuntimeException("HTTP $code: ${sb.toString().take(300)}")
        return sb.toString()
    }
}


