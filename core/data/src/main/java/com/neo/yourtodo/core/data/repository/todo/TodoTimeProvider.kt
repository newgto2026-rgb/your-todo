package com.neo.yourtodo.core.data.repository.todo

internal fun interface TodoTimeProvider {
    fun currentTimeMillis(): Long
}
