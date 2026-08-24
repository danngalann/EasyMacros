package com.danngalann.easymacros.ui

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
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Today) }

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
            if (destination == AppDestination.Today) {
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
                AppDestination.Recipes -> RecipesScreen(onIngredients = { destination = AppDestination.Ingredients })
                AppDestination.Ingredients -> IngredientsScreen(onBack = { destination = AppDestination.Recipes })
                else -> TrackingScreen(onRecipes = { destination = AppDestination.Recipes }, viewModel = trackingViewModel)
            }
        }
    }
}