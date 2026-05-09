package com.neo.yourtodo.core.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WorkspaceSyncNotifier @Inject constructor() {
    private val mutableSnapshots = MutableStateFlow<WorkspaceRefreshSnapshot?>(null)
    val snapshots: StateFlow<WorkspaceRefreshSnapshot?> = mutableSnapshots.asStateFlow()

    fun publish(snapshot: WorkspaceRefreshSnapshot) {
        mutableSnapshots.value = snapshot
    }
}
