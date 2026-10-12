package iffan.budhi.uts2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment

class StoreLocationFragment : Fragment() {
    private var map: StoreMapView? = null
    private val address = "Jl. Deltasari Indah V No.9, Koreksari, Kureksari, Kec. Waru, Kabupaten Sidoarjo, Jawa Timur 61256"
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = requireContext().column()
        root.label("Lokasi toko", 27f, true)
        root.card().apply {
            label("Tama Lobster Air Tawar", 23f, true, green)
            label(address)
            secondaryAction("Buka Google Maps") {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://share.google/7wpfe71dscIQkKbnZ"))) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(requireContext(), "Pasang Google Maps atau browser untuk membuka lokasi toko.", Toast.LENGTH_LONG).show()
                }
            }
            action("Petunjuk arah ke toko") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}")))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(requireContext(), "Pasang Google Maps atau browser untuk membuka rute.", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.label("Peta lokasi penjual", 20f, true)
        val mapMessage = root.label("Memuat peta…", 14f, color = muted)
        val progress = android.widget.ProgressBar(requireContext())
        root.addView(progress)
        val retry = root.action("Coba muat peta lagi") { map?.reload() }.apply { visibility = View.GONE }
        map = StoreMapView(requireContext()).apply {
            onStatus = { message, loading ->
                mapMessage.text = message
                progress.visibility = if (loading) View.VISIBLE else View.GONE
                retry.visibility = if (!loading && message.isNotBlank()) View.VISIBLE else View.GONE
                visibility = if (!loading && loadedTileCount == 0) View.GONE else View.VISIBLE
            }
        }
        root.addView(map, LinearLayout.LayoutParams(-1, requireContext().dp(320)))
        val zoom = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(zoom)
        zoom.action("Perkecil −") { map?.changeZoom(-1) }.layoutParams = LinearLayout.LayoutParams(0, requireContext().dp(52), 1f)
        zoom.action("Perbesar +") { map?.changeZoom(1) }.layoutParams = LinearLayout.LayoutParams(0, requireContext().dp(52), 1f)
        root.label("© OpenStreetMap contributors", 12f, color = muted)
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onDestroyView() { map?.close(); map = null; super.onDestroyView() }
}
