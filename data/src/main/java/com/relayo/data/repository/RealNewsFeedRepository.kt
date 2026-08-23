package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.local.NewsPostDao
import com.relayo.data.local.NewsPostEntity
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE = "news_post"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealNewsFeedRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val contentFilter:ContentFilter,
    private val newsPostDao:NewsPostDao
):NewsFeedRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _posts = MutableStateFlow<List<NewsPost>>(emptyList())

    init {
        // Load persisted posts
        repositoryScope.launch(Dispatchers.IO) {
            val persisted = newsPostDao.getAll().map { it.toDomain() }
            if(persisted.isNotEmpty()) {
                _posts.value = persisted
            }
        }

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
                persistPost(post)
            }
        }
    }

    private fun NewsPostEntity.toDomain() = NewsPost(
        id = id,
        authorId = authorId,
        authorDisplayName = authorDisplayName,
        content = content,
        timestampEpochMillis = timestampEpochMillis,
        hopCount = hopCount
    )

    private fun NewsPost.toEntity() = NewsPostEntity(
        id = id,
        authorId = authorId,
        authorDisplayName = authorDisplayName,
        content = content,
        timestampEpochMillis = timestampEpochMillis,
        hopCount = hopCount
    )

    private suspend fun persistPost(post:NewsPost) = withContext(Dispatchers.IO) {
        try { newsPostDao.insert(post.toEntity()) } catch(_:Exception) {}
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
        persistPost(local)

        floodRouter.broadcast(PAYLOAD_TYPE, NewsPostWireCodec.encode(wire), initialTtl = MeshFloodRouter.DEFAULT_TTL)
    }
}
