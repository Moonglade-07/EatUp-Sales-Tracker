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

    suspend fun deleteMenuItem(item: MenuItemEntity) = salesDao.deleteMenuItem(item)

    fun getOrdersForToday(): Flow<List<OrderEntity>> {
        val today = getStartOfDay()
        return salesDao.getOrdersForDate(today)
    }

    suspend fun createOrder(
        itemsWithRestaurant: List<Triple<MenuItemEntity, String, Int>>,
        deliveryCharge: Double,
        discount: Double
    ) {
        val today = getStartOfDay()
        val nextOrderNumber = (salesDao.getMaxOrderNumberForDate(today) ?: 0) + 1
        
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
            date = today,
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

    suspend fun updateOrder(
        orderId: Long,
        itemsWithRestaurant: List<Triple<MenuItemEntity, String, Int>>,
        deliveryCharge: Double,
        discount: Double
    ) {
        var totalCost = 0.0
        var totalList = 0.0
        
        val newLineItems = itemsWithRestaurant.map { (menuItem, restaurantName, qty) ->
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
        
        val currentOrders = salesDao.getAllOrders().first()
        val currentOrder = currentOrders.find { it.id == orderId } ?: return

        val updatedOrder = currentOrder.copy(
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
    }

    suspend fun clearAllData() {
        salesDao.clearRestaurants()
        salesDao.clearMenuItems()
        salesDao.clearOrders()
    }

    suspend fun restoreData(response: CloudRestoreResponse) {
        clearAllData()
        response.restaurants.forEach { salesDao.insertRestaurant(it) }
        response.menuItems.forEach { salesDao.insertMenuItem(it) }
        response.orders.forEach { salesDao.insertOrder(it) }
        response.lineItems.forEach { salesDao.insertLineItem(it) }
    }

    fun getAllOrders(): Flow<List<OrderEntity>> = salesDao.getAllOrders()

    fun getAllLineItems(): Flow<List<OrderLineItemEntity>> = salesDao.getAllLineItems()

    fun getTopItems(): Flow<List<ItemInsight>> = salesDao.getTopItemsByProfit()
    
    fun getRestaurantInsights(): Flow<List<RestaurantInsight>> = salesDao.getRestaurantInsights()

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
