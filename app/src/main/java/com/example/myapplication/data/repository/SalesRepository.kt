package com.example.myapplication.data.repository

import com.example.myapplication.data.local.*
import com.example.myapplication.data.remote.CloudRestoreResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class SalesRepository(private val salesDao: SalesDao) {

    fun getAllRestaurants(): Flow<List<RestaurantEntity>> = salesDao.getAllRestaurants()

    suspend fun insertRestaurant(name: String) {
        salesDao.insertRestaurant(RestaurantEntity(name = name))
    }

    suspend fun deleteRestaurant(restaurant: RestaurantEntity) = salesDao.deleteRestaurant(restaurant)

    fun getMenuItemsForRestaurant(restaurantId: Long): Flow<List<MenuItemEntity>> =
        salesDao.getMenuItemsForRestaurant(restaurantId)

    fun getAllMenuItems(): Flow<List<MenuItemEntity>> = salesDao.getAllMenuItems()

    suspend fun insertMenuItem(restaurantId: Long, name: String, cost: Double, list: Double) {
        salesDao.insertMenuItem(MenuItemEntity(
            restaurantId = restaurantId,
            name = name,
            costPrice = cost,
            listPrice = list
        ))
    }

    suspend fun updateMenuItem(item: MenuItemEntity) {
        salesDao.updateMenuItem(item)
    }

    suspend fun deleteMenuItem(item: MenuItemEntity) = salesDao.deleteMenuItem(item)

    fun getOrdersForToday(): Flow<List<OrderEntity>> {
        val today = getStartOfDay(System.currentTimeMillis())
        return salesDao.getOrdersForDate(today)
    }

    suspend fun createOrder(
        itemsWithRestaurant: List<Triple<MenuItemEntity, String, Int>>,
        deliveryCharge: Double,
        discount: Double,
        customTimestamp: Long = System.currentTimeMillis()
    ) {
        val dateForOrder = getStartOfDay(customTimestamp)
        val nextOrderNumber = (salesDao.getMaxOrderNumberForDate(dateForOrder) ?: 0) + 1
        
        var totalCost = 0.0
        var totalList = 0.0
        
        val lineItems = itemsWithRestaurant.map { (menuItem, restaurantName, qty) ->
            totalCost += menuItem.costPrice * qty
            totalList += menuItem.listPrice * qty
            OrderLineItemEntity(
                orderId = 0,
                menuItemId = menuItem.id,
                itemName = menuItem.name,
                restaurantName = restaurantName,
                quantity = qty,
                costPriceAtTime = menuItem.costPrice,
                listPriceAtTime = menuItem.listPrice
            )
        }

        val profit = (totalList - totalCost) + deliveryCharge - discount

        val order = OrderEntity(
            date = dateForOrder,
            timestamp = customTimestamp,
            dailyOrderNumber = nextOrderNumber,
            deliveryCharge = deliveryCharge,
            discount = discount,
            totalCostPrice = totalCost,
            totalListPrice = totalList,
            profit = profit
        )

        salesDao.insertOrderWithItems(order, lineItems)
    }

    fun getLineItemsForOrder(orderId: Long): Flow<List<OrderLineItemEntity>> =
        salesDao.getLineItemsForOrder(orderId)

    suspend fun resetSyncStatus() = salesDao.markAllAsUnsynced()

    suspend fun resetSyncStatusForDate(date: Long) = salesDao.markDateAsUnsynced(date)

    // Resequence Logic: Fixes Order IDs 1, 2, 3... for a specific date
    suspend fun resequenceOrdersForDate(date: Long) {
        val orders = salesDao.getOrdersForDateOnce(date)
        orders.forEachIndexed { index, order ->
            val correctNumber = index + 1
            if (order.dailyOrderNumber != correctNumber) {
                salesDao.updateOrderNumber(order.id, correctNumber)
            }
        }
    }

    suspend fun updateOrder(
        orderId: Long,
        itemsWithNames: List<Triple<MenuItemEntity, String, Int>>,
        deliveryCharge: Double,
        discount: Double,
        customTimestamp: Long? = null
    ) {
        var totalCost = 0.0
        var totalList = 0.0
        
        val currentOrders = salesDao.getAllOrders().first()
        val currentOrder = currentOrders.find { it.id == orderId } ?: return
        
        val oldDate = currentOrder.date

        // TIME-MERGE LOGIC: Combine NEW DATE with ORIGINAL HOUR/MINUTE
        val finalTimestamp = if (customTimestamp != null) {
            val originalCal = Calendar.getInstance().apply { timeInMillis = currentOrder.timestamp }
            val newCal = Calendar.getInstance().apply { 
                timeInMillis = customTimestamp
                set(Calendar.HOUR_OF_DAY, originalCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, originalCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, originalCal.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, originalCal.get(Calendar.MILLISECOND))
            }
            newCal.timeInMillis
        } else {
            currentOrder.timestamp
        }
        
        val newDate = getStartOfDay(finalTimestamp)

        val newLineItems = itemsWithNames.map { (menuItem, restaurantName, qty) ->
            totalCost += menuItem.costPrice * qty
            totalList += menuItem.listPrice * qty
            OrderLineItemEntity(
                orderId = orderId,
                menuItemId = menuItem.id,
                itemName = menuItem.name,
                restaurantName = restaurantName,
                quantity = qty,
                costPriceAtTime = menuItem.costPrice,
                listPriceAtTime = menuItem.listPrice
            )
        }

        val profit = (totalList - totalCost) + deliveryCharge - discount

        val updatedOrder = currentOrder.copy(
            date = newDate,
            timestamp = finalTimestamp,
            deliveryCharge = deliveryCharge,
            discount = discount,
            totalCostPrice = totalCost,
            totalListPrice = totalList,
            profit = profit,
            isSynced = false
        )

        salesDao.deleteLineItemsForOrder(orderId)
        salesDao.updateOrder(updatedOrder)
        newLineItems.forEach { salesDao.insertLineItem(it) }

        // RESEQUENCE BOTH DAYS: Old date and new date
        resequenceOrdersForDate(oldDate)
        if (newDate != oldDate) {
            resequenceOrdersForDate(newDate)
        }
    }

    suspend fun deleteOrderLocally(orderId: Long) {
        val orders = salesDao.getAllOrders().first()
        val order = orders.find { it.id == orderId }
        val orderDate = order?.date
        
        salesDao.deleteLineItemsForOrder(orderId)
        salesDao.deleteOrder(orderId)
        
        // Fix the "Gap" left by the deleted order
        if (orderDate != null) {
            resequenceOrdersForDate(orderDate)
        }
    }

    suspend fun clearAllData() {
        salesDao.clearRestaurants()
        salesDao.clearMenuItems()
        salesDao.clearOrders()
    }

    suspend fun restoreData(response: CloudRestoreResponse) {
        clearAllData()
        
        val restaurantNameMap = mutableMapOf<String, Long>()
        if (response.restaurants.isNotEmpty()) {
            response.restaurants.forEach { oldRest ->
                val newId = salesDao.insertRestaurant(RestaurantEntity(name = oldRest.name))
                restaurantNameMap[oldRest.name] = newId
            }

            response.menuItems.forEach { oldItem ->
                val oldRestaurant = response.restaurants.find { it.id == oldItem.restaurantId }
                val newRestaurantId = if (oldRestaurant != null) restaurantNameMap[oldRestaurant.name] else null
                if (newRestaurantId != null) {
                    salesDao.insertMenuItem(oldItem.copy(id = 0, restaurantId = newRestaurantId))
                }
            }
        }

        if (restaurantNameMap.isEmpty() && response.lineItems.isNotEmpty()) {
            response.lineItems.forEach { line ->
                if (!restaurantNameMap.containsKey(line.restaurantName)) {
                    val newId = salesDao.insertRestaurant(RestaurantEntity(name = line.restaurantName))
                    restaurantNameMap[line.restaurantName] = newId
                }
                val restId = restaurantNameMap[line.restaurantName]!!
                val currentItems = salesDao.getAllMenuItems().first()
                if (currentItems.none { it.name == line.itemName && it.restaurantId == restId }) {
                    salesDao.insertMenuItem(MenuItemEntity(
                        restaurantId = restId,
                        name = line.itemName,
                        costPrice = line.costPriceAtTime,
                        listPrice = line.listPriceAtTime
                    ))
                }
            }
        }

        val orderSyncMap = mutableMapOf<String, Long>()
        response.orders.forEach { oldOrder ->
            if (!orderSyncMap.containsKey(oldOrder.syncId)) {
                val newOrderId = salesDao.insertOrder(oldOrder.copy(id = 0))
                orderSyncMap[oldOrder.syncId] = newOrderId
            }
        }

        val newlyCreatedMenuItems = salesDao.getAllMenuItems().first()
        response.lineItems.forEach { item ->
            val parentId = orderSyncMap[item.syncId] ?: return@forEach
            val catalogMatch = newlyCreatedMenuItems.find { 
                it.name.equals(item.itemName, ignoreCase = true) && 
                restaurantNameMap[item.restaurantName] == it.restaurantId 
            }
            salesDao.insertLineItem(OrderLineItemEntity(
                orderId = parentId,
                menuItemId = catalogMatch?.id ?: 0,
                itemName = item.itemName,
                restaurantName = item.restaurantName,
                quantity = item.quantity,
                costPriceAtTime = item.costPriceAtTime,
                listPriceAtTime = item.listPriceAtTime
            ))
        }
    }

    fun getAllOrders(): Flow<List<OrderEntity>> = salesDao.getAllOrders()

    fun getAllLineItems(): Flow<List<OrderLineItemEntity>> = salesDao.getAllLineItems()

    fun getTopItemsMulti(monthYears: List<String>): Flow<List<ItemInsight>> = 
        salesDao.getTopItemsBySalesMulti(monthYears)

    fun getTopItemsByProfitMulti(monthYears: List<String>): Flow<List<ItemInsight>> = 
        salesDao.getTopItemsByProfitMulti(monthYears)
    
    fun getRestaurantInsightsMulti(monthYears: List<String>): Flow<List<RestaurantInsight>> = 
        salesDao.getRestaurantInsightsBySalesMulti(monthYears)

    fun getRestaurantInsightsByProfitMulti(monthYears: List<String>): Flow<List<RestaurantInsight>> = 
        salesDao.getRestaurantInsightsByProfitMulti(monthYears)

    fun getWeeklyVelocity(startDate: Long, endDate: Long): Flow<List<DailyVelocity>> = 
        salesDao.getWeeklyVelocity(startDate, endDate)

    fun getWeekdayAverages(startDate: Long, endDate: Long): Flow<List<WeekdayAverage>> = 
        salesDao.getWeekdayAverages(startDate, endDate)

    fun getMonthlyTrends(): Flow<List<MonthlyTrend>> = 
        salesDao.getMonthlyTrendsRolling()

    fun getBundleOpportunities(): Flow<List<BundleOpportunity>> = salesDao.getBundleOpportunities()

    suspend fun getFirstOrderDate(): Long {
        return salesDao.getAllOrders().first().minByOrNull { it.date }?.date ?: System.currentTimeMillis()
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
}
