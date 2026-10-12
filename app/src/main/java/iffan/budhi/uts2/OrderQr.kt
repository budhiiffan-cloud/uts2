package iffan.budhi.uts2

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object OrderQr {
    fun payload(code: String): String {
        require(Regex("^TF-[A-Z0-9]{12}$").matches(code)) { "Kode pesanan tidak valid." }
        return "TAMAFARM:ORDER:$code"
    }
    fun matrix(code: String) = QRCodeWriter().encode(payload(code), BarcodeFormat.QR_CODE, 512, 512,
        mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
}
