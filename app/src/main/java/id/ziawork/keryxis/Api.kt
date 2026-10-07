package id.ziawork.keryxis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.util.UUID
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Api(private val context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val origin = "https://keryxis.ziawork.id"
    private val alias = "keryxis-session-key"
    private var cookie: String? = null
    private var expiry: Long = 0

    init { restore() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun restore() {
        try {
            val bytes = Base64.decode(prefs.getString("sealed", null) ?: return, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val obj = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8))
            if (obj.getLong("until") > System.currentTimeMillis() && obj.getString("value").isNotBlank()) {
                cookie = obj.getString("value"); expiry = obj.getLong("until")
            } else clear()
        } catch (_: Exception) { clear() } // ponytail: invalidated keystore logs user out; no token recovery.
    }

    private fun save(value: String, until: Long) {
        cookie = value; expiry = until
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(JSONObject().put("value", value).put("until", until).toString().toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("sealed", Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    fun hasSession(): Boolean = cookie != null && expiry > System.currentTimeMillis()
    fun clear() { cookie = null; expiry = 0; prefs.edit().remove("sealed").apply() }

    fun request(path: String, method: String = "GET", body: String? = null, contentType: String = "application/json"): String {
        require(path.startsWith("/api/") && !path.contains("//") && !path.contains(".."))
        val conn = URL(origin + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false // Never forward cookie to another origin.
            conn.connectTimeout = 12000; conn.readTimeout = 20000
            conn.setRequestProperty("Accept", "application/json")
            if (hasSession()) conn.setRequestProperty("Cookie", "keryxis_session=$cookie")
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            if (path == "/api/login" && conn.responseCode in 200..299) {
                val header = conn.headerFields.entries.firstOrNull { it.key?.equals("Set-Cookie", true) == true }?.value
                val parsed = header.orEmpty().flatMap { runCatching { HttpCookie.parse(it) }.getOrDefault(emptyList()) }
                    .firstOrNull { it.name == "keryxis_session" && it.secure && it.isHttpOnly && (it.domain == null || it.domain == "keryxis.ziawork.id") && (it.path == null || it.path == "/") }
                if (parsed == null || parsed.maxAge <= 0) throw IllegalStateException("Sesi login tidak tersedia")
                save(parsed.value, System.currentTimeMillis() + parsed.maxAge.coerceAtMost(43200) * 1000)
            }
            if (conn.responseCode == 401 && path != "/api/login") clear()
            val text = (if (conn.responseCode >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP ${conn.responseCode}" }
                throw ApiException(conn.responseCode, message)
            }
            return text
        } finally { conn.disconnect() }
    }

    private fun connection(path: String): HttpURLConnection {
        require(path.startsWith("/api/") && !path.contains("//") && !path.contains(".."))
        return (URL(origin + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false // Never send session cookie across origins.
            connectTimeout = 12000; readTimeout = 30000
            if (hasSession()) setRequestProperty("Cookie", "keryxis_session=$cookie")
        }
    }
    private fun response(conn: HttpURLConnection): String {
        val status = conn.responseCode
        if (status == 401) clear()
        val body = (if (status >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP $status" }
            throw ApiException(status, message)
        }
        return body
    }
    data class Document(val uri: Uri, val mime: String, val name: String, val size: Long)
    fun document(uri: Uri): Document {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: ""
        var name = "upload"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) ?: "upload" }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
            }
        }
        if (size >= 0) UploadRules.check(mime, size) else require(mime in UploadRules.ALLOWED) { "Tipe file tidak didukung" }
        return Document(uri, mime, name, size)
    }
    fun upload(file: Document, progress: (Long, Long) -> Unit): JSONObject {
        val boundary = "Keryxis${UUID.randomUUID().toString().replace("-", "")}"
        val extension = when (file.mime) {
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"
            "video/mp4" -> "mp4"; else -> "pdf"
        }
        val base = file.name.substringBeforeLast('.', file.name).replace(Regex("[^a-zA-Z0-9_-]"), "_").take(90).ifBlank { "upload" }
        val safeName = "$base.$extension"
        val conn = connection("/api/templates/upload")
        try {
            conn.requestMethod = "POST"; conn.doOutput = true; conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\nContent-Type: ${file.mime}\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                val input = context.contentResolver.openInputStream(file.uri) ?: error("File tidak dapat dibuka")
                input.use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        sent += read
                        require(sent <= UploadRules.MAX_BYTES) { "File melebihi 10MB" }
                        output.write(buffer, 0, read)
                        progress(sent, file.size)
                    }
                    UploadRules.check(file.mime, sent)
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            val result = JSONObject(response(conn))
            require(result.optString("filename").isNotBlank() && result.optString("url").startsWith("/api/uploads/")) { "Respons upload tidak valid" }
            return result
        } finally { conn.disconnect() }
    }
    fun mediaBytes(filename: String): ByteArray {
        require(Regex("[a-zA-Z0-9._-]+\\.(jpg|jpeg|png|webp|mp4|pdf)", RegexOption.IGNORE_CASE).matches(filename))
        val conn = connection("/api/uploads/$filename")
        try {
            conn.requestMethod = "GET"
            val status = conn.responseCode
            if (status == 401) { clear(); throw ApiException(status, "Sesi berakhir") }
            if (status !in 200..299) throw ApiException(status, "File tidak tersedia")
            require(conn.contentLengthLong <= UploadRules.MAX_BYTES) { "File terlalu besar" }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= UploadRules.MAX_BYTES) { "File terlalu besar" }
                    out.write(buffer, 0, count)
                }
            }
            return out.toByteArray()
        } finally { conn.disconnect() }
    }
    fun pdfPreview(filename: String): Bitmap? {
        val bytes = mediaBytes(filename)
        val cache = File.createTempFile("template-preview", ".pdf", context.cacheDir)
        try {
            cache.writeBytes(bytes)
            ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val width = 400; val height = (page.height.toFloat() / page.width * width).toInt().coerceIn(1, 800)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
        } finally { cache.delete() }
    }
    fun thumbnail(filename: String): Bitmap? {
        require(Regex("[a-zA-Z0-9._-]+\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE).matches(filename))
        val conn = connection("/api/uploads/$filename")
        try {
            conn.requestMethod = "GET"
            val status = conn.responseCode
            if (status == 401) { clear(); throw ApiException(status, "Sesi berakhir") }
            if (status !in 200..299) throw ApiException(status, "Gambar tidak tersedia")
            if (conn.contentLengthLong > 4 * 1024 * 1024) return null
            val bytes = conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 400)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally { conn.disconnect() }
    }
}

class ApiException(val status: Int, message: String) : Exception(message)
