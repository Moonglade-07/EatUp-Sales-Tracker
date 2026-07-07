package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.local.BundleOpportunity
import com.example.myapplication.data.local.DailyVelocity
import com.example.myapplication.data.local.ItemInsight
import com.example.myapplication.data.local.RestaurantInsight
import com.example.myapplication.data.local.MonthlyTrend
import com.example.myapplication.data.local.WeekdayAverage
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.Position
import java.text.SimpleDateFormat
import java.util.*

private val labelKey = ExtraStore.Key<List<String>>()

@Composable
fun rememberMarker(showTitle: Boolean = false): CartesianMarker {
    val labelBackground = rememberShapeComponent(
        shape = Shape.Rectangle,
        fill = fill(Color.Black.copy(alpha = 0.8f))
    )
    val label = rememberTextComponent(
        color = Color.White,
        background = labelBackground,
        padding = remember { Insets(allDp = 8f) },
    )
    val guideline = rememberLineComponent(fill = fill(Color.Gray.copy(alpha = 0.5f)), thickness = 1.dp)
    
    val valueFormatter = DefaultCartesianMarker.ValueFormatter { context, targets ->
        val labels = context.model.extraStore[labelKey]
        val x = targets.first().x.toInt()
        val title = labels.getOrNull(x) ?: ""
        
        val stringBuilder = StringBuilder()
        if (showTitle) {
            stringBuilder.append(title)
        }
        
        targets.forEach { target ->
            when (target) {
                is ColumnCartesianLayerMarkerTarget -> {
                    target.columns.forEachIndexed { index, column ->
                        val seriesLabel = if (target.columns.size > 1) {
                            if (index == 0) "Sales" else "Profit"
                        } else "Amount"
                        if (stringBuilder.isNotEmpty()) stringBuilder.append("\n")
                        stringBuilder.append("$seriesLabel: ₹${"%.2f".format(column.entry.y)}")
                    }
                }
                is LineCartesianLayerMarkerTarget -> {
                    target.points.forEach { point ->
                        if (stringBuilder.isNotEmpty()) stringBuilder.append("\n")
                        stringBuilder.append("Amount: ₹${"%.2f".format(point.entry.y)}")
                    }
                }
            }
        }
        stringBuilder
    }

    return rememberDefaultCartesianMarker(
        label = label,
        indicator = { _ -> 
            ShapeComponent(shape = Shape.Rectangle, fill = Fill.Black) 
        },
        guideline = guideline,
        valueFormatter = valueFormatter
    )
}

@Composable
fun GroupedSalesBarChart(
    data: List<DailyVelocity>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val marker = rememberMarker(showTitle = false) 
    
    val labels = remember(data) {
        data.map { SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date(it.date)) }
    }

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(data.map { it.totalSales })
                    series(data.map { it.totalProfit })
                }
                extras { it[labelKey] = labels }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weekly Sales & Profit", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            
            if (data.isEmpty()) {
                NoDataView(Modifier.height(200.dp))
            } else {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(
                            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                rememberLineComponent(fill = fill(Color(0xFF1976D2)), thickness = 8.dp),
                                rememberLineComponent(fill = fill(Color(0xFF4CAF50)), thickness = 8.dp)
                            ),
                            mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() }
                        ),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, x, _ -> 
                                labels.getOrNull(x.toInt()) ?: " "
                            }
                        ),
                        marker = marker
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.height(200.dp)
                )
            }
        }
    }
}

@Composable
fun WeekdayAvgChart(
    data: List<WeekdayAverage>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val marker = rememberMarker(showTitle = false) 
    
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(data.map { it.avgSales })
                    series(data.map { it.avgProfit })
                }
                extras { it[labelKey] = labels }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weekday Averages", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            
            if (data.isEmpty()) {
                NoDataView(Modifier.height(200.dp))
            } else {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(
                            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                rememberLineComponent(fill = fill(Color(0xFF1976D2)), thickness = 8.dp),
                                rememberLineComponent(fill = fill(Color(0xFF4CAF50)), thickness = 8.dp)
                            ),
                            mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() }
                        ),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, x, _ -> 
                                labels.getOrNull(x.toInt()) ?: " "
                            }
                        ),
                        marker = marker
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.height(200.dp)
                )
            }
        }
    }
}

@Composable
fun RevenueTrendLineChart(
    data: List<MonthlyTrend>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val marker = rememberMarker(showTitle = false) 
    
    val labels = remember(data) { data.map { it.monthYear } }

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(data.map { it.totalSales })
                }
                extras { it[labelKey] = labels }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Monthly Revenue Trend (Last 12 Months)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            
            if (data.isEmpty()) {
                NoDataView(Modifier.height(200.dp))
            } else {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, x, _ -> 
                                labels.getOrNull(x.toInt()) ?: " "
                            }
                        ),
                        marker = marker
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.height(200.dp)
                )
            }
        }
    }
}

@Composable
fun ItemInsightList(
    title: String,
    data: List<ItemInsight>,
    useProfit: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (data.isEmpty()) {
                NoDataView(Modifier.height(100.dp))
            } else {
                data.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (useProfit) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFF1976D2).copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (useProfit) Color(0xFF4CAF50) else Color(0xFF1976D2),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.count} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Text(
                            "₹${"%.2f".format(item.salesAmount)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (useProfit) Color(0xFF4CAF50) else Color(0xFF1976D2)
                        )
                    }
                    if (index < data.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 44.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoDataView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No data available yet.\nKeep selling to see insights!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BundleOpportunityCard(bundle: BundleOpportunity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${bundle.itemA} + ${bundle.itemB}", style = MaterialTheme.typography.titleSmall)
                Text("Ordered together ${bundle.count} times", style = MaterialTheme.typography.bodySmall)
            }
            Text("Combo Offer?", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun RestaurantInsightList(
    title: String,
    data: List<RestaurantInsight>,
    useProfit: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (data.isEmpty()) {
                NoDataView(Modifier.height(100.dp))
            } else {
                data.forEachIndexed { index, rest ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (useProfit) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFF1976D2).copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (useProfit) Color(0xFF4CAF50) else Color(0xFF1976D2),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rest.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${rest.count} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Text(
                            "₹${"%.2f".format(rest.salesAmount)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (useProfit) Color(0xFF4CAF50) else Color(0xFF1976D2)
                        )
                    }
                    if (index < data.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 44.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsRangeHeader(
    currentRange: Pair<Long, Long>,
    onDateSelected: (Long) -> Unit,
    title: String = "Range",
    isWeekly: Boolean = false,
    onReset: (() -> Unit)? = null,
    defaultStartText: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (currentRange.first == 0L) System.currentTimeMillis() else currentRange.first
    )

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val startText = if (currentRange.first == 0L && defaultStartText != null) defaultStartText else sdf.format(Date(currentRange.first))
    val rangeText = "$startText - ${sdf.format(Date(currentRange.second))}"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(rangeText, style = MaterialTheme.typography.bodySmall)
        }
        
        Row {
            if (onReset != null) {
                TextButton(onClick = onReset) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedButton(
                onClick = { showPicker = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select Start", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun MultiMonthHeader(
    title: String,
    selectedMonths: List<Long>,
    onToggleMonth: (Long) -> Unit,
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val selectionText = if (selectedMonths.size > 2) {
        "${selectedMonths.size} Months Selected"
    } else {
        selectedMonths.joinToString(", ") { sdf.format(Date(it)) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(selectionText, style = MaterialTheme.typography.bodySmall)
        }
        
        Row {
            TextButton(onClick = onReset) {
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { showDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select Months", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showDialog) {
        MultiMonthDialog(
            selectedMonths = selectedMonths,
            onToggleMonth = onToggleMonth,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun MultiMonthDialog(
    selectedMonths: List<Long>,
    onToggleMonth: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = Calendar.getInstance()
    val currentYear = cal.get(Calendar.YEAR)
    
    // Show 2 years: Previous and Current
    val months = mutableListOf<Long>()
    for (year in (currentYear - 1)..currentYear) {
        for (month in 0..11) {
            val c = Calendar.getInstance()
            c.set(year, month, 1, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            months.add(c.timeInMillis)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Months") },
        text = {
            Box(modifier = Modifier.height(400.dp)) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(months) { millis ->
                        val isSelected = selectedMonths.contains(millis)
                        val sdf = SimpleDateFormat("MMM\n''yy", Locale.getDefault())
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleMonth(millis) },
                            label = { 
                                Text(
                                    sdf.format(Date(millis)), 
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
