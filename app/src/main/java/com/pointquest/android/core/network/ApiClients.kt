package com.pointquest.android.core.network

import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.infrastructure.ApiClient
import com.pointquest.android.generated.infrastructure.Serializer
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class ApiClients(
    baseUrl: String,
    sessionState: SessionState,
    hostProvider: () -> String = { baseUrl },
) {
    val publicHttpClient: OkHttpClient = publicBuilder()
        .addInterceptor(RemoteHostInterceptor(hostProvider))
        .build()
    val protectedHttpClient: OkHttpClient = protectedBuilder(sessionState)
        .addInterceptor(RemoteHostInterceptor(hostProvider))
        .build()
    val publicApi: DefaultApi = defaultApi(baseUrl, publicHttpClient)
    val protectedApi: DefaultApi = defaultApi(baseUrl, protectedHttpClient)

    companion object {
        fun publicBuilder(): OkHttpClient.Builder = secureBaseBuilder()
            .addInterceptor(PublicRequestInterceptor())

        fun protectedBuilder(sessionState: SessionState): OkHttpClient.Builder = secureBaseBuilder()
            .addInterceptor(BearerInterceptor(sessionState))

        fun defaultApi(baseUrl: String, builder: OkHttpClient.Builder): DefaultApi =
            defaultApi(baseUrl, builder.build())

        fun defaultApi(baseUrl: String, client: OkHttpClient): DefaultApi = ApiClient(
            baseUrl = baseUrl,
            serializerBuilder = generatedSerializerBuilder,
            callFactory = client,
            converterFactories = listOf(
                GeneratedEnumQueryConverterFactory,
                ScalarsConverterFactory.create(),
                MoshiConverterFactory.create(generatedSerializerBuilder.build()),
            ),
        ).createService(DefaultApi::class.java)

        private val generatedSerializerBuilder = Serializer.moshiBuilder
            .add(UnknownDefaultEnumJsonAdapterFactory)

        private fun secureBaseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
    }
}

private object GeneratedEnumQueryConverterFactory : Converter.Factory() {
    override fun stringConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<*, String>? {
        val rawType = getRawType(type)
        if (!rawType.isEnum || !rawType.name.startsWith("com.pointquest.android.generated.")) {
            return null
        }
        val valueField = rawType.declaredFields.firstOrNull { it.name == "value" && it.type == String::class.java }
            ?: return null
        valueField.isAccessible = true
        return Converter<Any, String> { value -> valueField.get(value) as String }
    }
}

private class RemoteHostInterceptor(
    private val hostProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val origin = hostProvider().toHttpUrl()
        val request = chain.request()
        return chain.proceed(
            request.newBuilder()
                .url(
                    request.url.newBuilder()
                        .scheme(origin.scheme)
                        .host(origin.host)
                        .port(origin.port)
                        .build(),
                )
                .build(),
        )
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
