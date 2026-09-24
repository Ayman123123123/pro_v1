package com.red.sovereign.core.auth

/**
 * RED Sovereign Identity Manager
 * Handles user identity, authentication tokens, and admin approval checks.
 */
interface IdentityManager {
    fun getRedId(): String
    fun getUserHandle(): String
    fun getAuthToken(): String?
    fun isLoggedIn(): Boolean
    fun logout()
}
