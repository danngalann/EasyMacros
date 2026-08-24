package com.danngalann.easymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danngalann.easymacros.data.repository.RecipeDetail

@Composable
fun RecipesScreen(
    onIngredients: () -> Unit,
    viewModel: RecipesViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = state.editor
    val detail = state.detail
    when {
        editor != null -> RecipeEditor(editor, viewModel)
        detail != null -> RecipeDetailContent(detail, viewModel)
        else -> RecipeList(state, viewModel, onIngredients)
    }
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete this recipe?") },
            text = { Text("It will no longer be available for new rations. Existing logged rations keep their original nutrition.") },
            confirmButton = { Button(onClick = viewModel::deleteRecipe) { Text("Delete recipe") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Keep it") } },
        )
    }
    editor?.ingredientEditor?.let { ingredient ->
        IngredientEditorDialog(
            state = ingredient,
            onUpdate = viewModel::updateIngredientEditor,
            onReview = viewModel::reviewIngredient,
            onConfirm = viewModel::confirmIngredient,
            onDismiss = viewModel::closeIngredientEditor,
        )
    }
}

@Composable
private fun RecipeList(state: RecipesUiState, viewModel: RecipesViewModel, onIngredients: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenIntro("Recipes", "Build reusable recipes, then log portions from Today.", Modifier.weight(1f))
                IconButton(onClick = viewModel::createRecipe) { Icon(Icons.Default.Add, contentDescription = "Create recipe") }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                label = { Text("Search recipes") },
                supportingText = { Text("Search by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedButton(onClick = onIngredients, modifier = Modifier.fillMaxWidth()) {
                Text("Manage ingredients")
            }
        }
        when {
            state.isLoading && state.recipes.isEmpty() -> item { CircularProgressIndicator() }
            state.error != null -> item { ErrorState(state.error, viewModel::retry) }
            state.recipes.isEmpty() -> item {
                EmptyState(
                    title = if (state.query.isBlank()) "Create your first recipe" else "No recipes found",
                    helper = if (state.query.isBlank()) "Combine ingredients once, then log any serving size in a few taps." else "Try a different name or clear the search.",
                    actionLabel = if (state.query.isBlank()) "New recipe" else null,
                    onAction = if (state.query.isBlank()) viewModel::createRecipe else null,
                    icon = Icons.Default.RestaurantMenu,
                )
            }
            else -> {
                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                items(state.recipes, key = { it.id }) { recipe ->
                    SectionCard(modifier = Modifier.clickable { viewModel.openRecipe(recipe.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                                Text("${recipe.yieldGrams.recipeNumber()} g prepared · version ${recipe.versionNumber}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open recipe")
                        }
                    }
                }
            }
        }
        item { Button(onClick = viewModel::createRecipe, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(" New recipe") } }
    }
}

@Composable
private fun RecipeDetailContent(recipe: RecipeDetail, viewModel: RecipesViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::closeDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recipes") }
                ScreenIntro(recipe.name, "Recipe version ${recipe.versionNumber}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::editRecipe, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text(" Edit") }
                OutlinedButton(onClick = viewModel::requestDelete, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Text(" Delete") }
            }
        }
        item {
            SectionCard {
                Text("Batch overview", style = MaterialTheme.typography.titleLarge)
                Text("${recipe.calories.recipeNumber()} kcal", style = MaterialTheme.typography.headlineMedium)
                Text("${recipe.yieldGrams.recipeNumber()} g final yield", color = MaterialTheme.colorScheme.onSurfaceVariant)
                recipe.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
            }
        }
        item { Text("Ingredients", style = MaterialTheme.typography.titleLarge) }
        items(recipe.components, key = { it.ingredientId }) { component ->
            SectionCard {
                Row {
                    Text(component.ingredientName, modifier = Modifier.weight(1f))
                    Text("${component.quantityGrams.recipeNumber()} g")
                }
            }
        }
        item { Text("Nutrition for the batch", style = MaterialTheme.typography.titleLarge) }
        item {
            SectionCard {
                recipe.nutrients.forEach { nutrient ->
                    Row {
                        Text(nutrient.code.recipeLabel(), modifier = Modifier.weight(1f))
                        Text("${nutrient.amount.recipeNumber()} g")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeEditor(editor: RecipeEditorState, viewModel: RecipesViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::discardEditor, enabled = !editor.isSubmitting) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Discard changes") }
                ScreenIntro(
                    if (editor.recipeId == null) "New recipe" else "Edit recipe",
                    "Describe the prepared batch and add the ingredients used.",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            SectionCard {
                Text("Recipe details", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(editor.name, { viewModel.updateEditor(name = it) }, label = { Text("Recipe name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting)
                OutlinedTextField(editor.yieldGrams, { viewModel.updateEditor(yieldGrams = it) }, label = { Text("Final prepared yield") }, suffix = { Text("g") }, supportingText = { Text("Weigh the full cooked or prepared batch") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting)
                OutlinedTextField(editor.notes, { viewModel.updateEditor(notes = it) }, label = { Text("Notes") }, supportingText = { Text("Optional") }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting)
            }
        }
        item { Text("Ingredients", style = MaterialTheme.typography.titleLarge) }
        if (editor.components.isEmpty()) item { Text("Add at least one ingredient and enter the amount used.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(editor.components, key = { it.ingredientId }) { component ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(component.ingredientName, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeComponent(component.ingredientId) }, enabled = !editor.isSubmitting) { Icon(Icons.Default.Delete, contentDescription = "Remove ${component.ingredientName}") }
                }
                OutlinedTextField(component.quantityGrams, { viewModel.updateComponentQuantity(component.ingredientId, it) }, label = { Text("Amount used") }, suffix = { Text("g") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting)
            }
        }
        item {
            SectionCard {
                Text("Add an ingredient", style = MaterialTheme.typography.titleMedium)
                Text("Search your ingredients or create one from its nutrition label.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = viewModel::openIngredientEditor, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Create ingredient")
                }
                OutlinedTextField(editor.ingredientQuery, { viewModel.updateEditor(ingredientQuery = it) }, label = { Text("Search ingredients") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(editor.showHiddenIngredients, { viewModel.updateEditor(showHiddenIngredients = it) }, enabled = !editor.isSubmitting)
                    Text("Include hidden ingredients")
                }
                if (editor.isLoadingIngredients) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                editor.ingredients.filterNot { candidate -> editor.components.any { it.ingredientId == candidate.id } }.forEach { ingredient ->
                    OutlinedButton(onClick = { viewModel.addIngredient(ingredient) }, modifier = Modifier.fillMaxWidth(), enabled = !editor.isSubmitting) {
                        Icon(Icons.Default.Add, null)
                        Text(" Add ${ingredient.name}")
                    }
                }
                if (!editor.isLoadingIngredients && editor.ingredients.isEmpty()) {
                    Text("No ingredients match this search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        editor.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        editor.preview?.let { preview ->
            item {
                SectionCard {
                    Text("Batch preview", style = MaterialTheme.typography.titleLarge)
                    Text("${preview.calories.recipeNumber()} kcal", style = MaterialTheme.typography.headlineMedium)
                    preview.nutrients.forEach { Text("${it.code.recipeLabel()}: ${it.amount.recipeNumber()} g") }
                    Text("Calculated by EasyMacros from the ingredient values above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(onClick = viewModel::preview, enabled = !editor.isSubmitting, modifier = Modifier.fillMaxWidth()) { Text("Preview nutrition") }
        }
        item {
            Button(onClick = viewModel::save, enabled = !editor.isSubmitting, modifier = Modifier.fillMaxWidth()) { Text(if (editor.isSubmitting) "Saving…" else "Save recipe") }
        }
    }
}

@Composable
fun IngredientEditorDialog(
    state: IngredientEditorState,
    onUpdate: (String?, String?, String?, String?, String?) -> Unit,
    onReview: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = { Text(if (state.isReviewing) "Review ingredient" else "Create ingredient") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isReviewing) {
                    Text(state.name, style = MaterialTheme.typography.titleLarge)
                    state.brand.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Nutrition per 100 g", style = MaterialTheme.typography.titleMedium)
                    Text("Protein: ${state.protein.recipeNumber()} g")
                    Text("Carbohydrates: ${state.carbohydrates.recipeNumber()} g")
                    Text("Fat: ${state.fat.recipeNumber()} g")
                    Text(
                        "Confirming creates a private ingredient with these nutrition values.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Use the values per 100 g from the package or a trusted source.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(state.name, { onUpdate(it, null, null, null, null) }, label = { Text("Ingredient name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.brand, { onUpdate(null, it, null, null, null) }, label = { Text("Brand") }, supportingText = { Text("Optional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.protein, { onUpdate(null, null, it, null, null) }, label = { Text("Protein per 100 g") }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.carbohydrates, { onUpdate(null, null, null, it, null) }, label = { Text("Carbohydrates per 100 g") }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.fat, { onUpdate(null, null, null, null, it) }, label = { Text("Fat per 100 g") }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = if (state.isReviewing) onConfirm else onReview, enabled = !state.isSubmitting) {
                Text(if (state.isSubmitting) "Creating…" else if (state.isReviewing) "Create ingredient" else "Review values")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSubmitting) { Text("Cancel") }
        },
    )
}

private fun String.recipeLabel() = split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun String.recipeNumber() = toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: this