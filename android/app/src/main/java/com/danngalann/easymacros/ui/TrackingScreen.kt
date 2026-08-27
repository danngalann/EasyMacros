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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.RationDetail
import com.danngalann.easymacros.data.repository.RationSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TrackingScreen(
    onRecipes: () -> Unit,
    viewModel: TrackingViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.destination) {
            TrackingDestination.Dashboard -> Dashboard(state, viewModel, onRecipes)
            TrackingDestination.Log -> LogRation(state, viewModel, onRecipes)
            TrackingDestination.Goals -> Goals(state, viewModel)
            TrackingDestination.History -> History(state, viewModel)
            TrackingDestination.Settings -> Settings(state, viewModel)
        }
    }
    state.detail?.let { detail -> RationDetailDialog(detail, viewModel::closeRationDetail, viewModel::requestDelete) }
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete this ration?") },
            text = { Text("It will be removed from totals and history. Corrections are made by logging a new ration.") },
            confirmButton = { Button(onClick = viewModel::deleteRation) { Text("Delete ration") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Dashboard(state: TrackingUiState, viewModel: TrackingViewModel, onRecipes: () -> Unit) {
    ScreenIntro("Today", "See what you’ve logged and what remains for the day.")
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousDay, enabled = !state.isLoadingDashboard) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.date.localized(), style = MaterialTheme.typography.titleMedium)
                if (state.date == LocalDate.now()) Text("Today", color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = viewModel::nextDay, enabled = !state.isLoadingDashboard) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
            }
        }
    }
    when {
        state.isLoadingDashboard && state.dashboard == null -> CircularProgressIndicator()
        state.dashboardError != null -> ErrorState(state.dashboardError, viewModel::refreshDashboard)
        state.dashboard != null -> {
            if (state.isLoadingDashboard) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            DashboardTotals(state.dashboard)
            if (state.dashboard.goal == null) {
                EmptyState(
                    title = "Set your daily targets",
                    helper = "Goals turn today’s totals into simple progress you can act on.",
                    actionLabel = "Set goals",
                    onAction = { viewModel.navigate(TrackingDestination.Goals) },
                    icon = Icons.Default.CheckCircle,
                )
            }
            Text("Rations", style = MaterialTheme.typography.titleLarge)
            Text("Everything logged for this day appears here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.dayRations.isEmpty()) {
                EmptyState(
                    title = "Nothing logged yet",
                    helper = "Create a recipe first, then log the amount you ate.",
                    actionLabel = "Browse recipes",
                    onAction = onRecipes,
                    icon = Icons.Default.Restaurant,
                )
            } else {
                state.dayRations.forEach { ration -> RationRow(ration) { viewModel.openRation(ration.id) } }
            }
        }
    }
}

@Composable
private fun DashboardTotals(dashboard: DailyDashboard) {
    SectionCard {
        Text("${dashboard.calories.toDisplayNumber()} kcal", style = MaterialTheme.typography.headlineMedium)
        Text("Consumed", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val consumed = dashboard.nutrients.associateBy { it.code }
        val goals = dashboard.goal?.nutrients?.associateBy { it.code }.orEmpty()
        listOf("protein", "carbohydrates", "fat").forEach { code ->
            val amount = consumed[code]?.amount ?: "0"
            val target = goals[code]?.amount
            val value = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val goal = target?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            Text(code.displayLabel(), fontWeight = FontWeight.SemiBold)
            Text("${amount.toDisplayNumber()} g${target?.let { " of ${it.toDisplayNumber()} g" } ?: ""}")
            goal?.let {
                val progress = value.divide(it, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$code progress" },
                )
                val remaining = it.subtract(value)
                Text(
                    if (remaining.signum() < 0) "${remaining.abs().toPlainString().toDisplayNumber()} g over target"
                    else "${remaining.toPlainString().toDisplayNumber()} g remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        dashboard.goal?.let { Text("Targets active since ${it.effectiveLocalDate.localizedDate()}", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun LogRation(state: TrackingUiState, viewModel: TrackingViewModel, onRecipes: () -> Unit) {
    val draft = state.rationDraft ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.navigate(TrackingDestination.Dashboard) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Today")
        }
        ScreenIntro("Log a ration", "Choose a recipe, optionally enter a portion weight, then review before saving.", Modifier.weight(1f))
    }
    Text("1. Choose a recipe", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = draft.recipeQuery,
        onValueChange = { viewModel.updateDraft(query = it) },
        label = { Text("Search recipes") },
        supportingText = { Text("Search by recipe name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.isLoadingRecipes) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    if (!draft.isLoadingRecipes && draft.recipes.isEmpty()) {
        EmptyState("No recipes found", "Recipes define the nutrition used for your ration.", "Create a recipe", onRecipes)
    } else {
        draft.recipes.forEach { recipe ->
            val selected = draft.selectedRecipe?.id == recipe.id
            OutlinedButton(onClick = { viewModel.selectRecipe(recipe) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "Selected: ${recipe.name}" else recipe.name, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null)
            }
        }
    }
    Text("2. Choose the portion", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = draft.grams,
        onValueChange = { viewModel.updateDraft(grams = it) },
        label = { Text("Portion weight (optional)") },
        suffix = { Text("g") },
        supportingText = {
            Text(
                draft.selectedRecipe?.let { "Leave blank for the whole dish (${it.yieldGrams.toDisplayNumber()} g)" }
                    ?: "Leave blank for the whole dish",
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val consumedLocalDate = runCatching { Instant.parse(draft.consumedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrDefault(LocalDate.now())
    LocaleDateField("Date eaten", consumedLocalDate.toString(), { date ->
        val current = runCatching { Instant.parse(draft.consumedAt).atZone(ZoneId.systemDefault()).toLocalTime() }.getOrDefault(java.time.LocalTime.now())
        viewModel.updateDraft(consumedAt = LocalDate.parse(date).atTime(current).atZone(ZoneId.systemDefault()).toInstant().toString())
    })
    Text("Recorded at ${draft.consumedAt.localizedDateTime()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = draft.note,
        onValueChange = { viewModel.updateDraft(note = it) },
        label = { Text("Note") },
        supportingText = { Text("Optional") },
        modifier = Modifier.fillMaxWidth(),
    )
    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = viewModel::previewRation, enabled = !draft.isSubmitting && draft.selectedRecipe != null, modifier = Modifier.fillMaxWidth()) {
        Text(if (draft.isSubmitting) "Reviewing…" else "Review contribution")
    }
    draft.preview?.let { preview ->
        SectionCard {
            Text("3. Review", style = MaterialTheme.typography.titleLarge)
            Text(preview.recipeName, style = MaterialTheme.typography.titleMedium)
            Text("${preview.consumedGrams.toDisplayNumber()} g")
            Text("${preview.calories.toDisplayNumber()} kcal")
            preview.nutrients.forEach { Text("${it.code.displayLabel()}: ${it.amount.toDisplayNumber()} g") }
            Button(onClick = viewModel::confirmRation, enabled = !draft.isSubmitting, modifier = Modifier.fillMaxWidth()) { Text("Log ration") }
        }
    }
}

@Composable
private fun Goals(state: TrackingUiState, viewModel: TrackingViewModel) {
    val editor = state.goalEditor
    ScreenIntro("Goals", "Set daily macro targets. Changes apply from the date you choose and keep older days interpretable.")
    LocaleDateField("Effective date", editor.effectiveDate, { viewModel.updateGoal(effectiveDate = it) })
    SectionCard {
        Text("Daily targets", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(editor.protein, { viewModel.updateGoal(protein = it) }, label = { Text("Protein") }, suffix = { Text("g") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(editor.carbohydrates, { viewModel.updateGoal(carbohydrates = it) }, label = { Text("Carbohydrates") }, suffix = { Text("g") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(editor.fat, { viewModel.updateGoal(fat = it) }, label = { Text("Fat") }, suffix = { Text("g") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = viewModel::saveGoal, enabled = !editor.isSubmitting, modifier = Modifier.fillMaxWidth()) { Text(if (editor.isSubmitting) "Saving…" else "Save goals") }
}

@Composable
private fun History(state: TrackingUiState, viewModel: TrackingViewModel) {
    ScreenIntro("History", "Find previous rations by recipe or date.")
    OutlinedTextField(state.historyQuery, { viewModel.updateHistory(query = it) }, label = { Text("Search history") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LocaleDateField("From", state.historyFrom, { viewModel.updateHistory(from = it) }, Modifier.weight(1f), optional = true)
        LocaleDateField("To", state.historyTo, { viewModel.updateHistory(to = it) }, Modifier.weight(1f), optional = true)
    }
    when {
        state.isLoadingHistory && state.history.isEmpty() -> CircularProgressIndicator()
        state.historyError != null -> ErrorState(state.historyError) { viewModel.navigate(TrackingDestination.History) }
        state.history.isEmpty() -> EmptyState("No matching rations", "Try another search or clear the date filters.")
        else -> {
            if (state.isLoadingHistory) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.history.forEach { ration -> RationRow(ration) { viewModel.openRation(ration.id) } }
            state.historyCursor?.let { OutlinedButton(onClick = viewModel::loadMoreHistory, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
        }
    }
}

@Composable
private fun Settings(state: TrackingUiState, viewModel: TrackingViewModel) {
    ScreenIntro("Settings", "Keep local dates aligned with where you live.")
    SectionCard {
        Text("Timezone", style = MaterialTheme.typography.titleLarge)
        Text("Changing timezone reclassifies existing rations into local days. Their nutrition and exact recorded time do not change.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(state.timezone, { viewModel.updateTimezoneDraft(it) }, label = { Text("IANA timezone") }, supportingText = { Text("Example: Europe/Berlin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    state.timezoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { viewModel.updateTimezone(state.timezone) }, enabled = !state.isUpdatingTimezone, modifier = Modifier.fillMaxWidth()) { Text(if (state.isUpdatingTimezone) "Saving…" else "Save timezone") }
}

@Composable
private fun RationRow(ration: RationSummary, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ration.recipeName, style = MaterialTheme.typography.titleMedium)
                Text("${ration.consumedGrams.toDisplayNumber()} g · ${ration.consumedAt.localizedDateTime()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open ration")
        }
    }
}

@Composable
private fun RationDetailDialog(detail: RationDetail, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.recipeName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${detail.consumedGrams.toDisplayNumber()} g · ${detail.consumedAt.localizedDateTime()}")
                Text("${detail.calories.toDisplayNumber()} kcal", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                detail.nutrients.forEach { Text("${it.code.displayLabel()}: ${it.amount.toDisplayNumber()} g") }
                detail.note?.takeIf(String::isNotBlank)?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete and re-log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun String.displayLabel() = split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun String.toDisplayNumber() = toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: this