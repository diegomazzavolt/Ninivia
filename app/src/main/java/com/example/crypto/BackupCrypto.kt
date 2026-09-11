package com.example.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password backup. Header, salt and nonce are authenticated. */
object BackupCrypto {
    private val magic = "NINIVIA1".toByteArray(Charsets.US_ASCII)
    const val MAX_BYTES = 20 * 1024 * 1024
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    fun encrypt(text: String, password: CharArray): ByteArray {
        require(password.size >= 8) { "Use uma senha de pelo menos 8 caracteres." }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES - 52) { "O backup excede 20 MB." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + iv
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            updateAAD(header)
            header + doFinal(bytes)
        }
    }
    fun decrypt(bytes: ByteArray, password: CharArray): String {
        require(bytes.size in 52..MAX_BYTES && bytes.copyOfRange(0, 8).contentEquals(magic)) { "Arquivo de backup inválido." }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(password, bytes.copyOfRange(8, 24)), GCMParameterSpec(128, bytes.copyOfRange(24, 36)))
            updateAAD(bytes.copyOfRange(0, 36))
            String(doFinal(bytes.copyOfRange(36, bytes.size)), Charsets.UTF_8)
        }
    }
}
