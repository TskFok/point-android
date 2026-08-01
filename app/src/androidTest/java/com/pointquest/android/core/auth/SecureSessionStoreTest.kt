package com.pointquest.android.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    private lateinit var sessionFile: File
    private lateinit var store: SecureSessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessionFile = File(context.noBackupFilesDir, "point_session_v1.json")
        store = SecureSessionStore(context)
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun writeReplacesRefreshTokenAndClearRemovesCiphertext() = runBlocking {
        store.write(StoredRefreshSession("old", Instant.parse("2030-01-01T00:00:00Z")))
        store.write(StoredRefreshSession("new", Instant.parse("2030-02-01T00:00:00Z")))

        assertEquals("new", store.read()!!.refreshToken)

        store.clear()
        assertNull(store.read())
        assertFalse(sessionFile.exists())
    }

    @Test
    fun repeatedWritesOfSameSessionUseDifferentCiphertext() = runBlocking {
        val session = StoredRefreshSession("same", Instant.parse("2030-01-01T00:00:00Z"))
        store.write(session)
        val firstEnvelope = sessionFile.readText()

        store.write(session)
        val secondEnvelope = sessionFile.readText()

        assertFalse(firstEnvelope == secondEnvelope)
        assertFalse(secondEnvelope.contains("same"))
        assertEquals(session, store.read())
    }

    @Test
    fun tamperedCiphertextNeverReturnsPartialSession() = runBlocking {
        store.write(StoredRefreshSession("secret", Instant.parse("2030-01-01T00:00:00Z")))
        val original = sessionFile.readText()
        val ciphertextMarker = "\"ciphertext\":\""
        val markerStart = original.indexOf(ciphertextMarker)
        assertTrue(markerStart >= 0)
        val ciphertextStart = markerStart + ciphertextMarker.length
        val replacement = if (original[ciphertextStart] == 'A') 'B' else 'A'
        sessionFile.writeText(original.replaceRange(ciphertextStart, ciphertextStart + 1, replacement.toString()))

        try {
            store.read()
            fail("Tampered ciphertext must fail authentication")
        } catch (_: IOException) {
            // Authentication and parsing failures are all-or-nothing reads.
        }
    }
}
