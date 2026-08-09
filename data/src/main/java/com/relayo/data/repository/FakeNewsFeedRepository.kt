package com.relayo.data.repository

import com.relayo.domain.model.NewsPost
import com.relayo.domain.repository.NewsFeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNewsFeedRepository @Inject constructor(): NewsFeedRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _posts = MutableStateFlow<List<NewsPost>>(emptyList())
    private val postsFlow:StateFlow<List<NewsPost>> = _posts.asStateFlow()

    private val simulatedIncoming = listOf(
        "Water point on 5th street is open again" to "Ada",
        "Road closure near the market, use the north route" to "Grace",
        "Clinic tent set up near the old school, free checkups today" to "Turing"
    )

    init {
        repositoryScope.launch {
            simulatedIncoming.forEachIndexed { index, (content, author) ->
                delay(2000L * (index + 1))
                addPost(
                    NewsPost(
                        id = "post-${System.nanoTime()}",
                        authorId = "device-00${index + 1}",
                        authorDisplayName = author,
                        content = content,
                        timestampEpochMillis = System.currentTimeMillis(),
                        hopCount = (1..3).random()
                    )
                )
            }
        }
    }

    override fun observeFeed() = postsFlow

    override suspend fun broadcastPost(content:String) {
        addPost(
            NewsPost(
                id = "post-${System.nanoTime()}",
                authorId = "me",
                authorDisplayName = "You",
                content = content,
                timestampEpochMillis = System.currentTimeMillis(),
                hopCount = 0
            )
        )
    }

    private fun addPost(post:NewsPost) {
        _posts.value = listOf(post) + _posts.value
    }
}