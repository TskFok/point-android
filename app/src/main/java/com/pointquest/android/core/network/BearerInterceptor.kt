package com.pointquest.android.core.network

import com.pointquest.android.core.auth.SessionState
import okhttp3.Interceptor
import okhttp3.Response

class BearerInterceptor(
    private val sessionState: SessionState,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .removeHeader("Authorization")
            .removeHeader("Cookie")
            .removeHeader("X-CSRF-Token")
            .apply {
                sessionState.active.value?.accessToken?.let { token ->
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        return chain.proceed(request)
    }
}
