package iffan.budhi.uts2

import java.math.BigDecimal

internal fun Product.minimumFor(type: String): BigDecimal = if (unit == "paket") BigDecimal.ONE else when (type) {
    "restoran" -> restaurantMinimum
    "luar_negeri" -> exportMinimum
    else -> BigDecimal.ONE
}
internal fun checkoutProblem(items: List<CartItem>, type: String): String? {
    if (items.isEmpty()) return "Pilih produk dari keranjang terlebih dahulu."
    if (items.size > 50) return "Maksimal 50 produk dalam satu pesanan."
    items.forEach { item ->
        val p = item.product
        if (!p.validQuantity(item.quantity)) return "Periksa jumlah dan stok ${p.name}."
        if (item.quantity < p.minimumFor(type)) return "Minimal ${p.name} untuk ${buyerTypeLabel(type)} adalah ${quantityText(p.minimumFor(type))} ${p.unit}. Ubah jumlah di keranjang."
    }
    val total = items.sumOf { it.product.subtotal(it.quantity) }
    return if (total <= 0 || total > 9_000_000_000L) "Nilai pesanan di luar batas yang didukung." else null
}
internal fun buyerTypeLabel(type: String) = when (type) { "restoran" -> "Restoran"; "luar_negeri" -> "Luar negeri"; else -> "Perorangan" }
internal fun orderStatusLabel(status: String) = when (status) {
    "menunggu_persetujuan" -> "Menunggu persetujuan admin"
    "disetujui" -> "Penawaran admin tersedia"
    "menunggu_dp" -> "Menunggu pembayaran DP"
    "dp_dibayar" -> "DP sudah dibayar"
    "diproses" -> "Sedang diproses"
    "dikirim" -> "Sedang dikirim"
    "diterima" -> "Pesanan diterima"
    "menunggu_pelunasan" -> "Menunggu pelunasan"
    "lunas" -> "Selesai / lunas"
    "ditolak" -> "Pesanan ditolak"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Belum tersedia" }
}
internal fun remainingCartQuantity(current: BigDecimal, submitted: BigDecimal): BigDecimal =
    if (current >= submitted) current - submitted else current
