package com.danngalann.easymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
fun EasyMacrosApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                state.isRestoring -> Text("Restoring session", modifier = Modifier.padding(24.dp))
                state.isAuthenticated -> AuthenticatedShell(onLogout = authViewModel::logout)
                else -> AuthScreen(state, authViewModel::login, authViewModel::register)
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(onLogout: () -> Unit) {
    var showRecipes by rememberSaveable { mutableStateOf(false) }
    if (showRecipes) {
        RecipesScreen(onBack = { showRecipes = false })
    } else {
        TrackingScreen(onLogout = onLogout, onRecipes = { showRecipes = true })
    }
}

@Composable
private fun DashboardScreen(
    onLogout: () -> Unit,
    onRecipes: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daily nutrition", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = onRecipes) { Text("Recipes") }
                TextButton(onClick = onLogout) { Text("Log out") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = viewModel::previousDay, enabled = !state.isLoading) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
            }
            Text(state.date.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = viewModel::nextDay, enabled = !state.isLoading) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::retry) { Text("Try again") }
            }
            state.dashboard != null -> state.dashboard?.let { dashboard -> DashboardContent(dashboard) }
        }
    }
}

@Composable
private fun DashboardContent(dashboard: com.danngalann.easymacros.data.repository.DailyDashboard) {
    val consumed = dashboard.nutrients.associateBy { it.code }
    val targets = dashboard.goal?.nutrients?.associateBy { it.code }.orEmpty()
    val codes = (consumed.keys + targets.keys).sorted()
    Text("Calories ${dashboard.calories}", style = MaterialTheme.typography.titleLarge)
    if (codes.isEmpty()) {
        Text("No rations recorded for this day.")
    } else {
        codes.forEach { code ->
            val target = targets[code]?.amount
            Text("${code.label()}: ${consumed[code]?.amount ?: "0"} g${target?.let { " / $it g" } ?: ""}")
        }
    }
    dashboard.goal?.let { Text("Goals effective from ${it.effectiveLocalDate}") }
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

private fun String.label() = split('_').joinToString(" ") { word ->
    word.replaceFirstChar { character -> character.uppercase() }
}