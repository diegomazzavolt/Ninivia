package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TextCipher { fun encrypt(data: String): String; fun decrypt(data: String): String }

/** Device-bound encryption. The alias and byte format preserve v1 notes. */
object CryptoManager : TextCipher {
    private const val ALIAS = "note_e2ee_key"
    private val store by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
    @Synchronized private fun key(create: Boolean): SecretKey {
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        check(create) { "A chave deste dispositivo não está disponível." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    override fun encrypt(data: String): String {
        if (data.isBlank()) return data
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        return Base64.encodeToString(cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun decrypt(data: String): String {
        if (data.isBlank()) return data
        val bytes = Base64.decode(data, Base64.DEFAULT)
        require(bytes.size >= 28) { "Conteúdo cifrado inválido." }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }
}
