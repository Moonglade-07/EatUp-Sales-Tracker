package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.myapplication.ui.viewmodel.SalesViewModel
import com.example.myapplication.ui.viewmodel.SalesViewModelFactory
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.ExportService
import com.example.myapplication.util.UpdateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SalesRepository(database.salesDao())
        val exportService = ExportService(applicationContext)
        val workManager = WorkManager.getInstance(applicationContext)
        val updateManager = UpdateManager(applicationContext)
        val factory = SalesViewModelFactory(repository, exportService, workManager, updateManager, applicationContext)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: SalesViewModel = viewModel(factory = factory)
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("EatUp Sales Tracker", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            HorizontalDivider()
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Dashboard, null) },
                                label = { Text("Today's Sales") },
                                selected = currentRoute == "dashboard",
                                onClick = {
                                    navController.navigate("dashboard")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.History, null) },
                                label = { Text("Order History") },
                                selected = currentRoute == "history",
                                onClick = {
                                    navController.navigate("history")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.RestaurantMenu, null) },
                                label = { Text("Manage Catalog") },
                                selected = currentRoute == "catalog",
                                onClick = {
                                    navController.navigate("catalog")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Insights, null) },
                                label = { Text("Business Insights") },
                                selected = currentRoute == "insights",
                                onClick = {
                                    navController.navigate("insights")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Summarize, null) },
                                label = { Text("Daily Closing Report") },
                                selected = currentRoute == "closing_report",
                                onClick = {
                                    navController.navigate("closing_report")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, null) },
                                label = { Text("Settings") },
                                selected = currentRoute == "settings",
                                onClick = {
                                    navController.navigate("settings")
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onNavigateToCreateOrder = { navController.navigate("create_order") }
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
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                        composable("insights") {
                            InsightsScreen(
                                viewModel = viewModel,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                        composable("closing_report") {
                            ClosingReportScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("create_order") {
                            CreateOrderScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
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
