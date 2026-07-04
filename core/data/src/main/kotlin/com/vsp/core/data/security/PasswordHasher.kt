package com.vsp.core.data.security

import com.vsp.core.model.auth.Credentials
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives and verifies salted PBKDF2 password hashes. The plaintext password never leaves this
 * class; only the derived hash + salt are persisted (locally and in RTDB). Verification is
 * constant-time to avoid timing side channels.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    fun hash(password: String): Credentials {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(password.toCharArray(), salt, ITERATIONS)
        return Credentials(
            algo = ALGORITHM,
            iterations = ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(derived),
        )
    }

    fun verify(password: String, credentials: Credentials): Boolean {
        if (credentials.salt.isBlank() || credentials.hash.isBlank()) return false
        val salt = runCatching { Base64.getDecoder().decode(credentials.salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(credentials.hash) }.getOrNull() ?: return false
        val iterations = credentials.iterations.takeIf { it > 0 } ?: ITERATIONS
        val actual = pbkdf2(password.toCharArray(), salt, iterations)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
    }
}
