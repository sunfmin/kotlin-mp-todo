package com.example.todo.uicompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.health.HealthUiState
import com.example.todo.clientcore.health.HealthViewModel

/**
 * Slice 1 shared UI: renders the walking-skeleton health state. Shared by the
 * Android, iOS, and Desktop clients (ADR-0001). Later slices grow this into the
 * real navigation and screens; the Web client renders equivalent state with its
 * own Compose HTML views.
 */
@Composable
fun App(viewModel: HealthViewModel) {
    LaunchedEffect(viewModel) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Collaborative Todo", style = MaterialTheme.typography.headlineMedium)
                when (val s = state) {
                    is HealthUiState.Loading ->
                        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))

                    is HealthUiState.Connected ->
                        Text(
                            "Connected to ${s.service}\nDatabase: ${if (s.databaseConnected) "up" else "down"}",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 24.dp),
                        )

                    is HealthUiState.Failed ->
                        Text(
                            "Cannot reach the server:\n${s.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                }
            }
        }
    }
}
