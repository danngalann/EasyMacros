package com.danngalann.easymacros.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Calories",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${dashboard.calories.toDisplayNumber()} kcal",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            dashboard.goal?.let { goal ->
                Text(
                    "of ${goal.calories.toDisplayNumber()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val consumedCalories = dashboard.calories.toBigDecimalOrNull()
                val targetCalories = goal.calories.toBigDecimalOrNull()
                if (consumedCalories != null && targetCalories != null) {
                    val progress = targetProgress(consumedCalories, targetCalories)
                    if (progress.isOver) {
                        Text(
                            "${progress.difference.toPlainString().toDisplayNumber()} kcal over target",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text("Macros", style = MaterialTheme.typography.titleLarge)
        val consumed = dashboard.nutrients.associateBy { it.code }
        val goals = dashboard.goal?.nutrients?.associateBy { it.code }.orEmpty()
        listOf("protein", "carbohydrates", "fat").forEachIndexed { index, code ->
            val amount = consumed[code]?.amount ?: "0"
            val target = goals[code]?.amount
            val value = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val goal = target?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    code.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${amount.toDisplayNumber()} g${target?.let { " of ${it.toDisplayNumber()} g" } ?: ""}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                goal?.let {
                    val progress = targetProgress(value, it)
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        color = if (progress.isOver) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = if (progress.isOver) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = if (progress.isOver) {
                                "$code over target"
                            } else {
                                "$code progress"
                            }
                        },
                    )
                    Text(
                        if (progress.isOver) {
                            "${progress.difference.toPlainString().toDisplayNumber()} g over target"
                        } else {
                            "${progress.difference.toPlainString().toDisplayNumber()} g remaining"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (progress.isOver) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (progress.isOver) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (index < 2) Spacer(modifier = Modifier.height(8.dp))
        }
        dashboard.goal?.let {
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            Text(
                "Targets active since ${it.effectiveLocalDate.localizedDate()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class TargetProgress(
    val fraction: Float,
    val difference: BigDecimal,
    val isOver: Boolean,
)

internal fun targetProgress(value: BigDecimal, target: BigDecimal): TargetProgress {
    val isOver = value > target
    return TargetProgress(
        fraction = value.divide(target, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f),
        difference = if (isOver) value.subtract(target) else target.subtract(value),
        isOver = isOver,
    )
}

@Composable
private fun LogRation(state: TrackingUiState, viewModel: TrackingViewModel, onRecipes: () -> Unit) {
    val draft = state.rationDraft ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::backWithinLog) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        ScreenIntro(
            "Log a ration",
            when (draft.method) {
                LogRationMethod.Choose -> "Choose how to describe what you ate."
                LogRationMethod.Recipe -> "Choose a recipe, then review its contribution."
                LogRationMethod.Ai -> "Add several views and context, then review the AI estimate."
            },
            Modifier.weight(1f),
        )
    }
    when (draft.method) {
        LogRationMethod.Choose -> LogRationMethodChooser(viewModel)
        LogRationMethod.Recipe -> RecipeLogRation(draft, viewModel, onRecipes)
        LogRationMethod.Ai -> AiLogRation(draft, viewModel)
    }
}

@Composable
private fun LogRationMethodChooser(viewModel: TrackingViewModel) {
    SectionCard {
        Text("AI + Images", style = MaterialTheme.typography.titleLarge)
        Text(
            "Photograph the complete meal from up to four angles and add useful portion details.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = viewModel::chooseAiLog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Text(" AI + Images")
        }
    }
    SectionCard {
        Text("Recipe", style = MaterialTheme.typography.titleLarge)
        Text(
            "Log a whole recipe or a weighed portion using its saved nutrient snapshot.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = viewModel::chooseRecipeLog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restaurant, contentDescription = null)
            Text(" Recipe")
        }
    }
}

@Composable
private fun RecipeLogRation(
    draft: RationDraft,
    viewModel: TrackingViewModel,
    onRecipes: () -> Unit,
) {
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
private fun AiLogRation(draft: RationDraft, viewModel: TrackingViewModel) {
        val context = LocalContext.current
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(4),
        ) { uris ->
            uris.take(4 - draft.images.size).forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    compressImage(stream.readBytes())?.let { bytes ->
                        viewModel.addRationImage(bytes, "image/jpeg")
                    }
                }
            }
        }
        val camera = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview(),
        ) { bitmap: Bitmap? ->
            bitmap?.let { viewModel.addRationImage(compressBitmap(it), "image/jpeg") }
        }
        val cameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) camera.launch(null) else viewModel.onRationCameraPermissionDenied()
        }

        Text("1. Show the complete ration", style = MaterialTheme.typography.titleLarge)
        Text(
            "Use multiple angles when possible. Include the full plate, side dishes, drinks, sauces, and a size reference.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                enabled = draft.images.size < 4 && !draft.isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text(" Capture")
            }
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = draft.images.size < 4 && !draft.isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Text(" Select")
            }
        }
        if (draft.cameraPermissionDenied) {
            Text("Camera permission was denied. You can still select photos from the system picker.")
        }
        draft.images.forEachIndexed { index, image ->
            SectionCard {
                val bitmap = remember(image.bytes) {
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Selected ration image ${index + 1}",
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.removeRationImage(index) },
                    enabled = !draft.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove image ${index + 1}")
                }
            }
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = viewModel::updateAiDescription,
            label = { Text("Description") },
            supportingText = {
                Text("Include restaurant/menu name, portion size, ingredients, sauces, and anything not visible.")
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::estimateAiRation,
            enabled = draft.images.isNotEmpty() && draft.description.isNotBlank() && !draft.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (draft.isSubmitting && draft.aiEstimate == null) "Estimating..." else "Estimate ration")
        }
        if (draft.isSubmitting && draft.aiEstimate == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Advanced analysis can take longer. Keep this screen open.")
        }
        draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        draft.aiEstimate?.let { estimate ->
            Text("2. Review the estimate", style = MaterialTheme.typography.titleLarge)
            SectionCard {
                Text(
                    "AI estimate - confidence ${estimate.confidence.toDisplayNumber()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                estimate.warnings.forEach {
                    Text(it, color = MaterialTheme.colorScheme.tertiary)
                }
                OutlinedTextField(
                    value = estimate.name,
                    onValueChange = { viewModel.updateAiEstimate(name = it) },
                    label = { Text("Ration name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = estimate.estimatedWeightGrams,
                    onValueChange = { viewModel.updateAiEstimate(weight = it) },
                    label = { Text("Estimated edible weight") },
                    suffix = { Text("g") },
                    modifier = Modifier.fillMaxWidth(),
                )
                estimate.nutrients
                    .filter { it.code in setOf("protein", "carbohydrates", "fat") }
                    .forEach { nutrient ->
                        OutlinedTextField(
                            value = nutrient.amount,
                            onValueChange = {
                                viewModel.updateAiEstimate(
                                    nutrientCode = nutrient.code,
                                    nutrientAmount = it,
                                )
                            },
                            label = { Text(nutrient.code.displayLabel()) },
                            suffix = { Text("g") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                Text("${estimate.calories.toDisplayNumber()} kcal (original estimate)")
            }
            val consumedLocalDate = runCatching {
                Instant.parse(draft.consumedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrDefault(LocalDate.now())
            LocaleDateField("Date eaten", consumedLocalDate.toString(), { date ->
                val current = runCatching {
                    Instant.parse(draft.consumedAt).atZone(ZoneId.systemDefault()).toLocalTime()
                }.getOrDefault(java.time.LocalTime.now())
                viewModel.updateDraft(
                    consumedAt = LocalDate.parse(date)
                        .atTime(current)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toString(),
                )
            })
            OutlinedTextField(
                value = draft.note,
                onValueChange = { viewModel.updateDraft(note = it) },
                label = { Text("Note") },
                supportingText = { Text("Optional") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::confirmAiRation,
                enabled = !draft.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (draft.isSubmitting) "Logging..." else "Log reviewed ration")
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
                if (ration.source == "ai") {
                    Text("AI estimate", color = MaterialTheme.colorScheme.tertiary)
                }
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
                if (detail.source == "ai") {
                    Text(
                        "AI estimate${detail.confidence?.let { " - confidence ${it.toDisplayNumber()}" } ?: ""}",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    detail.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                }
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