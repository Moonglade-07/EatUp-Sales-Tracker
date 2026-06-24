package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.SalesRepository
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.viewmodel.SalesViewModel
import com.example.myapplication.ui.viewmodel.SalesViewModelFactory
import com.example.myapplication.util.ExportService
import com.example.myapplication.util.UpdateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SalesRepository(database.salesDao())
        val exportService = ExportService(applicationContext)
        val workManager = WorkManager.getInstance(applicationContext)
        val updateManager = UpdateManager(applicationContext)
        val factory = SalesViewModelFactory(repository, exportService, workManager, updateManager, applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel: SalesViewModel = viewModel(factory = factory)
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "EatUp Management",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Dashboard, null) },
                                label = { Text("Today's Sales") },
                                selected = currentRoute == "dashboard",
                                onClick = {
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.History, null) },
                                label = { Text("Order History") },
                                selected = currentRoute == "history",
                                onClick = {
                                    navController.navigate("history") { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.RestaurantMenu, null) },
                                label = { Text("Manage Catalog") },
                                selected = currentRoute == "catalog",
                                onClick = {
                                    navController.navigate("catalog") { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Insights, null) },
                                label = { Text("Business Insights") },
                                selected = currentRoute == "insights",
                                onClick = {
                                    navController.navigate("insights") { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, null) },
                                label = { Text("Settings") },
                                selected = currentRoute == "settings",
                                onClick = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                    Scaffold { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            modifier = Modifier.padding(padding)
                        ) {
                            composable("dashboard") {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onNavigateToCreateOrder = { 
                                        viewModel.setOrderDate(System.currentTimeMillis())
                                        navController.navigate("create_order") 
                                    },
                                    onNavigateToEditOrder = { id -> navController.navigate("edit_order/$id") }
                                )
                            }
                            composable("create_order") {
                                CreateOrderScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("edit_order/{orderId}") { backStackEntry ->
                                val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull() ?: 0L
                                EditOrderScreen(
                                    viewModel = viewModel,
                                    orderId = orderId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("catalog") {
                                CatalogScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable("history") {
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onNavigateToEditOrder = { id -> 
                                        navController.navigate("edit_order/$id") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("insights") {
                                InsightsScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
