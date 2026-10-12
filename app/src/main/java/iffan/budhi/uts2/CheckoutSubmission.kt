package iffan.budhi.uts2

import android.content.Context
import com.android.volley.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Owns order creation beyond a fragment's lifetime; never retries an ambiguous POST. */
internal class CheckoutSubmission private constructor(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("tamafarm_checkout", Context.MODE_PRIVATE)
    private val observers = mutableSetOf<() -> Unit>()
    private var activeActor: String? = null
    fun actor(server: String, account: AccountStore): String = server + "|" + account.user()?.optString("id_user").orEmpty()
    private fun key(actor: String) = MessageDigest.getInstance("SHA-256").digest(actor.toByteArray()).joinToString("") { "%02x".format(it) }
    fun state(actor: String): JSONObject? = preferences.getString(key(actor), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun busy(actor: String) = activeActor == actor
    fun observe(observer: () -> Unit) { observers.add(observer) }
    fun unobserve(observer: () -> Unit) { observers.remove(observer) }
    private fun publish(actor: String, state: JSONObject) {
        preferences.edit().putString(key(actor), state.toString()).commit()
        observers.toList().forEach { runCatching { it() } }
    }
    fun clear(actor: String) {
        if (busy(actor)) return
        preferences.edit().remove(key(actor)).commit()
        observers.toList().forEach { it() }
    }
    fun submit(server: String, token: String, actor: String, payload: JSONObject, items: List<CartItem>): Boolean {
        if (activeActor != null || state(actor) != null || ApiClient.get(context).baseUrl != server) return false
        val record = JSONObject().put("status", "pending").put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.product.id).put("quantity", it.quantity.toPlainString())) }
        })
        record.put("cart_epoch", CartStore(context).use { it.epoch() })
        if (!preferences.edit().putString(key(actor), record.toString()).commit()) return false
        activeActor = actor
        observers.toList().forEach { it() }
        ApiClient.get(context).account(Request.Method.POST, "pembeli/pesanan", payload, Any(), token, { response ->
            val code = response.getJSONObject("data").getString("kode_pesanan")
            record.put("status", "success").put("code", code)
            activeActor = null
            publish(actor, record)
            finishCart(actor, server, code, record)
        }, { error ->
            activeActor = null
            val definite = error.status in listOf(400, 401, 403, 404, 409, 422, 429)
            record.put("status", if (definite) "failed" else "uncertain").put("message", error.message)
            publish(actor, record)
        })
        return true
    }
    fun finishCart(actor: String, server: String, code: String, record: JSONObject) {
        runCatching { consumeCart(actor, server, code, record) }
    }
    private fun consumeCart(actor: String, server: String, code: String, record: JSONObject) {
        val account = AccountStore(context)
        if (ApiClient.get(context).baseUrl != server || this.actor(server, account) != actor || account.token(server) == null) return
        val quantities = record.getJSONArray("items")
        val submitted = (0 until quantities.length()).associate { index ->
            val item = quantities.getJSONObject(index)
            item.getInt("id") to item.getString("quantity").toBigDecimal()
        }
        CartStore(context).use { it.consume(server + "|" + code, record.getString("cart_epoch"), submitted) }
    }
    companion object {
        private var instance: CheckoutSubmission? = null
        fun get(context: Context): CheckoutSubmission = instance ?: CheckoutSubmission(context).also { instance = it }
    }
}
