package com.example.myapplication.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.myapplication.data.local.*
import com.example.myapplication.data.remote.*
import com.example.myapplication.data.repository.SalesRepository
import com.example.myapplication.util.AppVersionResponse
import com.example.myapplication.util.ExportService
import com.example.myapplication.util.UpdateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SalesViewModel(
    private val repository: SalesRepository,
    private val exportService: ExportService,
    private val workManager: WorkManager,
    private val updateManager: UpdateManager,
    context: Context
) : ViewModel() {

    val restaurants = repository.getAllRestaurants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allMenuItems = repository.getAllMenuItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todayOrders = repository.getOrdersForToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allOrders = repository.getAllOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allLineItems = repository.getAllLineItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // --- Growth Analytics Range States ---

    private val _analyticsWeeklyRange = MutableStateFlow(
        Pair(System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L), System.currentTimeMillis())
    )
    val analyticsWeeklyRange = _analyticsWeeklyRange.asStateFlow()

    private val _analyticsWeekdayRange = MutableStateFlow(
        Pair(0L, System.currentTimeMillis())
    )
    val analyticsWeekdayRange = _analyticsWeekdayRange.asStateFlow()

    private val _analyticsSelectedMonths = MutableStateFlow<List<Long>>(listOf(getStartOfMonth()))
    val analyticsSelectedMonths = _analyticsSelectedMonths.asStateFlow()

    private val _analyticsSelectedRestMonths = MutableStateFlow<List<Long>>(listOf(getStartOfMonth()))
    val analyticsSelectedRestMonths = _analyticsSelectedRestMonths.asStateFlow()

    // --- Growth Analytics Flows ---

    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyVelocity = analyticsWeeklyRange.flatMapLatest { range ->
        repository.getWeeklyVelocity(repository.getStartOfDay(range.first), repository.getStartOfDay(range.second))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekdayAverages = analyticsWeekdayRange.flatMapLatest { range ->
        repository.getWeekdayAverages(repository.getStartOfDay(range.first), repository.getStartOfDay(range.second))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topItemsBySales = analyticsSelectedMonths.flatMapLatest { months ->
        repository.getTopItemsMulti(months.map { toMonthYearString(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topItemsByProfit = analyticsSelectedMonths.flatMapLatest { months ->
        repository.getTopItemsByProfitMulti(months.map { toMonthYearString(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val restaurantInsightsBySales = analyticsSelectedRestMonths.flatMapLatest { months ->
        repository.getRestaurantInsightsMulti(months.map { toMonthYearString(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val restaurantInsightsByProfit = analyticsSelectedRestMonths.flatMapLatest { months ->
        repository.getRestaurantInsightsByProfitMulti(months.map { toMonthYearString(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyTrends = repository.getMonthlyTrends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bundleOpportunities = repository.getBundleOpportunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = context.getSharedPreferences("eatup_prefs", Context.MODE_PRIVATE)
    private val _googleSheetsUrl = MutableStateFlow(prefs.getString("google_sheets_url", "") ?: "")
    val googleSheetsUrl = _googleSheetsUrl.asStateFlow()

    fun updateSheetsUrl(url: String) {
        prefs.edit().putString("google_sheets_url", url).apply()
        _googleSheetsUrl.value = url
    }

    fun forceResync(date: Long? = null) {
        viewModelScope.launch {
            if (date != null) {
                repository.resetSyncStatusForDate(repository.getStartOfDay(date))
            } else {
                repository.resetSyncStatus()
            }
            triggerSync()
        }
    }

    private val _historyDate = MutableStateFlow<Long?>(null)
    val historyDate = _historyDate.asStateFlow()

    fun setHistoryDate(date: Long?) { _historyDate.value = date }

    private val _historyMonth = MutableStateFlow(getStartOfMonth())
    val historyMonth = _historyMonth.asStateFlow()

    fun setHistoryMonth(millis: Long) { _historyMonth.value = millis }

    fun getLineItemsForOrder(orderId: Long) = repository.getLineItemsForOrder(orderId)

    private val _selectedItems = MutableStateFlow<Map<MenuItemEntity, Int>>(emptyMap())
    val selectedItems = _selectedItems.asStateFlow()

    private val _editDeliveryCharge = MutableStateFlow("")
    val editDeliveryCharge = _editDeliveryCharge.asStateFlow()

    private val _editDiscount = MutableStateFlow("")
    val editDiscount = _editDiscount.asStateFlow()

    private val _editOrderDate = MutableStateFlow(System.currentTimeMillis())
    val editOrderDate = _editOrderDate.asStateFlow()

    fun setEditOrderDate(millis: Long) { _editOrderDate.value = millis }

    fun setWeeklyRange(startDate: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startDate
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        _analyticsWeeklyRange.value = Pair(startDate, calendar.timeInMillis)
    }

    fun setWeekdayRange(startDate: Long, endDate: Long) {
        _analyticsWeekdayRange.value = Pair(startDate, endDate)
    }

    fun togglePerformanceMonth(millis: Long) {
        val current = _analyticsSelectedMonths.value.toMutableList()
        if (current.contains(millis)) {
            if (current.size > 1) current.remove(millis)
        } else {
            current.add(millis)
        }
        _analyticsSelectedMonths.value = current
    }

    fun toggleRestaurantMonth(millis: Long) {
        val current = _analyticsSelectedRestMonths.value.toMutableList()
        if (current.contains(millis)) {
            if (current.size > 1) current.remove(millis)
        } else {
            current.add(millis)
        }
        _analyticsSelectedRestMonths.value = current
    }

    fun resetAnalyticsFilters() {
        _analyticsWeeklyRange.value = Pair(System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L), System.currentTimeMillis())
        _analyticsWeekdayRange.value = Pair(0L, System.currentTimeMillis())
        _analyticsSelectedMonths.value = listOf(getStartOfMonth())
        _analyticsSelectedRestMonths.value = listOf(getStartOfMonth())
    }

    private fun toMonthYearString(millis: Long): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(millis))
    }

    fun addRestaurant(name: String) = viewModelScope.launch { 
        repository.insertRestaurant(name)
        backupToCloud()
    }
    fun deleteRestaurant(restaurant: RestaurantEntity) = viewModelScope.launch { repository.deleteRestaurant(restaurant) }

    fun addMenuItem(restaurantId: Long, name: String, cost: Double, list: Double) = 
        viewModelScope.launch { 
            repository.insertMenuItem(restaurantId, name, cost, list)
            backupToCloud()
        }
    
    fun editMenuItem(item: MenuItemEntity) = 
        viewModelScope.launch { 
            repository.updateMenuItem(item)
            backupToCloud()
        }

    fun deleteMenuItem(item: MenuItemEntity) = viewModelScope.launch { repository.deleteMenuItem(item) }

    fun addItemToOrder(item: MenuItemEntity) {
        val current = _selectedItems.value.toMutableMap()
        current[item] = (current[item] ?: 0) + 1
        _selectedItems.value = current
    }

    fun removeItemFromOrder(item: MenuItemEntity) {
        val current = _selectedItems.value.toMutableMap()
        val qty = current[item] ?: 0
        if (qty > 1) current[item] = qty - 1 else current.remove(item)
        _selectedItems.value = current
    }

    fun clearOrder() { 
        _selectedItems.value = emptyMap() 
        _editDeliveryCharge.value = ""
        _editDiscount.value = ""
    }

    private val _orderDate = MutableStateFlow(System.currentTimeMillis())
    val orderDate = _orderDate.asStateFlow()

    fun setOrderDate(millis: Long) { _orderDate.value = millis }

    fun submitOrder(deliveryCharge: Double, discount: Double) = viewModelScope.launch {
        val itemsWithNames = _selectedItems.value.map { (item, qty) ->
            val restaurantName = restaurants.value.find { it.id == item.restaurantId }?.name ?: "Unknown"
            Triple(item, restaurantName, qty)
        }
        repository.createOrder(itemsWithNames, deliveryCharge, discount, _orderDate.value)
        clearOrder()
        setOrderDate(System.currentTimeMillis())
        triggerSync()
    }

    // Editing Logic: Refined for v13.6 to handle deep-link recovery
    fun startEditingOrder(orderId: Long) {
        viewModelScope.launch {
            val orders = repository.getAllOrders().first()
            val order = orders.find { it.id == orderId }
            if (order != null) {
                _editDeliveryCharge.value = if (order.deliveryCharge > 0) order.deliveryCharge.toString() else ""
                _editDiscount.value = if (order.discount > 0) order.discount.toString() else ""
                _editOrderDate.value = order.timestamp
            }

            val lineItems = repository.getLineItemsForOrder(orderId).first()
            val catalogItems = repository.getAllMenuItems().first()
            
            val newSelection = mutableMapOf<MenuItemEntity, Int>()
            lineItems.forEach { line ->
                // Priority 1: Match by ID
                var menuMatch = catalogItems.find { it.id == line.menuItemId }
                
                // Priority 2: Match by Name (Recovery Logic for Restored Data)
                if (menuMatch == null) {
                   menuMatch = catalogItems.find { it.name.equals(line.itemName, ignoreCase = true) }
                }

                if (menuMatch != null) {
                    newSelection[menuMatch] = line.quantity
                } else {
                    // Scenario: Legacy item not in catalog - Create temporary object for UI persistence
                    val ghostItem = MenuItemEntity(
                        id = line.menuItemId,
                        restaurantId = 0,
                        name = line.itemName,
                        costPrice = line.costPriceAtTime,
                        listPrice = line.listPriceAtTime
                    )
                    newSelection[ghostItem] = line.quantity
                }
            }
            _selectedItems.value = newSelection
        }
    }

    fun updateOrder(orderId: Long, delivery: Double, discount: Double) = viewModelScope.launch {
        val itemsWithNames = _selectedItems.value.map { (item, qty) ->
            val restaurantName = restaurants.value.find { it.id == item.restaurantId }?.name ?: "Unknown"
            Triple(item, restaurantName, qty)
        }
        repository.updateOrder(orderId, itemsWithNames, delivery, discount, _editOrderDate.value)
        clearOrder()
        triggerSync()
    }

    fun deleteOrder(orderId: Long) = viewModelScope.launch {
        val url = googleSheetsUrl.value
        if (url.isNotBlank()) {
            val orders = repository.getAllOrders().first()
            val order = orders.find { it.id == orderId }
            if (order != null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://script.google.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(SheetsApiService::class.java)
                
                val dateObj = Date(order.timestamp)
                val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(dateObj).uppercase() + " sales"
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateObj)

                val deleteRequest = MultiRowSyncRequest(
                    syncId = order.syncId,
                    orderNumber = order.dailyOrderNumber,
                    date = dateStr,
                    monthName = monthName,
                    delivery = 0.0,
                    discount = 0.0,
                    shops = emptyList(),
                    isDelete = true
                )
                try {
                    api.syncOrder(url, deleteRequest)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        repository.deleteOrderLocally(orderId)
    }

    // Cloud Backup & Restore
    fun backupToCloud() = viewModelScope.launch {
        val url = googleSheetsUrl.value
        if (url.isBlank()) return@launch
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(SheetsApiService::class.java)
        
        try {
            api.backupCatalog(url, CatalogBackupRequest(restaurants.value, allMenuItems.value))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun fullRestoreFromCloud() = viewModelScope.launch {
        val url = googleSheetsUrl.value
        if (url.isBlank()) return@launch

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(SheetsApiService::class.java)

        try {
            val response = api.fullRestore(url)
            if (response.isSuccessful && response.body() != null) {
                repository.restoreData(response.body()!!)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun exportData() {
        viewModelScope.launch {
            exportService.exportToCsv(allOrders.value, allLineItems.value)
        }
    }

    fun triggerSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork("google_sheets_sync", ExistingWorkPolicy.REPLACE, syncRequest)
    }

    val syncStatus = workManager.getWorkInfosForUniqueWorkFlow("google_sheets_sync")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Update Checker
    private val _updateInfo = MutableStateFlow<AppVersionResponse?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateInfo.value = updateManager.checkForUpdate()
        }
    }

    fun downloadUpdate() {
        _updateInfo.value?.let { updateManager.downloadUpdate(it.downloadUrl) }
    }

    fun dismissUpdate() { _updateInfo.value = null }

    private fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getStartOfThreeMonthsAgo(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -2)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

class SalesViewModelFactory(
    private val repository: SalesRepository,
    private val exportService: ExportService,
    private val workManager: WorkManager,
    private val updateManager: UpdateManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SalesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SalesViewModel(repository, exportService, workManager, updateManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
