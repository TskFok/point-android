package com.pointquest.android.data.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductImageUrlFactoryTest {
    private val key = "products/550e8400-e29b-41d4-a716-446655440000.png"

    @Test
    fun acceptsOnlyCanonicalUuidProductImageKeys() {
        val factory = ProductImageUrlFactory("http://10.0.2.2:3000/")

        assertEquals(
            "http://10.0.2.2:3000/uploads/products/550e8400-e29b-41d4-a716-446655440000.png",
            factory.urlOrNull(key),
        )
    }

    @Test
    fun rejectsAbsoluteTraversalEncodedOrNonCanonicalImageKeys() {
        val factory = ProductImageUrlFactory("https://images.example.test/")
        val invalidKeys = listOf(
            "https://evil.test/a.png",
            "/products/550e8400-e29b-41d4-a716-446655440000.png",
            "products/../secret.png",
            "products/%2e%2e/secret.png",
            "products\\550e8400-e29b-41d4-a716-446655440000.png",
            "products/550e8400-e29b-41d4-a716-446655440000.png?size=small",
            "products/550e8400-e29b-41d4-a716-446655440000.png#preview",
            "products/550E8400-E29B-41D4-A716-446655440000.png",
            "products/550e8400-e29b-41d4-a716-446655440000.PNG",
            "products/550e8400-e29b-41d4-a716-446655440000.png/extra",
            "seed/products/demo.png",
            "products/550e8400-e29b-41d4-a716-44665544000.png",
        )

        invalidKeys.forEach { invalidKey ->
            assertNull("Expected image key to be rejected: $invalidKey", factory.urlOrNull(invalidKey))
        }
    }

    @Test
    fun rejectsImageBaseUrlsThatAreNotRootHttpOriginsWithTrailingSlash() {
        val invalidBaseUrls = listOf(
            "",
            "ftp://images.example.test/",
            "https://images.example.test",
            "https://images.example.test/api/",
            "https://images.example.test/%2e/",
            "https://images.example.test/?token=secret",
            "https://images.example.test/#fragment",
            "//images.example.test/",
        )

        invalidBaseUrls.forEach { baseUrl ->
            assertNull("Expected base URL to be rejected: $baseUrl", ProductImageUrlFactory(baseUrl).urlOrNull(key))
        }
    }
}
