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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    val topItems = repository.getTopItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val restaurantInsights = repository.getRestaurantInsights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = context.getSharedPreferences("eatup_prefs", Context.MODE_PRIVATE)
    private val _googleSheetsUrl = MutableStateFlow(prefs.getString("google_sheets_url", "") ?: "")
    val googleSheetsUrl = _googleSheetsUrl.asStateFlow()

    fun updateSheetsUrl(url: String) {
        prefs.edit().putString("google_sheets_url", url).apply()
        _googleSheetsUrl.value = url
    }

    fun forceResync() {
        viewModelScope.launch {
            repository.resetSyncStatus()
            triggerSync()
        }
    }

    private val _historyDate = MutableStateFlow<Long?>(null)
    val historyDate = _historyDate.asStateFlow()

    fun setHistoryDate(date: Long?) { _historyDate.value = date }

    fun getLineItemsForOrder(orderId: Long) = repository.getLineItemsForOrder(orderId)

    private val _selectedItems = MutableStateFlow<Map<MenuItemEntity, Int>>(emptyMap())
    val selectedItems = _selectedItems.asStateFlow()

    fun addRestaurant(name: String) = viewModelScope.launch { repository.insertRestaurant(name) }
    fun deleteRestaurant(restaurant: RestaurantEntity) = viewModelScope.launch { repository.deleteRestaurant(restaurant) }

    fun addMenuItem(restaurantId: Long, name: String, cost: Double, list: Double) = 
        viewModelScope.launch { repository.insertMenuItem(restaurantId, name, cost, list) }
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

    fun clearOrder() { _selectedItems.value = emptyMap() }

    fun submitOrder(deliveryCharge: Double, discount: Double) = viewModelScope.launch {
        val itemsWithNames = _selectedItems.value.map { (item, qty) ->
            val restaurantName = restaurants.value.find { it.id == item.restaurantId }?.name ?: "Unknown"
            Triple(item, restaurantName, qty)
        }
        repository.createOrder(itemsWithNames, deliveryCharge, discount)
        clearOrder()
        triggerSync()
    }

    // Editing Logic
    fun startEditingOrder(orderId: Long) {
        viewModelScope.launch {
            val lineItems = repository.getLineItemsForOrder(orderId).first()
            val menuItems = repository.getAllMenuItems().first()
            
            val newSelection = mutableMapOf<MenuItemEntity, Int>()
            lineItems.forEach { line ->
                val menu = menuItems.find { it.id == line.menuItemId }
                if (menu != null) newSelection[menu] = line.quantity
            }
            _selectedItems.value = newSelection
        }
    }

    fun updateOrder(orderId: Long, delivery: Double, discount: Double) = viewModelScope.launch {
        val itemsWithNames = _selectedItems.value.map { (item, qty) ->
            val restaurantName = restaurants.value.find { it.id == item.restaurantId }?.name ?: "Unknown"
            Triple(item, restaurantName, qty)
        }
        repository.updateOrder(orderId, itemsWithNames, delivery, discount)
        clearOrder()
        triggerSync()
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
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
