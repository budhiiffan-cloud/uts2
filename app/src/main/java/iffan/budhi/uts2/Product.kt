package iffan.budhi.uts2

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

data class Product(
    val id: Int, val name: String, val category: String, val size: String,
    val price: Long, val stock: BigDecimal, val unit: String, val description: String,
    val imageUrl: String?, val restaurantMinimum: BigDecimal, val exportMinimum: BigDecimal
) {
    val minimum: BigDecimal get() = BigDecimal.ONE
    val step: BigDecimal get() = if (unit == "paket") BigDecimal.ONE else BigDecimal("0.25")
    fun validQuantity(value: BigDecimal): Boolean = value >= minimum && value <= stock &&
        value.stripTrailingZeros().scale() <= 2 && (unit != "paket" || value.stripTrailingZeros().scale() <= 0)
    fun subtotal(quantity: BigDecimal): Long = BigDecimal.valueOf(price).multiply(quantity).setScale(0, RoundingMode.HALF_UP).longValueExact()
    fun json(): String = JSONObject().apply {
        put("id_produk", id); put("nama_produk", name); put("ukuran", size); put("harga", price)
        put("stok", stock.toPlainString()); put("satuan", unit); put("deskripsi", description)
        put("gambar_url", imageUrl ?: JSONObject.NULL)
        put("moq_restoran", restaurantMinimum.toPlainString()); put("moq_luar_negeri", exportMinimum.toPlainString())
        put("kategori", JSONObject().put("nama_kategori", category))
    }.toString()
    companion object {
        fun fromJson(json: JSONObject): Product {
            val unit = json.getString("satuan")
            require(unit == "kg" || unit == "paket")
            return Product(json.getInt("id_produk"), json.getString("nama_produk"),
                json.optJSONObject("kategori")?.optString("nama_kategori").orEmpty().ifBlank { "Tanpa kategori" },
                json.optionalText("ukuran"), json.getLong("harga"), BigDecimal(json.getString("stok")), unit,
                json.optionalText("deskripsi"), json.optionalText("gambar_url").ifBlank { null },
                json.optionalText("moq_restoran").toBigDecimalOrNull() ?: BigDecimal.ONE,
                json.optionalText("moq_luar_negeri").toBigDecimalOrNull() ?: BigDecimal.ONE)
        }
    }
}
private fun JSONObject.optionalText(key: String): String = if (isNull(key)) "" else optString(key)
fun quantityText(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')
fun parseQuantity(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()
fun rupiah(value: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
    .apply { maximumFractionDigits = 0 }.format(value)
