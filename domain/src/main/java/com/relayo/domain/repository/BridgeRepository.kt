package com.relayo.domain.repository

import com.relayo.domain.model.BridgeRequest
import com.relayo.domain.model.BridgeRequestType
import com.relayo.domain.model.BridgeResponse
import kotlinx.coroutines.flow.Flow

interface BridgeRepository {
    fun observeMyRequests():Flow<List<BridgeRequest>>
    fun observeResponses():Flow<List<BridgeResponse>>
    fun observeResponsesForRequest(requestId:String):Flow<BridgeResponse?>
    suspend fun sendRequest(type:BridgeRequestType, query:String):String
    suspend fun hasInternet():Boolean
}
