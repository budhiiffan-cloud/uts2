package iffan.budhi.uts2

import org.junit.Assert.*
import org.junit.Test

class AccountValidationTest {
    @Test fun phoneAcceptsLocalAndInternationalFormats() {
        assertTrue(AccountValidation.phone("081234567890"))
        assertTrue(AccountValidation.phone("+62 812-345-678"))
    }
    @Test fun phoneRejectsLettersAndMissingDigits() {
        assertFalse(AccountValidation.phone("08123abc"))
        assertFalse(AccountValidation.phone("------"))
        assertFalse(AccountValidation.phone("12345"))
        assertFalse(AccountValidation.phone("1".repeat(21)))
    }
    @Test fun registrationPasswordMatchesServerMinimum() {
        assertFalse(AccountValidation.password("12345"))
        assertTrue(AccountValidation.password("123456"))
    }
}
