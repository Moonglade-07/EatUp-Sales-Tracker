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

    @Update
    suspend fun updateMenuItem(item: MenuItemEntity)

    @Delete
    suspend fun deleteMenuItem(item: MenuItemEntity)

    @Query("SELECT * FROM orders WHERE date = :date")
    fun getOrdersForDate(date: Long): Flow<List<OrderEntity>>

    @Query("SELECT MAX(dailyOrderNumber) FROM orders WHERE date = :date")
    suspend fun getMaxOrderNumberForDate(date: Long): Int?

    @Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderLineItemEntity>) {
        val id = insertOrder(order)
        items.forEach { insertLineItem(it.copy(orderId = id)) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItem(item: OrderLineItemEntity)

    @Query("SELECT * FROM order_line_items WHERE orderId = :orderId")
    fun getLineItemsForOrder(orderId: Long): Flow<List<OrderLineItemEntity>>

    @Query("SELECT * FROM orders")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE isSynced = 0")
    suspend fun getUnsyncedOrders(): List<OrderEntity>

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderAsSynced(orderId: Long)

    @Query("UPDATE orders SET isSynced = 0 WHERE date = :dateInMillis")
    suspend fun markDateAsUnsynced(dateInMillis: Long)

    @Query("UPDATE orders SET isSynced = 0")
    suspend fun markAllAsUnsynced()

    @Query("DELETE FROM order_line_items WHERE orderId = :orderId")
    suspend fun deleteLineItemsForOrder(orderId: Long)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("UPDATE orders SET dailyOrderNumber = :newNumber, isSynced = 0 WHERE id = :orderId")
    suspend fun updateOrderNumber(orderId: Long, newNumber: Int)

    @Query("SELECT * FROM orders WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getOrdersForDateOnce(date: Long): List<OrderEntity>

    @Query("DELETE FROM restaurants")
    suspend fun clearRestaurants()

    @Query("DELETE FROM menu_items")
    suspend fun clearMenuItems()

    @Query("DELETE FROM orders")
    suspend fun clearOrders()

    @Query("SELECT * FROM order_line_items")
    fun getAllLineItems(): Flow<List<OrderLineItemEntity>>

    @Query("""
        SELECT itemName as name, restaurantName, SUM(quantity) as count, SUM(listPriceAtTime * quantity) as salesAmount 
        FROM order_line_items 
        INNER JOIN orders ON order_line_items.orderId = orders.id
        WHERE strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch', '+5 hours', '30 minutes')) IN (:monthYears)
        GROUP BY itemName, restaurantName 
        ORDER BY salesAmount DESC 
        LIMIT 10
    """)
    fun getTopItemsBySalesMulti(monthYears: List<String>): Flow<List<ItemInsight>>

    @Query("""
        SELECT restaurantName as name, COUNT(DISTINCT orderId) as count, SUM(listPriceAtTime * quantity) as salesAmount 
        FROM order_line_items 
        INNER JOIN orders ON order_line_items.orderId = orders.id
        WHERE strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch', '+5 hours', '30 minutes')) IN (:monthYears)
        GROUP BY restaurantName 
        ORDER BY salesAmount DESC 
        LIMIT 5
    """)
    fun getRestaurantInsightsBySalesMulti(monthYears: List<String>): Flow<List<RestaurantInsight>>

    @Query("""
        SELECT restaurantName as name, COUNT(DISTINCT orderId) as count, SUM((listPriceAtTime - costPriceAtTime) * quantity) as salesAmount 
        FROM order_line_items 
        INNER JOIN orders ON order_line_items.orderId = orders.id
        WHERE strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch', '+5 hours', '30 minutes')) IN (:monthYears)
        GROUP BY restaurantName 
        ORDER BY salesAmount DESC 
        LIMIT 5
    """)
    fun getRestaurantInsightsByProfitMulti(monthYears: List<String>): Flow<List<RestaurantInsight>>

    // --- Growth Analytics Queries ---

    @Query("""
        SELECT date, SUM(totalListPrice + deliveryCharge - discount) as totalSales, SUM(profit) as totalProfit 
        FROM orders 
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY date 
        ORDER BY date ASC
    """)
    fun getWeeklyVelocity(startDate: Long, endDate: Long): Flow<List<DailyVelocity>>

    @Query("""
        SELECT 
            CAST(strftime('%w', datetime(date / 1000, 'unixepoch')) AS INTEGER) as dayOfWeek,
            AVG(dailyTotalSales) as avgSales,
            AVG(dailyTotalProfit) as avgProfit
        FROM (
            SELECT 
                date,
                SUM(totalListPrice + deliveryCharge - discount) as dailyTotalSales,
                SUM(profit) as dailyTotalProfit
            FROM orders
            WHERE date >= :startDate AND date <= :endDate
            GROUP BY date
        )
        GROUP BY dayOfWeek
        ORDER BY dayOfWeek ASC
    """)
    fun getWeekdayAverages(startDate: Long, endDate: Long): Flow<List<WeekdayAverage>>

    @Query("""
        SELECT 
            strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch', '+5 hours', '30 minutes')) as monthYear,
            SUM(totalListPrice + deliveryCharge - discount) as totalSales
        FROM orders
        GROUP BY monthYear
        ORDER BY monthYear ASC
        LIMIT 12
    """)
    fun getMonthlyTrendsRolling(): Flow<List<MonthlyTrend>>

    @Query("""
        SELECT a.itemName as itemA, b.itemName as itemB, COUNT(*) as count 
        FROM order_line_items a 
        INNER JOIN order_line_items b ON a.orderId = b.orderId AND a.id < b.id 
        GROUP BY itemA, itemB 
        ORDER BY count DESC 
        LIMIT 10
    """)
    fun getBundleOpportunities(): Flow<List<BundleOpportunity>>

    @Query("""
        SELECT itemName as name, restaurantName, SUM(quantity) as count, SUM((listPriceAtTime - costPriceAtTime) * quantity) as salesAmount 
        FROM order_line_items 
        INNER JOIN orders ON order_line_items.orderId = orders.id
        WHERE strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch', '+5 hours', '30 minutes')) IN (:monthYears)
        GROUP BY itemName, restaurantName 
        ORDER BY salesAmount DESC 
        LIMIT 10
    """)
    fun getTopItemsByProfitMulti(monthYears: List<String>): Flow<List<ItemInsight>>
}

data class ItemInsight(val name: String, val restaurantName: String, val count: Int, val salesAmount: Double)
data class RestaurantInsight(val name: String, val count: Int, val salesAmount: Double)
