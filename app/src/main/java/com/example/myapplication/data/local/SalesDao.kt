package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    @Query("SELECT * FROM restaurants")
    fun getAllRestaurants(): Flow<List<RestaurantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestaurant(restaurant: RestaurantEntity): Long

    @Delete
    suspend fun deleteRestaurant(restaurant: RestaurantEntity)

    @Query("SELECT * FROM menu_items WHERE restaurantId = :restaurantId")
    fun getMenuItemsForRestaurant(restaurantId: Long): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items")
    fun getAllMenuItems(): Flow<List<MenuItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItem(item: MenuItemEntity)

    @Delete
    suspend fun deleteMenuItem(item: MenuItemEntity)

    @Query("SELECT * FROM orders WHERE date = :date ORDER BY dailyOrderNumber ASC")
    fun getOrdersForDate(date: Long): Flow<List<OrderEntity>>

    @Query("SELECT MAX(dailyOrderNumber) FROM orders WHERE date = :date")
    suspend fun getMaxOrderNumberForDate(date: Long): Int?

    @Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderLineItemEntity>) {
        val orderId = insertOrder(order)
        items.forEach { insertLineItem(it.copy(orderId = orderId)) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItem(item: OrderLineItemEntity)

    @Query("SELECT * FROM order_line_items WHERE orderId = :orderId")
    fun getLineItemsForOrder(orderId: Long): Flow<List<OrderLineItemEntity>>
    
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedOrders(): List<OrderEntity>

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderAsSynced(orderId: Long)

    @Query("UPDATE orders SET isSynced = 0")
    suspend fun markAllAsUnsynced()

    @Query("SELECT * FROM order_line_items")
    fun getAllLineItems(): Flow<List<OrderLineItemEntity>>

    @Query("""
        SELECT itemName as name, SUM(quantity) as count, SUM((listPriceAtTime - costPriceAtTime) * quantity) as profit 
        FROM order_line_items 
        GROUP BY menuItemId 
        ORDER BY profit DESC 
        LIMIT 5
    """)
    fun getTopItemsByProfit(): Flow<List<ItemInsight>>

    @Query("""
        SELECT restaurantName as name, COUNT(DISTINCT orderId) as count, SUM((listPriceAtTime - costPriceAtTime) * quantity) as profit 
        FROM order_line_items 
        GROUP BY restaurantName 
        ORDER BY profit DESC
    """)
    fun getRestaurantInsights(): Flow<List<RestaurantInsight>>
}

data class ItemInsight(val name: String, val count: Int, val profit: Double)
data class RestaurantInsight(val name: String, val count: Int, val profit: Double)
