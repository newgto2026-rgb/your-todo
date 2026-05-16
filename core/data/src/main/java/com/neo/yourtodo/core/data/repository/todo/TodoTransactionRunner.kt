package com.neo.yourtodo.core.data.repository.todo

internal interface TodoTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
