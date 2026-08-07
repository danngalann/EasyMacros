package com.danngalann.easymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.NutrientAmount
import com.danngalann.easymacros.data.repository.RationDetail
import com.danngalann.easymacros.data.repository.RationSummary
import java.math.BigDecimal

@Composable
fun TrackingScreen(
    onLogout: () -> Unit,
    onRecipes: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("EasyMacros", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onLogout) { Text("Log out") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.navigate(TrackingDestination.Dashboard) }) { Text("Today") }
            TextButton(onClick = viewModel::openLog) { Text("Log") }
            TextButton(onClick = { viewModel.navigate(TrackingDestination.Goals) }) { Text("Goals") }
            TextButton(onClick = { viewModel.navigate(TrackingDestination.History) }) { Text("History") }
            TextButton(onClick = onRecipes) { Text("Recipes") }
            TextButton(onClick = { viewModel.navigate(TrackingDestination.Settings) }) { Text("Settings") }
        }
        when (state.destination) {
            TrackingDestination.Dashboard -> DashboardTrackingContent(state, viewModel)
            TrackingDestination.Log -> LogRationContent(state, viewModel)
            TrackingDestination.Goals -> GoalEditorContent(state, viewModel)
            TrackingDestination.History -> HistoryContent(state, viewModel)
            TrackingDestination.Settings -> TimezoneContent(state, viewModel)
        }
    }
    state.detail?.let { detail -> RationDetailDialog(detail, viewModel::closeRationDetail, viewModel::requestDelete) }
    state.pendingDelete?.let { ration ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete ration?") },
            text = { Text("This removes the logged snapshot from totals and history. To correct it, log a new ration after deletion.") },
            confirmButton = { Button(onClick = viewModel::deleteRation) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Keep") } },
        )
    }
}

@Composable
private fun DashboardTrackingContent(state: TrackingUiState, viewModel: TrackingViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = viewModel::previousDay, enabled = !state.isLoadingDashboard) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Text(state.date.toString(), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = viewModel::nextDay, enabled = !state.isLoadingDashboard) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
    when {
        state.isLoadingDashboard -> CircularProgressIndicator()
        state.dashboardError != null -> {
            Text(state.dashboardError, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::refreshDashboard) { Text("Try again") }
        }
        state.dashboard != null -> DashboardTotals(state.dashboard)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::refreshDashboard) { Text("Refresh") }
        Button(onClick = viewModel::openLog) { Text("Log ration") }
    }
    Text("Rations", style = MaterialTheme.typography.titleMedium)
    if (state.dayRations.isEmpty() && !state.isLoadingDashboard) Text("No rations recorded for this day.")
    state.dayRations.forEach { ration -> RationRow(ration, { viewModel.openRation(ration.id) }) }
}

@Composable
private fun DashboardTotals(dashboard: DailyDashboard) {
    val consumed = dashboard.nutrients.associateBy { it.code }
    val goals = dashboard.goal?.nutrients?.associateBy { it.code }.orEmpty()
    Text("Calories ${dashboard.calories}", style = MaterialTheme.typography.titleLarge)
    listOf("protein", "carbohydrates", "fat").forEach { code ->
        MacroProgress(code, consumed[code]?.amount ?: "0", goals[code]?.amount)
    }
    dashboard.goal?.let { Text("Goals effective from ${it.effectiveLocalDate}") }
}

@Composable
private fun MacroProgress(code: String, amount: String, target: String?) {
    val consumed = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val goal = target?.toBigDecimalOrNull()
    val progress = goal?.takeIf { it.signum() > 0 }?.let { consumed.divide(it, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) }
    val remaining = goal?.subtract(consumed)
    Text("${code.displayName()}: $amount g${target?.let { " / $it g" } ?: ""}")
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$code progress" })
        val isOverGoal = (remaining?.signum() ?: 0) < 0
        Text(if (isOverGoal) "${remaining?.abs()} g over goal" else "${remaining ?: BigDecimal.ZERO} g remaining")
    }
}

@Composable
private fun LogRationContent(state: TrackingUiState, viewModel: TrackingViewModel) {
    val draft = state.rationDraft ?: return
    Text("Log ration", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(draft.recipeQuery, { viewModel.updateDraft(query = it) }, label = { Text("Search recipes") }, modifier = Modifier.fillMaxWidth())
    if (draft.isLoadingRecipes) CircularProgressIndicator()
    draft.recipes.forEach { recipe ->
        TextButton(onClick = { viewModel.selectRecipe(recipe) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (draft.selectedRecipe?.id == recipe.id) "Selected: ${recipe.name}" else recipe.name)
        }
    }
    OutlinedTextField(draft.grams, { viewModel.updateDraft(grams = it) }, label = { Text("Grams") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(draft.consumedAt, { viewModel.updateDraft(consumedAt = it) }, label = { Text("Exact time (ISO-8601)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(draft.note, { viewModel.updateDraft(note = it) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = viewModel::previewRation, enabled = !draft.isSubmitting) { Text("Review contribution") }
    draft.preview?.let { preview ->
        Text("${preview.recipeName}: ${preview.calories} calories", style = MaterialTheme.typography.titleMedium)
        preview.nutrients.forEach { nutrient -> Text("${nutrient.code.displayName()}: ${nutrient.amount} g") }
        Button(onClick = viewModel::confirmRation, enabled = !draft.isSubmitting) { Text("Confirm ration") }
    }
}

@Composable
private fun GoalEditorContent(state: TrackingUiState, viewModel: TrackingViewModel) {
    val editor = state.goalEditor
    Text("Goals", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(editor.effectiveDate, { viewModel.updateGoal(effectiveDate = it) }, label = { Text("Effective date") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(editor.protein, { viewModel.updateGoal(protein = it) }, label = { Text("Protein grams") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(editor.carbohydrates, { viewModel.updateGoal(carbohydrates = it) }, label = { Text("Carbohydrates grams") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(editor.fat, { viewModel.updateGoal(fat = it) }, label = { Text("Fat grams") }, modifier = Modifier.fillMaxWidth())
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = viewModel::saveGoal, enabled = !editor.isSubmitting) { Text("Save goals") }
}

@Composable
private fun HistoryContent(state: TrackingUiState, viewModel: TrackingViewModel) {
    Text("History", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(state.historyQuery, { viewModel.updateHistory(query = it) }, label = { Text("Search rations") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.historyFrom, { viewModel.updateHistory(from = it) }, label = { Text("From date") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.historyTo, { viewModel.updateHistory(to = it) }, label = { Text("To date") }, modifier = Modifier.fillMaxWidth())
    state.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.isLoadingHistory) CircularProgressIndicator()
    if (state.history.isEmpty() && !state.isLoadingHistory) Text("No rations match these filters.")
    state.history.forEach { ration -> RationRow(ration, { viewModel.openRation(ration.id) }) }
    state.historyCursor?.let { Button(onClick = viewModel::loadMoreHistory, enabled = !state.isLoadingHistory) { Text("Load more") } }
}

@Composable
private fun RationRow(ration: RationSummary, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${ration.recipeName} - ${ration.consumedGrams} g")
            Text("${ration.consumedLocalDate} ${ration.consumedAt}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RationDetailDialog(detail: RationDetail, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.recipeName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${detail.consumedGrams} g at ${detail.consumedAt}")
                Text("Day: ${detail.consumedLocalDate} (${detail.timezone})")
                Text("Calories ${detail.calories}")
                detail.nutrients.forEach { nutrient -> Text("${nutrient.code.displayName()}: ${nutrient.amount} g") }
                detail.note?.let { Text(it) }
            }
        },
        confirmButton = { Button(onClick = onDelete) { Text("Delete and re-log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TimezoneContent(state: TrackingUiState, viewModel: TrackingViewModel) {
    var timezone by rememberSaveable(state.timezone) { mutableStateOf(state.timezone) }
    Text("Timezone", style = MaterialTheme.typography.titleLarge)
    Text("Changing timezone reclassifies existing rations into the new local days. Nutrient snapshots remain unchanged.")
    OutlinedTextField(timezone, { timezone = it }, label = { Text("IANA timezone") }, modifier = Modifier.fillMaxWidth())
    state.timezoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { viewModel.updateTimezone(timezone) }, enabled = !state.isUpdatingTimezone) { Text("Save timezone") }
}

private fun String.displayName() = split('_').joinToString(" ") { it.replaceFirstChar { character -> character.uppercase() } }