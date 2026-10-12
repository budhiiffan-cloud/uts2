package iffan.budhi.uts2

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.net.URI
import java.io.ByteArrayOutputStream
import java.util.UUID
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.ParseError

class ApiClient private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("tamafarm_connection", Context.MODE_PRIVATE)
    val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    var baseUrl: String
        get() = preferences.getString("api_url", "http://10.0.2.2:8000/api")!!
        private set(value) { preferences.edit().putString("api_url", value).apply() }
    val images = ImageLoader(queue, object : ImageLoader.ImageCache {
        private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
        override fun getBitmap(url: String): Bitmap? = cache.get(url)
        override fun putBitmap(url: String, bitmap: Bitmap) { cache.put(url, bitmap) }
    })
    fun setServer(value: String) {
        val clean = value.trim().trimEnd('/')
        val uri = URI(clean)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.rawQuery == null && uri.fragment == null) {
            "Gunakan URL lengkap, misalnya http://10.0.2.2:8000/api"
        }
        require(BuildConfig.DEBUG || uri.scheme == "https") { "Versi rilis membutuhkan HTTPS." }
        baseUrl = if (uri.path.trimEnd('/').endsWith("/api")) clean else "$clean/api"
    }
    fun imageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val api = Uri.parse(baseUrl)
        val image = Uri.parse(raw)
        return if (image.host in listOf("localhost", "127.0.0.1", "0.0.0.0", "10.0.2.2") || image.host == null) {
            api.buildUpon().path(image.path).clearQuery().fragment(null).build().toString()
        } else raw
    }
    private fun get(path: String, tag: Any, success: (JSONObject) -> Unit, failure: (String) -> Unit) {
        val request = JsonObjectRequest(Request.Method.GET, "$baseUrl/$path", null, { json ->
            try { success(json) } catch (_: Exception) { failure("Format data produk dari server belum sesuai.") }
        }, { error -> failure(errorMessage(error)) })
        request.tag = tag
        request.setShouldCache(false)
        request.retryPolicy = DefaultRetryPolicy(15000, 0, 1f)
        queue.add(request)
    }
    fun products(tag: Any, success: (List<Product>) -> Unit, failure: (String) -> Unit) = get("produk", tag, {
        val data = it.getJSONArray("data")
        success((0 until data.length()).map { index -> Product.fromJson(data.getJSONObject(index)) })
    }, failure)
    fun product(id: Int, tag: Any, success: (Product) -> Unit, failure: (String) -> Unit) = get("produk/$id", tag, {
        success(Product.fromJson(it.getJSONObject("data")))
    }, failure)
    private fun errorMessage(error: VolleyError): String = when (error.networkResponse?.statusCode) {
        404 -> "Produk atau alamat API tidak ditemukan."
        401, 403 -> "Server menolak akses katalog."
        in 500..599 -> "Server Tamafarm bermasalah. Silakan coba lagi."
        else -> "Tidak dapat terhubung ke Tamafarm. Pastikan server aktif dan periksa alamat di menu Profil."
    }
    data class AccountError(val message: String, val status: Int? = null, val fields: Map<String, String> = emptyMap())
    fun account(method: Int, path: String, body: JSONObject?, tag: Any, token: String?, success: (JSONObject) -> Unit, failure: (AccountError) -> Unit) {
        val request = object : JsonObjectRequest(method, "$baseUrl/$path", body, { response ->
            try { success(response) } catch (_: Exception) { failure(AccountError("Respons server belum sesuai. Perbarui halaman untuk memeriksa hasilnya.")) }
        }, { error ->
            val status = error.networkResponse?.statusCode
            val json = runCatching { JSONObject(String(error.networkResponse?.data ?: byteArrayOf(), Charsets.UTF_8)) }.getOrNull()
            val errors = json?.optJSONObject("errors")
            val fields = mutableMapOf<String, String>()
            errors?.keys()?.forEach { key -> fields[key] = errors.optJSONArray(key)?.optString(0) ?: "Data belum valid." }
            val message = when (status) {
                401 -> if (token == null) "Username atau password salah." else "Sesi berakhir. Silakan masuk kembali."
                403 -> "Akses ditolak. Gunakan akun pembeli Tamafarm."
                422 -> fields.values.joinToString("\n").ifBlank { json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Periksa kembali data yang diisi." }
                429 -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba kembali."
                404 -> "Data atau layanan tidak ditemukan. Pastikan akun dan alamat server sesuai."
                409 -> json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Data pesanan berubah. Perbarui pesanan sebelum melanjutkan."
                in 500..599 -> "Server Tamafarm bermasalah. Silakan coba lagi."
                else -> "Tidak dapat terhubung ke Tamafarm. Pastikan server aktif dan periksa alamat di menu Profil."
            }
            failure(AccountError(message, status, fields))
        }) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf("Accept" to "application/json").apply {
                if (token != null) put("Authorization", "Bearer $token")
            }
        }
        request.tag = tag
        request.setShouldCache(false)
        request.retryPolicy = DefaultRetryPolicy(15000, 0, 1f)
        queue.add(request)
    }
    fun uploadProof(server: String, code: String, kind: String, uploadKey: String, image: ByteArray, token: String,
                    success: (JSONObject) -> Unit, failure: (AccountError) -> Unit) {
        val boundary = "Tamafarm${UUID.randomUUID()}"
        val output = ByteArrayOutputStream()
        fun write(value: String) { output.write(value.toByteArray(Charsets.UTF_8)) }
        mapOf("jenis" to kind, "upload_key" to uploadKey).forEach { (name, value) ->
            write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        }
        write("--$boundary\r\nContent-Disposition: form-data; name=\"bukti\"; filename=\"bukti.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
        output.write(image); write("\r\n--$boundary--\r\n")
        val request = object : Request<JSONObject>(Request.Method.POST, "$server/pembeli/pesanan/${Uri.encode(code)}/bukti", { error ->
            val status = error.networkResponse?.statusCode
            val json = runCatching { JSONObject(String(error.networkResponse?.data ?: byteArrayOf(), Charsets.UTF_8)) }.getOrNull()
            val message = json?.optJSONObject("errors")?.let { fields -> fields.keys().asSequence().map { fields.optJSONArray(it)?.optString(0).orEmpty() }.joinToString("\n") }
                ?.takeIf { it.isNotBlank() } ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Koneksi terputus. Periksa rincian pesanan sebelum mengirim bukti kembali."
            failure(AccountError(message, status))
        }) {
            override fun getHeaders() = mutableMapOf("Accept" to "application/json", "Authorization" to "Bearer $token")
            override fun getBodyContentType() = "multipart/form-data; boundary=$boundary"
            override fun getBody() = output.toByteArray()
            override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> = try {
                Response.success(JSONObject(String(response.data, Charsets.UTF_8)), null)
            } catch (error: Exception) { Response.error(ParseError(error)) }
            override fun deliverResponse(response: JSONObject) { success(response) }
        }
        request.setShouldCache(false)
        request.retryPolicy = DefaultRetryPolicy(45000, 0, 1f)
        queue.add(request)
    }
    companion object {
        @Volatile private var instance: ApiClient? = null
        fun get(context: Context): ApiClient = instance ?: synchronized(this) {
            instance ?: ApiClient(context).also { instance = it }
        }
    }
}






