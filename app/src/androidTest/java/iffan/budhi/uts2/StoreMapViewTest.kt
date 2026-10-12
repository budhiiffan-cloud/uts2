package iffan.budhi.uts2

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StoreMapViewTest {
    @Test fun nativeMapLoadsTilesAndRendersSellerOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val finished = CountDownLatch(1)
        lateinit var map: StoreMapView
        instrumentation.runOnMainSync {
            map = StoreMapView(context)
            map.onStatus = { _, loading -> if (!loading) finished.countDown() }
            map.layout(0, 0, context.dp(360), context.dp(320))
        }
        try {
            assertTrue("Peta melewati batas waktu", finished.await(45, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertTrue("Tidak ada tile yang dimuat", map.loadedTileCount > 0)
                assertTrue("Tile titik toko belum dimuat", map.centerTileLoaded)
                val image = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
                map.draw(Canvas(image))
                File(context.cacheDir, "store-map-preview-test.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        } finally { instrumentation.runOnMainSync { map.close() } }
    }
}
