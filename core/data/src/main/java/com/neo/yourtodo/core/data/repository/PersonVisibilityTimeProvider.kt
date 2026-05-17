package com.neo.yourtodo.core.data.repository

fun interface PersonVisibilityTimeProvider {
    fun currentTimeMillis(): Long
}
