package com.pointquest.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.pointquest.android.app.AppContainer
import com.pointquest.android.app.AppNavHost
import com.pointquest.android.core.ui.theme.PointQuestTheme

@Composable
fun PointQuestApp(container: AppContainer) {
    val sessionStatus by container.sessionState.status.collectAsStateWithLifecycle()

    PointQuestTheme {
        AppNavHost(sessionStatus)
    }
}
