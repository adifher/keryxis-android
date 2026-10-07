package id.ziawork.keryxis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BroadcastDraftTest {
    @Test fun validatesAndKeepsExactTarget() {
        val draft = BroadcastDraft.create("  Promo  ", 4, 7, "phone-1", "30", listOf("phone-1"), 12)
        assertEquals("Promo", draft.name)
        assertEquals(4L, draft.templateId)
        assertEquals(7L, draft.labelId)
        assertEquals(30, draft.delaySeconds)
        assertEquals(12, draft.recipientCount)
        assertEquals("phone-1", draft.session)
    }
    @Test fun rejectsUnsafeDrafts() {
        val connected = listOf("phone-1")
        val valid = { name: String, template: Long, label: Long?, session: String, delay: String, count: Int ->
            BroadcastDraft.create(name, template, label, session, delay, connected, count)
        }
        assertThrows(IllegalArgumentException::class.java) { valid(" ", 4, null, "phone-1", "30", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 0, null, "phone-1", "30", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, 0, "phone-1", "30", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, null, "other", "30", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, null, "phone-1", "4", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, null, "phone-1", "3601", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, null, "phone-1", "abc", 1) }
        assertThrows(IllegalArgumentException::class.java) { valid("Promo", 4, null, "phone-1", "30", 0) }
    }
}
