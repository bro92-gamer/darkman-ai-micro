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
import android.view.View
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val providers = arrayOf("Local AI (Qwen)", "Google Gemini Auto", "Google Gemini Flash-Lite", "Groq", "OpenRouter")
    private val requestCode = 42
    private val modelDownloader by lazy { ModelDownloader(this) }
    private val agent by lazy { AgentEngine(listOf(FileSystemTool(storageRoot()), TermuxTool(filesDir), GodotTool(storageRoot()))) }
    @Volatile private var localLoaded = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(R.layout.activity_main)
        securePrefs = SecurePrefs(this); db = MemoryDb(this); bindViews(); loadSettings(); wireActions()
        statusText.text = "Offline-ready • ${providerSpinner.selectedItem} selected"
    }
    override fun onDestroy() { if (localLoaded) LocalAiNative.freeModel(); super.onDestroy() }

    private fun bindViews() {
        chatInput = findViewById(R.id.chatInput); chatOutput = findViewById(R.id.chatOutput); toolOutput = findViewById(R.id.toolOutput); statusText = findViewById(R.id.statusText); providerSpinner = findViewById(R.id.providerSpinner); apiKeyInput = findViewById(R.id.apiKeyInput); baseUrlInput = findViewById(R.id.baseUrlInput)
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(p: AdapterView<*>?) = Unit; override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { apiKeyInput.setText(securePrefs.get("key_${providers[pos]}")); apiKeyInput.visibility = if (pos == 0) View.GONE else View.VISIBLE; statusText.text = "Offline-ready • ${providers[pos]} selected" } }
    }
    private fun wireActions() {
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendChat() }; findViewById<Button>(R.id.memoryButton).setOnClickListener { showMemoryDialog() }; findViewById<Button>(R.id.filesButton).setOnClickListener { showFileDialog() }; findViewById<Button>(R.id.appsButton).setOnClickListener { showAppsDialog() }; findViewById<Button>(R.id.saveButton).setOnClickListener { saveSettings(); toast("Settings saved locally") }; findViewById<Button>(R.id.testButton).setOnClickListener { testProvider() }; findViewById<Button>(R.id.permissionsButton).setOnClickListener { requestPermissionsAndOverlay() }
    }
    private fun saveSettings() { val provider = providers[providerSpinner.selectedItemPosition]; securePrefs.put("provider", provider); securePrefs.put("key_$provider", apiKeyInput.text.toString().trim()); securePrefs.put("baseUrl", baseUrlInput.text.toString().trim()) }
    private fun loadSettings() { val saved = securePrefs.get("provider"); providerSpinner.setSelection(providers.indexOf(saved).let { if (it < 0) 0 else it }); apiKeyInput.setText(securePrefs.get("key_${providers[providerSpinner.selectedItemPosition]}")); baseUrlInput.setText(securePrefs.get("baseUrl")) }

    private fun sendChat() {
        val prompt = chatInput.text.toString().trim(); if (prompt.isEmpty()) return
        db.add("You", prompt, "chat"); chatInput.setText("")
        val toolResult = agent.execute(prompt)
        if (toolResult != null) { toolOutput.text = "Agent tool result:\n${toolResult.output}"; db.add("Tool", toolResult.output, "agent"); routeFinal(prompt, toolResult.output); return }
        val provider = providers[providerSpinner.selectedItemPosition]; val key = apiKeyInput.text.toString().trim()
        if (provider == providers[0]) ensureLocalAndGenerate(prompt) else if (key.isEmpty()) showLocalResponse(prompt) else callProvider(provider, key, prompt)
    }
    private fun routeFinal(prompt: String, toolOutput: String) { val key = apiKeyInput.text.toString().trim(); if (key.isNotEmpty() && providerSpinner.selectedItemPosition != 0) callProvider(providers[providerSpinner.selectedItemPosition], key, "User request: $prompt\nTool output:\n$toolOutput") else showLocalResponse("Summarize this tool result: $toolOutput") }
    private fun ensureLocalAndGenerate(prompt: String) {
        statusText.text = "Preparing offline model…"
        Thread { try { val model = modelDownloader.ensure(LocalModelCatalog.primary) { done, total -> runOnUiThread { statusText.text = if (total > 0) "Downloading local model ${(done * 100 / total)}%" else "Downloading local model…" } }; if (!localLoaded) { localLoaded = LocalAiNative.loadModel(model.absolutePath, 2048, maxOf(1, Runtime.getRuntime().availableProcessors() / 2)); if (!localLoaded) error("Native model load failed") }; val context = db.relevant(prompt, 8).joinToString("\n") { "${it.first}: ${it.second}" }; val answer = LocalAiNative.generate("You are Darkman-AI. Reply naturally and preserve Arabic UTF-8.\nRelevant memory:\n$context\nUser: $prompt", 256); runOnUiThread { db.add("Darkman-AI", answer, "local"); chatOutput.text = historyText(); statusText.text = "Local AI response received (RAM/tokens logged by native layer)" } } catch (e: Exception) { runOnUiThread { statusText.text = "Local AI unavailable"; toast(e.message ?: "Local model error") } } }.start()
    }
    private fun showLocalResponse(prompt: String) { val local = when { prompt.contains("offline", true) -> "Darkman-AI is offline-ready. Qwen local inference, smart memory, files, and agent tools stay on this device."; prompt.contains("memory", true) -> "Smart Memory keeps the last 8 messages and retrieves relevant long-term facts by keyword."; else -> "Local mode saved your request. Select Local AI (Qwen) to download the model on first use." }; db.add("Darkman-AI", local, "local"); chatOutput.text = historyText() }

    private fun callProvider(provider: String, key: String, prompt: String) {
        statusText.text = "Contacting $provider…"; Thread { try { val body = if (provider.startsWith("Google Gemini")) geminiBody(prompt) else openAiBody(provider, prompt); val request = if (provider.startsWith("Google Gemini")) { val model = if (provider.contains("Lite")) "gemini-2.0-flash-lite" else if (provider.contains("Auto") && isSimple(prompt)) "gemini-2.0-flash-lite" else "gemini-2.0-flash"; Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key").post(body.toRequestBody(JSON)).build() } else Request.Builder().url(if (provider == "Groq") "https://api.groq.com/openai/v1/chat/completions" else "https://openrouter.ai/api/v1/chat/completions").addHeader("Authorization", "Bearer $key").post(body.toRequestBody(JSON)).build(); http.newCall(request).execute().use { response -> val text = response.body?.string().orEmpty(); if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${text.take(180)}"); val answer = parseProviderResponse(provider, text); runOnUiThread { db.add("Darkman-AI", answer, "cloud"); chatOutput.text = historyText(); statusText.text = "$provider response received" } } } catch (e: Exception) { runOnUiThread { statusText.text = "Provider failed; showing local fallback"; showLocalResponse(prompt); toast(e.message ?: "Network error") } } }.start()
    }
    private fun isSimple(p: String) = p.length < 180 && !p.contains("why|compare|debug|architect|analy[sz]e|خطة|حلل".toRegex(RegexOption.IGNORE_CASE))
    private fun testProvider() { saveSettings(); val provider = providers[providerSpinner.selectedItemPosition]; val key = apiKeyInput.text.toString().trim(); if (key.isEmpty()) { toast("Enter a provider key first"); return }; callProvider(provider, key, "Reply with exactly: Darkman-AI provider test OK") }
    private fun openAiBody(provider: String, prompt: String): String { val model = if (provider == "Groq") "llama-3.3-70b-versatile" else "openai/gpt-4o-mini"; return JSONObject().put("model", model).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", "You are Darkman-AI. Use relevant memory:\n${db.relevant(prompt, 8).joinToString("\n")}")).put(JSONObject().put("role", "user").put("content", prompt))).put("temperature", 0.2).toString() }
    private fun geminiBody(prompt: String): String = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are Darkman-AI. Relevant memory:\n${db.relevant(prompt, 8).joinToString("\n")}\n$prompt"))))).toString()
    private fun parseProviderResponse(provider: String, text: String): String { val json = JSONObject(text); return if (provider.startsWith("Google Gemini")) json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", text) ?: text else json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", text) ?: text }
    private fun historyText() = db.recent(40).joinToString("\n\n") { "${it.first}: ${it.second}" }

    private fun showMemoryDialog() { val input = EditText(this); input.hint = "New long-term fact or note"; AlertDialog.Builder(this).setTitle("Smart Memory").setMessage(db.recent(12).joinToString("\n") { "• ${it.second}" }.ifEmpty { "No memories yet." }).setView(input).setPositiveButton("Add") { _, _ -> val note = input.text.toString().trim(); if (note.isNotEmpty()) { db.add("Memory", note, "fact"); toolOutput.text = "Saved long-term memory:\n$note" } }.setNegativeButton("Close", null).show() }
    private fun showFileDialog() { val input = EditText(this); input.hint = "list | search:text | read:path | write:path:text"; input.inputType = InputType.TYPE_CLASS_TEXT; AlertDialog.Builder(this).setTitle("Agent files").setMessage("Root: ${storageRoot().absolutePath}").setView(input).setPositiveButton("Run") { _, _ -> val c = input.text.toString(); val result = if (c.startsWith("search:")) FileSystemTool(storageRoot()).execute(mapOf("action" to "search", "query" to c.substringAfter(":") )) else FileSystemTool(storageRoot()).execute(mapOf("action" to "list")); toolOutput.text = result.output }.setNegativeButton("Close", null).show() }
    private fun storageRoot() = if (Environment.getExternalStorageDirectory().exists()) Environment.getExternalStorageDirectory() else filesDir
    private fun showAppsDialog() { val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).sortedBy { it.loadLabel(packageManager).toString() }.take(80); AlertDialog.Builder(this).setTitle("Launch an installed app").setItems(apps.map { it.loadLabel(packageManager) }.toTypedArray()) { _, i -> val a = apps[i].activityInfo; startActivity(Intent().setClassName(a.packageName, a.name)) }.setNegativeButton("Close", null).show() }
    private fun requestPermissionsAndOverlay() { val list = arrayListOf<String>(); if (android.os.Build.VERSION.SDK_INT >= 23) { if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.CAMERA); if (android.os.Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.READ_EXTERNAL_STORAGE); if (android.os.Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE); if (list.isNotEmpty()) requestPermissions(list.toTypedArray(), requestCode); if (!Settings.canDrawOverlays(this)) runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }; statusText.text = "Permissions requested; SAF is preferred on Android 13+" }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}

private class MemoryDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "darkman_memory.db", null, 2) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) { db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL DEFAULT '', summary TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)") }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, old: Int, next: Int) { if (old < 2) { db.execSQL("ALTER TABLE memories ADD COLUMN tags TEXT NOT NULL DEFAULT ''"); db.execSQL("ALTER TABLE memories ADD COLUMN summary TEXT NOT NULL DEFAULT ''") } }
    fun add(speaker: String, body: String, tags: String = "chat") { writableDatabase.execSQL("INSERT INTO memories(speaker, body, tags, summary, created) VALUES(?,?,?,?,?)", arrayOf(speaker, body, tags, body.take(240), System.currentTimeMillis())) }
    fun recent(limit: Int): List<Pair<String, String>> { val out = mutableListOf<Pair<String, String>>(); readableDatabase.rawQuery("SELECT speaker, body FROM memories ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c -> while (c.moveToNext()) out.add(c.getString(0) to c.getString(1)) }; return out.reversed() }
    fun relevant(query: String, limit: Int): List<Pair<String, String>> { val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.take(8); if (terms.isEmpty()) return recent(limit); val where = terms.joinToString(" OR ") { "lower(body) LIKE ? OR lower(tags) LIKE ? OR lower(summary) LIKE ?" }; val args = terms.flatMap { listOf("%$it%", "%$it%", "%$it%") }.toTypedArray(); val out = mutableListOf<Pair<String, String>>(); readableDatabase.rawQuery("SELECT speaker, body FROM memories WHERE $where ORDER BY created DESC LIMIT ?", args + limit.toString()).use { c -> while (c.moveToNext()) out.add(c.getString(0) to c.getString(1)) }; return out.reversed() }
}

private class SecurePrefs(context: Context) { private val p = context.getSharedPreferences("darkman_settings", Context.MODE_PRIVATE); fun put(k: String, v: String) = p.edit().putString(k, v).apply(); fun get(k: String) = p.getString(k, "") ?: "" }
