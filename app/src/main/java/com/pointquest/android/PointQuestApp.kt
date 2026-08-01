package com.pointquest.android

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pointquest.android.core.ui.theme.PointQuestTheme

@Composable
fun PointQuestApp() {
    PointQuestTheme {
        Text(stringResource(R.string.app_name))
    }
}
