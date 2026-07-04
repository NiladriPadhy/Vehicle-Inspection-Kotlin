package com.vsp.inspection.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import com.vsp.core.ui.components.VspTextField

@Composable
fun SignUpScreen(
    onSignedIn: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    VspScaffold(title = "Create account") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Register inspector", style = MaterialTheme.typography.headlineSmall)
            state.error?.let { ErrorBanner(message = it) }
            VspTextField(
                value = state.displayName,
                onValueChange = viewModel::onNameChange,
                label = "Full name",
                keyboardType = KeyboardType.Text,
            )
            VspTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email,
            )
            VspTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            VspTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = "Confirm password",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            PrimaryButton(
                text = if (state.isLoading) "Creating account…" else "Create account",
                onClick = viewModel::submit,
                enabled = !state.isLoading && state.displayName.isNotBlank() &&
                    state.email.isNotBlank() && state.password.isNotBlank(),
            )
            TextButton(onClick = onBackToLogin) {
                Text("Already have an account? Sign in")
            }
        }
    }
}
