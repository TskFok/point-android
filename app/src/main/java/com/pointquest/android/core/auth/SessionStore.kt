package com.pointquest.android.core.auth

interface SessionStore {
    suspend fun read(): StoredRefreshSession?

    suspend fun write(value: StoredRefreshSession)

    suspend fun clear()
}
