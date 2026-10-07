package id.ziawork.keryxis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhoneTest {
    @Test fun normalizesIndonesianNumbers() {
        assertEquals("6281234567890", Phone.normalize("0812-3456-7890"))
        assertEquals("6281234567890", Phone.normalize("+62 812 3456 7890"))
        assertEquals("6281234567890", Phone.normalize("81234567890"))
    }
    @Test fun rejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException::class.java) { Phone.normalize("123") }
        assertThrows(IllegalArgumentException::class.java) { Phone.normalize("080") }
    }
}
