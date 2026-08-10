package com.pointquest.android.data.products

import com.pointquest.android.BuildConfig
import java.util.UUID
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ProductImageUrlFactory private constructor(
    private val imageBaseUrlProvider: () -> String,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) {
    constructor(imageBaseUrl: String = BuildConfig.IMAGE_BASE_URL) : this({ imageBaseUrl }, Unit)

    constructor(imageBaseUrlProvider: () -> String) : this(imageBaseUrlProvider, Unit)

    fun urlOrNull(imageKey: String): String? {
        val match = PRODUCT_IMAGE_KEY.matchEntire(imageKey) ?: return null
        val uuid = UUID.fromString(match.groupValues[1])
        if (uuid.toString() != match.groupValues[1]) return null

        return currentBaseUrlOrNull()
            ?.resolve("uploads/$imageKey")
            ?.takeIf { it.encodedPath == "/uploads/$imageKey" && it.query == null && it.fragment == null }
            ?.toString()
    }

    private fun currentBaseUrlOrNull(): HttpUrl? = imageBaseUrlProvider()
        .takeIf(ROOT_HTTP_ORIGIN::matches)
        ?.toHttpUrlOrNull()
        ?.takeIf(::isRootHttpOrigin)

    private fun isRootHttpOrigin(url: HttpUrl): Boolean =
        url.scheme in setOf("http", "https") &&
            url.encodedPath == "/" &&
            url.query == null &&
            url.fragment == null &&
            url.username.isEmpty() &&
            url.password.isEmpty()

    private companion object {
        val ROOT_HTTP_ORIGIN = Regex("^https?://[^/?#]+/$", RegexOption.IGNORE_CASE)
        val PRODUCT_IMAGE_KEY = Regex(
            "^products/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(?:jpg|png|webp)$",
        )
    }
}
