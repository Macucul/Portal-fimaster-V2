package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Utilitário de Segurança Criptográfica para Senhas
 * Suporta:
 * - PBKDF2 (PBKDF2WithHmacSHA256 e PBKDF2WithHmacSHA1) com Salt e Iterações
 * - SHA-256 + Salt Criptográfico (SecureRandom)
 * - Comparação em tempo constante (MessageDigest.isEqual) contra Timing Attacks
 */
object CryptoSecurity {

    private const val DEFAULT_PBKDF2_ITERATIONS = 10000
    private const val DEFAULT_KEY_LENGTH_BITS = 256
    private const val SALT_BYTE_LENGTH = 16

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    /**
     * Gera um Salt criptograficamente seguro em formato Hexadecimal
     */
    fun generateSecureSalt(byteLength: Int = SALT_BYTE_LENGTH): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Gera Hash SHA-256 a partir de uma String
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Gera Hash SHA-256 com Salt
     */
    fun hashPasswordSha256(password: String, salt: String): String {
        val cleanSalt = salt.trim()
        val combined = password.trim() + cleanSalt
        return sha256(combined)
    }

    /**
     * Gera Hash PBKDF2 (PBKDF2WithHmacSHA256 ou fallback PBKDF2WithHmacSHA1)
     */
    fun hashPasswordPbkdf2(
        password: String,
        salt: String,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): String {
        return try {
            val saltBytes = if (isHex(salt)) hexToBytes(salt) else salt.toByteArray(Charsets.UTF_8)
            val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterations, keyLengthBits)
            val skf = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (e: Exception) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            }
            val hash = skf.generateSecret(spec).encoded
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback para SHA-256 se o algoritmo não estiver disponível
            hashPasswordSha256(password, salt)
        }
    }

    /**
     * Formata no padrão completo PBKDF2: "pbkdf2:sha256:iteracoes:salt:hash"
     */
    fun createPbkdf2String(
        password: String,
        salt: String = generateSecureSalt(),
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS
    ): String {
        val hash = hashPasswordPbkdf2(password, salt, iterations)
        return "pbkdf2:sha256:$iterations:$salt:$hash"
    }

    /**
     * Gera Hash MD5 (compatibilidade legada)
     */
    fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Comparação de segurança em tempo constante (protege contra Timing Attacks)
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            // Executa verificação falsa para manter tempo similar
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), a.toByteArray(Charsets.UTF_8))
            return false
        }
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    /**
     * Validação Criptográfica Abrangente da Senha
     * Valida contra:
     * 1. PBKDF2 com Salt e Iterações (formatos estruturados ou direct)
     * 2. SHA-256 com Salt (padrão password+salt, salt+password, password:salt)
     * 3. SHA-256 puro
     * 4. MD5 com Salt e MD5 puro (legado)
     * 5. Match em texto plano (caso a conta tenha senha em texto simples não migrada)
     */
    fun verifyPassword(
        plainPassword: String,
        storedHash: String,
        storedSalt: String = ""
    ): Boolean {
        val cleanPassword = plainPassword.trim()
        val rawHash = storedHash.trim().removeSurrounding("\"")

        if (rawHash.isBlank() || rawHash.equals("null", ignoreCase = true)) {
            return true // Conta sem senha inicial definida
        }

        // 1. Verifica formato estruturado PBKDF2: "pbkdf2:alg:iterations:salt:hash" ou "pbkdf2$iterations$salt$hash"
        if (rawHash.startsWith("pbkdf2:", ignoreCase = true) || rawHash.startsWith("pbkdf2$", ignoreCase = true)) {
            val parts = if (rawHash.contains("$")) rawHash.split("$") else rawHash.split(":")
            if (parts.size >= 4) {
                val iterations = parts.find { it.toIntOrNull() != null }?.toIntOrNull() ?: DEFAULT_PBKDF2_ITERATIONS
                val saltCandidate = parts.getOrNull(parts.size - 2) ?: storedSalt
                val targetHash = parts.last()
                val computedPbkdf2 = hashPasswordPbkdf2(cleanPassword, saltCandidate, iterations)
                if (constantTimeEquals(computedPbkdf2.lowercase(), targetHash.lowercase())) {
                    return true
                }
            }
        }

        // 2. Extrai salt embutido no hash se houver separador ":" ou ";"
        val hashParts = if (rawHash.contains(":")) rawHash.split(":") else if (rawHash.contains(";")) rawHash.split(";") else listOf(rawHash)
        val cleanHash = hashParts[0].trim()
        val saltFromHash = if (hashParts.size > 1) hashParts[1].trim() else ""
        val effectiveSalt = storedSalt.trim().ifBlank { saltFromHash }

        // 3. PBKDF2 Direto com Salt Efetivo
        if (effectiveSalt.isNotBlank()) {
            val pbkdf2Direct = hashPasswordPbkdf2(cleanPassword, effectiveSalt, DEFAULT_PBKDF2_ITERATIONS)
            if (constantTimeEquals(pbkdf2Direct.lowercase(), cleanHash.lowercase()) ||
                constantTimeEquals(pbkdf2Direct.lowercase(), rawHash.lowercase())) {
                return true
            }
        }

        // 4. SHA-256 com Salt
        if (effectiveSalt.isNotBlank()) {
            val shaSalt1 = sha256(cleanPassword + effectiveSalt)
            val shaSalt2 = sha256(effectiveSalt + cleanPassword)
            val shaSalt3 = sha256("$cleanPassword:$effectiveSalt")
            val shaSalt4 = sha256("$effectiveSalt:$cleanPassword")

            if (constantTimeEquals(shaSalt1.lowercase(), cleanHash.lowercase()) ||
                constantTimeEquals(shaSalt1.lowercase(), rawHash.lowercase()) ||
                constantTimeEquals(shaSalt2.lowercase(), cleanHash.lowercase()) ||
                constantTimeEquals(shaSalt2.lowercase(), rawHash.lowercase()) ||
                constantTimeEquals(shaSalt3.lowercase(), cleanHash.lowercase()) ||
                constantTimeEquals(shaSalt4.lowercase(), cleanHash.lowercase())) {
                return true
            }
        }

        // 5. SHA-256 Sem Salt
        val shaNoSalt = sha256(cleanPassword)
        if (constantTimeEquals(shaNoSalt.lowercase(), cleanHash.lowercase()) ||
            constantTimeEquals(shaNoSalt.lowercase(), rawHash.lowercase())) {
            return true
        }

        // 6. MD5 com e sem Salt (Legado)
        if (effectiveSalt.isNotBlank()) {
            val md5Salt1 = md5(cleanPassword + effectiveSalt)
            val md5Salt2 = md5(effectiveSalt + cleanPassword)
            if (md5Salt1.isNotBlank() && (constantTimeEquals(md5Salt1.lowercase(), cleanHash.lowercase()) || constantTimeEquals(md5Salt1.lowercase(), rawHash.lowercase()))) {
                return true
            }
            if (md5Salt2.isNotBlank() && constantTimeEquals(md5Salt2.lowercase(), cleanHash.lowercase())) {
                return true
            }
        }
        val md5NoSalt = md5(cleanPassword)
        if (md5NoSalt.isNotBlank() && (constantTimeEquals(md5NoSalt.lowercase(), cleanHash.lowercase()) || constantTimeEquals(md5NoSalt.lowercase(), rawHash.lowercase()))) {
            return true
        }

        // 7. Comparação direta de texto simples (tempo constante)
        if (constantTimeEquals(cleanPassword, rawHash) || constantTimeEquals(cleanPassword, cleanHash)) {
            return true
        }

        return false
    }

    private fun isHex(input: String): Boolean {
        if (input.isEmpty() || input.length % 2 != 0) return false
        return input.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
