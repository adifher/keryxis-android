package id.ziawork.keryxis

import org.json.JSONArray
import org.json.JSONObject

/** Mutable editor state; payload copies all server fields to preserve unedited data. */
class TemplateDraft(source: JSONObject = JSONObject()) {
    private val original = JSONObject(source.toString())
    val id: Long = original.optLong("id", 0)
    val buttonsCount: Int = original.optJSONArray("buttons")?.length() ?: runCatching { JSONArray(original.optString("buttons")).length() }.getOrDefault(0)
    var title: String = original.optString("title")
    var body: String = original.optString("body")
    var headerType: String = original.optString("header_type", "none").takeIf { it in TYPES } ?: "none"
    var headerText: String = original.optString("header_text").takeUnless { it == "null" } ?: ""
    var headerMedia: String? = original.optString("header_media").takeUnless { it.isBlank() || it == "null" }
        private set
    var footer: String = original.optString("footer").takeUnless { it == "null" } ?: ""
    val attachments: MutableList<JSONObject> = (original.optJSONArray("attachments")
        ?: runCatching { JSONArray(original.optString("attachments")) }.getOrDefault(JSONArray())).let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item -> JSONObject(item.toString()) } }.toMutableList()
    }
    fun previewBody() = body.replace("{{nama}}", "Budi")
    fun setHeaderMedia(filename: String) { require(filename.isNotBlank()); headerMedia = filename }
    fun removeHeaderMedia() { headerMedia = null }
    fun addAttachment(filename: String, originalName: String, url: String) {
        attachments.add(JSONObject().put("filename", filename).put("originalName", originalName).put("url", url).put("caption", ""))
    }
    fun removeAttachment(index: Int) { attachments.removeAt(index) }
    fun payload(): JSONObject {
        require(title.isNotBlank() && body.isNotBlank()) { "Judul dan isi pesan wajib diisi" }
        require(headerType in TYPES) { "Tipe header tidak valid" }
        if (headerType in listOf("image", "video", "document") && headerMedia != null) {
            val extension = headerMedia!!.substringAfterLast('.', "").lowercase()
            val valid = when (headerType) {
                "image" -> extension in setOf("jpg", "jpeg", "png", "webp")
                "video" -> extension == "mp4"
                else -> extension == "pdf"
            }
            require(valid) { "File header tidak cocok; ganti atau hapus file" }
        }
        return JSONObject(original.toString()).put("title", title.trim()).put("body", body.trim())
            .put("header_type", headerType).put("header_text", headerText.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("header_media", headerMedia ?: JSONObject.NULL).put("footer", footer.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("buttons", original.opt("buttons")?.let { if (it is String) runCatching { JSONArray(it) }.getOrNull() else it } ?: JSONObject.NULL)
            .put("attachments", JSONArray().apply { attachments.forEach { put(it) } })
    }
    companion object { val TYPES = listOf("none", "text", "image", "video", "document") }
}

object UploadRules {
    const val MAX_BYTES = 10L * 1024 * 1024
    val ALLOWED = setOf("image/jpeg", "image/png", "image/webp", "video/mp4", "application/pdf")
    fun check(mime: String, size: Long) {
        require(mime in ALLOWED && size in 1..MAX_BYTES) { "File harus JPEG, PNG, WebP, MP4, atau PDF; maksimal 10MB" }
    }
    fun matchesHeader(type: String, mime: String): Boolean = when (type) {
        "image" -> mime in setOf("image/jpeg", "image/png", "image/webp")
        "video" -> mime == "video/mp4"
        "document" -> mime == "application/pdf"
        else -> false
    }
}
