package iffan.budhi.uts2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseMessageGuardTest {
    private val data = mapOf("uid" to "buyer_1", "server" to "http://192.168.1.105:8000/api", "code" to "TF-ABCDEFGHIJKL", "event_id" to "12345678-1234-1234-1234-123456789012")
    @Test fun acceptsOnlyCurrentAccountAndServer() {
        assertEquals("TF-ABCDEFGHIJKL", FirebaseMessageGuard.orderCode(data, "buyer_1", data.getValue("server")))
        assertNull(FirebaseMessageGuard.orderCode(data, "buyer_2", data.getValue("server")))
        assertNull(FirebaseMessageGuard.orderCode(data, "buyer_1", "https://other.example/api"))
    }
    @Test fun rejectsInvalidOrderAndMissingEvent() {
        assertNull(FirebaseMessageGuard.orderCode(data + ("code" to "../admin"), "buyer_1", data.getValue("server")))
        assertNull(FirebaseMessageGuard.orderCode(data - "event_id", "buyer_1", data.getValue("server")))
    }
}
