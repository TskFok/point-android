package com.pointquest.android.data.products

import com.pointquest.android.BuildConfig
import java.util.UUID
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ProductImageUrlFactory(
    imageBaseUrl: String = BuildConfig.IMAGE_BASE_URL,
) {
    private val baseUrl: HttpUrl? = imageBaseUrl
        .takeIf(ROOT_HTTP_ORIGIN::matches)
        ?.toHttpUrlOrNull()
        ?.takeIf(::isRootHttpOrigin)

    fun urlOrNull(imageKey: String): String? {
        val match = PRODUCT_IMAGE_KEY.matchEntire(imageKey) ?: return null
        val uuid = UUID.fromString(match.groupValues[1])
        if (uuid.toString() != match.groupValues[1]) return null

        return baseUrl
            ?.resolve("uploads/$imageKey")
            ?.takeIf { it.encodedPath == "/uploads/$imageKey" && it.query == null && it.fragment == null }
            ?.toString()
    }

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
