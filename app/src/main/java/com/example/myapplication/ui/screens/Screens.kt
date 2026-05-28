package com.example.myapplication.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.data.local.*
import com.example.myapplication.ui.viewmodel.SalesViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SalesViewModel,
    onMenuClick: () -> Unit,
    onNavigateToCreateOrder: () -> Unit
) {
    val orders by viewModel.todayOrders.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val totalProfit = orders.sumOf { it.profit }
    val totalSales = orders.sumOf { it.totalListPrice + it.deliveryCharge - it.discount }
    val updateInfo by viewModel.updateInfo.collectAsState()

    val isSyncing = syncStatus.any { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Sales") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.triggerSync() }) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Default.CloudSync else Icons.Default.CloudDone,
                            contentDescription = "Sync Status",
                            tint = if (isSyncing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreateOrder) {
                Icon(Icons.Default.Add, contentDescription = "New Order")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            SummaryCard(orders.size, totalSales, totalProfit)
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Today's Orders", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(orders) { order ->
                    ExpandableOrderRow(order, viewModel)
                }
            }
        }
    }

    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("New Version Available!") },
            text = { Text("A new version of EatUp (${updateInfo?.versionName}) is available on GitHub. Would you like to download it?") },
            confirmButton = {
                Button(onClick = { viewModel.downloadUpdate() }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Later") }
            }
        )
    }
}

@Composable
fun SummaryCard(count: Int, sales: Double, profit: Double, title: String = "Today's Overview") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Orders", style = MaterialTheme.typography.labelMedium)
                    Text("$count", style = MaterialTheme.typography.headlineSmall)
                }
                Column {
                    Text("Sales", style = MaterialTheme.typography.labelMedium)
                    Text("₹${"%.2f".format(sales)}", style = MaterialTheme.typography.headlineSmall)
                }
                Column {
                    Text("Profit", style = MaterialTheme.typography.labelMedium)
                    Text("₹${"%.2f".format(profit)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ExpandableOrderRow(order: OrderEntity, viewModel: SalesViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val lineItems by viewModel.getLineItemsForOrder(order.id).collectAsState(emptyList())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Order #${order.dailyOrderNumber}", fontWeight = FontWeight.Bold)
                    Text(
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(order.timestamp)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Profit: ₹${"%.2f".format(order.profit)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                lineItems.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(item.restaurantName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.itemName} x${item.quantity}", modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Cost: ₹${"%.2f".format(item.costPriceAtTime * item.quantity)}", style = MaterialTheme.typography.bodySmall)
                                Text("List: ₹${"%.2f".format(item.listPriceAtTime * item.quantity)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                OrderSummaryRow("Item Total (List)", order.totalListPrice)
                if (order.deliveryCharge > 0) OrderSummaryRow("Delivery Charge", order.deliveryCharge)
                if (order.discount > 0) OrderSummaryRow("Discount", -order.discount, color = MaterialTheme.colorScheme.error)
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Paid by Customer", fontWeight = FontWeight.ExtraBold)
                    Text("₹${"%.2f".format(order.totalListPrice + order.deliveryCharge - order.discount)}", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun OrderSummaryRow(label: String, amount: Double, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        Text("₹${"%.2f".format(amount)}", style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(viewModel: SalesViewModel, onMenuClick: () -> Unit) {
    var showAddRestaurant by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    val restaurants by viewModel.restaurants.collectAsState()
    val items by viewModel.allMenuItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Catalog") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Restaurants", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showAddRestaurant = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add New")
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(restaurants) { rest ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(rest.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteRestaurant(rest) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Menu Items", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddItem = true }, enabled = restaurants.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add New")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(items) { item ->
                    val restaurantName = restaurants.find { it.id == item.restaurantId }?.name ?: "Unknown"
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { 
                                Column {
                                    Text(restaurantName, style = MaterialTheme.typography.bodySmall)
                                    Text("Cost: ₹${item.costPrice} | List: ₹${item.listPrice}", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteMenuItem(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddRestaurant) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRestaurant = false },
            title = { Text("Add Restaurant") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Restaurant Name") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { if (name.isNotBlank()) { viewModel.addRestaurant(name); showAddRestaurant = false } }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddRestaurant = false }) { Text("Cancel") } }
        )
    }

    if (showAddItem) {
        var name by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("") }
        var list by remember { mutableStateOf("") }
        var selectedRestId by remember { mutableStateOf(restaurants.firstOrNull()?.id ?: 0L) }
        var expanded by remember { mutableStateOf(false) }
        val selectedRestaurant = restaurants.find { it.id == selectedRestId }

        AlertDialog(
            onDismissRequest = { showAddItem = false },
            title = { Text("Add Menu Item") },
            text = {
                Column {
                    Text("Select Restaurant", style = MaterialTheme.typography.labelSmall)
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedRestaurant?.name ?: "Select Restaurant",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                restaurants.forEach { restaurant ->
                                    DropdownMenuItem(
                                        text = { Text(restaurant.name) },
                                        onClick = {
                                            selectedRestId = restaurant.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost Price") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = list, onValueChange = { list = it }, label = { Text("List Price") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addMenuItem(selectedRestId, name, cost.toDoubleOrNull() ?: 0.0, list.toDoubleOrNull() ?: 0.0); showAddItem = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddItem = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderScreen(viewModel: SalesViewModel, onBack: () -> Unit) {
    val restaurants by viewModel.restaurants.collectAsState()
    val allItems by viewModel.allMenuItems.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    var deliveryCharge by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Order") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Catalog (Grouped by Restaurant)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                restaurants.forEach { restaurant ->
                    val restaurantItems = allItems.filter { it.restaurantId == restaurant.id }
                    if (restaurantItems.isNotEmpty()) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { expanded = !expanded }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(restaurant.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            if (expanded) {
                                Column {
                                    restaurantItems.forEach { item ->
                                        val qty = selectedItems[item] ?: 0
                                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name)
                                                Text("₹${item.listPrice}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = { viewModel.removeItemFromOrder(item) }) { Icon(Icons.Default.RemoveCircleOutline, null) }
                                            Text("$qty", fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { viewModel.addItemToOrder(item) }) { Icon(Icons.Default.AddCircleOutline, null) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = deliveryCharge, onValueChange = { deliveryCharge = it }, label = { Text("Delivery") }, modifier = Modifier.weight(1f), prefix = { Text("₹") })
                OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount") }, modifier = Modifier.weight(1f), prefix = { Text("₹") })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submitOrder(deliveryCharge.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0); onBack() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedItems.isNotEmpty()
            ) { Text("Confirm & Save Order", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: SalesViewModel, onMenuClick: () -> Unit) {
    val allOrders by viewModel.allOrders.collectAsState()
    val selectedDateMillis by viewModel.historyDate.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val filteredOrders = remember(allOrders, selectedDateMillis) {
        if (selectedDateMillis == null) allOrders
        else {
            val start = getStartOfDay(selectedDateMillis!!)
            allOrders.filter { it.date == start }
        }
    }

    val totalProfit = filteredOrders.sumOf { it.profit }
    val totalSales = filteredOrders.sumOf { it.totalListPrice + it.deliveryCharge - it.discount }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedDateMillis == null) "Full History" else SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMillis!!))) },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) } },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date") }
                    IconButton(onClick = { viewModel.exportData() }) { Icon(Icons.Default.FileDownload, contentDescription = "Export") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            SummaryCard(
                filteredOrders.size, 
                totalSales, 
                totalProfit, 
                title = if (selectedDateMillis == null) "Lifetime Summary" else "Day Summary"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedDateMillis != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.setHistoryDate(null) }) {
                        Icon(Icons.Default.Clear, null)
                        Text("Clear Date Filter")
                    }
                }
            }

            LazyColumn {
                val grouped = filteredOrders.groupBy { SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date(it.date)) }
                grouped.forEach { (date, dailyOrders) ->
                    item { Text(date, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary) }
                    items(dailyOrders) { order -> ExpandableOrderRow(order, viewModel) }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setHistoryDate(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: SalesViewModel, onMenuClick: () -> Unit) {
    val topItems by viewModel.topItems.collectAsState()
    val restInsights by viewModel.restaurantInsights.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Insights") },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text("Top 5 Items (by Profit)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(topItems) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text("Orders: ${item.count}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("₹${"%.2f".format(item.profit)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Restaurant Performance", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(restInsights) { rest ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rest.name, fontWeight = FontWeight.Bold)
                            Text("Total Orders: ${rest.count}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("₹${"%.2f".format(rest.profit)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosingReportScreen(viewModel: SalesViewModel, onBack: () -> Unit) {
    val orders by viewModel.todayOrders.collectAsState()
    val totalProfit = orders.sumOf { it.profit }
    val totalSales = orders.sumOf { it.totalListPrice + it.deliveryCharge - it.discount }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Closing Report") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Day Successfully Closed!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date()), color = MaterialTheme.colorScheme.secondary)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            SummaryCard(orders.size, totalSales, totalProfit, title = "Final Summary")
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.exportData() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Final Report to Excel")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Note: This will save a CSV file in your Downloads folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SalesViewModel, onMenuClick: () -> Unit) {
    val currentUrl by viewModel.googleSheetsUrl.collectAsState()
    var urlInput by remember { mutableStateOf(currentUrl) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Cloud Synchronization", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Paste your Google Apps Script Web App URL below to sync your sales live to Google Sheets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Google Sync URL") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://script.google.com/macros/s/.../exec") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.updateSheetsUrl(urlInput)
                    scope.launch { snackbarHostState.showSnackbar("Settings Saved Successfully") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Settings")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("App Information", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0 (Pro Version)") },
                trailingContent = {
                    TextButton(onClick = { viewModel.checkForUpdates() }) {
                        Text("Check for Updates")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.forceResync()
                    scope.launch { snackbarHostState.showSnackbar("Re-syncing all data to Cloud...") }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.CloudSync, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Force Re-sync All Data")
            }
            Text(
                "Use this if you deleted your Google Sheet or want to refresh all records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun getStartOfDay(millis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Preview(showBackground = true)
@Composable
fun HistoryPreview() {
    MyApplicationTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                SummaryCard(5, 1250.0, 320.0, title = "Day Summary (May 22)")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Monday, May 22", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Order #1", fontWeight = FontWeight.Bold)
                                Text("10:30 AM", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Profit: ₹70.00", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    MyApplicationTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                SummaryCard(2, 450.0, 120.0)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Today's Orders", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Order #1", fontWeight = FontWeight.Bold)
                                Text("10:30 AM", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Profit: ₹70.00", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
