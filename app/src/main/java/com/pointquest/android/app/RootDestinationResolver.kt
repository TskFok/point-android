package com.pointquest.android.app

import com.pointquest.android.core.auth.SessionStatus

class RootDestinationResolver {
    fun resolve(sessionStatus: SessionStatus): AppRoute = when (sessionStatus) {
        SessionStatus.Restoring -> AppRoute.Splash
        SessionStatus.SignedOut -> AppRoute.Login
        is SessionStatus.SignedIn -> AppRoute.Home
    }
}
