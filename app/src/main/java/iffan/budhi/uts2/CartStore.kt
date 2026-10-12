package iffan.budhi.uts2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal

data class CartItem(val product: Product, val quantity: BigDecimal, val selected: Boolean)

class CartStore(context: Context, databaseName: String = "tamafarm_cart.db") : SQLiteOpenHelper(context.applicationContext, databaseName, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cart (product_id INTEGER PRIMARY KEY, product_json TEXT NOT NULL, quantity TEXT NOT NULL, selected INTEGER NOT NULL DEFAULT 1)")
        checkoutTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only contained demo IDs, which must never be matched to real server products.
        if (oldVersion < 2) { db.execSQL("DROP TABLE IF EXISTS cart"); onCreate(db) } else if (oldVersion < 3) checkoutTables(db)
    }
    fun items(): List<CartItem> = readableDatabase.rawQuery("SELECT product_json, quantity, selected FROM cart ORDER BY product_id", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(CartItem(Product.fromJson(org.json.JSONObject(cursor.getString(0))), BigDecimal(cursor.getString(1)), cursor.getInt(2) == 1)) }
    }
    fun sync(products: List<Product>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            items().forEach { item ->
                val latest = products.find { it.id == item.product.id } ?: item.product.copy(stock = BigDecimal.ZERO)
                db.update("cart", ContentValues().apply {
                    put("product_json", latest.json())
                    if (!latest.validQuantity(item.quantity)) put("selected", 0)
                }, "product_id = ?", arrayOf(latest.id.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun add(product: Product, quantity: BigDecimal): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT quantity FROM cart WHERE product_id = ?", arrayOf(product.id.toString())).use {
                if (it.moveToFirst()) BigDecimal(it.getString(0)) else BigDecimal.ZERO
            }
            val total = existing + quantity
            if (!product.validQuantity(quantity) || !product.validQuantity(total)) return false
            val inserted = db.insertWithOnConflict("cart", null, ContentValues().apply {
                put("product_id", product.id); put("product_json", product.json()); put("quantity", total.toPlainString()); put("selected", 1)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            if (inserted == -1L) return false
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    fun quantity(product: Product, value: BigDecimal) {
        require(product.validQuantity(value))
        writableDatabase.update("cart", ContentValues().apply { put("quantity", value.toPlainString()) }, "product_id = ?", arrayOf(product.id.toString()))
    }
    fun select(id: Int, selected: Boolean) {
        writableDatabase.update("cart", ContentValues().apply { put("selected", if (selected) 1 else 0) }, "product_id = ?", arrayOf(id.toString()))
    }
    fun remove(id: Int) { writableDatabase.delete("cart", "product_id = ?", arrayOf(id.toString())) }
    private fun checkoutTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS checkout_receipts (receipt TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cart_metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.insertWithOnConflict("cart_metadata", null, ContentValues().apply { put("name", "epoch"); put("value", java.util.UUID.randomUUID().toString()) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun epoch(): String = readableDatabase.rawQuery("SELECT value FROM cart_metadata WHERE name = ?", arrayOf("epoch")).use { it.moveToFirst(); it.getString(0) }
    fun consume(receipt: String, expectedEpoch: String, submitted: Map<Int, BigDecimal>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (epoch() != expectedEpoch) return
            val inserted = db.insertWithOnConflict("checkout_receipts", null, ContentValues().apply { put("receipt", receipt) }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) return
            items().forEach { item ->
                val sent = submitted[item.product.id] ?: return@forEach
                val remaining = remainingCartQuantity(item.quantity, sent)
                if (remaining.signum() == 0) remove(item.product.id)
                else db.update("cart", ContentValues().apply {
                    put("quantity", remaining.toPlainString())
                    if (!item.product.validQuantity(remaining)) put("selected", 0)
                }, "product_id = ?", arrayOf(item.product.id.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun clear() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cart", null, null)
            db.update("cart_metadata", ContentValues().apply { put("value", java.util.UUID.randomUUID().toString()) }, "name = ?", arrayOf("epoch"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}




