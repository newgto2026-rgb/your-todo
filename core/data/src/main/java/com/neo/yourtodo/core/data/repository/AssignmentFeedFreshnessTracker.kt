package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCacheKey
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Singleton
class AssignmentFeedFreshnessTracker @Inject constructor() {
    private val refreshTimes = MutableStateFlow<Map<ScopedAssignmentFeedCacheKey, Long>>(emptyMap())

    fun observeRefreshTime(ownerUserId: String, feed: AssignmentFeedCacheKey): Flow<Long?> {
        val scopedKey = feed.toScopedKey(ownerUserId)
        return refreshTimes
            .map { refreshTimes -> refreshTimes[scopedKey] }
            .distinctUntilChanged()
    }

    fun recordRefresh(
        ownerUserId: String,
        feed: AssignmentFeedCacheKey,
        refreshedAt: Long
    ) {
        val scopedKey = feed.toScopedKey(ownerUserId)
        refreshTimes.update { refreshTimes -> refreshTimes + (scopedKey to refreshedAt) }
    }

    fun clear() {
        refreshTimes.value = emptyMap()
    }

    private fun AssignmentFeedCacheKey.toScopedKey(ownerUserId: String) =
        ScopedAssignmentFeedCacheKey(
            ownerUserId = ownerUserId,
            direction = direction,
            status = status,
            friendUserId = friendUserId
        )

    private data class ScopedAssignmentFeedCacheKey(
        val ownerUserId: String,
        val direction: AssignmentDirection,
        val status: AssignmentFeedStatus,
        val friendUserId: String?
    )
}
