package com.neo.yourtodo.core.model.personvisibility

data class PersonVisibilityGrant(
    val id: String,
    val ownerUserId: String,
    val observerUserId: String,
    val state: PersonVisibilityGrantState,
    val createdAt: String,
    val updatedAt: String,
    val revokedAt: String? = null
) {
    val isActive: Boolean
        get() = state == PersonVisibilityGrantState.ACTIVE
}

enum class PersonVisibilityGrantState {
    NONE,
    ACTIVE,
    REVOKED
}
