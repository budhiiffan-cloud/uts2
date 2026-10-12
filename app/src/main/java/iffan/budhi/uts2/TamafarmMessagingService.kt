package iffan.budhi.uts2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TamafarmMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { FirebaseSession.registerDevice(this) }

    override fun onMessageReceived(message: RemoteMessage) {
        val api = ApiClient.get(this)
        val account = AccountStore(this)
        val userId = account.user()?.optLong("id_user", -1) ?: -1
        if (account.token(api.baseUrl) == null) return
        val code = FirebaseMessageGuard.orderCode(message.data, "buyer_$userId", api.baseUrl) ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val events = getSharedPreferences("firebase_notifications", MODE_PRIVATE)
        val eventId = message.data.getValue("event_id")
        val seen = events.getStringSet("seen", emptySet()).orEmpty()
        if (eventId in seen) return
        events.edit().putStringSet("seen", (seen.toList().takeLast(99) + eventId).toSet()).apply()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("orders", "Pembaruan pesanan", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this, MainActivity::class.java).putExtra("order_code", code)
            .putExtra("order_uid", message.data["uid"]).putExtra("order_server", api.baseUrl)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, code.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "orders").setSmallIcon(R.drawable.ic_orders)
            .setContentTitle("Pesanan Tamafarm diperbarui").setContentText("Buka aplikasi untuk melihat status pesanan terbaru.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        manager.notify(eventId.hashCode(), notification)
    }
}
