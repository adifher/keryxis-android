package id.ziawork.keryxis

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TemplateDraftTest {
    @Test fun editingPreservesButtonsAndAttachments() {
        val original = JSONObject("""{"id":9,"title":"Old","body":"Halo {{nama}}","header_type":"image","header_text":"Top","header_media":"photo.png","footer":"Bye","buttons":[{"text":"Open","url":"https://example.org"}],"attachments":[{"filename":"extra.pdf","originalName":"Extra","url":"/api/uploads/extra.pdf","caption":"Keep"}]}""")
        val draft = TemplateDraft(original)
        draft.title = "New"
        val saved = draft.payload()
        assertEquals("New", saved.getString("title"))
        assertEquals("Halo Budi", draft.previewBody())
        assertEquals("photo.png", saved.getString("header_media"))
        assertEquals("Top", saved.getString("header_text"))
        assertEquals("Bye", saved.getString("footer"))
        assertEquals("Open", saved.getJSONArray("buttons").getJSONObject(0).getString("text"))
        assertEquals("Keep", saved.getJSONArray("attachments").getJSONObject(0).getString("caption"))
        assertEquals("Old", original.getString("title"))
    }
    @Test fun switchingHeaderDoesNotWipeStoredMediaUntilExplicitRemoval() {
        val draft = TemplateDraft(JSONObject("""{"title":"T","body":"B","header_type":"image","header_media":"old.jpg"}"""))
        draft.headerType = "text"
        assertEquals("old.jpg", draft.payload().getString("header_media"))
        draft.removeHeaderMedia()
        assertTrue(draft.payload().isNull("header_media"))
    }
    @Test fun parsesStringifiedJsonFieldsWithoutWiping() {
        val draft = TemplateDraft(JSONObject("""{"title":"T","body":"B","buttons":"[{\"text\":\"Keep\"}]","attachments":"[{\"filename\":\"x.pdf\"}]"}"""))
        assertEquals("Keep", draft.payload().getJSONArray("buttons").getJSONObject(0).getString("text"))
        assertEquals("x.pdf", draft.payload().getJSONArray("attachments").getJSONObject(0).getString("filename"))
    }
    @Test fun rejectsMismatchedExistingMediaOnHeaderChange() {
        val draft = TemplateDraft(JSONObject("""{"title":"T","body":"B","header_type":"image","header_media":"old.jpg"}"""))
        draft.headerType = "video"
        assertThrows(IllegalArgumentException::class.java) { draft.payload() }
        draft.setHeaderMedia("new.mp4")
        assertEquals("new.mp4", draft.payload().getString("header_media"))
    }
    @Test fun attachmentsRemoveOnlySelectedIndex() {
        val draft = TemplateDraft(JSONObject("""{"title":"T","body":"B","attachments":[{"filename":"a.pdf"},{"filename":"b.pdf"}]}"""))
        draft.removeAttachment(0)
        assertEquals("b.pdf", draft.payload().getJSONArray("attachments").getJSONObject(0).getString("filename"))
        assertEquals(1, draft.payload().getJSONArray("attachments").length())
    }
    @Test fun rejectsBadFilesAndEmptyForm() {
        assertThrows(IllegalArgumentException::class.java) { UploadRules.check("image/gif", 10) }
        assertThrows(IllegalArgumentException::class.java) { UploadRules.check("image/png", 0) }
        assertThrows(IllegalArgumentException::class.java) { UploadRules.check("application/pdf", 10L * 1024 * 1024 + 1) }
        UploadRules.check("video/mp4", 10L * 1024 * 1024)
        assertTrue(UploadRules.matchesHeader("document", "application/pdf"))
        assertFalse(UploadRules.matchesHeader("image", "video/mp4"))
        val draft = TemplateDraft()
        assertThrows(IllegalArgumentException::class.java) { draft.payload() }
    }
}
