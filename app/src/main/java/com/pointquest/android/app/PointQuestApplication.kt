package com.pointquest.android.app

import android.app.Application
import com.pointquest.android.core.auth.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PointQuestApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            if (container.sessionState.status.value == SessionStatus.Restoring) {
                container.authRepository.restore()
            }
        }
    }
}
