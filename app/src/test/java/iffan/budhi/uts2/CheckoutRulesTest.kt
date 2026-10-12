package iffan.budhi.uts2

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CheckoutRulesTest {
    private fun product(unit: String = "kg", stock: String = "100", price: Long = 120000) = Product(1, "Lobster", "Konsumsi", "Small", price, stock.toBigDecimal(), unit, "", null, "5".toBigDecimal(), "20".toBigDecimal())
    private fun items(qty: String, product: Product = product()) = listOf(CartItem(product, qty.toBigDecimal(), true))
    @Test fun personalBuyerCanOrderFractionalKilograms() { assertNull(checkoutProblem(items("1.25"), "perorangan")) }
    @Test fun restaurantAndExportMinimumsFollowCatalog() {
        assertNotNull(checkoutProblem(items("4.75"), "restoran"))
        assertNull(checkoutProblem(items("5"), "restoran"))
        assertNotNull(checkoutProblem(items("19.75"), "luar_negeri"))
        assertNull(checkoutProblem(items("20"), "luar_negeri"))
    }
    @Test fun packagesRequireWholeUnitsForAllBuyerTypes() {
        assertNull(checkoutProblem(items("1", product("paket")), "luar_negeri"))
        assertNotNull(checkoutProblem(items("1.5", product("paket")), "restoran"))
    }
    @Test fun unavailableStockAndEmptyCartPreventSubmission() {
        assertNotNull(checkoutProblem(items("2", product(stock = "1")), "perorangan"))
        assertNotNull(checkoutProblem(emptyList(), "perorangan"))
    }
    @Test fun totalCannotExceedBackendLimit() {
        assertNotNull(checkoutProblem(items("100", product(price = 100000000)), "perorangan"))
    }
    @Test fun cartKeepsAdditionalQuantityAndPreservesLaterEdits() {
        assertEquals(BigDecimal("0.75"), remainingCartQuantity(BigDecimal("2.00"), BigDecimal("1.25")))
        assertEquals(BigDecimal("0.00"), remainingCartQuantity(BigDecimal("1.25"), BigDecimal("1.25")))
        assertEquals(BigDecimal("1.00"), remainingCartQuantity(BigDecimal("1.00"), BigDecimal("1.25")))
    }
}
