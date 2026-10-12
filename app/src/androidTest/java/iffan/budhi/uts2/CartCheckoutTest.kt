package iffan.budhi.uts2

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CartCheckoutTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun product(id: Int = 1) = Product(id, "Lobster", "Konsumsi", "Small", 120000, "100".toBigDecimal(), "kg", "", null, "5".toBigDecimal(), "20".toBigDecimal())
    @Test fun successfulCheckoutConsumesOnlySubmittedItemsAndOnlyOnce() {
        val database = "checkout_test_${System.nanoTime()}.db"
        try {
            CartStore(context, database).use { cart ->
                cart.add(product(), "2".toBigDecimal()); cart.add(product(2), "1".toBigDecimal())
                val epoch = cart.epoch()
                cart.consume("TF-CHECK-1", epoch, mapOf(1 to "1.25".toBigDecimal()))
                assertEquals("0.75".toBigDecimal(), cart.items().first().quantity)
                assertFalse(cart.items().first().selected)
                assertEquals(2, cart.items().size)
                cart.consume("TF-CHECK-1", epoch, mapOf(1 to "1.25".toBigDecimal()))
                assertEquals("0.75".toBigDecimal(), cart.items().first().quantity)
            }
        } finally { context.deleteDatabase(database) }
    }
    @Test fun oldReceiptCannotConsumeCartAfterLogoutOrServerChange() {
        val database = "checkout_epoch_${System.nanoTime()}.db"
        try {
            CartStore(context, database).use { cart ->
                cart.add(product(), "1".toBigDecimal()); val epoch = cart.epoch()
                cart.clear(); cart.add(product(), "2".toBigDecimal())
                cart.consume("TF-CHECK-2", epoch, mapOf(1 to "1".toBigDecimal()))
                assertEquals("2".toBigDecimal(), cart.items().first().quantity)
            }
        } finally { context.deleteDatabase(database) }
    }
}
