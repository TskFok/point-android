package com.pointquest.android.app

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteSerializationTest {
    @Test
    fun everyRootAndParameterizedRouteRoundTripsThroughSerialization() {
        val routes = listOf(
            AppRoute.Splash,
            AppRoute.Login,
            AppRoute.Register,
            AppRoute.Home,
            AppRoute.Practice,
            AppRoute.Preview,
            AppRoute.Shop,
            AppRoute.Profile,
            AppRoute.Question(PracticeMode.FIRST, "question-1"),
            AppRoute.Question(PracticeMode.WRONG, null),
            AppRoute.WrongQuestions,
            AppRoute.ProductDetail("product-1"),
            AppRoute.Orders,
            AppRoute.OrderDetail("order-1"),
            AppRoute.Points,
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(AppRoute.serializer(), route)

            assertEquals(route, Json.decodeFromString(AppRoute.serializer(), encoded))
        }
    }
}
