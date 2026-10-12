package iffan.budhi.uts2

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test

class OrderQrTest {
    @Test fun qrCanBeReadBackAsTheSameOrderReference() {
        val matrix = OrderQr.matrix("TF-ABC123DEF456")
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        assertEquals("TAMAFARM:ORDER:TF-ABC123DEF456", QRCodeReader().decode(image).text)
    }
    @Test fun malformedOrderReferencesCannotProduceQrCodes() {
        listOf("", "TF-123", "https://example.com", "TF-ABC123DEF456/../admin").forEach {
            assertTrue(runCatching { OrderQr.payload(it) }.isFailure)
        }
    }
}
