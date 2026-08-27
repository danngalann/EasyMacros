package com.danngalann.easymacros.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class AppDestination(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Default.CalendarMonth),
    History("History", Icons.Default.History),
    Recipes("Recipes", Icons.Default.RestaurantMenu),
    Ingredients("Ingredients", Icons.Default.Inventory2),
    Goals("Goals", Icons.Default.Flag),
    Settings("Settings", Icons.Default.Settings),
}

@Composable
fun AuthenticatedApp(
    onLogout: () -> Unit,
    trackingViewModel: TrackingViewModel = hiltViewModel(),
    recipesViewModel: RecipesViewModel = hiltViewModel(),
    ingredientsViewModel: IngredientsViewModel = hiltViewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Today) }
    var ingredientReturnToRecipe by rememberSaveable { mutableStateOf(false) }
    val trackingState by trackingViewModel.state.collectAsStateWithLifecycle()
    val recipesState by recipesViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        val trackingDestination = when (destination) {
            AppDestination.Today -> TrackingDestination.Dashboard
            AppDestination.History -> TrackingDestination.History
            AppDestination.Goals -> TrackingDestination.Goals
            AppDestination.Settings -> TrackingDestination.Settings
            AppDestination.Recipes, AppDestination.Ingredients -> null
        }
        trackingDestination?.let(trackingViewModel::navigate)
    }

    BackHandler(
        enabled = destination != AppDestination.Ingredients &&
            (
                destination != AppDestination.Today ||
                    trackingState.destination != TrackingDestination.Dashboard ||
                    recipesState.editor != null ||
                    recipesState.detail != null
                ),
    ) {
        when {
            destination == AppDestination.Recipes && recipesState.editor != null ->
                recipesViewModel.discardEditor()
            destination == AppDestination.Recipes && recipesState.detail != null ->
                recipesViewModel.closeDetail()
            destination != AppDestination.Today -> destination = AppDestination.Today
            trackingState.destination != TrackingDestination.Dashboard ->
                if (trackingState.destination == TrackingDestination.Log) {
                    trackingViewModel.backWithinLog()
                } else {
                    trackingViewModel.navigate(TrackingDestination.Dashboard)
                }
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("EasyMacros", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out") }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.filterNot { it == AppDestination.Ingredients }.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item || (item == AppDestination.Recipes && destination == AppDestination.Ingredients),
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (shouldShowLogRationAction(destination.name, trackingState.destination)) {
                ExtendedFloatingActionButton(
                    onClick = trackingViewModel::openLog,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Log ration") },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (destination) {
                AppDestination.Recipes -> RecipesScreen(
                    onIngredients = {
                        ingredientReturnToRecipe = false
                        destination = AppDestination.Ingredients
                    },
                    onCreateIngredient = {
                        ingredientReturnToRecipe = true
                        ingredientsViewModel.chooseMethod()
                        destination = AppDestination.Ingredients
                    },
                    viewModel = recipesViewModel,
                )
                AppDestination.Ingredients -> IngredientsScreen(
                    onBack = {
                        ingredientReturnToRecipe = false
                        destination = AppDestination.Recipes
                    },
                    returnToRecipe = ingredientReturnToRecipe,
                    onIngredientCreated = { ingredient ->
                        if (ingredientReturnToRecipe) {
                            recipesViewModel.addCreatedIngredient(ingredient.id, ingredient.name)
                            ingredientReturnToRecipe = false
                            destination = AppDestination.Recipes
                        }
                    },
                    viewModel = ingredientsViewModel,
                )
                else -> TrackingScreen(onRecipes = { destination = AppDestination.Recipes }, viewModel = trackingViewModel)
            }
        }
    }
}

internal fun shouldShowLogRationAction(
    appDestination: String,
    trackingDestination: TrackingDestination,
) = appDestination == "Today" && trackingDestination != TrackingDestination.Log