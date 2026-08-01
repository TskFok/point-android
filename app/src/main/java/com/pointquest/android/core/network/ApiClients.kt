package com.pointquest.android.core.network

import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.infrastructure.ApiClient
import com.pointquest.android.generated.infrastructure.Serializer
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class ApiClients(
    baseUrl: String,
    sessionState: SessionState,
) {
    val publicApi: DefaultApi = defaultApi(baseUrl, publicBuilder())
    val protectedApi: DefaultApi = defaultApi(baseUrl, protectedBuilder(sessionState))

    companion object {
        fun publicBuilder(): OkHttpClient.Builder = secureBaseBuilder()
            .addInterceptor(PublicRequestInterceptor())

        fun protectedBuilder(sessionState: SessionState): OkHttpClient.Builder = secureBaseBuilder()
            .addInterceptor(BearerInterceptor(sessionState))

        fun defaultApi(baseUrl: String, builder: OkHttpClient.Builder): DefaultApi = ApiClient(
            baseUrl = baseUrl,
            okHttpClientBuilder = builder,
            serializerBuilder = Serializer.moshiBuilder,
        ).createService(DefaultApi::class.java)

        private fun secureBaseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
    }
}

private class PublicRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .removeHeader("Authorization")
            .removeHeader("Cookie")
            .removeHeader("X-CSRF-Token")
            .build(),
    )
}
