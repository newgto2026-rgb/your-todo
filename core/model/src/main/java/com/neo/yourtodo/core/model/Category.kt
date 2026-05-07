package com.neo.yourtodo.core.model

data class Category(
    val id: Long,
    val name: String,
    val colorHex: String?,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long
)
