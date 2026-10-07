package id.ziawork.keryxis

data class BroadcastDraft(
    val name: String,
    val templateId: Long,
    val labelId: Long?,
    val session: String,
    val delaySeconds: Int,
    val recipientCount: Int
) {
    companion object {
        fun create(name: String, templateId: Long, labelId: Long?, session: String, delay: String, connected: List<String>, recipients: Int): BroadcastDraft {
            val seconds = delay.trim().toIntOrNull()
            require(name.trim().isNotEmpty()) { "Nama kampanye wajib diisi" }
            require(templateId > 0) { "Pilih template" }
            require(labelId == null || labelId > 0) { "Label tidak valid" }
            require(session.isNotBlank() && session in connected) { "Pilih sesi WhatsApp terhubung" }
            require(seconds != null && seconds in 5..3600) { "Jeda harus 5–3600 detik" }
            require(recipients > 0) { "Target tidak memiliki kontak" }
            return BroadcastDraft(name.trim(), templateId, labelId, session, seconds, recipients)
        }
    }
}
