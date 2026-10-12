package iffan.budhi.uts2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.volley.Request
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

class CheckoutFragment : RemoteFragment() {
    private val reload: () -> Unit = ::load
    private val redraw: () -> Unit = ::render
    private lateinit var root: LinearLayout
    private var profile: JSONObject? = null
    private var items: List<CartItem> = emptyList()
    private var loadError: String? = null
    private var note = ""
    private val submission get() = CheckoutSubmission.get(requireContext())
    private val observer: () -> Unit = { if (view != null) redraw() }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        note = state?.getString("note") ?: note
        root = requireContext().column(); profile = null; loadError = null
        redraw()
        if (AccountStore(requireContext()).token(api.baseUrl) != null) reload()
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { submission.observe(observer); redraw() }
            override fun onStop(owner: LifecycleOwner) { submission.unobserve(observer) }
        })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("note", note)
        super.onSaveInstanceState(outState)
    }
    private fun load() {
        api.queue.cancelAll(requestTag); profile = null; loadError = null
        val account = AccountStore(requireContext()); val token = account.token(api.baseUrl) ?: return redraw()
        redraw()
        api.account(Request.Method.GET, "pembeli/profil", null, requestTag, token, { response ->
            val loaded = response.getJSONObject("data")
            api.products(requestTag, { products ->
                if (view == null) return@products
                host.cart.sync(products); items = host.cart.items().filter { it.selected }; profile = loaded; redraw()
            }, { loadError = it; if (view != null) redraw() })
        }, { error ->
            if (error.status == 401) account.clear()
            loadError = error.message
            if (view != null) redraw()
        })
    }
    private fun render() {
        root.removeAllViews()
        root.label("Checkout", 27f, true)
        val account = AccountStore(requireContext()); val token = account.token(api.baseUrl)
        if (token == null) {
            root.label("Masuk sebagai pembeli sebelum mengajukan pesanan.")
            root.action("Masuk") { host.openAuth(false) }; return
        }
        val actor = submission.actor(api.baseUrl, account)
        val record = submission.state(actor)
        if (record != null) {
            when (record.optString("status")) {
                "success" -> {
                    val code = record.getString("code")
                    submission.finishCart(actor, api.baseUrl, code, record); host.updateCartBadge()
                    root.label("Pesanan berhasil diajukan", 22f, true, green)
                    root.label(code, 20f, true)
                    root.label("Menunggu penawaran dari toko.", color = muted)
                    root.action("Lihat pesanan") { submission.clear(actor); host.openOrder(code, true) }
                }
                "pending", "uncertain" -> {
                    if (submission.busy(actor)) {
                        root.addView(ProgressBar(requireContext())); root.label("Mengirim pesanan ke admin…")
                    } else {
                        root.label("Hasil pengiriman perlu diperiksa", 22f, true)
                        root.label("Koneksi terputus sebelum hasil pesanan diterima. Periksa riwayat pesanan terlebih dahulu agar tidak mengirim pesanan yang sama dua kali.")
                        root.action("Periksa riwayat pesanan") { host.openOrders() }
                        root.action("Pesanan belum ada, ulangi checkout") {
                            MaterialAlertDialogBuilder(requireContext()).setTitle("Ulangi checkout?")
                                .setMessage("Pastikan pesanan sebelumnya belum tercatat di riwayat atau dikonfirmasi admin. Mengulang dapat membuat pesanan baru.")
                                .setNegativeButton("Batal", null).setPositiveButton("Kembali ke checkout") { _, _ -> submission.clear(actor); reload() }.show()
                        }
                    }
                }
                else -> {
                    root.label(record.optString("message", "Pesanan belum dapat diajukan."))
                    root.action("Periksa dan perbaiki checkout") { submission.clear(actor); reload() }
                }
            }
            return
        }
        loadError?.let {
            root.label(it); root.action("Coba lagi") { reload() }; root.action("Lengkapi profil") { host.openProfile() }; return
        }
        val buyer = profile
        if (buyer == null) { root.addView(ProgressBar(requireContext())); root.label("Memeriksa profil, harga, dan stok…"); return }
        val shipping = root.card()
        shipping.label("Data pembeli dan pengiriman", 19f, true)
        shipping.label(buyer.optString("nama"), 18f, true)
        shipping.label("HP: ${buyer.optString("no_hp")}")
        shipping.label("Alamat: ${buyer.optString("alamat")}")
        shipping.label("Negara: ${buyer.optString("negara")}")
        val type = buyer.optString("tipe_pembeli", "perorangan")
        shipping.label("Jenis pembeli: ${buyerTypeLabel(type)}")
        shipping.action("Ubah data pengiriman") { host.openEditProfile(buyer.toString()) }
        val complete = buyer.optString("nama").isNotBlank() && AccountValidation.phone(buyer.optString("no_hp")) &&
            buyer.optString("alamat").let { it.isNotBlank() && it != "-" } && buyer.optString("negara").isNotBlank()
        val problem = if (!complete) "Lengkapi profil dan alamat pengiriman terlebih dahulu." else checkoutProblem(items, type)
        root.label("Produk yang dipesan", 20f, true)
        items.forEach { item ->
            root.card().apply {
                label(item.product.name, 18f, true)
                label("${quantityText(item.quantity)} ${item.product.unit} × ${rupiah(item.product.price)}")
                label("Subtotal: ${rupiah(item.product.subtotal(item.quantity))}", 17f, true, green)
            }
        }
        val total = items.sumOf { it.product.subtotal(it.quantity) }
        root.label("Estimasi produk: ${rupiah(total)}", 22f, true, green)
        root.label("Ongkir mengikuti penawaran toko.", 14f, color = muted)
        root.label("Catatan pesanan (opsional)", 15f, true)
        val notes = EditText(requireContext()).apply {
            id = R.id.checkout_note; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Contoh: waktu pengiriman yang diinginkan"; setText(note)
            doAfterTextChanged { note = it?.toString().orEmpty() }
        }
        root.addView(notes)
        if (problem != null) root.label(problem)
        root.action("Kembali ke keranjang") { host.openCart() }
        root.action("Ajukan pesanan ke admin") {
            if (note.length > 2000) { notes.error = "Maksimal 2000 karakter."; return@action }
            val current = host.cart.items().filter { it.selected }
            if (current.map { it.product.id to it.quantity } != items.map { it.product.id to it.quantity }) { reload(); return@action }
            MaterialAlertDialogBuilder(requireContext()).setTitle("Ajukan pesanan?")
                .setMessage("${items.size} produk untuk ${buyer.optString("nama")}\nEstimasi: ${rupiah(total)}\n\nPesanan akan dikirim ke admin untuk penawaran harga dan ongkir.")
                .setNegativeButton("Periksa lagi", null).setPositiveButton("Ajukan pesanan") { _, _ ->
                    val active = AccountStore(requireContext())
                    val currentToken = active.token(api.baseUrl)
                    if (currentToken == null || submission.actor(api.baseUrl, active) != actor) { redraw(); return@setPositiveButton }
                    val payload = JSONObject()
                    listOf("nama", "no_hp", "alamat", "negara", "tipe_pembeli").forEach { payload.put(it, buyer.getString(it)) }
                    payload.put("catatan", note.trim()).put("items", JSONArray().apply {
                        items.forEach { put(JSONObject().put("id_produk", it.product.id).put("jumlah", it.quantity.toPlainString())) }
                    })
                    if (!submission.submit(api.baseUrl, currentToken, actor, payload, items)) {
                        Toast.makeText(requireContext(), "Pengajuan lain sedang berjalan atau belum diperiksa.", Toast.LENGTH_LONG).show()
                    }
                    redraw()
                }.show()
        }.isEnabled = problem == null
    }
}

class OrdersFragment : RemoteFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener("firebase_order_update", viewLifecycleOwner) { _, _ -> reload() }
    }
    private val reload: () -> Unit = ::load
    private var page = 1
    private var category = "aktif"
    private lateinit var root: LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        page = state?.getInt("page") ?: page
        category = state?.getString("category") ?: category
        root = requireContext().column(); reload()
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("page", page); outState.putString("category", category); super.onSaveInstanceState(outState) }
    private fun load() {
        root.removeAllViews(); root.label("Pesanan", 25f, true)
        api.queue.cancelAll(requestTag)
        root.orderTabs(category) { category = it; page = 1; reload() }
        val account = AccountStore(requireContext()); val token = account.token(api.baseUrl)
        if (token == null) { root.label("Masuk untuk melihat pesanan Anda."); root.action("Masuk") { host.openAuth(false) }; return }
        val panel = requireContext().column(0); root.addView(panel); loading(panel)
        root.secondaryAction("Perbarui") { reload() }
        api.account(Request.Method.GET, "pembeli/pesanan?page=$page&kategori=$category", null, requestTag, token, { response ->
            if (view == null) return@account
            panel.removeAllViews()
            val data = response.getJSONObject("data"); val orders = data.getJSONArray("data")
            val submission = CheckoutSubmission.get(requireContext()); val actor = submission.actor(api.baseUrl, account)
            submission.state(actor)?.let { record ->
                if (record.optString("status") == "success") {
                    val code = record.getString("code")
                    submission.finishCart(actor, api.baseUrl, code, record); host.updateCartBadge()
                    panel.action("Buka pesanan terakhir: $code") { submission.clear(actor); host.openOrder(code) }
                } else panel.label(if (submission.busy(actor)) "Pengajuan pesanan sedang diproses." else "Pengajuan sebelumnya perlu diperiksa. Cocokkan pesanan di bawah dengan isi keranjang sebelum mengulang checkout.", 14f, color = muted)
            }
            if (orders.length() == 0) panel.card().apply {
                label(when (category) { "selesai" -> "Belum ada pesanan selesai"; "dibatalkan" -> "Tidak ada pesanan dibatalkan"; else -> "Belum ada pesanan aktif" }, 18f, true)
                if (category == "aktif") secondaryAction("Mulai belanja") { host.openCatalog() }
            }
            for (index in 0 until orders.length()) {
                val order = orders.getJSONObject(index); val code = order.getString("kode_pesanan")
                panel.orderCard(code, order.optString("status_pesanan"), order.optString("tanggal"), rupiah(order.getString("total").toBigDecimal().toLong())) { host.openOrder(code) }
            }
            val current = data.getInt("current_page"); val last = data.getInt("last_page")
            if (last > 1) panel.label("$current / $last", 13f, color = muted)
            if (current > 1) panel.secondaryAction("Sebelumnya") { page = current - 1; reload() }
            if (current < last) panel.secondaryAction("Berikutnya") { page = current + 1; reload() }
        }, { failure ->
            if (view == null) return@account
            if (failure.status == 401) { account.clear(); reload() } else error(panel, failure.message) { reload() }
        })
    }
}

class OrderDetailFragment : RemoteFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener("firebase_order_update", viewLifecycleOwner) { _, _ -> if (!busy) reload() }
    }
    private val reload: () -> Unit = ::load
    private lateinit var root: LinearLayout
    private var busy = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = requireContext().column(); reload()
        return ScrollView(requireContext()).apply { addView(root) }
    }
    private val code get() = requireArguments().getString("code")!!
    private fun load() {
        root.removeAllViews(); root.label("Rincian pesanan", 26f, true)
        root.secondaryAction("← Pesanan") { host.openOrders() }
        val account = AccountStore(requireContext()); val token = account.token(api.baseUrl)
        if (token == null) { root.label("Masuk untuk membuka pesanan Anda."); root.action("Masuk") { host.openAuth(false) }; return }
        val panel = requireContext().column(0); root.addView(panel); loading(panel)
        api.account(Request.Method.GET, "pembeli/pesanan/$code", null, requestTag, token, { response ->
            if (view == null) return@account
            busy = false; panel.removeAllViews(); render(panel, response.getJSONObject("data"), token)
        }, { failure ->
            if (view == null) return@account
            if (failure.status == 401) { account.clear(); reload() } else error(panel, failure.message) { reload() }
        })
    }
    private fun render(panel: LinearLayout, order: JSONObject, token: String) {
        val simulation = order.optBoolean("simulasi_pembayaran")
        panel.card().apply {
            label(code, 18f, true)
            badge(orderStatusLabel(order.optString("status_pesanan")))
            label(order.optString("tanggal").take(10), 13f, color = muted)
            if (simulation) badge("Pembayaran simulasi", muted)
            secondaryAction("QR Code pesanan") { host.openOrderQr(code) }
        }
        panel.secondaryAction("Perbarui status") { if (!busy) reload() }
        val shipping = order.optJSONObject("data_pengiriman")
        shipping?.let { buyer ->
            panel.card().apply {
                label("Penerima", 14f, true, muted); label(buyer.optString("nama"), 18f, true)
                label(buyer.optString("no_hp"), 14f, color = muted)
                label(buyer.optString("alamat"), 14f)
                if (buyer.optString("negara") != "Indonesia") label(buyer.optString("negara"), 14f)
            }
        }
        panel.label("Produk dan rincian biaya", 20f, true)
        val items = order.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            panel.card().apply {
                label(item.getString("nama_produk"), 18f, true)
                label("${quantityText(item.getString("jumlah").toBigDecimal())} ${item.getString("satuan")} × ${money(item, "harga_satuan")}")
                label(money(item, "subtotal"), 18f, true, green)
            }
        }
        panel.label("Ongkir: ${money(order, "biaya_pengiriman")}")
        panel.label("Total: ${money(order, "total")}", 23f, true, green)
        if (order.optString("status_pesanan") == "menunggu_persetujuan") panel.label("Total sementara. Ongkir dan harga akhir menunggu penawaran admin.", 14f, color = muted)
        text(order, "catatan")?.let { panel.label("Catatan pembeli: $it") }
        text(order, "catatan_penawaran")?.let { panel.label("Catatan admin: $it") }
        if (order.optString("status_penawaran") == "menunggu" && order.optString("status_pesanan") == "disetujui") {
            panel.label("Periksa penawaran harga dan ongkir di atas sebelum memberikan keputusan.")
            panel.action("Terima penawaran") { confirm(panel, token, "penawaran", JSONObject().put("keputusan", "diterima").put("versi_penawaran", order.getInt("versi_penawaran")), "Terima penawaran?", "Total penawaran: ${money(order, "total")}. Setelah disetujui, pesanan dilanjutkan ke pembayaran DP.") }
            panel.action("Tolak penawaran") { confirm(panel, token, "penawaran", JSONObject().put("keputusan", "ditolak").put("versi_penawaran", order.getInt("versi_penawaran")), "Tolak penawaran?", "Pesanan akan berstatus ditolak. Anda dapat membuat pengajuan baru bila diperlukan.") }
        }
        val payments = order.optJSONArray("pembayaran") ?: JSONArray()
        panel.label("Pembayaran tercatat", 20f, true)
        if (payments.length() == 0) panel.label("Belum ada pembayaran tercatat.", 14f, color = muted)
        for (index in 0 until payments.length()) {
            val payment = payments.getJSONObject(index)
            panel.label("${payment.optString("jenis").uppercase()}: ${money(payment, "jumlah")} • ${orderStatusLabel(payment.optString("status_bayar"))}", 15f)
            text(payment, "catatan_verifikasi")?.let { panel.label("Catatan verifikasi: $it", 14f) }
            if (payment.optBoolean("ada_bukti")) panel.action("Lihat bukti ${payment.optString("jenis").uppercase()}") {
                api.account(Request.Method.GET, "pembeli/pesanan/$code/bukti/${payment.getInt("id_pembayaran")}", null, requestTag, token, { response ->
                    if (view != null) {
                        val image = com.android.volley.toolbox.NetworkImageView(requireContext()).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(-1, requireContext().dp(400))
                            setImageUrl(response.getJSONObject("data").getString("url"), api.images)
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                        MaterialAlertDialogBuilder(requireContext()).setTitle("Bukti pembayaran").setView(image).setPositiveButton("Tutup", null).show()
                    }
                }, { failure -> if (view != null) Toast.makeText(requireContext(), failure.message, Toast.LENGTH_LONG).show() })
            }
        }
        panel.detailRow("Dibayar", money(order, "total_dibayar"))
        if (order.optString("status_pesanan") == "menunggu_dp") {
            panel.label("DP ${order.optInt("dp_persen", 50)}%: ${money(order, "nominal_dp")}", 18f, true)
            panel.label("Pembayaran akan diverifikasi oleh toko.", 14f, color = muted)
        }
        val paymentRows = (0 until payments.length()).map { payments.getJSONObject(it) }
        val pending = paymentRows.any { it.optString("status_bayar") == "menunggu" }
        val dpPaid = paymentRows.any { it.optString("jenis") == "dp" && it.optString("status_bayar") == "dibayar" }
        if (pending) panel.label("Pembayaran masih menunggu. Perbarui status setelah admin memeriksanya.", 15f, color = muted)
        else if (order.optString("status_penawaran") == "diterima" && order.optString("status_pesanan") !in listOf("ditolak", "lunas")) {
            if (order.optBoolean("transfer_aktif")) panel.action(if (dpPaid) "Bayar pelunasan" else "Bayar DP") {
                host.openPayment(code, if (dpPaid) "pelunasan" else "dp")
            } else panel.label("Pembayaran belum tersedia. Hubungi toko.", 14f, color = muted)
        }
        order.optJSONObject("pengiriman")?.let { shipment ->
            panel.card().apply {
                label("Informasi pengiriman", 20f, true)
                label("Kurir: ${shipment.optString("kurir")}"); label("Resi: ${shipment.optString("no_resi")}")
                label("Status: ${orderStatusLabel(shipment.optString("status_pengiriman"))}")
                text(shipment, "tanggal_kirim")?.let { label("Tanggal kirim: $it") }
                text(shipment, "tanggal_terima")?.let { label("Tanggal diterima: $it") }
            }
            if (shipment.optString("status_pengiriman") == "dikirim") panel.action("Konfirmasi pesanan diterima") {
                confirm(panel, token, "diterima", JSONObject(), "Pesanan sudah diterima?", "Konfirmasi hanya setelah barang benar-benar Anda terima.")
            }
        }
    }
    private fun confirm(panel: LinearLayout, token: String, action: String, payload: JSONObject, title: String, message: String) {
        if (busy) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message).setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi") { _, _ ->
                if (busy) return@setPositiveButton
                busy = true
                fun disable(view: View) {
                    if (view is android.widget.Button) view.isEnabled = false
                    if (view is ViewGroup) for (index in 0 until view.childCount) disable(view.getChildAt(index))
                }
                disable(root)
                panel.label("Memproses keputusan…", color = muted)
                // Decisions are versioned by the backend. Refresh before making another decision.
                api.account(Request.Method.POST, "pembeli/pesanan/$code/$action", payload, requestTag, token, {
                    busy = false; if (view != null) reload()
                }, { failure ->
                    busy = false
                    if (view == null) return@account
                    if (failure.status == 401) AccountStore(requireContext()).clear()
                    panel.label(failure.message)
                    panel.action("Perbarui pesanan") { reload() }
                })
            }.show()
    }
    private fun money(json: JSONObject, key: String) = rupiah(text(json, key)?.toBigDecimalOrNull()?.toLong() ?: 0L)
    private fun text(json: JSONObject, key: String): String? = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
    companion object { fun create(code: String) = OrderDetailFragment().apply { arguments = Bundle().apply { putString("code", code) } } }
}

class PaymentFragment : RemoteFragment() {
    private val reload: () -> Unit = ::load
    private val redraw: () -> Unit = ::render
    private lateinit var root: LinearLayout
    private lateinit var server: String
    private var accountId = 0
    private var order: JSONObject? = null
    private var imagePath: String? = null
    private var cameraPath: String? = null
    private var uploadKey = UUID.randomUUID().toString()
    private var busy = false
    private var message: String? = null
    private val code get() = requireArguments().getString("code")!!
    private val kind get() = requireArguments().getString("kind")!!
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { prepareImage(it) } }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraPath?.let(::File)
        if (taken && file != null) prepareImage(Uri.fromFile(file))
        else { file?.delete(); cameraPath = null }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        server = state?.getString("server") ?: api.baseUrl
        accountId = state?.getInt("accountId") ?: AccountStore(requireContext()).user()?.optInt("id_user") ?: 0
        imagePath = state?.getString("image")
        cameraPath = state?.getString("camera")
        uploadKey = state?.getString("uploadKey") ?: uploadKey
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = requireContext().column()
        reload()
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("server", server); outState.putInt("accountId", accountId)
        outState.putString("image", imagePath); outState.putString("camera", cameraPath)
        outState.putString("uploadKey", uploadKey)
        super.onSaveInstanceState(outState)
    }
    private fun validSession(): Boolean = api.baseUrl == server && AccountStore(requireContext()).let {
        it.token(server) != null && it.user()?.optInt("id_user") == accountId
    }
    private fun load() {
        if (!validSession()) { message = "Akun atau alamat server berubah. Buka kembali pesanan dari riwayat."; redraw(); return }
        val token = AccountStore(requireContext()).token(server)!!
        root.removeAllViews(); root.label("Memeriksa tagihan…"); root.addView(ProgressBar(requireContext()))
        api.account(Request.Method.GET, "pembeli/pesanan/${Uri.encode(code)}", null, requestTag, token, { response ->
            if (view != null) { order = response.getJSONObject("data"); redraw() }
        }, { failure -> if (view != null) { message = failure.message; redraw() } })
    }
    private fun render() {
        if (!::root.isInitialized || !isAdded) return
        root.removeAllViews(); root.label(if (kind == "dp") "Pembayaran DP" else "Pelunasan", 26f, true)
        root.label(code, 18f, true)
        root.action("Kembali ke rincian pesanan") { host.openOrder(code) }.isEnabled = !busy
        message?.let { root.label(it, 15f) }
        if (!validSession()) return
        val data = order ?: run { root.action("Perbarui tagihan") { reload() }; return }
        val simulation = data.optBoolean("simulasi_pembayaran")
        if (simulation) root.badge("Simulasi • tanpa transfer uang", muted)
        val payments = data.optJSONArray("pembayaran")
        val rows = (0 until (payments?.length() ?: 0)).map { payments!!.getJSONObject(it) }
        val pending = rows.any { it.optString("status_bayar") == "menunggu" }
        val dpPaid = rows.any { it.optString("jenis") == "dp" && it.optString("status_bayar") == "dibayar" }
        val amount = if (kind == "dp") data.optString("nominal_dp", "0").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            else (data.optString("total", "0").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) - (data.optString("total_dibayar", "0").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
        if (pending || data.optString("status_penawaran") != "diterima" || data.optString("status_pesanan") in listOf("ditolak", "lunas") || amount.signum() <= 0 || (kind == "dp" && dpPaid) || (kind == "pelunasan" && !dpPaid)) {
            root.label(if (pending) "Pembayaran sedang menunggu pemeriksaan. Lihat status di rincian pesanan." else "Tagihan ini belum tersedia atau sudah dibayar.")
            return
        }
        if (!data.optBoolean("transfer_aktif")) { root.label("Pembayaran belum tersedia. Hubungi toko."); return }
        root.label("Tagihan: ${rupiah(amount.toLong())}", 23f, true, green)
        if (!simulation) {
            val bank = data.getJSONObject("transfer")
            root.card().apply {
                label("Rekening Tamafarm", 19f, true); label(bank.getString("bank"), 18f, true)
                label(bank.getString("account"), 21f, true); label("Atas nama: ${bank.getString("holder")}")
            }
        }
        root.label("Unggah bukti untuk diperiksa toko.", 14f, color = muted)
        imagePath?.let(::File)?.takeIf { it.isFile }?.let { file ->
            root.addView(ImageView(requireContext()).apply {
                setImageURI(Uri.fromFile(file)); scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Bukti transfer yang dipilih"
            }, LinearLayout.LayoutParams(-1, requireContext().dp(320)))
        }
        root.action("Pilih dari galeri") { runCatching { gallery.launch("image/*") }.onFailure { message = "Galeri belum tersedia."; redraw() } }.isEnabled = !busy
        root.action("Foto dengan kamera") {
            val directory = File(requireContext().cacheDir, "payment_proofs").apply { mkdirs() }
            val file = File.createTempFile("camera_", ".jpg", directory)
            cameraPath = file.absolutePath
            runCatching { camera.launch(FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.proofs", file)) }
                .onFailure { file.delete(); cameraPath = null; message = "Kamera belum tersedia pada perangkat ini."; redraw() }
        }.isEnabled = !busy
        if (busy) root.addView(ProgressBar(requireContext()))
        root.action("Kirim bukti pembayaran") {
            MaterialAlertDialogBuilder(requireContext()).setTitle(if (simulation) "Kirim contoh bukti simulasi?" else "Kirim bukti transfer?")
                .setMessage(if (simulation) "Simulasi ${rupiah(amount.toLong())} tanpa transfer uang. Admin akan memeriksa contoh bukti untuk melanjutkan alur pesanan." else "Pastikan transfer ${rupiah(amount.toLong())} sudah dilakukan ke rekening Tamafarm. Admin akan memeriksa bukti dan dana masuk.")
                .setNegativeButton("Periksa lagi", null).setPositiveButton("Kirim") { _, _ -> submit() }.show()
        }.isEnabled = !busy && imagePath?.let(::File)?.isFile == true
        root.action("Perbarui tagihan") { reload() }.isEnabled = !busy
    }
    private fun prepareImage(uri: Uri) {
        if (!validSession() || busy) return
        busy = true; message = "Menyiapkan foto bukti…"; redraw()
        val appContext = requireContext().applicationContext
        val previous = imagePath
        Executors.newSingleThreadExecutor().also { worker -> worker.execute {
            val result = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Pilih foto JPG, PNG, atau WebP yang valid." }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Foto tidak dapat dibaca.")
                val bytes = ByteArrayOutputStream()
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                    if (bytes.size() > 1500000) { bytes.reset(); bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bytes) }
                } finally { bitmap.recycle() }
                require(bytes.size() <= 1500000) { "Foto terlalu besar. Pilih foto yang lebih kecil." }
                val directory = File(appContext.cacheDir, "payment_proofs").apply { mkdirs() }
                File.createTempFile("proof_", ".jpg", directory).apply { writeBytes(bytes.toByteArray()) }.absolutePath
            }
            Handler(Looper.getMainLooper()).post {
                busy = false
                result.fold({ path ->
                    previous?.let(::File)?.delete(); imagePath = path; uploadKey = UUID.randomUUID().toString()
                    message = "Foto siap dikirim. Pastikan tulisan dan nominalnya terbaca."
                }, { message = it.message ?: "Foto tidak dapat diproses." })
                cameraPath?.let(::File)?.delete(); cameraPath = null
                if (view != null) redraw()
            }
            worker.shutdown()
        } }
    }
    private fun submit() {
        if (busy || !validSession()) return
        val file = imagePath?.let(::File)?.takeIf { it.isFile } ?: return
        val token = AccountStore(requireContext()).token(server) ?: return
        busy = true; message = "Mengirim bukti. Tunggu sampai hasilnya tampil…"; redraw()
        api.uploadProof(server, code, kind, uploadKey, file.readBytes(), token, {
            busy = false; file.delete(); imagePath = null; message = "Bukti terkirim. Tunggu verifikasi admin."
            if (view != null) reload()
        }, { failure ->
            busy = false; message = failure.message
            if (view != null) reload()
        })
    }
    companion object {
        fun create(code: String, kind: String) = PaymentFragment().apply {
            arguments = Bundle().apply { putString("code", code); putString("kind", kind) }
        }
    }
}
