package com.pointquest.android.core.auth

import android.content.Context
import android.util.Base64
import androidx.core.util.AtomicFile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SecureSessionStore(
    context: Context,
    private val cipher: AndroidKeystoreCipher = AndroidKeystoreCipher(),
    moshi: Moshi = defaultMoshi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {
    private val mutex = Mutex()
    private val sessionFile = File(context.noBackupFilesDir, FILE_NAME)
    private val atomicFile = AtomicFile(sessionFile)
    private val payloadAdapter = moshi.adapter(SessionPayload::class.java)
    private val envelopeAdapter = moshi.adapter(SessionEnvelope::class.java)

    override suspend fun read(): StoredRefreshSession? = withContext(ioDispatcher) {
        mutex.withLock {
            if (!sessionFile.exists()) return@withLock null

            try {
                val envelopeJson = atomicFile.openRead().use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
                val envelope = envelopeAdapter.fromJson(envelopeJson)
                    ?: throw JsonDataException("Session envelope is null")
                if (envelope.version != ENVELOPE_VERSION) {
                    throw JsonDataException("Unsupported session envelope version")
                }
                val plaintext = cipher.decrypt(
                    EncryptedSessionPayload(
                        iv = Base64.decode(envelope.iv, Base64.NO_WRAP),
                        ciphertext = Base64.decode(envelope.ciphertext, Base64.NO_WRAP),
                    ),
                )
                val payload = payloadAdapter.fromJson(plaintext.toString(Charsets.UTF_8))
                    ?: throw JsonDataException("Session payload is null")
                if (payload.refreshToken.isBlank()) {
                    throw JsonDataException("Refresh token is blank")
                }
                StoredRefreshSession(
                    refreshToken = payload.refreshToken,
                    expiresAt = Instant.parse(payload.expiresAt),
                )
            } catch (failure: Exception) {
                throw IOException("Unable to read secure session", failure)
            }
        }
    }

    override suspend fun write(value: StoredRefreshSession) = withContext(ioDispatcher) {
        mutex.withLock {
            require(value.refreshToken.isNotBlank()) { "Refresh token must not be blank" }
            val plaintext = payloadAdapter.toJson(
                SessionPayload(
                    refreshToken = value.refreshToken,
                    expiresAt = value.expiresAt.toString(),
                ),
            ).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plaintext)
            val envelope = SessionEnvelope(
                version = ENVELOPE_VERSION,
                iv = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
            )
            val encodedEnvelope = envelopeAdapter.toJson(envelope).toByteArray(Charsets.UTF_8)

            val output = atomicFile.startWrite()
            try {
                output.write(encodedEnvelope)
                atomicFile.finishWrite(output)
            } catch (failure: Throwable) {
                runCatching { atomicFile.failWrite(output) }
                throw failure
            }
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        mutex.withLock {
            atomicFile.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "point_session_v1.json"
        const val ENVELOPE_VERSION = 1

        fun defaultMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

@JsonClass(generateAdapter = false)
private data class SessionPayload(
    val refreshToken: String,
    val expiresAt: String,
)

@JsonClass(generateAdapter = false)
private data class SessionEnvelope(
    val version: Int,
    val iv: String,
    val ciphertext: String,
)
