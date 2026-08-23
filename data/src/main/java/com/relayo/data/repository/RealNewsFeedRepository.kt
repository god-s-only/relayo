package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.wire.NewsPostWire
import com.relayo.data.wire.NewsPostWireCodec
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.NewsPost
import com.relayo.domain.repository.NewsFeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE = "news_post"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealNewsFeedRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val contentFilter:ContentFilter
):NewsFeedRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _posts = MutableStateFlow<List<NewsPost>>(emptyList())

    init {
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType != PAYLOAD_TYPE) return@collect
                val wire = NewsPostWireCodec.decode(received.payloadBytes) ?: return@collect
                if(!contentFilter.isAllowed(wire.content)) return@collect
                val hopCount = (MeshFloodRouter.DEFAULT_TTL - received.remainingTtl).coerceAtLeast(0)
                val post = NewsPost(
                    id = "post-${System.nanoTime()}",
                    authorId = wire.authorId,
                    authorDisplayName = wire.authorDisplayName,
                    content = wire.content,
                    timestampEpochMillis = wire.timestampEpochMillis,
                    hopCount = hopCount
                )
                _posts.value = listOf(post) + _posts.value
            }
        }
    }

    override fun observeFeed() = _posts.asStateFlow()

    override suspend fun broadcastPost(content:String) {
        if(!contentFilter.isAllowed(content)) return
        val wire = NewsPostWire(
            authorId = "me",
            authorDisplayName = "You",
            content = content,
            timestampEpochMillis = System.currentTimeMillis()
        )
        val local = NewsPost(
            id = "post-${System.nanoTime()}",
            authorId = "me",
            authorDisplayName = "You",
            content = content,
            timestampEpochMillis = wire.timestampEpochMillis,
            hopCount = 0
        )
        _posts.value = listOf(local) + _posts.value

        floodRouter.broadcast(PAYLOAD_TYPE, NewsPostWireCodec.encode(wire), initialTtl = MeshFloodRouter.DEFAULT_TTL)
    }
}