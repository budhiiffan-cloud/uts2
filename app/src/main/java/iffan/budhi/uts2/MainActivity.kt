package iffan.budhi.uts2

import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    lateinit var cart: CartStore
        private set
    private lateinit var firebase: FirebaseSession
    private var permissionPending = false
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        permissionPending = false
        getSharedPreferences("firebase_notifications", MODE_PRIVATE).edit().putBoolean("permission_requested", true).apply()
        refreshNotificationStatus()
    }
    private lateinit var navigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cart = CartStore(this)
        firebase = FirebaseSession(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        navigation = findViewById(R.id.bottom_navigation)
        navigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_catalog -> CatalogFragment()
                R.id.nav_orders -> OrdersFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            show(fragment)
            true
        }
        findViewById<TextView>(R.id.cart_button).setOnClickListener { openCart() }
        if (savedInstanceState == null) show(HomeFragment())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStackImmediate()
                    navigation.selectedItemId != R.id.nav_home -> navigation.selectedItemId = R.id.nav_home
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        updateCartBadge()
        handleNotification(intent)
    }

    private fun show(fragment: Fragment, back: Boolean = false) {
        if (::firebase.isInitialized && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            firebase.start()
            if (AccountStore(this).token(ApiClient.get(this).baseUrl) != null) askNotificationPermission()
        }
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).apply {
            if (back) addToBackStack(null)
        }.commit()
    }
    fun openAuth(register: Boolean) { show(AuthFragment.create(register), true) }
    fun openEditProfile(profile: String) { show(EditProfileFragment.create(profile), true) }
    fun openProfile() {
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        if (navigation.selectedItemId != R.id.nav_profile) navigation.selectedItemId = R.id.nav_profile
        else {
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            show(ProfileFragment())
        }
    }
    fun openCheckout() { show(CheckoutFragment(), true) }
    fun openStoreLocation() { show(StoreLocationFragment(), true) }
    fun openPayment(code: String, kind: String) { show(PaymentFragment.create(code, kind), true) }
    fun openOrderQr(code: String) { show(OrderQrFragment.create(code), true) }
    fun openOrders() {
        if (navigation.selectedItemId != R.id.nav_orders) navigation.selectedItemId = R.id.nav_orders
        else {
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            show(OrdersFragment())
        }
    }
    fun openOrder(code: String, reset: Boolean = false) {
        if (reset) {
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            if (navigation.selectedItemId != R.id.nav_orders) navigation.selectedItemId = R.id.nav_orders
            else show(OrdersFragment())
        }
        show(OrderDetailFragment.create(code), true)
    }
    fun openCatalog() { navigation.selectedItemId = R.id.nav_catalog }
    fun openProduct(id: Int) { show(ProductDetailFragment.newInstance(id), true) }
    fun openCart() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) !is CartFragment) show(CartFragment(), true)
    }
    fun updateCartBadge() {
        findViewById<TextView>(R.id.cart_button).text = buildString { append("Keranjang ("); append(cart.items().size); append(')') }
    }
    fun askNotificationPermission(manual: Boolean = false) {
        if (permissionPending) return
        val enabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (manual && !enabled) openNotificationSettings()
            refreshNotificationStatus()
            return
        }
        val settings = getSharedPreferences("firebase_notifications", MODE_PRIVATE)
        if (settings.getBoolean("permission_requested", false)) {
            if (!manual) return
            if (!shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings()
                return
            }
        }
        permissionPending = true
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun openNotificationSettings() {
        startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
    }
    fun notificationStatus(): String {
        val permission = if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) "Izin notifikasi aktif." else "Izin notifikasi belum aktif."
        return "$permission\n${firebase.status}"
    }
    fun refreshNotificationStatus() {
        if (::firebase.isInitialized && !supportFragmentManager.isDestroyed) {
            supportFragmentManager.setFragmentResult("firebase_status", Bundle().apply { putString("status", notificationStatus()) })
        }
    }
    fun reconnectNotifications() { firebase.reconnect() }
    private fun handleNotification(incoming: android.content.Intent?) {
        val code = incoming?.getStringExtra("order_code") ?: return
        val api = ApiClient.get(this); val account = AccountStore(this)
        val uid = "buyer_${account.user()?.optLong("id_user", -1)}"
        incoming.removeExtra("order_code")
        if (!Regex("^TF-[A-Z0-9]{12}$").matches(code)) return
        if (account.token(api.baseUrl) != null && incoming.getStringExtra("order_uid") == uid && incoming.getStringExtra("order_server") == api.baseUrl) openOrder(code, true)
        else openProfile()
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleNotification(intent) }
    override fun onResume() {
        super.onResume()
        firebase.start()
        if (AccountStore(this).token(ApiClient.get(this).baseUrl) != null) askNotificationPermission()
        refreshNotificationStatus()
    }
    override fun onStop() { firebase.stop(); super.onStop() }
    override fun onDestroy() { firebase.stop(); cart.close(); super.onDestroy() }
}






