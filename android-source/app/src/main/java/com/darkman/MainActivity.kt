package com.darkman

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Darkman-AI Micro Edition: a deliberately small, XML-only client for API 21+.
 * It avoids large UI frameworks and keeps simple tasks local to the device.
 */
class MainActivity : Activity() {
    private lateinit var securePrefs: SecurePrefs
    private lateinit var db: MemoryDb
    private lateinit var chatInput: EditText
    private lateinit var chatOutput: TextView
    private lateinit var toolOutput: TextView
    private lateinit var statusText: TextView
    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var baseUrlInput: EditText
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val providers = arrayOf("Groq", "Google Gemini", "OpenRouter")
    private val requestCode = 42

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        securePrefs = SecurePrefs(this)
        db = MemoryDb(this)
        bindViews()
        loadSettings()
        wireActions()
        statusText.text = "Offline-ready • ${providerSpinner.selectedItem} selected"
    }

    private fun bindViews() {
        chatInput = findViewById(R.id.chatInput)
        chatOutput = findViewById(R.id.chatOutput)
        toolOutput = findViewById(R.id.toolOutput)
        statusText = findViewById(R.id.statusText)
        providerSpinner = findViewById(R.id.providerSpinner)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        baseUrlInput = findViewById(R.id.baseUrlInput)
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                apiKeyInput.setText(securePrefs.get("key_${providers[position]}"))
                statusText.text = "Offline-ready • ${providers[position]} selected"
            }
        }
    }

    private fun wireActions() {
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendChat() }
        findViewById<Button>(R.id.memoryButton).setOnClickListener { showMemoryDialog() }
        findViewById<Button>(R.id.filesButton).setOnClickListener { showFileDialog() }
        findViewById<Button>(R.id.appsButton).setOnClickListener { showAppsDialog() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveSettings(); toast("Settings saved locally") }
        findViewById<Button>(R.id.testButton).setOnClickListener { testProvider() }
        findViewById<Button>(R.id.permissionsButton).setOnClickListener { requestPermissionsAndOverlay() }
    }

    private fun saveSettings() {
        val provider = providers[providerSpinner.selectedItemPosition]
        securePrefs.put("provider", provider)
        securePrefs.put("key_$provider", apiKeyInput.text.toString().trim())
        securePrefs.put("baseUrl", baseUrlInput.text.toString().trim())
    }

    private fun loadSettings() {
        val provider = securePrefs.get("provider")
        val index = providers.indexOf(provider).let { if (it < 0) 0 else it }
        providerSpinner.setSelection(index)
        apiKeyInput.setText(securePrefs.get("key_${providers[index]}"))
        baseUrlInput.setText(securePrefs.get("baseUrl"))
    }

    private fun sendChat() {
        val prompt = chatInput.text.toString().trim()
        if (prompt.isEmpty()) return
        db.add("You", prompt)
        val key = apiKeyInput.text.toString().trim()
        val provider = providers[providerSpinner.selectedItemPosition]
        if (prompt.startsWith("cloud:", true)) {
            callCloud(prompt.removePrefix("cloud:").trim(), provider)
        } else if (key.isEmpty()) {
            showLocalResponse(prompt)
        } else {
            callProvider(provider, key, prompt)
        }
        chatInput.setText("")
    }

    private fun showLocalResponse(prompt: String) {
        val local = when {
            prompt.contains("offline", true) -> "Darkman-AI is offline-ready. File tools, memory, chat history, permissions, and app launching stay on this device."
            prompt.contains("memory", true) -> "Use Memory to save a note locally in SQLite. It remains available without an internet connection."
            prompt.contains("file", true) -> "Use Files to list storage or run copy, move, and delete commands inside the shared storage directory."
            else -> "Local mode: I saved this message to chat history. Add a provider key in settings for an AI response, or prefix a request with cloud: for the configured server."
        }
        db.add("Darkman-AI", local)
        chatOutput.text = historyText()
    }

    private fun callProvider(provider: String, key: String, prompt: String) {
        statusText.text = "Contacting $provider…"
        Thread {
            try {
                val body = when (provider) {
                    "Google Gemini" -> geminiBody(prompt)
                    else -> openAiBody(provider, prompt)
                }
                val request = when (provider) {
                    "Google Gemini" -> Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
                        .post(body.toRequestBody(JSON))
                        .build()
                    "Groq" -> Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $key").post(body.toRequestBody(JSON)).build()
                    else -> Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("HTTP-Referer", "https://darkman-ai.local")
                        .addHeader("X-Title", "Darkman-AI").post(body.toRequestBody(JSON)).build()
                }
                val response = http.newCall(request).execute()
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${text.take(180)}")
                val answer = parseProviderResponse(provider, text)
                runOnUiThread {
                    db.add("Darkman-AI", answer)
                    chatOutput.text = historyText()
                    statusText.text = "$provider response received"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Provider failed; showing local fallback"
                    showLocalResponse(prompt)
                    toast(e.message ?: "Network error")
                }
            }
        }.start()
    }

    private fun testProvider() {
        saveSettings()
        val provider = providers[providerSpinner.selectedItemPosition]
        val key = apiKeyInput.text.toString().trim()
        if (key.isEmpty()) { toast("Enter a $provider key first"); return }
        callProvider(provider, key, "Reply with exactly: Darkman-AI provider test OK")
    }

    private fun callCloud(task: String, provider: String) {
        val base = baseUrlInput.text.toString().trim().trimEnd('/')
        if (base.isEmpty()) { toast("Set the cloud BASE_URL first"); return }
        statusText.text = "Sending complex task to cloud…"
        Thread {
            try {
                val json = JSONObject().put("task", task).put("provider", provider)
                val request = Request.Builder().url("$base/v1/ai/task")
                    .post(json.toString().toRequestBody(JSON)).build()
                val response = http.newCall(request).execute()
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${text.take(180)}")
                val answer = JSONObject(text).optString("result", text)
                runOnUiThread {
                    db.add("Darkman-AI Cloud", answer)
                    chatOutput.text = historyText()
                    statusText.text = "Cloud task complete"
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Cloud unavailable"; toast(e.message ?: "Cloud error") }
            }
        }.start()
    }

    private fun openAiBody(provider: String, prompt: String): String {
        val model = if (provider == "Groq") "llama-3.3-70b-versatile" else "openai/gpt-4o-mini"
        return JSONObject().put("model", model).put("messages", JSONArray().put(
            JSONObject().put("role", "system").put("content", "You are Darkman-AI, a concise helpful assistant.")
        ).put(JSONObject().put("role", "user").put("content", prompt))).put("temperature", 0.2).toString()
    }

    private fun geminiBody(prompt: String): String = JSONObject().put("contents", JSONArray().put(
        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are Darkman-AI. $prompt")))
    )).toString()

    private fun parseProviderResponse(provider: String, text: String): String {
        val json = JSONObject(text)
        return if (provider == "Google Gemini") {
            json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", text) ?: text
        } else {
            json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", text) ?: text
        }
    }

    private fun historyText(): String = db.list().joinToString("\n\n") { "${it.first}: ${it.second}" }

    private fun showMemoryDialog() {
        val input = EditText(this)
        input.hint = "New local memory note"
        AlertDialog.Builder(this).setTitle("Local memory").setMessage(db.list().joinToString("\n") { "• ${it.second}" }.ifEmpty { "No memories yet." })
            .setView(input).setPositiveButton("Add") { _, _ ->
                val note = input.text.toString().trim()
                if (note.isNotEmpty()) { db.add("Memory", note); toolOutput.text = "Saved memory:\n$note" }
            }.setNegativeButton("Close", null).show()
    }

    private fun showFileDialog() {
        val input = EditText(this)
        input.hint = "list  |  delete:/path  |  copy:/src:/dest  |  move:/src:/dest"
        input.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(this).setTitle("Offline files").setMessage("Root: ${storageRoot().absolutePath}\n\n${listFiles(storageRoot())}")
            .setView(input).setPositiveButton("Run") { _, _ -> runFileCommand(input.text.toString()) }
            .setNegativeButton("Close", null).show()
    }

    private fun storageRoot(): File = Environment.getExternalStorageDirectory()

    private fun listFiles(dir: File): String = dir.listFiles()?.sortedBy { it.name }?.take(60)?.joinToString("\n") {
        if (it.isDirectory) "[DIR] ${it.name}" else "${it.name} (${it.length()} bytes)"
    } ?: "Unable to read storage; grant storage permission."

    private fun safeFile(path: String): File? {
        val root = storageRoot().canonicalFile
        val candidate = File(if (path.startsWith("/")) path else root.absolutePath + "/" + path).canonicalFile
        return if (candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)) candidate else null
    }

    private fun runFileCommand(command: String) {
        try {
            val parts = command.trim().split(":")
            when (parts.firstOrNull()?.lowercase()) {
                "list" -> toolOutput.text = listFiles(storageRoot())
                "delete" -> { val f = safeFile(parts.drop(1).joinToString(":")); if (f != null && f.exists() && f.deleteRecursively()) toolOutput.text = "Deleted ${f.path}" else toolOutput.text = "Delete failed" }
                "copy", "move" -> {
                    if (parts.size < 3) throw IOException("Use copy:/source:/destination")
                    val src = safeFile(parts[1]) ?: throw IOException("Unsafe source")
                    val dst = safeFile(parts.drop(2).joinToString(":")) ?: throw IOException("Unsafe destination")
                    if (!src.exists()) throw IOException("Source does not exist")
                    if (src.isDirectory) throw IOException("Directory copy is not supported in Micro Edition")
                    dst.parentFile?.mkdirs()
                    src.inputStream().use { from -> dst.outputStream().use { to -> from.copyTo(to) } }
                    if (parts[0].equals("move", true)) src.delete()
                    toolOutput.text = "${parts[0].replaceFirstChar { it.uppercaseChar() }} complete: ${dst.path}"
                }
                else -> toolOutput.text = "Unknown command"
            }
        } catch (e: Exception) { toolOutput.text = "File error: ${e.message}" }
    }

    private fun showAppsDialog() {
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .sortedBy { it.loadLabel(packageManager).toString() }.take(80)
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Launch an installed app").setItems(labels) { _, which ->
            val info = apps[which].activityInfo
            val intent = Intent().setClassName(info.packageName, info.name)
            startActivity(intent)
        }.setNegativeButton("Close", null).show()
    }

    private fun requestPermissionsAndOverlay() {
        val list = arrayListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.CAMERA)
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (android.os.Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (list.isNotEmpty()) requestPermissions(list.toTypedArray(), requestCode)
            if (!Settings.canDrawOverlays(this)) try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) { }
        }
        statusText.text = "Permissions: storage/camera requested; overlay is optional"
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}

private class MemoryDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "darkman_memory.db", null, 1) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) { db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, body TEXT NOT NULL, created INTEGER NOT NULL)") }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, old: Int, next: Int) { db.execSQL("DROP TABLE IF EXISTS memories"); onCreate(db) }
    fun add(speaker: String, body: String) { writableDatabase.execSQL("INSERT INTO memories(speaker, body, created) VALUES(?,?,?)", arrayOf(speaker, body, System.currentTimeMillis())) }
    fun list(): List<Pair<String, String>> { val out = mutableListOf<Pair<String, String>>(); readableDatabase.rawQuery("SELECT speaker, body FROM memories ORDER BY id DESC LIMIT 80", null).use { c -> while (c.moveToNext()) out.add(c.getString(0) to c.getString(1)) }; return out.reversed() }
}

/** API-21-compatible encrypted SharedPreferences using AES/CBC and a per-install derived key. */
private class SecurePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("darkman_secure", Context.MODE_PRIVATE)
    private val saltKey = "install_salt"
    private val secret: SecretKeySpec by lazy {
        var salt = prefs.getString(saltKey, null)
        if (salt == null) { val bytes = ByteArray(16); SecureRandom().nextBytes(bytes); salt = Base64.encodeToString(bytes, Base64.NO_WRAP); prefs.edit().putString(saltKey, salt).apply() }
        val device = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "darkman"
        val spec = PBEKeySpec(("Darkman-AI:$device").toCharArray(), Base64.decode(salt, Base64.DEFAULT), 12000, 128)
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
    }
    fun put(key: String, value: String) { try { val iv = ByteArray(16); SecureRandom().nextBytes(iv); val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(iv)); val data = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)); prefs.edit().putString(key, Base64.encodeToString(data, Base64.NO_WRAP)).apply() } catch (_: Exception) { } }
    fun get(key: String): String { return try { val data = Base64.decode(prefs.getString(key, "") ?: "", Base64.DEFAULT); if (data.size < 17) return ""; val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(data.copyOfRange(0, 16))); String(cipher.doFinal(data.copyOfRange(16, data.size)), Charsets.UTF_8) } catch (_: Exception) { "" } }
}
