package id.ziawork.keryxis

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.text.TextWatcher
import android.text.Editable
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.SurfaceView
import android.widget.ScrollView
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val lime = Color.rgb(200, 245, 96)
    private val pale = Color.rgb(232, 236, 229)
    private val muted = Color.rgb(170, 183, 168)
    private val panelColor = Color.rgb(34, 40, 39)
    private val border = Color.rgb(63, 73, 67)
    private val surface = Color.rgb(43, 51, 47)
    private lateinit var api: Api
    private lateinit var content: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var navigation: View
    private lateinit var pageTitle: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private val campaignSubmitting = java.util.concurrent.atomic.AtomicBoolean(false)
    private var section = "Dashboard"
    private var page = 1
    private var query = ""
    private var draft: TemplateDraft? = null
    private var uploadBusy = false
    private var templateBusy = false
    private var templateGeneration = 0
    private var campaignGeneration = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var campaignPoll: Runnable? = null
    private val pickCode = 704
    private var pickExtra = false
    private var mediaPlayer: MediaPlayer? = null
    private var mediaCache: java.io.File? = null
    private var activeSectionToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
                insets
            }
        }
        api = Api(this)
        content = findViewById(R.id.content)
        tabs = findViewById(R.id.tabs)
        navigation = findViewById(R.id.navigation)
        pageTitle = findViewById(R.id.page_title)
        if (api.hasSession()) open("Dashboard") else login()
    }
    override fun onDestroy() { stopCampaignPoll(); clearMedia(); worker.shutdownNow(); super.onDestroy() }
    override fun onStop() { stopCampaignPoll(); mediaPlayer?.pause(); super.onStop() }
    private fun clearMedia() { mediaPlayer?.release(); mediaPlayer = null; mediaCache?.delete(); mediaCache = null }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int = panelColor, radius: Int = 18, stroke: Int = border) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun text(value: String, size: Float = 16f, color: Int = pale): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = true
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun line(value: String, size: Float = 16f, color: Int = pale) {
        content.addView(text(value, size, color), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun button(label: String, holder: LinearLayout = content, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(52); minimumHeight = dp(52)
        setPadding(dp(16), dp(8), dp(16), dp(8)); setTextColor(Color.rgb(23, 26, 27))
        background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape(lime, 14, lime), null)
        holder.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        setOnClickListener { action() }
    }
    private fun secondary(label: String, holder: LinearLayout = content, action: () -> Unit): Button = button(label, holder, action).apply {
        setTextColor(pale); background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(surface, 14), null)
    }
    private fun input(hint: String, initial: String = "", keyboard: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
        setText(initial); setHint(hint); contentDescription = hint; inputType = keyboard
        setTextColor(pale); setHintTextColor(muted); setSingleLine(hint != "Isi pesan" && hint != "Isi template")
        minHeight = dp(54); setPadding(dp(16), dp(10), dp(16), dp(10)); textSize = 15f
        background = shape(surface, 12)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape()
    }
    private fun card(title: String, detail: String) {
        val box = panel()
        box.addView(text(title, 17f, pale).apply { typeface = Typeface.DEFAULT_BOLD })
        box.addView(text(detail, 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun state(title: String, detail: String) {
        val box = panel().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        box.addView(text(title, 17f, pale).apply { typeface = Typeface.DEFAULT_BOLD })
        box.addView(text(detail, 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }
    private fun divider(holder: LinearLayout) {
        holder.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
    }
    private fun row(title: String, detail: String, eyebrow: String = "", action: (() -> Unit)? = null) {
        val box = panel()
        if (eyebrow.isNotEmpty()) box.addView(text(eyebrow.uppercase(), 11f, lime).apply { letterSpacing = .10f }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(text(title, 17f, pale).apply { typeface = Typeface.DEFAULT_BOLD })
        if (detail.isNotBlank()) box.addView(text(detail, 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        if (action != null) { divider(box); secondary("Lihat detail  ›", box, action) }
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    private fun picker(label: String, names: List<String>): Spinner = Spinner(this).apply {
        contentDescription = label
        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, names) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(pale); textSize = 15f; setPadding(dp(16), dp(12), dp(16), dp(12))
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(pale); setBackgroundColor(panelColor); setPadding(dp(16), dp(14), dp(16), dp(14))
                }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        minimumHeight = dp(54); background = shape(surface, 12)
    }
    private fun task(request: () -> String, success: (String) -> Unit) {
        val target = section
        val token = activeSectionToken
        val loading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64)
            addView(ProgressBar(this@MainActivity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
            addView(text("Memuat data…", 14f, muted))
        }
        content.addView(loading, LinearLayout.LayoutParams(-1, dp(64)))
        worker.execute {
            val response = try { request() } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || section != target || token != activeSectionToken) return@runOnUiThread
                    content.removeView(loading)
                    if (e is ApiException && e.status == 401 && !api.hasSession() && section != "Login") { login(); state("Sesi berakhir", "Masuk kembali untuk melanjutkan.") }
                    else { state("Data belum dapat dimuat", e.message ?: "Periksa koneksi lalu coba lagi."); secondary("Coba lagi") { if (section == "Login") login() else open(section) } }
                }
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || section != target || token != activeSectionToken) return@runOnUiThread
                content.removeView(loading)
                try { success(response) } catch (e: Exception) { state("Respons tidak dapat dibaca", e.message ?: "Format data tidak valid.") }
            }
        }
    }
    private fun menu() {
        tabs.removeAllViews()
        navigation.visibility = View.VISIBLE
        val destinations = listOf("Dashboard", "Kontak", "Broadcast", "Kirim", "Lainnya")
        destinations.forEach { name ->
            val selected = if (name == "Lainnya") section in listOf("Label", "Template", "WhatsApp", "Akun") else name == section
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                minimumHeight = dp(72)
                contentDescription = if (selected) "$name, dipilih" else name
                isSelected = selected; isFocusable = true
                background = RippleDrawable(ColorStateList.valueOf(0x33C8F560), null, null)
                setOnClickListener {
                    if (name == "Lainnya") {
                        AlertDialog.Builder(this@MainActivity).setTitle("Menu Keryxis")
                            .setItems(arrayOf("Label", "Template", "WhatsApp", "Akun")) { _, position ->
                                open(listOf("Label", "Template", "WhatsApp", "Akun")[position])
                            }.show()
                    } else open(name)
                }
            }
            val icon = when (name) {
                "Dashboard" -> R.drawable.nav_dashboard; "Kontak" -> R.drawable.nav_contacts
                "Broadcast" -> R.drawable.nav_broadcast; "Kirim" -> R.drawable.nav_send
                else -> R.drawable.nav_more
            }
            tab.addView(ImageView(this).apply {
                setImageResource(icon); imageTintList = ColorStateList.valueOf(if (selected) lime else muted)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(23), dp(23)))
            tab.addView(text(name, 11f, if (selected) lime else muted).apply {
                gravity = Gravity.CENTER; typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(72), 1f))
        }
    }
    private fun open(name: String) {
        stopCampaignPoll()
        clearMedia()
        activeSectionToken++
        templateGeneration++
        if (name != "Template") draft = null
        section = name; content.removeAllViews(); pageTitle.text = name
        findViewById<TextView>(R.id.header_status).visibility = View.GONE
        findViewById<ScrollView>(R.id.body).scrollTo(0, 0)
        findViewById<TextView>(R.id.page_subtitle).text = when (name) {
            "Dashboard" -> "Ringkasan aktivitas dan performa"
            "Kontak" -> "Kelola audiens pesan Anda"
            "Broadcast" -> "Siapkan kampanye dengan aman"
            "Kirim" -> "Kirim pesan pribadi"
            "Label" -> "Kelompokkan kontak"
            "Template" -> "Susun pesan sekali, pakai kembali"
            "WhatsApp" -> "Koneksi dan sesi pengiriman"
            else -> "Pengaturan akun"
        }
        menu()
        when (name) {
            "Dashboard" -> dashboard()
            "Kontak" -> contacts()
            "Label" -> labels()
            "Template" -> templates()
            "Broadcast" -> broadcasts()
            "Kirim" -> send()
            "WhatsApp" -> whatsapp()
            "Akun" -> profile()
        }
    }
    private fun login() {
        activeSectionToken++
        section = "Login"; pageTitle.text = "Masuk"; navigation.visibility = View.GONE; content.removeAllViews()
        findViewById<TextView>(R.id.header_status).visibility = View.GONE
        findViewById<TextView>(R.id.page_subtitle).text = "Satu ruang kerja untuk komunikasi Anda"
        heading("SELAMAT DATANG")
        state("Masuk ke Keryxis", "Gunakan akun Keryxis Anda untuk mengakses ruang kerja.")
        val email = input("Email", keyboard = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = input("Password", keyboard = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(email); content.addView(password)
        val submit = button("Masuk") {
            val mail = email.text.toString().trim(); val secret = password.text.toString()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches() || secret.isBlank()) { email.error = "Email dan password wajib valid"; return@button }
            password.text.clear()
            submitLogin(mail, secret)
        }
    }
    private fun submitLogin(mail: String, secret: String) {
        task({ api.request("/api/login", "POST", JSONObject().put("email", mail).put("password", secret).toString()) }) { open("Dashboard") }
    }
    private fun heading(value: String) {
        content.addView(text(value, 12f, lime).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .14f },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(16) })
    }
    private fun dashboard() {
        task({ api.request("/api/dashboard") }) { result ->
            content.removeAllViews()
            val stats = JSONObject(result).getJSONObject("stats")
            heading("PERFORMA RUANG KERJA")
            val metrics = listOf("contacts" to "Total kontak", "campaigns_30d" to "Kampanye · 30 hari",
                "sent_30d" to "Terkirim · 30 hari", "failed_30d" to "Gagal · 30 hari")
            metrics.chunked(2).forEach { pair ->
                val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { (key, label) ->
                    val tile = panel().apply { minimumHeight = dp(116) }
                    tile.addView(text(label, 12f, muted))
                    tile.addView(text(stats.optString(key, "—"), 32f, if (key == "failed_30d") pale else lime).apply {
                        typeface = Typeface.DEFAULT_BOLD
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                    grid.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6); marginStart = dp(6) })
                }
                content.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12); marginStart = -dp(6); marginEnd = -dp(6) })
            }
            heading("AKSES CEPAT")
            button("Buat kampanye") { open("Broadcast") }
            secondary("Kelola kontak") { open("Kontak") }
            heading("AKTIVITAS HARIAN")
            val daily = JSONObject(result).optJSONArray("daily") ?: JSONArray()
            if (daily.length() == 0) state("Belum ada aktivitas", "Aktivitas pengiriman akan muncul di sini.")
            for (i in 0 until daily.length()) daily.optJSONObject(i)?.let {
                row(it.optString("event_day"), "${it.optString("count", "0")} peristiwa")
            }
        }
    }
    private fun contacts() {
        val search = input("Cari nama / nomor", query); content.addView(search)
        secondary("Cari kontak") { query = search.text.toString().trim(); page = 1; open("Kontak") }
        button("+ Tambah kontak") { editContact(null) }
        heading("DAFTAR KONTAK")
        val url = "/api/contacts?search=${URLEncoder.encode(query, "UTF-8")}&page=$page&limit=25"
        task({ api.request(url) }) { result ->
            val data = JSONObject(result); val rows = data.optJSONArray("rows") ?: JSONArray(); val total = data.optInt("total")
            line("$total kontak · halaman $page", 14f, muted)
            if (rows.length() == 0) state("Tidak ada kontak", if (query.isBlank()) "Tambahkan kontak pertama untuk mulai mengirim pesan." else "Coba nama atau nomor lain.")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.optLong("id")
                val box = panel()
                val name = row.optString("name").ifBlank { "Tanpa nama" }
                box.addView(text(name, 17f, pale).apply { typeface = Typeface.DEFAULT_BOLD })
                box.addView(text(row.optString("phone"), 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                row.optString("label_name").takeIf { it.isNotBlank() }?.let {
                    box.addView(text(it, 12f, lime), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                }
                divider(box)
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                secondary("Ubah", actions) { editContact(row) }
                secondary("Hapus", actions) {
                    confirm("Hapus kontak ini?") { task({ api.request("/api/contacts/$id", "DELETE") }) { open("Kontak") } }
                }
                actions.getChildAt(0).layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) }
                actions.getChildAt(1).layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) }
                box.addView(actions)
                content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            if (page > 1) secondary("← Sebelumnya") { page--; open("Kontak") }
            if (page * 25 < total) secondary("Berikutnya →") { page++; open("Kontak") }
        }
    }
    private fun editContact(row: JSONObject?) {
        task({ api.request("/api/labels") }) { data ->
            val labels = JSONArray(data)
            val ids = (0 until labels.length()).map { labels.getJSONObject(it).getLong("id") }
            val names = (0 until labels.length()).map { labels.getJSONObject(it).getString("name") }
            val existing = row?.optLong("label_id", 0) ?: 0
            if (existing != 0L && existing !in ids) { line("Label kontak tidak tersedia. Ubah kontak ditunda agar label tidak terhapus.", 14f); return@task }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
            val phone = input("Nomor WhatsApp", row?.optString("phone") ?: "", InputType.TYPE_CLASS_PHONE)
            val name = input("Nama", row?.optString("name") ?: "")
            val label = picker("Label kontak (opsional)", listOf("Tanpa label") + names)
            label.setSelection(if (existing == 0L) 0 else ids.indexOf(existing) + 1)
            listOf(phone, name, text("LABEL KONTAK", 13f, muted), label).forEach(box::addView)
            AlertDialog.Builder(this).setTitle(if (row == null) "Tambah kontak" else "Ubah kontak").setView(box)
                .setNegativeButton("Batal", null).setPositiveButton("Simpan", null).show().apply {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val normalized = try { Phone.normalize(phone.text.toString()) } catch (e: IllegalArgumentException) { phone.error = e.message; return@setOnClickListener }
                        val labelId = if (label.selectedItemPosition == 0) JSONObject.NULL else ids[label.selectedItemPosition - 1]
                        val payload = JSONObject().put("phone", normalized).put("name", name.text.toString().trim()).put("label_id", labelId).toString()
                        dismiss(); task({ api.request(if (row == null) "/api/contacts" else "/api/contacts/${row.optLong("id")}", if (row == null) "POST" else "PUT", payload) }) { open("Kontak") }
                    }
                }
        }
    }
    private fun labels() {
        button("+ Buat label") {
            form("Label baru", listOf("Nama", "Warna hex (#RRGGBB)"), listOf("", "#C8F560")) { fields ->
                val name = fields[0].trim(); val color = fields[1].trim()
                if (name.isBlank() || !Regex("#[A-Fa-f0-9]{6}").matches(color)) "Nama / warna tidak valid" else {
                    task({ api.request("/api/labels", "POST", JSONObject().put("name", name).put("color", color).toString()) }) { open("Label") }; null
                }
            }
        }
        task({ api.request("/api/labels") }) { data ->
            val rows = JSONArray(data); if (rows.length() == 0) state("Belum ada label", "Buat label untuk mengelompokkan kontak.")
            for (i in 0 until rows.length()) { val item = rows.getJSONObject(i); row(item.optString("name"), "${item.optString("contact_count")} kontak", "Label") }
        }
    }
    private fun templates() {
        button("+ Buat template") { showTemplate(TemplateDraft()) }
        heading("TEMPLATE TERSIMPAN")
        task({ api.request("/api/templates") }) { data ->
            if (draft != null) return@task
            val rows = JSONArray(data)
            if (rows.length() == 0) state("Belum ada template", "Buat template untuk pratinjau dan pemakaian ulang.")
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                row(item.optString("title"), item.optString("body"), item.optString("header_type", "none")) {
                    showTemplate(TemplateDraft(item))
                }
            }
        }
    }
    private fun observe(field: EditText, changed: (String) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun showTemplate(model: TemplateDraft) {
        if (section != "Template") return
        clearMedia()
        activeSectionToken++
        draft = model
        val generation = ++templateGeneration
        content.removeAllViews()
        heading(if (model.id == 0L) "TEMPLATE BARU" else "TEMPLATE · ${model.id}")
        secondary("Kembali ke daftar") { if (!uploadBusy && !templateBusy) { draft = null; open("Template") } }
        val title = input("Judul template", model.title)
        content.addView(title)
        val type = picker("Tipe header", listOf("Tanpa header", "Teks", "Gambar", "Video", "PDF"))
        content.addView(text("TIPE HEADER", 12f, lime)); content.addView(type)
        type.setSelection(TemplateDraft.TYPES.indexOf(model.headerType).coerceAtLeast(0))
        val headerHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(headerHolder)
        val body = input("Isi template", model.body).apply { setSingleLine(false); minLines = 4 }
        content.addView(body)
        line("Gunakan {{nama}} untuk personalisasi nama penerima.", 13f, muted)
        val footer = input("Footer (opsional)", model.footer)
        content.addView(footer)
        val preview = panel()
        content.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val extra = panel()
        content.addView(extra, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        fun renderPreview() {
            preview.removeAllViews()
            preview.addView(text("PRATINJAU · nama contoh: Budi", 12f, lime))
            if (model.headerType == "text" && model.headerText.isNotBlank()) preview.addView(text(model.headerText, 18f))
            if (model.headerType in listOf("image", "video", "document") && model.headerMedia != null)
                preview.addView(text("Header ${model.headerType}: ${model.headerMedia}", 13f, muted))
            preview.addView(text(model.previewBody().ifBlank { "Isi pesan akan tampil di sini…" }, 16f))
            if (model.footer.isNotBlank()) preview.addView(text(model.footer, 13f, muted))
            if (model.attachments.isNotEmpty()) preview.addView(text("Lampiran: ${model.attachments.size}", 13f, lime))
        }
        fun image(filename: String, holder: LinearLayout) {
            val image = ImageView(this).apply {
                contentDescription = "Pratinjau $filename"; adjustViewBounds = true; maxHeight = dp(180)
            }
            holder.addView(image, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            worker.execute {
                val bitmap = runCatching { api.thumbnail(filename) }.getOrNull()
                runOnUiThread { if (!isFinishing && !isDestroyed && generation == templateGeneration && section == "Template") {
                    if (bitmap != null) image.setImageBitmap(bitmap) else image.contentDescription = "Gambar tidak tersedia: $filename"
                } }
            }
        }
        fun pdf(filename: String, holder: LinearLayout) {
            val view = ImageView(this).apply { contentDescription = "Halaman pertama PDF $filename"; adjustViewBounds = true; maxHeight = dp(240) }
            holder.addView(view)
            worker.execute {
                val bitmap = runCatching { api.pdfPreview(filename) }.getOrNull()
                runOnUiThread { if (!isFinishing && !isDestroyed && section == "Template" && generation == templateGeneration) {
                    if (bitmap != null) view.setImageBitmap(bitmap) else view.contentDescription = "PDF tidak tersedia: $filename"
                } }
            }
        }
        fun video(filename: String, holder: LinearLayout) {
            clearMedia()
            val holderView = FrameLayout(this).apply { minimumHeight = dp(180) }
            val display = SurfaceView(this)
            holderView.addView(display, FrameLayout.LayoutParams(-1, dp(180)))
            holder.addView(holderView)
            val controls = secondary("Putar / jeda video", holder) {
                val player = mediaPlayer
                if (player != null) { if (player.isPlaying) player.pause() else player.start() }
            }
            controls.isEnabled = false
            worker.execute {
                val file = runCatching {
                    val bytes = api.mediaBytes(filename)
                    java.io.File.createTempFile("template-video", ".mp4", cacheDir).also { it.writeBytes(bytes) }
                }.getOrNull()
                runOnUiThread {
                    if (file == null || generation != templateGeneration || section != "Template" || isDestroyed) { file?.delete(); return@runOnUiThread }
                    mediaCache = file
                    val player = MediaPlayer()
                    mediaPlayer = player
                    runCatching {
                        player.setDataSource(file.absolutePath)
                        display.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) { if (mediaPlayer === player) player.setDisplay(holder) }
                            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) { if (mediaPlayer === player) player.setDisplay(null) }
                        })
                        player.setOnPreparedListener { controls.isEnabled = true }
                        player.setOnErrorListener { _, _, _ -> controls.isEnabled = false; true }
                        player.prepareAsync()
                    }.onFailure { controls.isEnabled = false; clearMedia() }
                }
            }
        }
        fun renderAttachments() {
            extra.removeAllViews()
            extra.addView(text("LAMPIRAN TAMBAHAN · ${model.attachments.size}", 13f, lime))
            secondary("+ Tambah lampiran", extra) { if (!uploadBusy && !templateBusy) pickDocument(true, "*/*") }
            model.attachments.forEachIndexed { index, attachment ->
                val filename = attachment.optString("filename")
                extra.addView(text(attachment.optString("originalName").ifBlank { filename }, 14f))
                when {
                    Regex(".*\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE).matches(filename) -> image(filename, extra)
                    filename.endsWith(".pdf", true) -> pdf(filename, extra)
                    filename.endsWith(".mp4", true) -> secondary("Pratinjau video ${index + 1}", extra) { video(filename, extra) }
                }
                secondary("Hapus lampiran ${index + 1}", extra) { if (!uploadBusy && !templateBusy) { model.removeAttachment(index); renderAttachments(); renderPreview() } }
            }
        }
        fun renderHeader() {
            headerHolder.removeAllViews()
            when (model.headerType) {
                "text" -> {
                    val field = input("Teks header", model.headerText)
                    headerHolder.addView(field)
                    observe(field) { model.headerText = it; renderPreview() }
                }
                "image", "video", "document" -> {
                    secondary(if (model.headerMedia == null) "Tambah file header" else "Ganti file header", headerHolder) {
                        if (!uploadBusy && !templateBusy) pickDocument(false, when (model.headerType) {
                            "image" -> "image/*"; "video" -> "video/mp4"; else -> "application/pdf"
                        })
                    }
                    model.headerMedia?.let { filename ->
                        headerHolder.addView(text("File tersimpan: $filename", 13f, muted))
                        when (model.headerType) {
                            "image" -> image(filename, headerHolder)
                            "document" -> pdf(filename, headerHolder)
                            "video" -> video(filename, headerHolder)
                        }
                        secondary("Hapus file header", headerHolder) { if (!uploadBusy && !templateBusy) { clearMedia(); model.removeHeaderMedia(); renderHeader(); renderPreview() } }
                    }
                }
            }
        }
        type.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                model.headerType = TemplateDraft.TYPES[position]; clearMedia(); renderHeader(); renderPreview()
            }
        }
        observe(title) { model.title = it }
        observe(body) { model.body = it; renderPreview() }
        observe(footer) { model.footer = it; renderPreview() }
        renderHeader(); renderAttachments(); renderPreview()
        if (model.id != 0L && (model.buttonsCount > 0)) {
            line("Tombol lama dipertahankan saat menyimpan; pengaturan tombol tersedia di web.", 13f, muted)
        }
        button(if (model.id == 0L) "Buat template" else "Simpan perubahan") {
            if (uploadBusy || templateBusy) return@button
            val payload = try { model.payload().toString() } catch (e: IllegalArgumentException) { title.error = e.message; return@button }
            templateWrite(if (model.id == 0L) "/api/templates" else "/api/templates/${model.id}", if (model.id == 0L) "POST" else "PUT", payload, model) { open("Template"); line("Template tersimpan.", 14f, lime) }
        }
        if (model.id != 0L) secondary("Hapus template") {
            if (!templateBusy && !uploadBusy) confirm("Hapus template ${model.title}? Data template akan dihapus.") {
                templateWrite("/api/templates/${model.id}", "DELETE", null, model) { open("Template"); line("Template dihapus.", 14f, lime) }
            }
        }
    }
    private fun templateWrite(path: String, method: String, payload: String?, model: TemplateDraft, done: () -> Unit) {
        templateBusy = true
        val generation = templateGeneration
        val status = text("Menyimpan…", 14f, lime)
        content.addView(status)
        worker.execute {
            val result = runCatching { api.request(path, method, payload) }
            // Network failure after a write is indeterminate: read exact target before another write.
            val verified = if (result.isFailure && model.id > 0) runCatching {
                val rows = JSONArray(api.request("/api/templates"))
                (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optLong("id") == model.id }
            } else null
            runOnUiThread {
                templateBusy = false
                if (isFinishing || isDestroyed || section != "Template" || draft !== model || generation != templateGeneration) return@runOnUiThread
                if (result.isSuccess || (result.isFailure && model.id > 0 && verified?.isSuccess == true && ((method == "DELETE" && verified.getOrNull() == null) || (method == "PUT" && verified.getOrNull()?.optString("title") == model.title.trim() && verified.getOrNull()?.optString("body") == model.body.trim())))) {
                    draft = null; done()
                } else status.text = "Gagal atau status belum pasti: ${result.exceptionOrNull()?.message ?: "Koneksi gagal"}. Periksa daftar sebelum mencoba ulang."
            }
        }
    }
    private fun pickDocument(extra: Boolean, mime: String) {
        pickExtra = extra
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            if (mime == "*/*") putExtra(Intent.EXTRA_MIME_TYPES, UploadRules.ALLOWED.toTypedArray())
        }, pickCode)
    }
    @Deprecated("ACTION_OPEN_DOCUMENT result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickCode || resultCode != RESULT_OK || section != "Template" || uploadBusy) return
        val uri = data?.data ?: return
        val model = draft ?: return
        val extra = pickExtra
        val generation = templateGeneration
        uploadBusy = true
        val status = text("Memeriksa file…", 14f, lime)
        content.addView(status)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true; contentDescription = "Progres unggah" }
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(24)))
        worker.execute {
            try {
                val file = api.document(uri)
                if (!extra) require(UploadRules.matchesHeader(model.headerType, file.mime)) { "Tipe file tidak cocok dengan header" }
                val response = api.upload(file) { sent, total ->
                    runOnUiThread {
                        if (generation == templateGeneration && section == "Template") {
                            progress.isIndeterminate = total <= 0
                            if (total > 0) progress.progress = ((sent * 100) / total).toInt().coerceAtMost(100)
                            status.text = if (total > 0) "Mengunggah ${progress.progress}%" else "Mengunggah ${sent / 1024} KB"
                        }
                    }
                }
                runOnUiThread {
                    uploadBusy = false
                    if (section == "Template" && templateGeneration == generation && draft === model) {
                        if (extra) model.addAttachment(response.getString("filename"), file.name, response.getString("url"))
                        else model.setHeaderMedia(response.getString("filename"))
                        showTemplate(model)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { uploadBusy = false; if (section == "Template" && generation == templateGeneration) {
                    content.removeView(progress); status.text = "Upload gagal: ${e.message ?: "Coba lagi"}"
                } }
            }
        }
    }
    private fun stopCampaignPoll() {
        campaignGeneration++
        campaignPoll?.let { mainHandler.removeCallbacks(it) }
        campaignPoll = null
    }
    private fun campaignDetail(id: Long) {
        if (section != "Broadcast") return
        stopCampaignPoll()
        activeSectionToken++
        val generation = campaignGeneration
        content.removeAllViews()
        secondary("Kembali ke kampanye") { open("Broadcast") }
        val box = panel()
        content.addView(box)
        var remaining = 8 // ponytail: bounded foreground polling only; manual refresh after limit.
        fun refresh() {
            worker.execute {
                val data = runCatching { JSONObject(api.request("/api/broadcast/$id")) }
                runOnUiThread {
                    if (section != "Broadcast" || isFinishing || isDestroyed || generation != campaignGeneration) return@runOnUiThread
                    box.removeAllViews()
                    if (data.isFailure) {
                        box.addView(text("Status gagal dimuat: ${data.exceptionOrNull()?.message}", 14f, muted))
                        return@runOnUiThread
                    }
                    val result = data.getOrThrow(); val campaign = result.getJSONObject("campaign")
                    val items = result.optJSONArray("items") ?: JSONArray()
                    val sent = (0 until items.length()).count { items.optJSONObject(it)?.optString("status") in listOf("sent", "delivered", "read") }
                    val failed = (0 until items.length()).count { items.optJSONObject(it)?.optString("status") == "failed" }
                    val total = maxOf(items.length(), campaign.optInt("total"))
                    box.addView(text(campaign.optString("name"), 19f, pale))
                    box.addView(text("Status: ${campaign.optString("status")} · $sent terkirim · $failed gagal · $total total", 14f, muted))
                    box.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = total.coerceAtLeast(1)
                        val completed = (sent + failed).coerceAtMost(max)
                        contentDescription = "$completed dari $total diproses"
                        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                            android.animation.ObjectAnimator.ofInt(this, "progress", 0, completed).apply { duration = 350; start() }
                        } else progress = completed
                    }, LinearLayout.LayoutParams(-1, dp(24)))
                    if (campaign.optString("status") == "running" && --remaining > 0 && hasWindowFocus()) {
                        val next = Runnable { refresh() }; campaignPoll = next; mainHandler.postDelayed(next, 4000)
                    }
                }
            }
        }
        secondary("Muat ulang status") { stopCampaignPoll(); campaignDetail(id) }
        refresh()
    }
    private fun broadcasts() {
        val screenGeneration = campaignGeneration
        state("Kampanye baru", "Pilih target dan pratinjau jumlah penerima. Kampanye berjalan setelah dua konfirmasi.")
        task({
            JSONObject().put("templates", JSONArray(api.request("/api/templates")))
                .put("labels", JSONArray(api.request("/api/labels")))
                .put("sessions", JSONObject(api.request("/api/broadcast/sessions")).getJSONArray("sessions")).toString()
        }) { data ->
            if (screenGeneration != campaignGeneration) return@task
            val options = JSONObject(data)
            val templates = options.getJSONArray("templates")
            val labels = options.getJSONArray("labels")
            val sessions = options.getJSONArray("sessions")
            if (templates.length() == 0 || sessions.length() == 0) {
                state("Belum siap", "Buat template dan hubungkan sesi WhatsApp sebelum membuat kampanye.")
            } else {
                val name = input("Nama kampanye")
                val template = picker("Pilih template", (0 until templates.length()).map { templates.getJSONObject(it).optString("title") })
                val label = picker("Target label", listOf("Semua kontak") + (0 until labels.length()).map { labels.getJSONObject(it).optString("name") })
                val session = picker("Sesi WhatsApp terhubung", (0 until sessions.length()).map { sessions.getString(it) })
                val delay = input("Jeda antar pesan (detik, 5–3600)", "30", InputType.TYPE_CLASS_NUMBER)
                listOf(text("NAMA KAMPANYE", 13f, muted), name, text("TEMPLATE", 13f, muted), template,
                    text("TARGET", 13f, muted), label, text("SESI TERHUBUNG", 13f, muted), session,
                    text("JEDA (DETIK)", 13f, muted), delay).forEach(content::addView)
                button("Pratinjau penerima") {
                    val templateRow = templates.getJSONObject(template.selectedItemPosition)
                    val labelRow = if (label.selectedItemPosition == 0) null else labels.getJSONObject(label.selectedItemPosition - 1)
                    val sessionName = sessions.getString(session.selectedItemPosition)
                    val draft = try {
                        BroadcastDraft.create(name.text.toString(), templateRow.getLong("id"), labelRow?.getLong("id"),
                            sessionName, delay.text.toString(), (0 until sessions.length()).map { sessions.getString(it) }, 1)
                    } catch (e: IllegalArgumentException) { name.error = e.message; return@button }
                    // ponytail: count endpoint is preview, not reservation; recheck immediately before POST and stop on change.
                    task({ api.request("/api/contacts/count" + (draft.labelId?.let { "?label_id=$it" } ?: "")) }) { countData ->
                        val count = JSONObject(countData).getInt("total")
                        if (count <= 0) { card("Target kosong", "Tidak ada kontak untuk target ini."); return@task }
                        val preview = draft.copy(recipientCount = count)
                        AlertDialog.Builder(this).setTitle("Pratinjau kampanye")
                            .setMessage("${preview.name}\nTemplate: ${templateRow.optString("title")}\nTarget: ${labelRow?.optString("name") ?: "Semua kontak"}\nSesi: ${preview.session}\nJeda: ${preview.delaySeconds} detik\nPenerima: $count kontak\n\nBelum ada pesan dikirim. Lanjut ke konfirmasi akhir?")
                            .setNegativeButton("Batal", null).setPositiveButton("Lanjut") { _, _ ->
                                AlertDialog.Builder(this).setTitle("Mulai kampanye sekarang?")
                                    .setMessage("Kirim ke $count kontak melalui ${preview.session}. POST akan memulai kampanye nyata tanpa jadwal tunda.")
                                    .setNegativeButton("Batal", null).setPositiveButton("Ya, mulai kirim", null).show().apply {
                                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                            if (section != "Broadcast" || !campaignSubmitting.compareAndSet(false, true)) return@setOnClickListener
                                            dismiss()
                                            val targetSection = section
                                            card("Memproses", "Memeriksa target dan memulai kampanye. Jangan kirim ulang.")
                                            worker.execute {
                                                var posted = false
                                                val result = try {
                                                    val freshCount = JSONObject(api.request("/api/contacts/count" + (preview.labelId?.let { "?label_id=$it" } ?: ""))).getInt("total")
                                                    val freshSessions = JSONObject(api.request("/api/broadcast/sessions")).getJSONArray("sessions")
                                                    if (freshCount != preview.recipientCount || (0 until freshSessions.length()).none { freshSessions.getString(it) == preview.session })
                                                        "Pratinjau berubah. Muat ulang dan pratinjau kembali."
                                                    else {
                                                        val body = JSONObject().put("name", preview.name).put("template_id", preview.templateId)
                                                            .put("label_id", preview.labelId ?: JSONObject.NULL).put("session", preview.session)
                                                            .put("delay_ms", preview.delaySeconds).toString()
                                                        posted = true
                                                        val response = JSONObject(api.request("/api/broadcast", "POST", body))
                                                        "Kampanye dimulai: ID ${response.getLong("id")} · ${response.getInt("total")} penerima"
                                                    }
                                                } catch (e: Exception) {
                                                    if (posted) "Status kirim belum pasti (${e.message ?: "koneksi gagal"}). Periksa riwayat sebelum mencoba lagi; jangan kirim ulang otomatis."
                                                    else "Tidak dikirim: ${e.message ?: "koneksi gagal"}"
                                                }
                                                runOnUiThread {
                                                    campaignSubmitting.set(false)
                                                    if (!isFinishing && !isDestroyed && section == targetSection) {
                                                        open("Broadcast"); card("Hasil kampanye", result)
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }.show()
                    }
                }
            }
        }
        heading("RIWAYAT KAMPANYE")
        task({ api.request("/api/broadcast") }) { data ->
            if (screenGeneration != campaignGeneration) return@task
            val rows = JSONArray(data); if (rows.length() == 0) state("Belum ada kampanye", "Kampanye yang dibuat akan tercatat di sini.")
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                row(item.optString("name", "Kampanye"), "${item.optString("template_title")}\nTerkirim ${item.optString("sent", "0")}  ·  Gagal ${item.optString("failed", "0")}", item.optString("status", "Status tidak diketahui")) { campaignDetail(item.getLong("id")) }
            }
        }
    }
    private fun send() {
        state("Pesan langsung", "Pesan teks dikirim melalui sesi WhatsApp default setelah konfirmasi.")
        val phone = input("Nomor WhatsApp", keyboard = InputType.TYPE_CLASS_PHONE)
        val body = input("Isi pesan"); body.setSingleLine(false); body.minLines = 4
        content.addView(phone); content.addView(body)
        button("Kirim pesan") {
            val normalized = try { Phone.normalize(phone.text.toString()) } catch (e: IllegalArgumentException) { phone.error = e.message; return@button }
            val message = body.text.toString().trim()
            if (message.isEmpty()) { body.error = "Pesan wajib diisi"; return@button }
            confirm("Kirim pesan ke $normalized? Pesan akan terkirim sungguhan.") {
                val boundary = "KeryxisNativeBoundary"
                val payload = "--$boundary\r\nContent-Disposition: form-data; name=\"phone\"\r\n\r\n$normalized\r\n--$boundary\r\nContent-Disposition: form-data; name=\"text\"\r\n\r\n$message\r\n--$boundary--\r\n"
                task({ api.request("/api/message", "POST", payload, "multipart/form-data; boundary=$boundary") }) { open("Kirim"); line("Pesan terkirim", 14f, lime) }
            }
        }
        heading("Pesan terbaru")
        task({ api.request("/api/messages") }) { data ->
            val rows = JSONArray(data); if (rows.length() == 0) state("Belum ada pesan", "Pesan yang terkirim akan muncul di sini.")
            for (i in 0 until rows.length()) { val item = rows.getJSONObject(i); row(item.optString("phone"), item.optString("text"), item.optString("status")) }
        }
    }
    private fun whatsapp() {
        task({ api.request("/api/wa/status") }) { data ->
            val state = JSONObject(data)
            val status = state.optString("status", "Tidak diketahui")
            findViewById<TextView>(R.id.header_status).apply { text = "WA · $status"; visibility = View.VISIBLE }
            row("WhatsApp", state.optJSONObject("me")?.let { "${it.optString("name")} · ${it.optString("phone")}" } ?: "Sesi belum terhubung", status)
            secondary("Muat QR") { loadQr() }
            button("Mulai / sambungkan ulang") { confirm("Mulai ulang sesi WhatsApp?") { waAction("start") } }
            secondary("Putuskan WhatsApp") { confirm("Putuskan WhatsApp dari akun ini?") { waAction("logout") } }
        }
    }
    private fun loadQr() {
        task({ api.request("/api/wa/qr") }) { result ->
            val qr = JSONObject(result).optString("qr")
            if (!qr.startsWith("data:image/png;base64,")) { line("QR belum tersedia. Periksa status dan coba lagi.", 14f, muted); return@task }
            try {
                val raw = Base64.decode(qr.substringAfter(','), Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: error("QR tidak valid")
                val image = ImageView(this).apply { setImageBitmap(bitmap); contentDescription = "QR untuk menghubungkan WhatsApp"; adjustViewBounds = true; maxHeight = dp(360) }
                content.addView(image)
            } catch (_: Exception) { line("QR tidak dapat dibaca", 14f, muted) }
        }
    }
    private fun waAction(action: String) { task({ api.request("/api/wa/action", "POST", JSONObject().put("action", action).toString()) }) { open("WhatsApp") } }
    private fun profile() {
        task({ api.request("/api/profile") }) { data ->
            val user = JSONObject(data)
            user.optString("account_name").takeIf { it.isNotBlank() }?.let { account ->
                findViewById<TextView>(R.id.header_status).apply { text = account; visibility = View.VISIBLE }
            }
            row(user.optString("name"), "${user.optString("email")}\n${user.optString("account_name")} · ${user.optString("role")}", "AKUN AKTIF")
            secondary("Keluar") {
                confirm("Keluar dari akun ini?") {
                    task({ api.request("/api/logout", "POST", "{}") }) { api.clear(); login() }
                }
            }
        }
    }
    private fun confirm(message: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(message).setNegativeButton("Batal", null).setPositiveButton("Ya") { _, _ -> yes() }.show()
    }
    private fun form(title: String, hints: List<String>, defaults: List<String>, save: (List<String>) -> String?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val fields = hints.mapIndexed { i, hint -> input(hint, defaults[i]).also { box.addView(it) } }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).show().apply {
            getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = save(fields.map { field -> field.text.toString() })
                if (error == null) dismiss() else fields.first().error = error
            }
        }
    }
}
