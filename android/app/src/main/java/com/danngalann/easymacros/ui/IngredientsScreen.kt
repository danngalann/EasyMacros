package com.danngalann.easymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun IngredientsScreen(
    onBack: () -> Unit,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recipes") }
                ScreenIntro("Ingredients", "Build your reusable nutrition library before creating recipes.", Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = viewModel::openEditor, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" New ingredient")
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                label = { Text("Search ingredients") },
                supportingText = { Text("Search your private ingredient library") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            state.isLoading && state.ingredients.isEmpty() -> item { CircularProgressIndicator() }
            state.error != null -> item { ErrorState(state.error.orEmpty(), viewModel::retry) }
            state.ingredients.isEmpty() -> item {
                EmptyState(
                    title = if (state.query.isBlank()) "Create your first ingredient" else "No ingredients found",
                    helper = if (state.query.isBlank()) "Add nutrition values per 100 g now, then reuse the ingredient in any recipe." else "Try another name or clear the search.",
                    actionLabel = if (state.query.isBlank()) "New ingredient" else null,
                    onAction = if (state.query.isBlank()) viewModel::openEditor else null,
                    icon = Icons.Default.Inventory2,
                )
            }
            else -> {
                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                items(state.ingredients, key = { it.id }) { ingredient ->
                    SectionCard {
                        Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
                        Text("Available for recipes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    state.editor?.let { editor ->
        IngredientEditorDialog(
            state = editor,
            onUpdate = viewModel::updateEditor,
            onReview = viewModel::review,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::closeEditor,
        )
    }
}