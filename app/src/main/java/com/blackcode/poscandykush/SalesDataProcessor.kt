package com.blackcode.poscandykush

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Processor for raw API data - converts day-by-day raw data into aggregated summaries
 * This runs on the device instead of server for better performance and offline capability
 */
class SalesDataProcessor(private val cache: SalesDataCache) {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Process raw monthly data and create aggregated summaries
     */
    fun processMonthlyData(rawData: CurrentMonthRawData): ProcessedMonthData {
        // Extract daily data from raw API responses
        val dailyDataMap = extractDailyData(rawData)

        // Aggregate all daily data into monthly totals
        val aggregatedSummary = aggregateSalesSummary(rawData.salesSummary, dailyDataMap)
        val aggregatedItems = aggregateSalesByItem(rawData.salesByItem, dailyDataMap)
        val aggregatedCategories = aggregateSalesByCategory(rawData.salesByCategory, dailyDataMap)
        val aggregatedEmployees = aggregateSalesByEmployee(rawData.salesByEmployee, rawData.salesSummary, dailyDataMap)

        return ProcessedMonthData(
            monthKey = rawData.monthKey,
            startDate = rawData.startDate,
            endDate = rawData.endDate,
            aggregatedSummary = aggregatedSummary,
            aggregatedItems = aggregatedItems,
            aggregatedCategories = aggregatedCategories,
            aggregatedEmployees = aggregatedEmployees,
            dailyData = dailyDataMap
        )
    }

    /**
     * Extract and organize daily data from raw API responses
     */
    private fun extractDailyData(rawData: CurrentMonthRawData): Map<String, DailyProcessedData> {
        val dailyMap = mutableMapOf<String, DailyProcessedData>()

        // Extract daily_data arrays from each response
        val summaryDailyData = rawData.salesSummary.optJSONObject("data")?.optJSONArray("daily_data")
        val itemsDailyData = rawData.salesByItem.optJSONObject("data")?.optJSONArray("daily_data")
        val categoriesDailyData = rawData.salesByCategory.optJSONObject("data")?.optJSONArray("daily_data")
        val employeesDailyData = rawData.salesByEmployee.optJSONObject("data")?.optJSONArray("daily_data")

        // Process sales summary daily data
        summaryDailyData?.let { arr ->
            for (i in 0 until arr.length()) {
                val dayObj = arr.optJSONObject(i)
                val date = dayObj?.optString("date") ?: continue

                val dailyData = dailyMap.getOrPut(date) {
                    DailyProcessedData(
                        summary = JSONObject(),
                        items = JSONObject(),
                        categories = JSONObject(),
                        employees = JSONObject()
                    )
                }

                // Create a proper summary response for this day
                dailyMap[date] = dailyData.copy(
                    summary = createDaySummaryResponse(dayObj)
                )
            }
        }

        // Process items daily data
        itemsDailyData?.let { arr ->
            for (i in 0 until arr.length()) {
                val dayObj = arr.optJSONObject(i)
                val date = dayObj?.optString("date") ?: continue

                val dailyData = dailyMap.getOrPut(date) {
                    DailyProcessedData(
                        summary = JSONObject(),
                        items = JSONObject(),
                        categories = JSONObject(),
                        employees = JSONObject()
                    )
                }

                dailyMap[date] = dailyData.copy(
                    items = createDayItemsResponse(dayObj)
                )
            }
        }

        // Process categories daily data
        categoriesDailyData?.let { arr ->
            for (i in 0 until arr.length()) {
                val dayObj = arr.optJSONObject(i)
                val date = dayObj?.optString("date") ?: continue

                val dailyData = dailyMap.getOrPut(date) {
                    DailyProcessedData(
                        summary = JSONObject(),
                        items = JSONObject(),
                        categories = JSONObject(),
                        employees = JSONObject()
                    )
                }

                dailyMap[date] = dailyData.copy(
                    categories = createDayCategoriesResponse(dayObj)
                )
            }
        }

        // Process employees daily data
        employeesDailyData?.let { arr ->
            for (i in 0 until arr.length()) {
                val dayObj = arr.optJSONObject(i)
                val date = dayObj?.optString("date") ?: continue

                val dailyData = dailyMap.getOrPut(date) {
                    DailyProcessedData(
                        summary = JSONObject(),
                        items = JSONObject(),
                        categories = JSONObject(),
                        employees = JSONObject()
                    )
                }

                dailyMap[date] = dailyData.copy(
                    employees = createDayEmployeesResponse(dayObj)
                )
            }
        }

        return dailyMap
    }

    private fun createDaySummaryResponse(dayObj: JSONObject): JSONObject {
        val metrics = dayObj.optJSONObject("metrics") ?: JSONObject()
        val transactions = dayObj.optJSONObject("transactions") ?: JSONObject()
        val receipts = dayObj.optJSONArray("receipts") ?: JSONArray()

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-summary")
            put("data", JSONObject().apply {
                put("metrics", metrics)
                put("transactions", transactions)
                put("receipts", receipts)
                put("chart_data", createChartDataFromReceipts(receipts))
            })
        }
    }

    private fun createDayItemsResponse(dayObj: JSONObject): JSONObject {
        val items = dayObj.optJSONArray("items") ?: JSONArray()

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-item")
            put("data", JSONObject().apply {
                put("items", items)
            })
        }
    }

    private fun createDayCategoriesResponse(dayObj: JSONObject): JSONObject {
        val categories = dayObj.optJSONArray("categories") ?: JSONArray()

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-category")
            put("data", JSONObject().apply {
                put("categories", categories)
            })
        }
    }

    private fun createDayEmployeesResponse(dayObj: JSONObject): JSONObject {
        val employees = dayObj.optJSONArray("employees") ?: JSONArray()

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-employee")
            put("data", JSONObject().apply {
                put("employees", employees)
            })
        }
    }

    /**
     * Aggregate sales summary from daily data
     */
    private fun aggregateSalesSummary(
        rawResponse: JSONObject,
        dailyData: Map<String, DailyProcessedData>
    ): JSONObject {
        var totalGrossSales = 0.0
        var totalRefunds = 0.0
        var totalDiscounts = 0.0
        var totalTaxes = 0.0
        var totalNetSales = 0.0
        var totalCostOfGoods = 0.0
        var totalGrossProfit = 0.0
        var totalTransactions = 0
        var totalRefundCount = 0
        var totalItemsSold = 0

        val chartDataList = mutableListOf<JSONObject>()

        // Process daily_data from raw response
        val dailyDataArray = rawResponse.optJSONObject("data")?.optJSONArray("daily_data")
        if (dailyDataArray != null) {
            for (i in 0 until dailyDataArray.length()) {
                val dayObj = dailyDataArray.optJSONObject(i) ?: continue
                val metrics = dayObj.optJSONObject("metrics") ?: continue
                val transactions = dayObj.optJSONObject("transactions") ?: JSONObject()
                val date = dayObj.optString("date", "")

                totalGrossSales += metrics.optDouble("gross_sales", 0.0)
                totalRefunds += metrics.optDouble("refunds", 0.0)
                totalDiscounts += metrics.optDouble("discounts", 0.0)
                totalTaxes += metrics.optDouble("taxes", 0.0)
                totalNetSales += metrics.optDouble("net_sales", 0.0)
                totalCostOfGoods += metrics.optDouble("cost_of_goods", 0.0)
                totalGrossProfit += metrics.optDouble("gross_profit", 0.0)

                totalTransactions += transactions.optInt("total_count", 0)
                totalRefundCount += transactions.optInt("refund_count", 0)
                totalItemsSold += transactions.optInt("items_sold", 0)

                // Add to chart data
                chartDataList.add(JSONObject().apply {
                    put("x", i.toFloat())
                    put("y", metrics.optDouble("net_sales", 0.0))
                    put("date", date)
                })
            }
        }

        val avgTransaction = if (totalTransactions > 0) totalNetSales / totalTransactions else 0.0
        val profitMargin = if (totalNetSales > 0) (totalGrossProfit / totalNetSales) * 100 else 0.0

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-summary")
            put("data", JSONObject().apply {
                put("metrics", JSONObject().apply {
                    put("gross_sales", totalGrossSales)
                    put("refunds", totalRefunds)
                    put("discounts", totalDiscounts)
                    put("taxes", totalTaxes)
                    put("net_sales", totalNetSales)
                    put("cost_of_goods", totalCostOfGoods)
                    put("gross_profit", totalGrossProfit)
                    put("profit_margin", profitMargin)
                })
                put("transactions", JSONObject().apply {
                    put("total_count", totalTransactions)
                    put("refund_count", totalRefundCount)
                    put("average_value", avgTransaction)
                    put("items_sold", totalItemsSold)
                })
                put("chart_data", JSONArray(chartDataList.map { it }))
            })
        }
    }

    /**
     * Aggregate sales by item from daily data
     */
    private fun aggregateSalesByItem(
        rawResponse: JSONObject,
        dailyData: Map<String, DailyProcessedData>
    ): JSONObject {
        val itemsMap = mutableMapOf<String, MutableMap<String, Any>>()

        val dailyDataArray = rawResponse.optJSONObject("data")?.optJSONArray("daily_data")
        if (dailyDataArray != null) {
            for (i in 0 until dailyDataArray.length()) {
                val dayObj = dailyDataArray.optJSONObject(i) ?: continue
                val itemsArray = dayObj.optJSONArray("items") ?: continue

                for (j in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(j) ?: continue
                    val itemId = item.optString("item_id", "unknown_$j")
                    val itemName = item.optString("item_name", "Unknown")

                    val existing = itemsMap.getOrPut(itemId) {
                        mutableMapOf(
                            "item_id" to itemId,
                            "item_name" to itemName,
                            "category" to item.optString("category", ""),
                            "sku" to item.optString("sku", ""),
                            "quantity_sold" to 0,
                            "gross_sales" to 0.0,
                            "net_sales" to 0.0,
                            "cost_of_goods" to 0.0,
                            "discounts" to 0.0,
                            "gross_profit" to 0.0,
                            "transaction_count" to 0
                        )
                    }

                    existing["quantity_sold"] = (existing["quantity_sold"] as Int) + item.optInt("quantity_sold", 0)
                    existing["gross_sales"] = (existing["gross_sales"] as Double) + item.optDouble("gross_sales", 0.0)
                    existing["net_sales"] = (existing["net_sales"] as Double) + item.optDouble("net_sales", 0.0)
                    existing["cost_of_goods"] = (existing["cost_of_goods"] as Double) + item.optDouble("cost_of_goods", 0.0)
                    existing["discounts"] = (existing["discounts"] as Double) + item.optDouble("discounts", 0.0)
                    existing["gross_profit"] = (existing["gross_profit"] as Double) + item.optDouble("gross_profit", 0.0)
                    existing["transaction_count"] = (existing["transaction_count"] as Int) + item.optInt("transaction_count", 0)
                }
            }
        }

        // Calculate averages and margins, sort by gross_sales
        val itemsList = itemsMap.values.map { itemData ->
            val grossSales = itemData["gross_sales"] as Double
            val netSales = itemData["net_sales"] as Double
            val grossProfit = itemData["gross_profit"] as Double
            val quantitySold = itemData["quantity_sold"] as Int

            val profitMargin = if (netSales > 0) (grossProfit / netSales) * 100 else 0.0
            val avgPrice = if (quantitySold > 0) grossSales / quantitySold else 0.0

            JSONObject().apply {
                put("item_id", itemData["item_id"])
                put("item_name", itemData["item_name"])
                put("category", itemData["category"])
                put("sku", itemData["sku"])
                put("quantity_sold", quantitySold)
                put("gross_sales", grossSales)
                put("net_sales", netSales)
                put("cost_of_goods", itemData["cost_of_goods"])
                put("discounts", itemData["discounts"])
                put("gross_profit", grossProfit)
                put("profit_margin", profitMargin)
                put("average_price", avgPrice)
                put("transaction_count", itemData["transaction_count"])
            }
        }.sortedByDescending { it.optDouble("gross_sales", 0.0) }

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-item")
            put("data", JSONObject().apply {
                put("items", JSONArray(itemsList))
            })
        }
    }

    /**
     * Aggregate sales by category from daily data
     */
    private fun aggregateSalesByCategory(
        rawResponse: JSONObject,
        dailyData: Map<String, DailyProcessedData>
    ): JSONObject {
        val categoriesMap = mutableMapOf<String, MutableMap<String, Any>>()
        var totalSales = 0.0

        val dailyDataArray = rawResponse.optJSONObject("data")?.optJSONArray("daily_data")
        if (dailyDataArray != null) {
            for (i in 0 until dailyDataArray.length()) {
                val dayObj = dailyDataArray.optJSONObject(i) ?: continue
                val categoriesArray = dayObj.optJSONArray("categories") ?: continue

                for (j in 0 until categoriesArray.length()) {
                    val category = categoriesArray.optJSONObject(j) ?: continue
                    val categoryId = category.optString("category_id", "unknown_$j")
                    val categoryName = category.optString("category_name", "Unknown")

                    val existing = categoriesMap.getOrPut(categoryId) {
                        mutableMapOf(
                            "category_id" to categoryId,
                            "category_name" to categoryName,
                            "quantity_sold" to 0,
                            "gross_sales" to 0.0,
                            "net_sales" to 0.0,
                            "cost_of_goods" to 0.0,
                            "discounts" to 0.0,
                            "gross_profit" to 0.0,
                            "item_count" to 0
                        )
                    }

                    val grossSales = category.optDouble("gross_sales", 0.0)
                    totalSales += grossSales

                    existing["quantity_sold"] = (existing["quantity_sold"] as Int) + category.optInt("quantity_sold", 0)
                    existing["gross_sales"] = (existing["gross_sales"] as Double) + grossSales
                    existing["net_sales"] = (existing["net_sales"] as Double) + category.optDouble("net_sales", 0.0)
                    existing["cost_of_goods"] = (existing["cost_of_goods"] as Double) + category.optDouble("cost_of_goods", 0.0)
                    existing["discounts"] = (existing["discounts"] as Double) + category.optDouble("discounts", 0.0)
                    existing["gross_profit"] = (existing["gross_profit"] as Double) + category.optDouble("gross_profit", 0.0)
                    existing["item_count"] = (existing["item_count"] as Int) + category.optInt("item_count", 0)
                }
            }
        }

        val categoriesList = categoriesMap.values.map { catData ->
            val grossSales = catData["gross_sales"] as Double
            val percentOfSales = if (totalSales > 0) (grossSales / totalSales) * 100 else 0.0

            JSONObject().apply {
                put("category_id", catData["category_id"])
                put("category_name", catData["category_name"])
                put("quantity_sold", catData["quantity_sold"])
                put("gross_sales", grossSales)
                put("net_sales", catData["net_sales"])
                put("cost_of_goods", catData["cost_of_goods"])
                put("discounts", catData["discounts"])
                put("gross_profit", catData["gross_profit"])
                put("item_count", catData["item_count"])
                put("percentage_of_sales", percentOfSales)
            }
        }.sortedByDescending { it.optDouble("gross_sales", 0.0) }

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-category")
            put("data", JSONObject().apply {
                put("categories", JSONArray(categoriesList))
            })
        }
    }

    /**
     * Aggregate sales by employee from daily data
     */
    private fun aggregateSalesByEmployee(
        rawResponse: JSONObject,
        salesSummary: JSONObject,
        dailyData: Map<String, DailyProcessedData>
    ): JSONObject {
        // Extract employee names from sales summary receipts
        val employeeNameMap = extractEmployeeNamesFromReceipts(salesSummary)
        Log.d("SalesDataProcessor", "Employee name map from receipts: $employeeNameMap")

        val employeesMap = mutableMapOf<String, MutableMap<String, Any>>()

        val dailyDataArray = rawResponse.optJSONObject("data")?.optJSONArray("daily_data")
        if (dailyDataArray != null) {
            for (i in 0 until dailyDataArray.length()) {
                val dayObj = dailyDataArray.optJSONObject(i) ?: continue
                val employeesArray = dayObj.optJSONArray("employees") ?: continue

                for (j in 0 until employeesArray.length()) {
                    val employee = employeesArray.optJSONObject(j) ?: continue
                    val employeeId = employee.optString("employee_id", "unknown_$j")
                    var employeeName = employee.optString("employee_name", "Unknown")

                    Log.d("SalesDataProcessor", "Processing employee: ID=$employeeId, original name='$employeeName'")

                    // If name is unknown, try to look it up from receipts
                    if (employeeName == "Unknown" || employeeName.isEmpty()) {
                        employeeNameMap[employeeId]?.let { lookedUpName ->
                            if (lookedUpName.isNotEmpty() && lookedUpName != "Unknown") {
                                employeeName = lookedUpName
                                Log.d("SalesDataProcessor", "Looked up name for $employeeId: '$employeeName'")
                            } else {
                                Log.d("SalesDataProcessor", "Lookup failed for $employeeId: looked up '$lookedUpName'")
                            }
                        } ?: Log.d("SalesDataProcessor", "No lookup entry for $employeeId")
                    }

                    val existing = employeesMap.getOrPut(employeeId) {
                        mutableMapOf(
                            "employee_id" to employeeId,
                            "employee_name" to employeeName,
                            "gross_sales" to 0.0,
                            "refunds" to 0.0,
                            "discounts" to 0.0,
                            "net_sales" to 0.0,
                            "transaction_count" to 0,
                            "refund_count" to 0,
                            "items_sold" to 0
                        )
                    }

                    // Update name if we found a better one
                    if ((existing["employee_name"] as String) == "Unknown" && employeeName != "Unknown") {
                        existing["employee_name"] = employeeName
                        Log.d("SalesDataProcessor", "Updated existing employee name for $employeeId to '$employeeName'")
                    }

                    existing["gross_sales"] = (existing["gross_sales"] as Double) + employee.optDouble("gross_sales", 0.0)
                    existing["refunds"] = (existing["refunds"] as Double) + employee.optDouble("refunds", 0.0)
                    existing["discounts"] = (existing["discounts"] as Double) + employee.optDouble("discounts", 0.0)
                    existing["net_sales"] = (existing["net_sales"] as Double) + employee.optDouble("net_sales", 0.0)
                    existing["transaction_count"] = (existing["transaction_count"] as Int) + employee.optInt("transaction_count", 0)
                    existing["refund_count"] = (existing["refund_count"] as Int) + employee.optInt("refund_count", 0)
                    existing["items_sold"] = (existing["items_sold"] as Int) + employee.optInt("items_sold", 0)
                }
            }
        }

        val employeesList = employeesMap.values.map { empData ->
            val netSales = empData["net_sales"] as Double
            val transactionCount = empData["transaction_count"] as Int
            val avgTransaction = if (transactionCount > 0) netSales / transactionCount else 0.0

            JSONObject().apply {
                put("employee_id", empData["employee_id"])
                put("employee_name", empData["employee_name"])
                put("gross_sales", empData["gross_sales"])
                put("refunds", empData["refunds"])
                put("discounts", empData["discounts"])
                put("net_sales", netSales)
                put("transaction_count", transactionCount)
                put("refund_count", empData["refund_count"])
                put("items_sold", empData["items_sold"])
                put("average_transaction", avgTransaction)
            }
        }.sortedByDescending { it.optDouble("gross_sales", 0.0) }

        Log.d("SalesDataProcessor", "Final employees list: ${employeesList.map { "${it.optString("employee_id")}: ${it.optString("employee_name")}" }}")

        return JSONObject().apply {
            put("success", true)
            put("action", "sales-by-employee")
            put("data", JSONObject().apply {
                put("employees", JSONArray(employeesList))
            })
        }
    }

    /**
     * Extract employee names from sales summary receipts
     */
    private fun extractEmployeeNamesFromReceipts(salesSummary: JSONObject): Map<String, String> {
        val employeeNames = mutableMapOf<String, String>()

        val dailyDataArray = salesSummary.optJSONObject("data")?.optJSONArray("daily_data")
        if (dailyDataArray != null) {
            for (i in 0 until dailyDataArray.length()) {
                val dayObj = dailyDataArray.optJSONObject(i) ?: continue
                val receipts = dayObj.optJSONArray("receipts") ?: continue

                for (j in 0 until receipts.length()) {
                    val receipt = receipts.optJSONObject(j) ?: continue
                    val employeeId = receipt.optString("employee_id", "")
                    val employeeName = receipt.optString("employee_name", "")

                    if (employeeId.isNotEmpty() && employeeName.isNotEmpty() && employeeName != "Unknown") {
                        employeeNames[employeeId] = employeeName
                    }
                }
            }
        }

        return employeeNames
    }

    private fun createChartDataFromReceipts(receipts: JSONArray): JSONArray {
        val chartData = JSONArray()
        // Simple chart data from receipts - can be enhanced later
        for (i in 0 until receipts.length()) {
            val receipt = receipts.optJSONObject(i)
            if (receipt != null) {
                chartData.put(JSONObject().apply {
                    put("x", i.toFloat())
                    put("y", receipt.optDouble("total", 0.0))
                })
            }
        }
        return chartData
    }

    /**
     * Process a single month's raw data for background sync
     */
    fun processRawMonthData(
        monthKey: String,
        startDate: String,
        endDate: String,
        salesSummary: JSONObject,
        salesByItem: JSONObject,
        salesByCategory: JSONObject,
        salesByEmployee: JSONObject
    ): ProcessedMonthData {
        val rawData = CurrentMonthRawData(
            monthKey = monthKey,
            startDate = startDate,
            endDate = endDate,
            salesSummary = salesSummary,
            salesByItem = salesByItem,
            salesByCategory = salesByCategory,
            salesByEmployee = salesByEmployee
        )
        return processMonthlyData(rawData)
    }
}
