package com.danngalann.easymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EasyMacrosApp(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                state.isRestoring -> Text("Restoring session", modifier = Modifier.padding(24.dp))
                state.isAuthenticated -> HomeScreen(viewModel::logout)
                else -> AuthScreen(state, viewModel::login, viewModel::register)
            }
        }
    }
}

@Composable
private fun HomeScreen(onLogout: () -> Unit) = Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("EasyMacros", style = MaterialTheme.typography.headlineMedium)
    Text("Your nutrition workspace is ready.")
    Button(onClick = onLogout) { Text("Log out") }
}

@Composable
private fun AuthScreen(state: AuthUiState, onLogin: (String, String) -> Unit, onRegister: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (register) "Create account" else "Sign in", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading)
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading)
        TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.isLoading) { Text(if (passwordVisible) "Hide password" else "Show password") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (register) onRegister(username, password) else onLogin(username, password) }, enabled = username.isNotBlank() && password.isNotBlank() && !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text(if (state.isLoading) "Working" else if (register) "Register" else "Sign in") }
        TextButton(onClick = { register = !register }, enabled = !state.isLoading) { Text(if (register) "Already have an account? Sign in" else "Create an account") }
    }
}