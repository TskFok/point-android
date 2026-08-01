package com.pointquest.android.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.register_title), modifier = modifier) { padding ->
        AuthFormContainer(padding) {
            Text(
                text = stringResource(R.string.register_welcome),
                style = MaterialTheme.typography.headlineSmall,
            )
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            AuthTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = stringResource(R.string.auth_username),
                error = state.usernameError?.asString(),
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.testTag("register_username"),
            )
            AuthTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_password),
                error = state.passwordError?.asString(),
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                password = true,
                modifier = Modifier.testTag("register_password"),
            )
            AuthTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = stringResource(R.string.auth_confirm_password),
                error = state.confirmPasswordError?.asString(),
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onRegister() }),
                password = true,
                modifier = Modifier.testTag("register_confirm_password"),
            )
            Text(
                text = stringResource(R.string.auth_password_helper),
                style = MaterialTheme.typography.bodyMedium,
            )
            PointPrimaryButton(
                text = if (state.submitting) {
                    stringResource(R.string.auth_submitting)
                } else {
                    stringResource(R.string.auth_register_action)
                },
                onClick = onRegister,
                enabled = !state.submitting,
                modifier = Modifier.testTag("register_submit"),
            )
            TextButton(
                onClick = onBackToLogin,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(stringResource(R.string.auth_back_to_login))
            }
        }
    }
}
