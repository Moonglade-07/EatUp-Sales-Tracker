package com.example.myapplication.data.remote

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.AppDatabase
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "EatUpSync"
    private val gson = Gson()

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val dao = database.salesDao()
            
            val prefs = applicationContext.getSharedPreferences("eatup_prefs", Context.MODE_PRIVATE)
            val settingsUrl = prefs.getString("google_sheets_url", "") ?: ""
            
            val syncUrl = settingsUrl

            if (syncUrl.isBlank()) {
                Log.w(TAG, "No Google Sheets URL found in settings. Sync skipped.")
                return Result.success()
            }

            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://script.google.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val api = retrofit.create(SheetsApiService::class.java)

            // ALWAYS BACKUP CATALOG BEFORE SYNCING ORDERS
            try {
                val restaurants = dao.getAllRestaurants().first()
                val menuItems = dao.getAllMenuItems().first()
                api.backupCatalog(syncUrl, CatalogBackupRequest(restaurants, menuItems))
            } catch (e: Exception) {
                Log.e(TAG, "Catalog backup failed: ${e.message}")
            }

            val unsyncedOrders = dao.getUnsyncedOrders()
            if (unsyncedOrders.isEmpty()) return Result.success()

            var allSuccess = true
            unsyncedOrders.forEach { order ->
                try {
                    val lineItems = dao.getLineItemsForOrder(order.id).first()
                    
                    val shopGroupings = lineItems.groupBy { it.restaurantName }.map { (shopName, items) ->
                        val itemsStr = items.joinToString(" + ") { "${it.quantity} x ${it.itemName}" }
                        val foodAmount = items.sumOf { it.listPriceAtTime * it.quantity }
                        val shopProfit = items.sumOf { (it.listPriceAtTime - it.costPriceAtTime) * it.quantity }
                        val shopCost = items.sumOf { it.costPriceAtTime * it.quantity }
                        
                        // Metadata for perfect restore
                        val metadata = items.map { 
                            mapOf(
                                "name" to it.itemName,
                                "qty" to it.quantity,
                                "cost" to it.costPriceAtTime,
                                "list" to it.listPriceAtTime
                            )
                        }
                        
                        ShopSyncData(
                            shopName = shopName,
                            items = itemsStr,
                            foodAmount = foodAmount,
                            shopProfit = shopProfit,
                            shopCost = shopCost,
                            itemsMetadata = gson.toJson(metadata)
                        )
                    }
                    
                    val dateObj = Date(order.timestamp)
                    val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(dateObj).uppercase() + " sales"
                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateObj)

                    val request = MultiRowSyncRequest(
                        syncId = order.syncId,
                        orderNumber = order.dailyOrderNumber,
                        date = dateStr,
                        monthName = monthName,
                        delivery = order.deliveryCharge,
                        discount = order.discount,
                        shops = shopGroupings,
                        isEdit = true
                    )

                    val response = api.syncOrder(syncUrl, request)
                    if (response.isSuccessful) {
                        dao.markOrderAsSynced(order.id)
                        // Add delay to prevent Apps Script lock contention during batch resync
                        kotlinx.coroutines.delay(1000)
                    } else {
                        allSuccess = false
                    }
                } catch (e: Exception) {
                    allSuccess = false
                }
            }

            if (allSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
