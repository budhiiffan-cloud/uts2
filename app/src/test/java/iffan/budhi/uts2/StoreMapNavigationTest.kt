package iffan.budhi.uts2

import org.junit.Assert.*
import org.junit.Test

class StoreMapNavigationTest {
    @Test fun googleEmbedRedirectIsAllowed() {
        assertTrue(StoreMapNavigation.allowed("https://www.google.com/maps/embed?origin=mfe&pb=test"))
        assertTrue(StoreMapNavigation.allowed("https://maps.google.co.id/maps?query=toko"))
    }
    @Test fun unrelatedOrUnsafeNavigationIsBlocked() {
        listOf("http://www.google.com/maps/embed", "https://www.google.com.evil.example/maps/embed", "https://www.google.com/accounts", "intent://maps", "not a url").forEach {
            assertFalse(StoreMapNavigation.allowed(it))
        }
    }
}
