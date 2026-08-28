package com.example.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 helpers for password hashing and verification.
 */
object SecurityUtilsPBKDF2 {
    private const val DEFAULT_ITERATIONS = 100_000
    private const val DEFAULT_KEY_LENGTH = 256 // bits

    fun generateSalt(size: Int = 16): ByteArray {
        val salt = ByteArray(size)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hashPasswordPBKDF2(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS, keyLength: Int = DEFAULT_KEY_LENGTH): String {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = skf.generateSecret(spec).encoded
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val keyB64 = Base64.encodeToString(key, Base64.NO_WRAP)
        // Format: pbkdf2_sha256:iterations:saltB64:keyB64
        return "pbkdf2_sha256:$iterations:$saltB64:$keyB64"
    }

    fun verifyPassword(password: CharArray, stored: String): Boolean {
        try {
            val parts = stored.split(":")
            if (parts.size != 4) return false
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val candidate = hashPasswordPBKDF2(password, salt, iterations)
            return constantTimeEquals(candidate.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            return false
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
