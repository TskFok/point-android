package com.pointquest.android.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun PracticeHubScreen(
    onFirstPractice: () -> Unit,
    onWrongQuestions: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(
        title = stringResource(R.string.practice_title),
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PointPrimaryButton(stringResource(R.string.practice_first_action), onFirstPractice)
            PointPrimaryButton(stringResource(R.string.practice_wrong_action), onWrongQuestions)
        }
    }
}
