package com.example.myapplication.data.remote

import com.example.myapplication.data.local.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

data class ShopSyncData(
    val shopName: String,
    val items: String,
    val foodAmount: Double,
    val shopProfit: Double,
    val shopCost: Double,
    val itemsMetadata: String // JSON string for perfect recovery
)

data class MultiRowSyncRequest(
    val syncId: String,
    val orderNumber: Int,
    val date: String,
    val monthName: String,
    val delivery: Double,
    val discount: Double,
    val shops: List<ShopSyncData>,
    val isEdit: Boolean = false,
    val isDelete: Boolean = false
)

data class CatalogBackupRequest(
    val restaurants: List<RestaurantEntity>,
    val menuItems: List<MenuItemEntity>
)

data class RestoreLineItem(
    val itemName: String,
    val restaurantName: String,
    val quantity: Int,
    val costPriceAtTime: Double,
    val listPriceAtTime: Double,
    val syncId: String // Matches "syncId" in Apps Script output
)

data class CloudRestoreResponse(
    val restaurants: List<RestaurantEntity>,
    val menuItems: List<MenuItemEntity>,
    val orders: List<OrderEntity>,
    val lineItems: List<RestoreLineItem>
)

interface SheetsApiService {
    @POST
    suspend fun syncOrder(
        @Url url: String,
        @Body request: MultiRowSyncRequest
    ): Response<Unit>

    @POST
    suspend fun backupCatalog(
        @Url url: String,
        @Body request: CatalogBackupRequest
    ): Response<Unit>

    @GET
    suspend fun fullRestore(
        @Url url: String
    ): Response<CloudRestoreResponse>
}
