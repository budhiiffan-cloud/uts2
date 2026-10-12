package iffan.budhi.uts2

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.android.volley.Request
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

private fun LinearLayout.field(title: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
    label(title, 14f, true)
    return EditText(context).also {
        it.hint = title; it.setText(value); it.inputType = type
        it.setSingleLine(type != (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        it.id = (title.hashCode() and 0x00FFFFFF).coerceAtLeast(1)
        it.isSaveEnabled = type and InputType.TYPE_TEXT_VARIATION_PASSWORD == 0
        addView(it, LinearLayout.LayoutParams(-1, -2))
    }
}

class AuthFragment : RemoteFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val register = arguments?.getBoolean("register") == true
        val root = requireContext().column()
        root.label(if (register) "Daftar sebagai pembeli" else "Masuk ke Tamafarm", 25f, true)
        val name = if (register) root.field("Nama lengkap") else null
        val username = root.field("Username")
        val phone = if (register) root.field("Nomor HP", type = InputType.TYPE_CLASS_PHONE) else null
        val password = root.field("Password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmation = if (register) root.field("Ulangi password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null
        val message = root.label("", color = muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val submit = root.action(if (register) "Daftar" else "Masuk") {}
        val switch = root.action(if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") { host.openAuth(!register) }
        submit.setOnClickListener {
            val fields = listOfNotNull(name, username, phone, password, confirmation)
            fields.forEach { it.error = null }
            fun invalid(field: EditText, text: String) { field.error = text; field.requestFocus() }
            when {
                name != null && (name.text.toString().trim().isEmpty() || name.text.length > 100) -> invalid(name, "Isi nama, maksimal 100 karakter.")
                username.text.toString().trim().isEmpty() || username.text.length > 50 -> invalid(username, "Isi username, maksimal 50 karakter.")
                phone != null && !AccountValidation.phone(phone.text.toString().trim()) -> invalid(phone, "Isi nomor HP yang valid (6–20 karakter).")
                password.text.isEmpty() || (register && !AccountValidation.password(password.text.toString())) -> invalid(password, "Password minimal 6 karakter untuk mendaftar.")
                confirmation != null && confirmation.text.toString() != password.text.toString() -> invalid(confirmation, "Password belum sama.")
                else -> {
                    submit.isEnabled = false; switch.isEnabled = false
                    fields.forEach { it.isEnabled = false }; message.text = "Menghubungkan akun…"
                    val server = api.baseUrl
                    val data = JSONObject().put("username", username.text.toString().trim()).put("password", password.text.toString())
                    if (register) data.put("nama", name!!.text.toString().trim()).put("no_hp", phone!!.text.toString().trim())
                    fun failed(text: String) {
                        submit.isEnabled = true; switch.isEnabled = true; fields.forEach { it.isEnabled = true }; message.text = text
                    }
                    api.account(Request.Method.POST, if (register) "register" else "login", data, requestTag, null, { response ->
                        val result = response.getJSONObject("data")
                        val user = result.getJSONObject("user")
                        val token = result.getString("token")
                        if (user.optString("role") != "pelanggan") {
                            api.account(Request.Method.POST, "logout", JSONObject(), requestTag, token, {}, {})
                            failed("Aplikasi ini khusus akun pembeli. Gunakan akun dengan peran pelanggan.")
                        } else if (server == api.baseUrl) {
                            AccountStore(requireContext()).save(server, token, user)
                            password.text.clear(); confirmation?.text?.clear()
                            host.openProfile()
                        } else failed("Alamat server berubah. Silakan masuk kembali.")
                    }, { failed(it.message) })
                }
            }
        }
        return ScrollView(requireContext()).apply { addView(root) }
    }
    companion object {
        fun create(register: Boolean) = AuthFragment().apply { arguments = Bundle().apply { putBoolean("register", register) } }
    }
}

class ProfileFragment : RemoteFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val status = view.findViewWithTag<TextView>("notification_status") ?: return
        status.text = host.notificationStatus()
        parentFragmentManager.setFragmentResultListener("firebase_status", viewLifecycleOwner) { _, result ->
            status.text = result.getString("status")
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = requireContext().column()
        root.label("Profil", 25f, true)
        val account = AccountStore(requireContext())
        val token = account.token(api.baseUrl)
        val content = root.card()
        if (token == null) {
            content.label("Selamat datang di Tamafarm", 20f, true)
            content.label("Masuk untuk melengkapi profil pembeli dan menyiapkan pemesanan.")
            content.action("Masuk") { host.openAuth(false) }
            content.action("Daftar akun pembeli") { host.openAuth(true) }
        } else {
            fun load() {
                loading(content)
                api.account(Request.Method.GET, "pembeli/profil", null, requestTag, token, { response ->
                    content.removeAllViews()
                    val profile = response.getJSONObject("data")
                    content.label(profile.optString("nama"), 22f, true)
                    content.badge(when (profile.optString("tipe_pembeli")) { "restoran" -> "Restoran"; "luar_negeri" -> "Luar negeri"; else -> "Perorangan" })
                    content.detailRow("Username", account.user()?.optString("username").orEmpty())
                    content.detailRow("No. HP", profile.optString("no_hp"))
                    content.detailRow("Alamat", profile.optString("alamat").takeUnless { it == "-" || it.isBlank() } ?: "Belum diisi")
                    content.secondaryAction("Edit profil") { host.openEditProfile(profile.toString()) }
                }, { failure ->
                    if (failure.status == 401) {
                        if (account.token(api.baseUrl) == token) account.clear()
                        content.removeAllViews(); content.label("Sesi berakhir. Silakan masuk kembali.")
                        content.action("Masuk kembali") { host.openAuth(false) }
                    } else if (failure.status == 404) {
                        content.removeAllViews(); content.label("Profil belum tersedia. Lengkapi data pembeli Anda.")
                        content.action("Lengkapi profil") {
                            host.openEditProfile(JSONObject().put("nama", account.user()?.optString("nama").orEmpty()).put("negara", "Indonesia").toString())
                        }
                    } else error(content, failure.message) { load() }
                })
            }
            load()
            root.secondaryAction("Keluar dari akun") {
                MaterialAlertDialogBuilder(requireContext()).setTitle("Keluar dari Tamafarm?").setMessage("Anda bisa masuk kembali menggunakan akun yang sama.")
                    .setNegativeButton("Batal", null).setPositiveButton("Keluar") { _, _ ->
                        val application = requireContext().applicationContext
                        // Keep revocation independent of the profile view, which is replaced immediately.
                        api.account(Request.Method.POST, "logout", JSONObject(), Any(), token, {}, {
                            Toast.makeText(application, "Akun sudah keluar di perangkat ini. Server belum dapat dikonfirmasi.", Toast.LENGTH_LONG).show()
                        })
                        account.clear(); host.cart.clear(); host.updateCartBadge(); host.openProfile()
                    }.show()
            }
        }
        val settingsPanel = requireContext().column(0).apply { visibility = View.GONE }
        root.secondaryAction("Pengaturan aplikasi") { settingsPanel.visibility = if (settingsPanel.visibility == View.GONE) View.VISIBLE else View.GONE }
        root.addView(settingsPanel)
        if (token != null) {
            val notifications = settingsPanel.card()
            notifications.label("Notifikasi pesanan", 18f, true)
            notifications.label(host.notificationStatus(), 14f).apply {
                tag = "notification_status"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            notifications.action("Aktifkan izin notifikasi") { host.askNotificationPermission(manual = true) }
            notifications.action("Coba hubungkan kembali") { host.reconnectNotifications() }
        }
        val connection = settingsPanel.card()
        connection.label("Koneksi Tamafarm", 18f, true)
        connection.label(api.baseUrl, 14f, color = muted)
        connection.action("Atur alamat server") {
            val input = EditText(requireContext()).apply { setText(api.baseUrl); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
            val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Alamat server").setMessage("Mengganti server akan mengeluarkan akun dan mengosongkan keranjang.")
                .setView(input).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create()
            dialog.setOnShowListener {
                dialog.getButton(-1).setOnClickListener {
                    try {
                        val previous = api.baseUrl; api.setServer(input.text.toString())
                        if (previous != api.baseUrl) { account.clear(); host.cart.clear(); host.updateCartBadge() }
                        dialog.dismiss(); host.openProfile()
                    } catch (e: Exception) { input.error = e.message ?: "Alamat tidak valid." }
                }
            }
            dialog.show()
        }
        return ScrollView(requireContext()).apply { addView(root) }
    }
}

class EditProfileFragment : RemoteFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val profile = JSONObject(requireArguments().getString("profile")!!)
        val root = requireContext().column()
        root.label("Edit profil pembeli", 25f, true)
        val name = root.field("Nama lengkap", profile.optString("nama"))
        val phone = root.field("Nomor HP", profile.optString("no_hp"), InputType.TYPE_CLASS_PHONE)
        val address = root.field("Alamat lengkap", profile.optString("alamat").takeUnless { it == "-" }.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val country = root.field("Negara", profile.optString("negara", "Indonesia"))
        root.label("Jenis pembeli", 14f, true)
        val types = listOf("perorangan", "restoran", "luar_negeri")
        val spinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Perorangan", "Restoran", "Luar negeri"))
            setSelection(types.indexOf(profile.optString("tipe_pembeli")).coerceAtLeast(0))
        }
        root.addView(spinner)
        val message = root.label("", color = muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val button = root.action("Simpan profil") {}
        button.setOnClickListener {
            val fields = mapOf("nama" to name, "no_hp" to phone, "alamat" to address, "negara" to country)
            fields.values.forEach { it.error = null }
            var valid = true
            fields.forEach { (key, field) ->
                val value = field.text.toString().trim()
                val limit = when (key) { "nama" -> 100; "no_hp" -> 20; "alamat" -> 2000; else -> 50 }
                if (value.isEmpty() || value.length > limit || (key == "no_hp" && !AccountValidation.phone(value))) {
                    field.error = "Isi ${field.hint} yang valid, maksimal $limit karakter."; valid = false
                }
            }
            if (valid) {
                val account = AccountStore(requireContext()); val token = account.token(api.baseUrl)
                if (token == null) { host.openAuth(false); return@setOnClickListener }
                val payload = JSONObject(); fields.forEach { (key, field) -> payload.put(key, field.text.toString().trim()) }
                payload.put("tipe_pembeli", types[spinner.selectedItemPosition])
                button.isEnabled = false; message.text = "Menyimpan profil…"
                api.account(Request.Method.PUT, "pembeli/profil", payload, requestTag, token, {
                    host.openProfile()
                }, { failure ->
                    button.isEnabled = true; message.text = failure.message
                    failure.fields.forEach { (key, text) -> fields[key]?.error = text }
                    if (failure.status == 401) { account.clear(); host.openProfile() }
                })
            }
        }
        return ScrollView(requireContext()).apply { addView(root) }
    }
    companion object {
        fun create(profile: String) = EditProfileFragment().apply { arguments = Bundle().apply { putString("profile", profile) } }
    }
}
