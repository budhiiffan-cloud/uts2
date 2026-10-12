package iffan.budhi.uts2

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal

abstract class RemoteFragment : Fragment() {
    protected val requestTag = Any()
    protected val api get() = ApiClient.get(requireContext())
    protected val host get() = requireActivity() as MainActivity
    override fun onDestroyView() { api.queue.cancelAll(requestTag); super.onDestroyView() }
    protected fun error(root: LinearLayout, message: String, retry: () -> Unit) {
        root.removeAllViews(); root.label(message, 16f, color = muted)
        root.action("Coba lagi", retry)
    }
    protected fun loading(root: LinearLayout) {
        api.queue.cancelAll(requestTag); root.removeAllViews()
        root.addView(ProgressBar(requireContext()), LinearLayout.LayoutParams(-1, requireContext().dp(48)))
        root.label("Memuat data Tamafarm…", 14f, color = muted)
    }
}

class HomeFragment : RemoteFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = requireContext().column()
        val hero = root.card()
        hero.background = requireContext().background(green, 24)
        hero.label("Lobster pilihan\nuntuk Anda.", 28f, true, Color.WHITE)
        hero.action("Belanja sekarang") { host.openCatalog() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE); setTextColor(green)
        }
        root.card().apply {
            label("Lokasi toko", 18f, true)
            secondaryAction("Lihat lokasi toko") { host.openStoreLocation() }
        }
        root.label("Pilihan untuk Anda", 24f, true)
        val products = requireContext().column(0)
        root.addView(products)
        fun refresh() {
            loading(products)
            api.products(requestTag, { items ->
                if (view == null) return@products
                host.cart.sync(items); products.removeAllViews()
                if (items.isEmpty()) products.label("Belum ada produk di katalog.")
                items.take(4).chunked(2).forEach { group ->
                    val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                    products.addView(row)
                    group.forEach { p ->
                        row.card().apply {
                            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(6); bottomMargin = context.dp(12) }
                            productArt(p, 100, false)
                            label(p.name, 16f, true).apply { minLines = 2; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                            label("${rupiah(p.price)}/${p.unit}", 15f, true, green)
                            isClickable = true; isFocusable = true
                            setOnClickListener { host.openProduct(p.id) }
                        }
                    }
                    if (group.size == 1) row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }, { if (view != null) error(products, it, ::refresh) })
        }
        root.secondaryAction("Perbarui produk", ::refresh)
        refresh()
        return ScrollView(requireContext()).apply { addView(root) }
    }
}

class CatalogFragment : RemoteFragment() {
    private var keyword = ""
    private var category = "Semua kategori"
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        keyword = state?.getString("keyword") ?: keyword
        category = state?.getString("category") ?: category
        val ctx = requireContext()
        val root = ctx.column()
        root.label("Katalog lobster", 28f, true)
        val panel = ctx.column(0)
        root.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f))
        fun refresh() {
            loading(panel)
            api.products(requestTag, { products ->
                if (view == null) return@products
                host.cart.sync(products); panel.removeAllViews()
                val search = AutoCompleteTextView(ctx).apply {
                    id = R.id.catalog_search; hint = "Cari nama produk"; inputType = InputType.TYPE_CLASS_TEXT
                    threshold = 1; setSingleLine(true)
                    setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, products.map { it.name }))
                    setText(keyword, false)
                }
                panel.addView(search, LinearLayout.LayoutParams(-1, ctx.dp(56)))
                val categories = listOf("Semua kategori") + products.map { it.category }.distinct()
                if (category !in categories) category = categories.first()
                val filter = Spinner(ctx).apply {
                    id = R.id.catalog_filter; contentDescription = "Filter kategori"
                    adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, categories)
                    setSelection(categories.indexOf(category))
                }
                panel.addView(filter, LinearLayout.LayoutParams(-1, ctx.dp(48)))
                val count = panel.label("", 13f, color = muted)
                val empty = panel.label("", 16f)
                val list = ListView(ctx).apply { id = R.id.catalog_list; divider = null; dividerHeight = ctx.dp(10) }
                panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
                fun filterProducts() {
                    val matches = products.filter { it.name.contains(keyword.trim(), true) && (category == categories.first() || it.category == category) }
                    count.text = "${matches.size} produk"
                    empty.text = if (products.isEmpty()) "Belum ada produk di web Tamafarm." else "Produk tidak ditemukan. Coba pencarian lain."
                    empty.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
                    list.adapter = ProductAdapter(matches)
                    list.setOnItemClickListener { _, _, position, _ -> host.openProduct(matches[position].id) }
                }
                search.doAfterTextChanged { keyword = it?.toString().orEmpty(); filterProducts() }
                filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { category = categories[position]; filterProducts() }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                filterProducts()
            }, { if (view != null) error(panel, it, ::refresh) })
        }
        root.secondaryAction("Perbarui katalog", ::refresh)
        refresh(); return root
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("keyword", keyword); outState.putString("category", category); super.onSaveInstanceState(outState)
    }
    private inner class ProductAdapter(private val products: List<Product>) : BaseAdapter() {
        override fun getCount() = products.size
        override fun getItem(position: Int) = products[position]
        override fun getItemId(position: Int) = products[position].id.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View = requireContext().column(12).apply {
            val p = products[position]; background = context.background(Color.WHITE); orientation = LinearLayout.HORIZONTAL
            val image = com.android.volley.toolbox.NetworkImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; background = context.background(Color.rgb(227, 239, 229), 12); clipToOutline = true
                contentDescription = p.name; setDefaultImageResId(R.drawable.ic_catalog); setErrorImageResId(R.drawable.ic_catalog)
                api.imageUrl(p.imageUrl)?.let { setImageUrl(it, api.images) }
            }
            addView(image, LinearLayout.LayoutParams(context.dp(88), context.dp(96)))
            val info = context.column(0).apply { setPadding(context.dp(14), 0, 0, 0) }
            addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            info.label(p.name, 16f, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            info.label(p.size, 12f, color = muted)
            info.label("${rupiah(p.price)}/${p.unit}", 16f, true, green)
            info.label(if (p.stock < p.minimum) "Stok terbatas" else "Stok ${quantityText(p.stock)} ${p.unit}", 12f, color = muted)
        }
    }
}

class ProductDetailFragment : RemoteFragment() {
    private var quantityValue = "1"
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        quantityValue = state?.getString("quantity") ?: quantityValue
        val ctx = requireContext(); val root = ctx.column()
        root.action("← Kembali") { host.onBackPressedDispatcher.onBackPressed() }
        val panel = ctx.column(0); root.addView(panel)
        fun refresh() {
            loading(panel)
            api.product(requireArguments().getInt("product_id"), requestTag, { p ->
                if (view == null) return@product
                panel.removeAllViews(); panel.productArt(p)
                panel.label(p.name, 25f, true); panel.label("${rupiah(p.price)} / ${p.unit}", 22f, true, green)
                val details = panel.card()
                details.label("Ukuran: ${p.size.ifBlank { "—" }}")
                details.label("Stok: ${quantityText(p.stock)} ${p.unit}")
                details.label("Minimal perorangan: 1 ${p.unit}")
                if (p.unit == "kg") {
                    details.label("Minimal restoran: ${quantityText(p.restaurantMinimum)} kg")
                    details.label("Minimal luar negeri: ${quantityText(p.exportMinimum)} kg")
                }
                panel.label(p.description, 16f, color = muted)
                panel.label("Jumlah pembelian (${p.unit})", 17f, true)
                val amount = EditText(ctx).apply {
                    id = R.id.detail_quantity; inputType = InputType.TYPE_CLASS_NUMBER or if (p.unit == "kg") InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                    setSingleLine(true); setText(quantityValue); isEnabled = p.stock >= p.minimum
                }
                panel.addView(amount, LinearLayout.LayoutParams(-1, ctx.dp(56)))
                val subtotal = panel.label("", 20f, true, green)
                fun update() {
                    quantityValue = amount.text.toString()
                    val qty = parseQuantity(quantityValue)
                    subtotal.text = if (qty != null && p.validQuantity(qty)) "Subtotal: ${rupiah(p.subtotal(qty))}" else "Masukkan jumlah yang sesuai stok dan minimal pembelian."
                }
                amount.doAfterTextChanged { update() }; update()
                panel.action("Tambah ke keranjang") {
                    val qty = parseQuantity(amount.text.toString())
                    if (qty == null || !p.validQuantity(qty)) {
                        amount.error = if (p.unit == "paket") "Paket harus bulat, minimal 1 dan tidak melebihi stok." else "Minimal 1 kg, maksimal ${quantityText(p.stock)} kg, hingga 2 angka desimal."
                        return@action
                    }
                    if (host.cart.add(p, qty)) {
                        host.updateCartBadge()
                        MaterialAlertDialogBuilder(ctx).setTitle("Masuk keranjang").setMessage("${p.name}, ${quantityText(qty)} ${p.unit}, berhasil ditambahkan.")
                            .setNegativeButton("Lanjut belanja", null).setPositiveButton("Lihat keranjang") { _, _ -> host.openCart() }.show()
                    } else Toast.makeText(ctx, "Jumlah gabungan di keranjang melebihi stok atau tidak sesuai satuan.", Toast.LENGTH_LONG).show()
                }.isEnabled = p.stock >= p.minimum
            }, { if (view != null) error(panel, it, ::refresh) })
        }
        refresh(); return ScrollView(ctx).apply { addView(root) }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("quantity", quantityValue); super.onSaveInstanceState(outState) }
    companion object { fun newInstance(id: Int) = ProductDetailFragment().apply { arguments = Bundle().apply { putInt("product_id", id) } } }
}

class CartFragment : RemoteFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext(); val root = ctx.column()
        val panel = ctx.column(0)
        root.action("← Kembali") { host.onBackPressedDispatcher.onBackPressed() }
        root.label("Keranjang Anda", 28f, true)
        val status = root.label("Memeriksa harga dan stok…", 14f, color = muted)
        root.addView(panel)
        var verified = false
        fun render() {
            panel.removeAllViews()
            val items = host.cart.items()
            if (items.isEmpty()) {
                panel.label("Keranjang masih kosong", 22f, true)
                panel.action("Buka katalog") { host.openCatalog() }; return
            }
            val totalLabel = TextView(ctx).apply { textSize = 23f; setTextColor(green) }
            val checkout = com.google.android.material.button.MaterialButton(ctx).apply { text = "Lanjut ke checkout"; isAllCaps = false }
            fun updateTotal() {
                val chosen = host.cart.items().filter { it.selected && it.product.validQuantity(it.quantity) }
                totalLabel.text = "Total: ${rupiah(chosen.sumOf { it.product.subtotal(it.quantity) })}"
                checkout.isEnabled = verified && chosen.isNotEmpty()
            }
            items.forEach { item ->
                val p = item.product; val card = panel.card(); val valid = p.validQuantity(item.quantity)
                card.addView(CheckBox(ctx).apply {
                    text = p.name; textSize = 17f; setTextColor(ink); isChecked = item.selected && valid; isEnabled = verified && valid
                    setOnCheckedChangeListener { _, checked -> host.cart.select(p.id, checked); updateTotal() }
                })
                card.label("${rupiah(p.price)} / ${p.unit} • Stok ${quantityText(p.stock)} ${p.unit}", 14f, color = muted)
                if (!valid) card.label("Jumlah melebihi stok atau produk sudah tidak tersedia. Ubah jumlah atau hapus produk.", 14f)
                val controls = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                val minus = Button(ctx).apply { text = "−"; contentDescription = "Kurangi jumlah ${p.name}"; isEnabled = verified && p.validQuantity(item.quantity - p.step) }
                val qty = TextView(ctx).apply { text = "${quantityText(item.quantity)} ${p.unit}"; gravity = android.view.Gravity.CENTER; textSize = 18f }
                val plus = Button(ctx).apply { text = "+"; contentDescription = "Tambah jumlah ${p.name}"; isEnabled = verified && p.validQuantity(item.quantity + p.step) }
                controls.addView(minus, LinearLayout.LayoutParams(ctx.dp(64), ctx.dp(52)))
                controls.addView(qty, LinearLayout.LayoutParams(0, -2, 1f))
                controls.addView(plus, LinearLayout.LayoutParams(ctx.dp(64), ctx.dp(52)))
                minus.setOnClickListener { host.cart.quantity(p, item.quantity - p.step); render() }
                plus.setOnClickListener { host.cart.quantity(p, item.quantity + p.step); render() }
                card.addView(controls)
                card.action("Ubah jumlah") {
                    val input = EditText(ctx).apply { inputType = InputType.TYPE_CLASS_NUMBER or if (p.unit == "kg") InputType.TYPE_NUMBER_FLAG_DECIMAL else 0; setText(item.quantity.toPlainString()) }
                    val dialog = MaterialAlertDialogBuilder(ctx).setTitle("Jumlah (${p.unit})").setView(input).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create()
                    dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = parseQuantity(input.text.toString())
                        if (value == null || !p.validQuantity(value)) input.error = "Jumlah harus sesuai satuan, minimal 1, dan tidak melebihi stok."
                        else { host.cart.quantity(p, value); dialog.dismiss(); render() }
                    } }; dialog.show()
                }.isEnabled = verified && p.stock >= p.minimum
                card.label("Subtotal: ${rupiah(p.subtotal(item.quantity))}", 17f, true)
                card.action("Hapus produk") {
                    MaterialAlertDialogBuilder(ctx).setTitle("Hapus dari keranjang?").setMessage(p.name).setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus") { _, _ -> host.cart.remove(p.id); host.updateCartBadge(); render() }.show()
                }
            }
            panel.addView(totalLabel); panel.addView(checkout, LinearLayout.LayoutParams(-1, -2))
            checkout.setOnClickListener { host.openCheckout() }
            updateTotal()
        }
        fun refresh() {
            api.queue.cancelAll(requestTag); verified = false; status.text = "Memeriksa harga dan stok…"; render()
            api.products(requestTag, { products ->
                if (view == null) return@products
                host.cart.sync(products); verified = true; status.text = ""; status.visibility = View.GONE; render()
            }, { if (view != null) { status.text = "$it\nKeranjang lokal tetap tersimpan; ringkasan menunggu pemeriksaan stok."; render() } })
        }
        root.action("Perbarui harga dan stok", ::refresh)
        refresh(); return ScrollView(ctx).apply { addView(root) }
    }
}





