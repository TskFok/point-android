package com.pointquest.android.app

import kotlinx.serialization.Serializable

@Serializable
enum class PracticeMode {
    FIRST,
    WRONG,
}

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Splash : AppRoute

    @Serializable
    data object Login : AppRoute

    @Serializable
    data object Register : AppRoute

    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Practice : AppRoute

    @Serializable
    data object Preview : AppRoute

    @Serializable
    data object Shop : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data class Question(val mode: PracticeMode, val questionId: String?) : AppRoute

    @Serializable
    data object WrongQuestions : AppRoute

    @Serializable
    data class ProductDetail(val productId: String) : AppRoute

    @Serializable
    data object Orders : AppRoute

    @Serializable
    data class OrderDetail(val orderId: String) : AppRoute

    @Serializable
    data object Points : AppRoute
}
