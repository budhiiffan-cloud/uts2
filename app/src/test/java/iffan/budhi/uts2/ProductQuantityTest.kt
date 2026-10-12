package iffan.budhi.uts2

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ProductQuantityTest {
    private val product = Product(1, "Uji", "Lobster", "", 101, BigDecimal("25.50"), "kg", "", null, BigDecimal("2"), BigDecimal("20"))
    @Test fun kilogramsAcceptTwoDecimalPlacesAndRespectStock() {
        assertTrue(product.validQuantity(BigDecimal("1.25")))
        assertTrue(product.validQuantity(BigDecimal("25.50")))
        assertFalse(product.validQuantity(BigDecimal("25.51")))
        assertFalse(product.validQuantity(BigDecimal("1.001")))
        assertFalse(product.validQuantity(BigDecimal("0.75")))
    }
    @Test fun packagesOnlyAcceptWholeQuantities() {
        val pack = product.copy(unit = "paket")
        assertTrue(pack.validQuantity(BigDecimal("2.00")))
        assertFalse(pack.validQuantity(BigDecimal("1.5")))
    }
    @Test fun subtotalUsesServerCompatibleRounding() {
        assertEquals(152L, product.subtotal(BigDecimal("1.5")))
        assertEquals(150000L, product.copy(price = 120000).subtotal(BigDecimal("1.25")))
    }
    @Test fun localizedInputIsParsedWithoutLosingPrecision() {
        assertEquals(BigDecimal("1.25"), parseQuantity("1,25"))
        assertEquals(BigDecimal("1.25"), parseQuantity(" 1.25 "))
        assertNull(parseQuantity("abc"))
        assertEquals("25,5", quantityText(BigDecimal("25.50")))
    }
}
