package com.example.myapplication.util

import android.content.Context
import android.os.Environment
import com.example.myapplication.data.local.OrderEntity
import com.example.myapplication.data.local.OrderLineItemEntity
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExportService(private val context: Context) {

    fun exportToCsv(orders: List<OrderEntity>, lineItems: List<OrderLineItemEntity>): String? {
        val fileName = "EatUp_Sales_Export_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        val rows = mutableListOf<List<String>>()
        
        rows.add(listOf("Date", "Order #", "Item Name", "Qty", "Cost Price", "List Price", "Delivery", "Discount", "Total Sales", "Total Profit"))
        
        orders.forEach { order ->
            val itemsForOrder = lineItems.filter { it.orderId == order.id }
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(order.timestamp))
            
            if (itemsForOrder.isEmpty()) {
                rows.add(listOf(
                    dateStr, order.dailyOrderNumber.toString(), "No Items", "0", "0", "0",
                    order.deliveryCharge.toString(), order.discount.toString(),
                    (order.totalListPrice + order.deliveryCharge - order.discount).toString(),
                    order.profit.toString()
                ))
            } else {
                itemsForOrder.forEachIndexed { index, item ->
                    rows.add(listOf(
                        if (index == 0) dateStr else "",
                        if (index == 0) order.dailyOrderNumber.toString() else "",
                        item.itemName,
                        item.quantity.toString(),
                        item.costPriceAtTime.toString(),
                        item.listPriceAtTime.toString(),
                        if (index == 0) order.deliveryCharge.toString() else "",
                        if (index == 0) order.discount.toString() else "",
                        if (index == 0) (order.totalListPrice + order.deliveryCharge - order.discount).toString() else "",
                        if (index == 0) order.profit.toString() else ""
                    ))
                }
            }
        }

        return try {
            csvWriter().writeAll(rows, file)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
