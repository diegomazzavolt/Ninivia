package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.crypto.CryptoManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an Android device/emulator to exercise the real hardware-backed provider. */
@RunWith(AndroidJUnit4::class)
class CryptoInstrumentedTest {
    @Test fun keystoreRoundTripAndRandomNonce() {
        val text = "Nota com acentuação e emoji 🌿"
        val first = CryptoManager.encrypt(text)
        val second = CryptoManager.encrypt(text)
        assertNotEquals(first, second)
        assertEquals(text, CryptoManager.decrypt(first))
        assertEquals(text, CryptoManager.decrypt(second))
    }
    @Test fun invalidCiphertextIsNotReturnedAsEditableContent() {
        assertThrows(Exception::class.java) { CryptoManager.decrypt("invalid ciphertext") }
    }
}
