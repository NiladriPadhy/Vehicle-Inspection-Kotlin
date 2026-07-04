package com.vsp.core.model.auth

/**
 * A registered application user (custom account model stored in Firebase RTDB, not Firebase Auth).
 * Credentials are never held in plaintext; see [Credentials].
 */
data class AppUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val vendorId: String = "",
    val createdAt: Long = 0L,
)

/**
 * Salted password-derivation material for a user. The plaintext password is never stored or
 * transmitted; only [hash] (PBKDF2 of password+salt) is persisted, locally and in RTDB.
 */
data class Credentials(
    val algo: String,
    val iterations: Int,
    val salt: String,
    val hash: String,
)
