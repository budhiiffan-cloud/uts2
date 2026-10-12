package iffan.budhi.uts2

import android.os.Bundle
import com.android.volley.Request
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

class FirebaseSession(private val host: MainActivity) {
    private var listener: ListenerRegistration? = null
    private var connecting = false
    private var authPending = false
    private var attempt = 0
    private var identity: String? = null
    private val requestTag = Any()
    private val api = ApiClient.get(host)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryDelay = 15000L
    private var lastDeviceAttempt = 0L
    var status = "Belum terhubung."
        private set
    private val retryTask = Runnable { if (listener == null) start() else registerDevice() }

    private fun updateStatus(message: String) {
        status = message
        host.refreshNotificationStatus()
    }

    private fun retry() {
        handler.removeCallbacks(retryTask)
        if (!host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        handler.postDelayed(retryTask, retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(120000L)
    }

    fun reconnect() {
        stop()
        retryDelay = 15000L
        lastDeviceAttempt = 0L
        start()
    }

    fun start() {
        if (FirebaseApp.getApps(host).isEmpty()) { updateStatus("Konfigurasi Firebase belum tersedia. Perbarui aplikasi."); return }
        val account = AccountStore(host)
        val token = account.token(api.baseUrl)
        val currentIdentity = token?.let { api.baseUrl + ":" + it }
        if (identity != currentIdentity) { stop(); FirebaseAuth.getInstance().signOut(); identity = currentIdentity }
        if (token == null) { updateStatus("Masuk untuk menerima pembaruan pesanan."); return }
        if (connecting) return
        if (listener != null) {
            if (android.os.SystemClock.elapsedRealtime() - lastDeviceAttempt > 30000L) registerDevice()
            return
        }
        val server = api.baseUrl
        val generation = AccountStore.generation
        val currentAttempt = attempt
        fun valid() = currentAttempt == attempt && generation == AccountStore.generation && api.baseUrl == server && account.token(server) == token && host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        connecting = true
        handler.removeCallbacks(retryTask)
        updateStatus("Menghubungkan pembaruan pesanan…")
        api.account(Request.Method.POST, "pembeli/firebase/session", JSONObject(), requestTag, token, { response ->
            if (!valid()) { connecting = false; return@account }
            val data = response.getJSONObject("data")
            val appProject = FirebaseApp.getInstance().options.projectId
            if (data.optString("project_id") != appProject) { connecting = false; updateStatus("Konfigurasi Firebase aplikasi dan server berbeda."); return@account }
            authPending = true
            FirebaseAuth.getInstance().signInWithCustomToken(data.getString("token")).addOnCompleteListener { result ->
                authPending = false
                connecting = false
                if (!valid()) {
                    FirebaseAuth.getInstance().signOut()
                    if (host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) start()
                    return@addOnCompleteListener
                }
                if (!result.isSuccessful || result.result?.user?.uid != data.getString("uid")) {
                    updateStatus("Login Firebase belum berhasil. Periksa internet; aplikasi akan mencoba lagi.")
                    retry()
                    return@addOnCompleteListener
                }
                val firestore = database()
                var initialized = false
                listener = firestore.collection("buyer_orders").whereEqualTo("uid", data.getString("uid"))
                    .addSnapshotListener { snapshot, error ->
                        if (!valid()) { stop(); return@addSnapshotListener }
                        if (error != null) {
                            listener?.remove(); listener = null
                            updateStatus("Pembaruan pesanan belum terhubung. Akan dicoba kembali.")
                            retry()
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        if (initialized && snapshot.documentChanges.isNotEmpty()) {
                            host.supportFragmentManager.setFragmentResult("firebase_order_update", Bundle())
                        }
                        initialized = true
                    }
                registerDevice()
            }
        }, { error ->
            if (!valid()) return@account
            connecting = false
            if (error.status == 401) { account.clear(); updateStatus("Sesi berakhir. Silakan masuk kembali.") }
            else { updateStatus("Koneksi pembaruan pesanan gagal. ${error.message}"); retry() }
        })
    }

    fun stop() {
        attempt++
        handler.removeCallbacks(retryTask)
        api.queue.cancelAll(requestTag)
        listener?.remove(); listener = null; connecting = authPending
    }

    companion object {
        private var firestore: FirebaseFirestore? = null
        private fun database(): FirebaseFirestore = firestore ?: FirebaseFirestore.getInstance().also {
            it.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            firestore = it
        }

        fun registerDevice(context: android.content.Context, completed: (Boolean) -> Unit = {}) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            val api = ApiClient.get(context)
            val account = AccountStore(context)
            val server = api.baseUrl
            val session = account.token(server) ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { device ->
                if (api.baseUrl != server || account.token(server) != session) return@addOnSuccessListener
                api.account(Request.Method.POST, "pembeli/firebase/perangkat", JSONObject().put("token", device).put("server", server),
                    "firebase-device", session,
                    { if (api.baseUrl == server && account.token(server) == session) completed(true) },
                    { if (api.baseUrl == server && account.token(server) == session) completed(false) })
            }.addOnFailureListener { if (api.baseUrl == server && account.token(server) == session) completed(false) }
        }
    }

    private fun registerDevice() {
        lastDeviceAttempt = android.os.SystemClock.elapsedRealtime()
        val currentAttempt = attempt
        registerDevice(host) { success ->
            if (currentAttempt != attempt || !host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@registerDevice
            if (success) {
                retryDelay = 15000L
                updateStatus("Perangkat notifikasi sudah terdaftar.")
            } else {
                updateStatus("Notifikasi belum terhubung. Periksa internet; akan dicoba kembali.")
                retry()
            }
        }
    }
}
