package com.danngalann.easymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danngalann.easymacros.data.repository.RecipeDetail

@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = state.editor
    val detail = state.detail
    when {
        editor != null -> RecipeEditor(
            editor = editor,
            onBack = viewModel::discardEditor,
            onUpdate = viewModel::updateEditor,
            onAddIngredient = viewModel::addIngredient,
            onUpdateComponent = viewModel::updateComponentQuantity,
            onRemoveComponent = viewModel::removeComponent,
            onPreview = viewModel::preview,
            onSave = viewModel::save,
        )
        detail != null -> RecipeDetailScreen(
            recipe = detail,
            onBack = viewModel::closeDetail,
            onEdit = viewModel::editRecipe,
            onDelete = viewModel::requestDelete,
        )
        else -> RecipeListScreen(
            state = state,
            onBack = onBack,
            onSearch = viewModel::search,
            onCreate = viewModel::createRecipe,
            onOpen = viewModel::openRecipe,
            onRetry = viewModel::retry,
        )
    }
    state.pendingDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete recipe?") },
            text = { Text("This recipe will no longer be available for logging. Existing ration snapshots remain unchanged.") },
            confirmButton = { TextButton(onClick = viewModel::deleteRecipe) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecipeListScreen(
    state: RecipesUiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to dashboard") }
            Text("Recipes", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onCreate) { Icon(Icons.Default.Add, "Create recipe") }
        }
        OutlinedTextField(state.query, onSearch, label = { Text("Search recipes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Try again") }
            }
            state.recipes.isEmpty() -> Text("No recipes yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.recipes, key = { it.id }) { recipe ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(recipe.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                        Text("${recipe.yieldGrams} g yield, version ${recipe.versionNumber}")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeDetailScreen(recipe: RecipeDetail, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to recipes") }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit recipe") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete recipe") }
            }
        }
        Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
        Text("Version ${recipe.versionNumber} | ${recipe.yieldGrams} g yield")
        recipe.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
        Text("Batch calories ${recipe.calories}", style = MaterialTheme.typography.titleLarge)
        Text("Ingredients", style = MaterialTheme.typography.titleMedium)
        recipe.components.forEach { Text("${it.ingredientName}: ${it.quantityGrams} g") }
        Text("Nutrients", style = MaterialTheme.typography.titleMedium)
        recipe.nutrients.forEach { nutrient -> Text("${nutrient.code.displayLabel()}: ${nutrient.amount} g") }
    }
}

@Composable
private fun RecipeEditor(
    editor: RecipeEditorState,
    onBack: () -> Unit,
    onUpdate: (String?, String?, String?, String?, Boolean?) -> Unit,
    onAddIngredient: (com.danngalann.easymacros.data.repository.IngredientSummary) -> Unit,
    onUpdateComponent: (String, String) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack, enabled = !editor.isSubmitting) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Discard changes") }
                Text(if (editor.recipeId == null) "New recipe" else "Edit recipe", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { OutlinedTextField(editor.name, { onUpdate(it, null, null, null, null) }, label = { Text("Recipe name") }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) }
        item { OutlinedTextField(editor.yieldGrams, { onUpdate(null, null, it, null, null) }, label = { Text("Final yield (g)") }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) }
        item { OutlinedTextField(editor.notes, { onUpdate(null, it, null, null, null) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) }
        item { Text("Components", style = MaterialTheme.typography.titleMedium) }
        items(editor.components, key = { it.ingredientId }) { component ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(component.ingredientName, modifier = Modifier.weight(1f).padding(top = 16.dp))
                OutlinedTextField(component.quantityGrams, { onUpdateComponent(component.ingredientId, it) }, label = { Text("g") }, modifier = Modifier.weight(1f), enabled = !editor.isSubmitting)
                IconButton(onClick = { onRemoveComponent(component.ingredientId) }, enabled = !editor.isSubmitting) { Icon(Icons.Default.Delete, "Remove ${component.ingredientName}") }
            }
        }
        item { Text("Add ingredient", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(editor.ingredientQuery, { onUpdate(null, null, null, it, null) }, label = { Text("Search ingredients") }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(editor.showHiddenIngredients, { onUpdate(null, null, null, null, it) }, enabled = !editor.isSubmitting)
                Text("Include hidden ingredients")
            }
        }
        if (editor.isLoadingIngredients) item { CircularProgressIndicator() }
        items(editor.ingredients.filterNot { candidate -> editor.components.any { it.ingredientId == candidate.id } }, key = { it.id }) { ingredient ->
            TextButton(onClick = { onAddIngredient(ingredient) }, enabled = !editor.isSubmitting) { Text("Add ${ingredient.name}") }
        }
        editor.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        editor.preview?.let { preview ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server preview", style = MaterialTheme.typography.titleMedium)
                    Text("Calories ${preview.calories}")
                    preview.nutrients.forEach { nutrient -> Text("${nutrient.code.displayLabel()}: ${nutrient.amount} g") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview, enabled = !editor.isSubmitting) { Text("Preview totals") }
                Button(onClick = onSave, enabled = !editor.isSubmitting) { Text(if (editor.isSubmitting) "Saving" else "Save recipe") }
            }
        }
    }
}

private fun String.displayLabel() = split('_').joinToString(" ") { word ->
    word.replaceFirstChar { character -> character.uppercase() }
}