package com.neo.yourtodo.core.model

enum class TodoSyncStatus {
    LOCAL_ONLY,
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    FAILED
}
