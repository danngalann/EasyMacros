package com.danngalann.easymacros.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danngalann.easymacros.data.repository.IngredientDetailModel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay

@Composable
fun IngredientsScreen(
    onBack: () -> Unit,
    returnToRecipe: Boolean = false,
    onIngredientCreated: ((IngredientDetailModel) -> Unit)? = null,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.confirmedIngredient?.id) {
        state.confirmedIngredient?.let { ingredient ->
            onIngredientCreated?.invoke(ingredient)
            viewModel.consumeConfirmedIngredient()
        }
    }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) {
            delay(3_000)
            viewModel.clearSuccessMessage()
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSuccessMessage() }
    }
    val back = {
        if (state.flow == IngredientFlow.List || shouldReturnToRecipe(returnToRecipe, state.flow)) {
            onBack()
        } else {
            viewModel.backToList()
        }
    }
    BackHandler(onBack = back)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                ScreenIntro(
                    if (state.flow == IngredientFlow.List) "Ingredients" else "Create ingredient",
                    "Every source stays editable and requires confirmation.",
                    Modifier.weight(1f),
                )
            }
        }
        when (state.flow) {
            IngredientFlow.List -> ingredientList(state, viewModel)
            IngredientFlow.Method -> creationMethods(viewModel)
            IngredientFlow.Manual -> manualForm(state, viewModel)
            IngredientFlow.Barcode -> barcodeForm(state, viewModel)
            IngredientFlow.Image -> imageForm(state, viewModel)
            IngredientFlow.Processing -> processing(state, viewModel)
            IngredientFlow.Review -> candidateReview(state, viewModel)
            IngredientFlow.Detail -> ingredientDetail(state, viewModel)
        }
        state.error?.let { item { ErrorState(it, viewModel::retry) } }
        state.successMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
    }
}

internal fun shouldReturnToRecipe(returnToRecipe: Boolean, flow: IngredientFlow) =
    returnToRecipe && flow == IngredientFlow.Method

private fun androidx.compose.foundation.lazy.LazyListScope.ingredientList(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    item {
        Button(onClick = viewModel::chooseMethod, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" New ingredient")
        }
    }
    item {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            label = { Text("Search ingredients") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        FilterChip(
            selected = state.includeHidden,
            onClick = { viewModel.setIncludeHidden(!state.includeHidden) },
            label = { Text("Show hidden") },
        )
    }
    when {
        state.isLoading && state.ingredients.isEmpty() -> item {
            CircularProgressIndicator(Modifier.semantics { stateDescription = "Loading ingredients" })
        }
        state.ingredients.isEmpty() && state.error == null -> item {
            EmptyState(
                "No ingredients found",
                "Create one manually, scan a barcode, or analyze an image.",
                "New ingredient",
                viewModel::chooseMethod,
                Icons.Default.Inventory2,
            )
        }
        else -> {
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(state.ingredients, key = { it.id }) { ingredient ->
                SectionCard {
                    Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (ingredient.hidden) "Hidden" else "Available for recipes")
                    OutlinedButton(
                        onClick = { viewModel.openDetail(ingredient.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("View details") }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.creationMethods(viewModel: IngredientsViewModel) {
    item { MethodButton("Manual", Icons.Default.Keyboard, viewModel::chooseManual) }
    item { MethodButton("Scan barcode", Icons.Default.CameraAlt, viewModel::chooseBarcode) }
    item { MethodButton("Image + AI", Icons.Default.AutoAwesome, viewModel::chooseImage) }
}

@Composable
private fun MethodButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Text(" $label")
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.manualForm(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    item { Text("Enter values per 100 g. They will open in the same review editor used by barcode and AI.") }
    item { IngredientFields(state.manual, viewModel::updateManual) }
    item {
        Button(
            onClick = viewModel::submitManual,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isSubmitting) "Preparing..." else "Review candidate") }
    }
}

@Composable
private fun IngredientFields(
    state: IngredientEditorState,
    onUpdate: (String?, String?, String?, String?, String?) -> Unit,
) {
    SectionCard {
        OutlinedTextField(
            state.name,
            { onUpdate(it, null, null, null, null) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.brand,
            { onUpdate(null, it, null, null, null) },
            label = { Text("Brand") },
            modifier = Modifier.fillMaxWidth(),
        )
        listOf(
            Triple("Protein (g)", state.protein, 2),
            Triple("Carbohydrates (g)", state.carbohydrates, 3),
            Triple("Fat (g)", state.fat, 4),
        ).forEach { (label, value, field) ->
            OutlinedTextField(
                value,
                {
                    onUpdate(
                        null,
                        null,
                        it.takeIf { field == 2 },
                        it.takeIf { field == 3 },
                        it.takeIf { field == 4 },
                    )
                },
                label = { Text("$label per 100 g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.barcodeForm(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    item {
        OutlinedTextField(
            value = state.barcode,
            onValueChange = viewModel::updateBarcode,
            label = { Text("UPC-A or EAN-13") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) viewModel.setScannerEnabled(true) else viewModel.onPermissionDenied()
        }
        Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.scannerEnabled) "Camera active" else "Open scanner") }
    }
    if (state.scannerEnabled) {
        item {
            BarcodeScannerView(
                onBarcode = viewModel::onBarcodeScanned,
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        }
    }
    if (state.permissionDenied) item {
        Text("Camera permission was denied. Manual barcode entry remains available.")
    }
    item {
        Button(
            onClick = viewModel::submitBarcode,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isSubmitting) "Looking up..." else "Look up barcode") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.imageForm(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    item {
        val context = LocalContext.current
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(4),
        ) { uris ->
            uris.take(4 - state.images.size).forEach {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    compressImage(stream.readBytes())?.let { bytes ->
                        viewModel.addImage(bytes, "image/jpeg")
                    }
                }
            }
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            bitmap?.let { viewModel.addImage(compressBitmap(it), "image/jpeg") }
        }
        val cameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) camera.launch(null) else viewModel.onPermissionDenied()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f),
                enabled = state.images.size < 4,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text(" Capture")
            }
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
                enabled = state.images.size < 4,
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Text(" Select")
            }
        }
    }
    if (state.permissionDenied) item {
        Text("Camera permission was denied. You can still select photos from the system picker.")
    }
    itemsIndexed(state.images) { index, selected ->
        SectionCard {
            val bytes = selected.bytes
            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Selected ingredient image ${index + 1}",
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            OutlinedButton(
                onClick = { viewModel.removeImage(index) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove image ${index + 1}") }
        }
    }
    item {
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::updateDescription,
            label = { Text("Optional description") },
            supportingText = { Text("Example: large carrot, about 60 g") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.uploadProgress?.let { progress ->
        item {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item {
        Button(
            onClick = viewModel::submitImage,
            enabled = state.images.isNotEmpty() && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isSubmitting) "Uploading..."
                else "Analyze ${state.images.size} image${if (state.images.size == 1) "" else "s"}",
            )
        }
    }
    if (state.isSubmitting) item {
        OutlinedButton(onClick = viewModel::cancelUpload, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.processing(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    item {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    item { Text("The image is being analyzed. This candidate resumes after process recreation.") }
    item {
        OutlinedButton(onClick = viewModel::cancelUpload, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel analysis")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.candidateReview(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    val candidate = state.candidate ?: return
    item {
        SectionCard {
            Text("Source: ${candidate.provider ?: candidate.source}", style = MaterialTheme.typography.titleMedium)
            candidate.confidence?.let { Text("Confidence: $it") }
            candidate.warnings.filterNot { it.isFieldWarning(candidate) }.forEach {
                Text(it, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    item {
        OutlinedTextField(
            candidate.name,
            { viewModel.updateCandidate(name = it) },
            label = { Text("Name (required)") },
            supportingText = { candidate.warningFor("name")?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        OutlinedTextField(
            candidate.brand,
            { viewModel.updateCandidate(brand = it) },
            label = { Text("Brand (optional)") },
            supportingText = { candidate.warningFor("brand")?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        OutlinedTextField(
            candidate.barcode,
            { viewModel.updateCandidate(barcode = it) },
            label = { Text("Barcode (optional)") },
            supportingText = { candidate.warningFor("barcode")?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    items(candidate.nutrients, key = { it.code }) { nutrient ->
        OutlinedTextField(
            nutrient.amount,
            { viewModel.updateCandidate(nutrientCode = nutrient.code, nutrientAmount = it) },
            label = {
                Text(
                    "${nutrient.displayName} (${if (nutrient.code in requiredNutrients) "required" else "optional"}, ${nutrient.unit}/100 g)",
                )
            },
            supportingText = {
                Text(
                    candidate.warningFor(nutrient.code)
                        ?: if (nutrient.derived) "Derived value" else "Editable imported value",
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (candidate.source == "barcode") {
        item {
            OutlinedButton(onClick = viewModel::useImageInstead, modifier = Modifier.fillMaxWidth()) {
                Text("Use image + AI instead")
            }
        }
    }
    if (candidate.status == "failed") {
        item {
            OutlinedButton(onClick = viewModel::chooseManual, modifier = Modifier.fillMaxWidth()) {
                Text("Enter manually")
            }
        }
    } else {
        item {
            Button(
                onClick = viewModel::confirmCandidate,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isSubmitting) "Creating..." else "Confirm and create ingredient") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ingredientDetail(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
) {
    val detail = state.detail
    if (detail == null) {
        item { CircularProgressIndicator() }
        return
    }
    item {
        var name by rememberSaveable(detail.id, detail.name) { mutableStateOf(detail.name) }
        var brand by rememberSaveable(detail.id, detail.brand) { mutableStateOf(detail.brand) }
        SectionCard {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
            Text("Source: ${detail.source}")
            detail.confidence?.let { Text("Confidence: $it") }
            detail.barcode.takeIf(String::isNotBlank)?.let { Text("Barcode: $it") }
            detail.warnings.forEach { Text("Warning: $it") }
            detail.nutrients.forEach { Text("${it.code.replace('_', ' ')}: ${it.amount} per 100 g") }
            Button(
                onClick = { viewModel.saveDetail(name, brand) },
                enabled = !state.isSubmitting && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save details") }
            OutlinedButton(
                onClick = viewModel::toggleHidden,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (detail.hidden) "Unhide ingredient" else "Hide ingredient") }
        }
    }
}

internal fun compressImage(bytes: ByteArray): ByteArray? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::compressBitmap)

internal fun compressBitmap(bitmap: Bitmap): ByteArray {
    val scale = minOf(1f, 2048f / maxOf(bitmap.width, bitmap.height))
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap
    return ByteArrayOutputStream().use {
        resized.compress(Bitmap.CompressFormat.JPEG, 85, it)
        it.toByteArray()
    }
}

private val requiredNutrients = setOf("protein", "carbohydrates", "fat")

private fun CandidateEditorState.warningFor(field: String): String? {
    val terms = when (field) {
        "carbohydrates" -> listOf("carbohydrate", "carbs")
        "saturated_fat" -> listOf("saturated fat")
        else -> listOf(field.replace('_', ' '))
    }
    return warnings.firstOrNull { warning -> terms.any { warning.contains(it, ignoreCase = true) } }
}

private fun String.isFieldWarning(candidate: CandidateEditorState): Boolean =
    listOf("name", "brand", "barcode").any { candidate.warningFor(it) == this } ||
        candidate.nutrients.any { candidate.warningFor(it.code) == this }
