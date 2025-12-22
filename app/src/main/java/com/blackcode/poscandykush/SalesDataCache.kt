package com.blackcode.poscandykush

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class SalesDataCache(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "sales_cache.db"
        private const val DATABASE_VERSION = 2 // Upgraded version for new schema

        private const val TABLE_CACHE = "sales_cache"
        private const val COLUMN_ID = "id"
        private const val COLUMN_KEY = "cache_key"
        private const val COLUMN_DATA = "data"
        private const val COLUMN_PERIOD = "period"
        private const val COLUMN_DATE_KEY = "date_key" // Exact date identifier (YYYY-MM-DD for day, YYYY-MM for month, YYYY for year)
        private const val COLUMN_START_DATE = "start_date"
        private const val COLUMN_END_DATE = "end_date"
        private const val COLUMN_TIMESTAMP = "timestamp"

        // Items stock cache table
        private const val TABLE_ITEMS_CACHE = "items_cache"
        private const val COLUMN_ITEMS_DATA = "items_data"
        private const val COLUMN_ITEMS_TIMESTAMP = "items_timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Sales cache table
        val createSalesTable = """
            CREATE TABLE $TABLE_CACHE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_KEY TEXT NOT NULL,
                $COLUMN_DATA TEXT NOT NULL,
                $COLUMN_PERIOD TEXT NOT NULL,
                $COLUMN_DATE_KEY TEXT NOT NULL,
                $COLUMN_START_DATE TEXT,
                $COLUMN_END_DATE TEXT,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                UNIQUE($COLUMN_KEY, $COLUMN_PERIOD, $COLUMN_DATE_KEY) ON CONFLICT REPLACE
            )
        """.trimIndent()
        db.execSQL(createSalesTable)

        // Create index for faster lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_lookup ON $TABLE_CACHE ($COLUMN_KEY, $COLUMN_PERIOD, $COLUMN_DATE_KEY)")

        // Items stock cache table
        val createItemsTable = """
            CREATE TABLE $TABLE_ITEMS_CACHE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_KEY TEXT NOT NULL UNIQUE,
                $COLUMN_ITEMS_DATA TEXT NOT NULL,
                $COLUMN_ITEMS_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createItemsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ITEMS_CACHE")
        onCreate(db)
    }

    /**
     * Save data to cache with precise date identification
     * @param key - Type of data (sales-summary, sales-by-item, etc.)
     * @param period - Period type (today, this_week, this_month, this_year, custom)
     * @param dateKey - Exact date identifier (YYYY-MM-DD for day, YYYY-MM for month, YYYY for year, or YYYY-wWW for week)
     * @param startDate - Start date of the period (YYYY-MM-DD)
     * @param endDate - End date of the period (YYYY-MM-DD)
     * @param data - JSON data to cache
     */
    fun saveToCacheWithDate(key: String, period: String, dateKey: String, startDate: String, endDate: String, data: JSONObject) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_DATA, data.toString())
            put(COLUMN_PERIOD, period)
            put(COLUMN_DATE_KEY, dateKey)
            put(COLUMN_START_DATE, startDate)
            put(COLUMN_END_DATE, endDate)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    /**
     * Get cached data by exact date key
     * @param key - Type of data
     * @param period - Period type
     * @param dateKey - Exact date identifier
     * @return Pair of JSONObject and timestamp, or null if not found
     */
    fun getFromCacheByDate(key: String, period: String, dateKey: String): Pair<JSONObject, Long>? {
        val db = readableDatabase
        val selection = "$COLUMN_KEY = ? AND $COLUMN_PERIOD = ? AND $COLUMN_DATE_KEY = ?"
        val selectionArgs = arrayOf(key, period, dateKey)

        val cursor = db.query(
            TABLE_CACHE,
            arrayOf(COLUMN_DATA, COLUMN_TIMESTAMP),
            selection,
            selectionArgs,
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                try {
                    val data = JSONObject(it.getString(it.getColumnIndexOrThrow(COLUMN_DATA)))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                    Pair(data, timestamp)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Check if cache exists and is fresh for specific date
     * @param maxAge - Maximum age in milliseconds (default 5 minutes for current day, 1 hour for historical)
     */
    fun isCacheFreshByDate(key: String, period: String, dateKey: String, maxAge: Long): Boolean {
        val cached = getFromCacheByDate(key, period, dateKey) ?: return false
        return System.currentTimeMillis() - cached.second < maxAge
    }

    /**
     * Get cache age in milliseconds
     */
    fun getCacheAge(key: String, period: String, dateKey: String): Long? {
        val cached = getFromCacheByDate(key, period, dateKey) ?: return null
        return System.currentTimeMillis() - cached.second
    }

    /**
     * Save items/stock data to cache
     */
    fun saveItemsToCache(key: String, data: JSONObject) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_ITEMS_DATA, data.toString())
            put(COLUMN_ITEMS_TIMESTAMP, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_ITEMS_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    /**
     * Get items/stock data from cache
     */
    fun getItemsFromCache(key: String): Pair<JSONObject, Long>? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ITEMS_CACHE,
            arrayOf(COLUMN_ITEMS_DATA, COLUMN_ITEMS_TIMESTAMP),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                try {
                    val data = JSONObject(it.getString(it.getColumnIndexOrThrow(COLUMN_ITEMS_DATA)))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_ITEMS_TIMESTAMP))
                    Pair(data, timestamp)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Check if items cache is fresh
     */
    fun isItemsCacheFresh(key: String, maxAge: Long = 5 * 60 * 1000): Boolean {
        val cached = getItemsFromCache(key) ?: return false
        return System.currentTimeMillis() - cached.second < maxAge
    }

    /**
     * Get all cached date keys for a specific data type
     */
    fun getCachedDateKeys(key: String, period: String): List<String> {
        val db = readableDatabase
        val dateKeys = mutableListOf<String>()

        val cursor = db.query(
            true, // distinct
            TABLE_CACHE,
            arrayOf(COLUMN_DATE_KEY),
            "$COLUMN_KEY = ? AND $COLUMN_PERIOD = ?",
            arrayOf(key, period),
            null, null,
            "$COLUMN_DATE_KEY DESC",
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                dateKeys.add(it.getString(it.getColumnIndexOrThrow(COLUMN_DATE_KEY)))
            }
        }
        return dateKeys
    }

    /**
     * Clear all cache
     */
    fun clearCache() {
        val db = writableDatabase
        db.delete(TABLE_CACHE, null, null)
        db.delete(TABLE_ITEMS_CACHE, null, null)
        db.close()
    }

    /**
     * Clear cache for specific date key
     */
    fun clearCacheForDate(dateKey: String) {
        val db = writableDatabase
        db.delete(TABLE_CACHE, "$COLUMN_DATE_KEY = ?", arrayOf(dateKey))
        db.close()
    }

    /**
     * Clear old cache entries (older than specified days)
     */
    fun clearOldCache(maxAgeDays: Int = 30) {
        val db = writableDatabase
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        db.delete(TABLE_CACHE, "$COLUMN_TIMESTAMP < ?", arrayOf(cutoffTime.toString()))
        db.close()
    }

    /**
     * Check if we have any data for the current month (for initial load check)
     */
    fun hasCurrentMonthData(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val monthKey = String.format("%04d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1)

        return getFromCacheByDate("sales-summary", "this_month", monthKey) != null
    }

    /**
     * Get total cached data count (for debugging)
     */
    fun getCachedDataCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CACHE", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * Get sync progress info - how many months we have cached
     */
    fun getSyncProgress(): Pair<Int, List<String>> {
        val months = getCachedDateKeys("sales-summary", "this_month")
        return Pair(months.size, months)
    }

    // Legacy methods for backward compatibility
    @Deprecated("Use saveToCacheWithDate instead", ReplaceWith("saveToCacheWithDate(key, period, monthKey ?: \"\", \"\", \"\", data)"))
    fun saveToCache(key: String, period: String, monthKey: String?, data: JSONObject) {
        saveToCacheWithDate(key, period, monthKey ?: "", "", "", data)
    }

    @Deprecated("Use getFromCacheByDate instead")
    fun getFromCache(key: String, period: String, monthKey: String? = null): Pair<JSONObject, Long>? {
        return getFromCacheByDate(key, period, monthKey ?: "")
    }

    @Deprecated("Use isCacheFreshByDate instead")
    fun isCacheFresh(key: String, period: String, monthKey: String? = null, maxAge: Long = 5 * 60 * 1000): Boolean {
        return isCacheFreshByDate(key, period, monthKey ?: "", maxAge)
    }

    @Deprecated("Use getCachedDateKeys instead")
    fun getCachedMonths(): List<String> {
        return getCachedDateKeys("sales-summary", "this_month")
    }

    @Deprecated("Use clearCacheForDate instead")
    fun clearCacheForPeriod(period: String) {
        val db = writableDatabase
        db.delete(TABLE_CACHE, "$COLUMN_PERIOD = ?", arrayOf(period))
        db.close()
    }
}

