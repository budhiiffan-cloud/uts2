package iffan.budhi.uts2

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment

class OrderQrFragment : Fragment() {
    private var bitmap: Bitmap? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = requireContext().column()
        root.label("QR Code pesanan", 26f, true)
        val code = arguments?.getString("code").orEmpty()
        if (!Regex("^TF-[A-Z0-9]{12}$").matches(code)) { root.label("Kode pesanan tidak valid. Buka kembali pesanan dari riwayat."); return root }
        root.label(code, 22f, true, green)
        root.label("Tunjukkan QR ini kepada admin.", 14f, color = muted)
        val matrix = OrderQr.matrix(code)
        val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
        bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        root.addView(ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "QR pesanan $code"
            minimumHeight = requireContext().dp(280)
        }, LinearLayout.LayoutParams(-1, requireContext().dp(320)))
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onDestroyView() { bitmap = null; super.onDestroyView() }
    companion object { fun create(code: String) = OrderQrFragment().apply { arguments = Bundle().apply { putString("code", code) } } }
}
