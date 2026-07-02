package com.example.todo.uicompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.AuthState
import com.example.todo.clientcore.auth.AuthViewModel

/**
 * Slice 2 shared UI (ADR-0001): passwordless sign-in flow (email -> code -> home),
 * shared by Android, iOS, and Desktop. Web renders the equivalent with Compose HTML.
 */
@Composable
fun App(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsState()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Collaborative Todo", style = MaterialTheme.typography.headlineMedium)
                Column(modifier = Modifier.widthIn(max = 360.dp).padding(top = 24.dp)) {
                    when (state.phase) {
                        AuthPhase.EMAIL -> EmailStep(state) { viewModel.submitEmail(it) }
                        AuthPhase.CODE -> CodeStep(
                            state,
                            onSubmit = { viewModel.submitCode(it) },
                            onChangeEmail = { viewModel.changeEmail() },
                        )
                        AuthPhase.AUTHENTICATED -> Home(state.email) { viewModel.signOut() }
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailStep(state: AuthState, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf(state.email) }
    Text("Sign in with your email", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Button(
        onClick = { onSubmit(email.trim()) },
        enabled = !state.loading && email.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (state.loading) CircularProgressIndicator() else Text("Send code")
    }
}

@Composable
private fun CodeStep(state: AuthState, onSubmit: (String) -> Unit, onChangeEmail: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Text("Enter the code sent to ${state.email}", style = MaterialTheme.typography.titleMedium)
    state.devCode?.let {
        Text("Dev code: $it", modifier = Modifier.padding(top = 4.dp))
    }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(6) },
        label = { Text("6-digit code") },
        singleLine = true,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Button(
        onClick = { onSubmit(code) },
        enabled = !state.loading && code.length == 6,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (state.loading) CircularProgressIndicator() else Text("Verify")
    }
    TextButton(onClick = onChangeEmail, modifier = Modifier.padding(top = 4.dp)) {
        Text("Use a different email")
    }
}

@Composable
private fun Home(email: String, onSignOut: () -> Unit) {
    Text(
        "Signed in as $email",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Your lists will appear here.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Sign out")
    }
}
