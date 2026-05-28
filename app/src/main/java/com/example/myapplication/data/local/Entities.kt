package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactInfo: String = ""
)

@Entity(
    tableName = "menu_items",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("restaurantId")]
)
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val restaurantId: Long,
    val name: String,
    val costPrice: Double,
    val listPrice: Double
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val dailyOrderNumber: Int,
    val deliveryCharge: Double = 0.0,
    val discount: Double = 0.0,
    val totalCostPrice: Double = 0.0,
    val totalListPrice: Double = 0.0,
    val profit: Double = 0.0,
    val isSynced: Boolean = false,
    val syncId: String = UUID.randomUUID().toString()
)

@Entity(
    tableName = "order_line_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderLineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val menuItemId: Long,
    val itemName: String,
    val restaurantName: String,
    val quantity: Int,
    val costPriceAtTime: Double,
    val listPriceAtTime: Double
)
