package com.example.myapplication.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

data class ShopSyncData(
    val shopName: String,
    val items: String,
    val foodAmount: Double,
    val shopProfit: Double,
    val shopCost: Double
)

data class MultiRowSyncRequest(
    val syncId: String,
    val orderNumber: Int,
    val date: String,
    val monthName: String,
    val delivery: Double,
    val discount: Double,
    val shops: List<ShopSyncData>
)

interface SheetsApiService {
    @POST
    suspend fun syncOrder(
        @Url url: String,
        @Body request: MultiRowSyncRequest
    ): Response<Unit>
}
