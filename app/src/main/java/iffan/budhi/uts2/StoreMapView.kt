package iffan.budhi.uts2

import android.content.Context
import android.graphics.*
import android.view.View
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.ImageRequest
import android.widget.ImageView
import kotlin.math.floor

class StoreMapView(context: Context) : View(context) {
    private val queue = ApiClient.get(context).queue
    private val requestTag = Any()
    private var generation = 0
    private var tiles = mutableMapOf<String, Bitmap>()
    private var visibleTiles = emptyList<Triple<Int, Int, String>>()
    private var remaining = 0
    private var errors = 0
    private var zoom = 17
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var onStatus: ((String, Boolean) -> Unit)? = null
    val loadedTileCount get() = visibleTiles.count { tiles.containsKey(it.third) }
    val centerTileLoaded get(): Boolean {
        val point = StoreMapCoordinates.tilePoint(StoreMapCoordinates.LATITUDE, StoreMapCoordinates.LONGITUDE, zoom)
        return tiles.containsKey("$zoom/${floor(point.first).toInt()}/${floor(point.second).toInt()}")
    }
    init { contentDescription = "Peta lokasi Tama Lobster Air Tawar dengan penanda toko"; setBackgroundColor(Color.rgb(230, 237, 231)) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); reload() }
    fun changeZoom(change: Int) { val next = (zoom + change).coerceIn(14, 18); if (next != zoom) { zoom = next; tiles.clear(); reload() } }
    fun reload() {
        if (width <= 0 || height <= 0) return
        queue.cancelAll(requestTag)
        val current = ++generation
        val size = context.dp(256).toDouble()
        val point = StoreMapCoordinates.tilePoint(StoreMapCoordinates.LATITUDE, StoreMapCoordinates.LONGITUDE, zoom)
        visibleTiles = buildList {
            for (x in floor(point.first - width / (2 * size)).toInt()..floor(point.first + width / (2 * size)).toInt()) {
                for (y in floor(point.second - height / (2 * size)).toInt()..floor(point.second + height / (2 * size)).toInt()) add(Triple(x, y, "$zoom/$x/$y"))
            }
        }
        val required = visibleTiles.filterNot { tiles.containsKey(it.third) }
        remaining = required.size; errors = 0
        updateStatus(); invalidate()
        required.forEach { (_, _, key) ->
            val request = object : ImageRequest("https://tile.openstreetmap.org/$key.png", received@{ image ->
                if (current != generation) return@received
                tiles[key] = image
                remaining--; invalidate(); updateStatus()
            }, 0, 0, ImageView.ScaleType.CENTER, Bitmap.Config.RGB_565, {
                if (current == generation) { remaining--; errors++; updateStatus(); invalidate() }
            }) {
                override fun getHeaders(): MutableMap<String, String> = mutableMapOf("User-Agent" to "TamafarmBuyer/1.0 (iffan.budhi.uts2)")
            }
            request.tag = requestTag
            request.retryPolicy = DefaultRetryPolicy(12000, 1, 1f)
            queue.add(request)
        }
    }
    private fun updateStatus() {
        val message = when {
            remaining > 0 -> "Memuat peta…"
            loadedTileCount == 0 -> "Peta belum termuat. Periksa internet lalu tekan Coba lagi."
            errors > 0 -> "Sebagian peta belum termuat. Tekan Coba lagi untuk melengkapi."
            else -> ""
        }
        onStatus?.invoke(message, remaining > 0)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val point = StoreMapCoordinates.tilePoint(StoreMapCoordinates.LATITUDE, StoreMapCoordinates.LONGITUDE, zoom)
        val size = context.dp(256).toFloat()
        visibleTiles.forEach { (x, y, key) ->
            val left = (width / 2.0 + (x - point.first) * size).toFloat()
            val top = (height / 2.0 + (y - point.second) * size).toFloat()
            tiles[key]?.let { canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), paint) }
        }
        val x = width / 2f; val y = height / 2f
        paint.color = green
        val radius = context.dp(13).toFloat()
        canvas.drawCircle(x, y - radius, radius, paint)
        val tip = Path().apply { moveTo(x - radius * 0.65f, y - radius * 0.4f); lineTo(x, y + radius * 0.7f); lineTo(x + radius * 0.65f, y - radius * 0.4f); close() }
        canvas.drawPath(tip, paint)
        paint.color = Color.WHITE; canvas.drawCircle(x, y - radius, radius * 0.4f, paint)
        paint.textSize = context.dp(13).toFloat(); paint.typeface = Typeface.DEFAULT_BOLD
        val label = "Tama Lobster Air Tawar"
        val labelWidth = paint.measureText(label)
        val labelY = y - context.dp(39)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(x - labelWidth / 2 - context.dp(8), labelY - context.dp(17), x + labelWidth / 2 + context.dp(8), labelY + context.dp(7)), 8f, 8f, paint)
        paint.color = ink; canvas.drawText(label, x - labelWidth / 2, labelY, paint)
    }
    fun close() { generation++; queue.cancelAll(requestTag); onStatus = null }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
}
