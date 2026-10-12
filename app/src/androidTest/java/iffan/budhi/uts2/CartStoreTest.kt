package iffan.budhi.uts2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CartStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: CartStore
    private val product = Product(9001, "Produk uji", "Lobster", "Small", 120000, BigDecimal("25.50"), "kg", "", null, BigDecimal("2"), BigDecimal("20"))
    @Before fun prepare() { context.deleteDatabase("tamafarm_cart_test.db"); store = CartStore(context, "tamafarm_cart_test.db") }
    @After fun clean() { store.close(); context.deleteDatabase("tamafarm_cart_test.db") }
    @Test fun fractionalQuantityAndSelectionSurviveReopening() {
        assertTrue(store.add(product, BigDecimal("1.25"))); store.select(product.id, false)
        store.close(); store = CartStore(context, "tamafarm_cart_test.db")
        assertEquals(BigDecimal("1.25"), store.items().single().quantity)
        assertEquals(product, store.items().single().product)
        assertFalse(store.items().single().selected)
        assertEquals(150000L, product.subtotal(BigDecimal("1.25")))
    }
    @Test fun quantityCannotExceedStockOrUseTooManyDecimals() {
        assertFalse(store.add(product, BigDecimal("1.001")))
        assertTrue(store.add(product, product.stock)); assertFalse(store.add(product, BigDecimal.ONE))
        assertEquals(product.stock, store.items().single().quantity)
    }
    @Test fun packagesMustBeWholeAndMinimumIsOne() {
        val pack = product.copy(unit = "paket")
        assertFalse(store.add(pack, BigDecimal("1.5")))
        assertFalse(store.add(product, BigDecimal("0.5")))
        assertTrue(store.add(pack, BigDecimal("2")))
    }
    @Test fun serverUpdatesPricesAndDeselectsUnavailableItems() {
        assertTrue(store.add(product, BigDecimal("2")))
        store.sync(listOf(product.copy(price = 130000, stock = BigDecimal.ONE)))
        assertEquals(130000L, store.items().single().product.price)
        assertFalse(store.items().single().selected)
        store.sync(emptyList()); assertEquals(0, store.items().single().product.stock.compareTo(BigDecimal.ZERO))
    }
    @Test fun demoDatabaseIsNotMatchedToRealProducts() {
        store.close(); context.deleteDatabase("tamafarm_cart_test.db")
        context.openOrCreateDatabase("tamafarm_cart_test.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE cart (product_id INTEGER PRIMARY KEY, quantity INTEGER, selected INTEGER)")
            db.execSQL("INSERT INTO cart VALUES (1, 2, 1)"); db.version = 1
        }
        store = CartStore(context, "tamafarm_cart_test.db")
        assertTrue(store.items().isEmpty())
    }
    @Test fun commaInputAndJsonRoundTripPreserveDecimals() {
        assertEquals(BigDecimal("1.25"), parseQuantity("1,25"))
        assertEquals(product, Product.fromJson(org.json.JSONObject(product.json())))
    }
}
