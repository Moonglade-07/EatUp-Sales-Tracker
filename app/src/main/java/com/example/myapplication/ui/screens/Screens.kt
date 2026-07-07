package com.example.myapplication.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.myapplication.ui.components.*
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
    onNavigateToCreateOrder: () -> Unit,
    onNavigateToEditOrder: (Long) -> Unit
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
            SummaryCard(orders.size, totalSales, totalProfit, title = "Today's Overview")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Today's Orders", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(orders) { order ->
                    ExpandableOrderRow(order, viewModel, onEdit = { onNavigateToEditOrder(order.id) })
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
fun SummaryCard(count: Int, sales: Double, profit: Double, title: String) {
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
fun ExpandableOrderRow(order: OrderEntity, viewModel: SalesViewModel, onEdit: () -> Unit) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEdit() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
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
    var searchQuery by remember { mutableStateOf("") }
    
    val restaurants by viewModel.restaurants.collectAsState()
    val allItems by viewModel.allMenuItems.collectAsState()
    
    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search items...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                items(filteredItems) { item ->
                    val restaurantName = restaurants.find { it.id == item.restaurantId }?.name ?: "Unknown"
                    var showEditItem by remember { mutableStateOf(false) }

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
                                Row {
                                    IconButton(onClick = { showEditItem = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { viewModel.deleteMenuItem(item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }

                    if (showEditItem) {
                        var editName by remember { mutableStateOf(item.name) }
                        var editCost by remember { mutableStateOf(item.costPrice.toString()) }
                        var editList by remember { mutableStateOf(item.listPrice.toString()) }

                        AlertDialog(
                            onDismissRequest = { showEditItem = false },
                            title = { Text("Edit Menu Item") },
                            text = {
                                Column {
                                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(value = editCost, onValueChange = { editCost = it }, label = { Text("Cost Price") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(value = editList, onValueChange = { editList = it }, label = { Text("List Price") }, modifier = Modifier.fillMaxWidth())
                                }
                            },
                            confirmButton = {
                                Button(onClick = { 
                                    viewModel.editMenuItem(item.copy(name = editName, costPrice = editCost.toDoubleOrNull() ?: item.costPrice, listPrice = editList.toDoubleOrNull() ?: item.listPrice))
                                    showEditItem = false 
                                }) { Text("Update") }
                            },
                            dismissButton = { TextButton(onClick = { showEditItem = false }) { Text("Cancel") } }
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
                Button(onClick = { 
                    if (name.isNotBlank()) { 
                        viewModel.addRestaurant(name)
                        showAddRestaurant = false 
                    } 
                }) { Text("Save") }
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
                Button(onClick = { 
                    viewModel.addMenuItem(selectedRestId, name, cost.toDoubleOrNull() ?: 0.0, list.toDoubleOrNull() ?: 0.0)
                    showAddItem = false 
                }) { Text("Save") }
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
    val selectedDateMillis by viewModel.orderDate.collectAsState()
    
    var deliveryCharge by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Order") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(4.dp))
                        Text(SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis)))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search items to add...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                restaurants.forEach { restaurant ->
                    val restaurantItems = allItems.filter { 
                        it.restaurantId == restaurant.id && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
                    }
                    if (restaurantItems.isNotEmpty()) {
                        item {
                            var expanded by remember { mutableStateOf(searchQuery.isNotBlank()) }
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setOrderDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOrderScreen(viewModel: SalesViewModel, orderId: Long, onBack: () -> Unit) {
    val restaurants by viewModel.restaurants.collectAsState()
    val allItems by viewModel.allMenuItems.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    
    val deliveryCharge by viewModel.editDeliveryCharge.collectAsState()
    val discount by viewModel.editDiscount.collectAsState()
    val selectedDateMillis by viewModel.editOrderDate.collectAsState()
    
    val allOrders by viewModel.allOrders.collectAsState()
    val order = allOrders.find { it.id == orderId }
    
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        viewModel.startEditingOrder(orderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Order #${order?.dailyOrderNumber ?: ""}") },
                navigationIcon = { IconButton(onClick = { viewModel.clearOrder(); onBack() }) { Icon(Icons.Default.Close, null) } },
                actions = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(4.dp))
                        Text(SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis)))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Order", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Modify Items", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search items...") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                restaurants.forEach { restaurant ->
                    val restaurantItems = allItems.filter { it.restaurantId == restaurant.id && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)) }
                    if (restaurantItems.isNotEmpty()) {
                        item {
                            var expanded by remember { mutableStateOf(true) }
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clickable { expanded = !expanded }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
            
            var localDelivery by remember { mutableStateOf("") }
            var localDiscount by remember { mutableStateOf("") }
            
            LaunchedEffect(deliveryCharge, discount) {
                if (localDelivery.isEmpty() && deliveryCharge.isNotEmpty()) localDelivery = deliveryCharge
                if (localDiscount.isEmpty() && discount.isNotEmpty()) localDiscount = discount
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = localDelivery, onValueChange = { localDelivery = it }, label = { Text("Delivery") }, modifier = Modifier.weight(1f), prefix = { Text("₹") })
                OutlinedTextField(value = localDiscount, onValueChange = { localDiscount = it }, label = { Text("Discount") }, modifier = Modifier.weight(1f), prefix = { Text("₹") })
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    viewModel.updateOrder(orderId, localDelivery.toDoubleOrNull() ?: 0.0, localDiscount.toDoubleOrNull() ?: 0.0)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Update Order Records", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Order?") },
            text = { Text("This will permanently remove the order from your phone and the Google Sheet.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteOrder(orderId); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setEditOrderDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: SalesViewModel,
    onMenuClick: () -> Unit,
    onNavigateToEditOrder: (Long) -> Unit
) {
    val allOrders by viewModel.allOrders.collectAsState()
    val selectedDateMillis by viewModel.historyDate.collectAsState()
    val selectedMonthMillis by viewModel.historyMonth.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val filteredOrders = remember(allOrders, selectedDateMillis, selectedMonthMillis) {
        if (selectedDateMillis != null) {
            val start = getStartOfDay(selectedDateMillis!!)
            allOrders.filter { it.date == start }
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedMonthMillis }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            
            allOrders.filter { order ->
                val oCal = Calendar.getInstance().apply { timeInMillis = order.date }
                oCal.get(Calendar.YEAR) == year && oCal.get(Calendar.MONTH) == month
            }
        }
    }

    val totalProfit = filteredOrders.sumOf { it.profit }
    val totalSales = filteredOrders.sumOf { it.totalListPrice + it.deliveryCharge - it.discount }
    
    val summaryTitle = when {
        selectedDateMillis != null -> "Day Summary (${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis!!))})"
        else -> "${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(selectedMonthMillis))} Summary"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order History") },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) } },
                actions = {
                    IconButton(onClick = { showMonthPicker = true }) { Icon(Icons.Default.CalendarViewMonth, contentDescription = "Pick Month") }
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            SummaryCard(
                count = filteredOrders.size, 
                sales = totalSales, 
                profit = totalProfit, 
                title = summaryTitle
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedDateMillis != null || Calendar.getInstance().apply { timeInMillis = selectedMonthMillis }.get(Calendar.MONTH) != Calendar.getInstance().get(Calendar.MONTH)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { 
                        viewModel.setHistoryDate(null)
                        viewModel.setHistoryMonth(Calendar.getInstance().timeInMillis)
                    }) {
                        Icon(Icons.Default.Clear, null)
                        Text("Reset Filters")
                    }
                }
            }

            LazyColumn {
                val grouped = filteredOrders.groupBy { SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date(it.date)) }
                grouped.forEach { (date, dailyOrders) ->
                    item { Text(date, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary) }
                    items(dailyOrders) { order -> 
                        ExpandableOrderRow(order, viewModel, onEdit = { onNavigateToEditOrder(order.id) }) 
                    }
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

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialDate = selectedMonthMillis,
            onDismiss = { showMonthPicker = false },
            onDateSelected = { 
                viewModel.setHistoryDate(null)
                viewModel.setHistoryMonth(it)
                showMonthPicker = false
            }
        )
    }
}

@Composable
fun MonthYearPickerDialog(initialDate: Long, onDismiss: () -> Unit, onDateSelected: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialDate }
    var selectedMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Month & Year") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Simple Month Selector
                    var monthExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { monthExpanded = true }) { Text(months[selectedMonth]) }
                        DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                            months.forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { selectedMonth = index; monthExpanded = false })
                            }
                        }
                    }
                    // Simple Year Selector
                    var yearExpanded by remember { mutableStateOf(false) }
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val years = (currentYear - 2..currentYear + 1).toList()
                    Box {
                        TextButton(onClick = { yearExpanded = true }) { Text("$selectedYear") }
                        DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                            years.forEach { year ->
                                DropdownMenuItem(text = { Text("$year") }, onClick = { selectedYear = year; yearExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                onDateSelected(result.timeInMillis)
            }) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: SalesViewModel, onMenuClick: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Market Trends", "Performance")
    
    val weeklyData by viewModel.weeklyVelocity.collectAsState()
    val weekdayAverages by viewModel.weekdayAverages.collectAsState()
    val monthlyTrends by viewModel.monthlyTrends.collectAsState()
    val topItemsByRevenue by viewModel.topItemsBySales.collectAsState()
    val topItemsByProfit by viewModel.topItemsByProfit.collectAsState()
    val restBySales by viewModel.restaurantInsightsBySales.collectAsState()
    val restByProfit by viewModel.restaurantInsightsByProfit.collectAsState()
    val bundles by viewModel.bundleOpportunities.collectAsState()

    val weeklyRange by viewModel.analyticsWeeklyRange.collectAsState()
    val weekdayRange by viewModel.analyticsWeekdayRange.collectAsState()
    val selectedMonths by viewModel.analyticsSelectedMonths.collectAsState()
    val selectedRestMonths by viewModel.analyticsSelectedRestMonths.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Growth Analytics") },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                if (selectedTab == 0) {
                    // TAB 1: MARKET TRENDS
                    AnalyticsRangeHeader(
                        title = "Select 7-Day Window",
                        currentRange = weeklyRange,
                        onDateSelected = { viewModel.setWeeklyRange(it) },
                        isWeekly = true,
                        onReset = { viewModel.resetAnalyticsFilters() }
                    )
                    GroupedSalesBarChart(weeklyData)
                    Spacer(Modifier.height(16.dp))
                    
                    AnalyticsRangeHeader(
                        title = "Weekday Analysis Range",
                        currentRange = weekdayRange,
                        onDateSelected = { viewModel.setWeekdayRange(it, System.currentTimeMillis()) },
                        onReset = { viewModel.resetAnalyticsFilters() },
                        defaultStartText = "All Time"
                    )
                    WeekdayAvgChart(weekdayAverages)
                    Spacer(Modifier.height(16.dp))
                    
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    RevenueTrendLineChart(monthlyTrends)
                } else {
                    // TAB 2: PERFORMANCE
                    MultiMonthHeader(
                        title = "Item Performance (Selected Months)",
                        selectedMonths = selectedMonths,
                        onToggleMonth = { viewModel.togglePerformanceMonth(it) },
                        onReset = { viewModel.resetAnalyticsFilters() }
                    )
                    
                    ItemInsightList("Top 10 Items by Revenue", topItemsByRevenue, false)
                    Spacer(Modifier.height(16.dp))
                    ItemInsightList("Top 10 Items by Profit", topItemsByProfit, true)
                    
                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(32.dp))

                    MultiMonthHeader(
                        title = "Restaurant Strategy (Selected Months)",
                        selectedMonths = selectedRestMonths,
                        onToggleMonth = { viewModel.toggleRestaurantMonth(it) },
                        onReset = { viewModel.resetAnalyticsFilters() }
                    )

                    RestaurantInsightList("Top 5 Restaurants by Sales", restBySales, false)
                    Spacer(Modifier.height(16.dp))
                    RestaurantInsightList("Top 5 Restaurants by Profit", restByProfit, true)

                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(32.dp))
                    
                    Text("Bundle Opportunities", style = MaterialTheme.typography.titleMedium)
                    Text("Top 10 items frequently ordered together", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    bundles.forEach { BundleOpportunityCard(it) }
                }
                Spacer(Modifier.height(100.dp))
            }
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
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
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
            
            Text("Catalog Mirror", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.backupToCloud()
                    scope.launch { snackbarHostState.showSnackbar("Syncing catalog to Cloud...") }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Catalog to Cloud")
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(32.dp))

            Text("Cloud Recovery", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.fullRestoreFromCloud()
                    scope.launch { snackbarHostState.showSnackbar("Restoring data from Cloud...") }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore All Data from Cloud")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("App Information", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            val context = LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("$versionName (Titan Suite)") },
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
        }
    }
}

fun getStartOfDay(millis: Long): Long {
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
                SummaryCard(2, 450.0, 120.0, title = "Today's Overview")
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
