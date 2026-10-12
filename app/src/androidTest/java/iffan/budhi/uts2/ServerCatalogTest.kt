package iffan.budhi.uts2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Integration check: run with the Tamafarm backend active at the configured API URL. */
@RunWith(AndroidJUnit4::class)
class ServerCatalogTest {
    @Test fun catalogAndDetailAreLoadedFromLaravel() {
        val api = ApiClient.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val tag = Any()
        val latch = CountDownLatch(1)
        var result: List<Product>? = null
        var error: String? = null
        api.products(tag, { result = it; latch.countDown() }, { error = it; latch.countDown() })
        try {
            assertTrue("API katalog tidak merespons", latch.await(25, TimeUnit.SECONDS))
            assertNull(error, error)
            val products = requireNotNull(result)
            assertTrue("Katalog web belum memiliki produk", products.isNotEmpty())
            val selected = products.first()
            val detailLatch = CountDownLatch(1)
            var detail: Product? = null
            api.product(selected.id, tag, { detail = it; detailLatch.countDown() }, { error = it; detailLatch.countDown() })
            assertTrue("API detail tidak merespons", detailLatch.await(25, TimeUnit.SECONDS))
            assertNull(error, error)
            assertEquals(selected.id, detail?.id)
            assertEquals(selected.name, detail?.name)
            assertEquals(selected.unit, detail?.unit)
        } finally { api.queue.cancelAll(tag) }
    }
    @Test fun localImageAddressUsesConfiguredApiHost() {
        val api = ApiClient.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val rewritten = android.net.Uri.parse(api.imageUrl("http://127.0.0.1:8000/storage/produk/test.jpg"))
        assertEquals(android.net.Uri.parse(api.baseUrl).host, rewritten.host)
        assertEquals("/storage/produk/test.jpg", rewritten.path)
        assertEquals("https://images.example.com/photo.jpg", api.imageUrl("https://images.example.com/photo.jpg"))
    }
}
