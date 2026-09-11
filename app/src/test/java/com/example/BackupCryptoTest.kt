package com.example

import com.example.crypto.BackupCrypto
import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    private val password get() = "uma senha longa".toCharArray()
    @Test fun roundTripPreservesUnicodeAndLineBreaks() {
        val text = "Revisão semanal 🌿\nPróxima ação\n\"referência\""
        assertEquals(text, BackupCrypto.decrypt(BackupCrypto.encrypt(text, password), password))
    }
    @Test fun sameInputGetsDifferentSaltAndNonce() {
        assertFalse(BackupCrypto.encrypt("nota", password).contentEquals(BackupCrypto.encrypt("nota", password)))
    }
    @Test fun incorrectPasswordCannotReadBackup() {
        val backup = BackupCrypto.encrypt("nota", password)
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(backup, "senha diferente".toCharArray()) }
    }
    @Test fun tamperingAndTruncationAreDetected() {
        val backup = BackupCrypto.encrypt("nota", password)
        listOf(8, 25, backup.lastIndex).forEach { index ->
            val altered = backup.clone()
            altered[index] = (altered[index].toInt() xor 1).toByte()
            assertThrows(Exception::class.java) { BackupCrypto.decrypt(altered, password) }
        }
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(backup.copyOf(30), password) }
    }
    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt("nota", "curta".toCharArray()) }
    }
}
