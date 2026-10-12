package iffan.budhi.uts2

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.core.widget.doAfterTextChanged

internal val ink = Color.rgb(24, 51, 43)
internal val muted = Color.rgb(99, 117, 109)
internal val green = Color.rgb(24, 103, 77)
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
internal fun Context.background(color: Int, radius: Int = 16): GradientDrawable = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
}
internal fun LinearLayout.label(text: String, size: Float = 16f, bold: Boolean = false, color: Int = ink): TextView = TextView(context).also {
    it.text = presentationText(text); it.textSize = size; it.setTextColor(color)
    it.setLineSpacing(context.dp(2).toFloat(), 1f)
    if (bold) it.setTypeface(null, Typeface.BOLD)
    it.setPadding(0, context.dp(3), 0, context.dp(6))
    if (text.isBlank()) it.visibility = View.GONE
    val label = it
    label.doAfterTextChanged { value -> label.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE }
    addView(it, LinearLayout.LayoutParams(-1, -2))
}
internal fun LinearLayout.action(text: String, clicked: () -> Unit): MaterialButton = MaterialButton(context).also {
    it.text = text; it.isAllCaps = false; it.minHeight = context.dp(48)
    it.cornerRadius = context.dp(14)
    it.insetTop = 0; it.insetBottom = 0
    addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
    it.setOnClickListener { clicked() }
}
internal fun Context.column(padding: Int = 20): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
}
internal fun LinearLayout.card(): LinearLayout = context.column(18).also {
    it.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = context.dp(20).toFloat(); setStroke(context.dp(1), Color.rgb(231, 237, 231)) }
    addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(14) })
}
internal fun LinearLayout.productArt(product: Product, height: Int = 150, category: Boolean = true) {
    if (category) label(product.category, 12f, true, green)
    com.android.volley.toolbox.NetworkImageView(context).also {
        it.contentDescription = "Foto ${product.name}"
        it.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        it.background = context.background(Color.rgb(227, 239, 229))
        it.setDefaultImageResId(R.drawable.ic_catalog)
        it.setErrorImageResId(R.drawable.ic_catalog)
        it.clipToOutline = true
        addView(it, LinearLayout.LayoutParams(-1, context.dp(height)).apply { bottomMargin = context.dp(10) })
        val api = ApiClient.get(context)
        val url = api.imageUrl(product.imageUrl)
        if (url != null) it.setImageUrl(url, api.images)
    }
}

internal fun presentationText(value: String): String = value
    .replace(Regex("(?i)[^.!?]*merupakan data demo[.!?]?"), "")
    .replace(Regex("(?i)contoh produk katalog:\\s*"), "")
    .replace(Regex("(?i)contoh paket untuk"), "Paket untuk")
    .replace(Regex("(?i)\\bdemo\\b\\s*"), "")
    .replace(Regex("[ \\t]{2,}"), " ").trim()

internal fun LinearLayout.secondaryAction(text: String, clicked: () -> Unit): MaterialButton = action(text, clicked).apply {
    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
    setTextColor(green)
    strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(200, 218, 207))
    strokeWidth = context.dp(1)
}
internal fun LinearLayout.badge(text: String, tone: Int = green): TextView = label(text, 12f, true, tone).apply {
    background = context.background(Color.rgb(235, 243, 237), 8)
    setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = context.dp(10) }
}
internal fun LinearLayout.detailRow(title: String, value: String) {
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, context.dp(4), 0, context.dp(4)) }
    row.addView(TextView(context).apply { text = title; textSize = 14f; setTextColor(muted) }, LinearLayout.LayoutParams(context.dp(100), -2))
    row.addView(TextView(context).apply { text = presentationText(value); textSize = 14f; setTextColor(ink) }, LinearLayout.LayoutParams(0, -2, 1f))
    addView(row)
}
internal fun LinearLayout.orderTabs(selected: String, changed: (String) -> Unit) {
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("aktif" to "Aktif", "selesai" to "Selesai", "dibatalkan" to "Dibatalkan").forEach { (key, text) ->
        val tab = com.google.android.material.button.MaterialButton(context).apply {
            this.text = text; textSize = 12f; isAllCaps = false; minWidth = 0
            setPadding(context.dp(4), 0, context.dp(4), 0)
            cornerRadius = context.dp(12); insetTop = 0; insetBottom = 0
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (key == selected) green else Color.WHITE)
            setTextColor(if (key == selected) Color.WHITE else muted)
            isSelected = key == selected
            setOnClickListener { if (key != selected) changed(key) }
        }
        row.addView(tab, LinearLayout.LayoutParams(0, context.dp(44), 1f).apply { marginEnd = context.dp(4) })
    }
    addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(16) })
}
internal fun LinearLayout.orderCard(code: String, status: String, date: String, total: String, opened: () -> Unit) {
    card().apply {
        label(code, 16f, true)
        badge(orderStatusLabel(status))
        label(date.take(10), 13f, color = muted)
        label(total, 22f, true, green)
        secondaryAction("Lihat pesanan", opened)
    }
}
