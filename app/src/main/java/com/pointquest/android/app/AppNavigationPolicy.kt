package com.pointquest.android.app

import com.pointquest.android.core.auth.SessionStatus

internal data class RootNavigationRequest(
    val target: AppRoute,
    val clearBackStack: Boolean = true,
    val launchSingleTop: Boolean = true,
)

internal data class TopLevelNavigationRequest(
    val target: AppRoute,
    val anchor: AppRoute = AppRoute.Home,
    val launchSingleTop: Boolean = true,
    val saveState: Boolean = true,
    val restoreState: Boolean = true,
)

internal object AppNavigationPolicy {
    private val topLevelRoutes = setOf(
        AppRoute.Home,
        AppRoute.Practice,
        AppRoute.Shop,
        AppRoute.Profile,
    )

    fun rootRequest(
        sessionStatus: SessionStatus,
        alreadyAtTarget: Boolean,
    ): RootNavigationRequest? = if (alreadyAtTarget) {
        null
    } else {
        RootNavigationRequest(RootDestinationResolver().resolve(sessionStatus))
    }

    fun topLevelRequest(target: AppRoute): TopLevelNavigationRequest {
        require(target in topLevelRoutes) { "Route is not a top-level destination: $target" }
        return TopLevelNavigationRequest(target)
    }

    fun showsBottomBar(route: AppRoute): Boolean = route in topLevelRoutes
}
