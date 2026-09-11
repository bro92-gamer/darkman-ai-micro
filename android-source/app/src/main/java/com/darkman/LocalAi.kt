package com.darkman

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object LocalAiNative {
    init { System.loadLibrary("darkman-native") }
    external fun loadModel(path: String, contextSize: Int, threads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int): String
    external fun freeModel()
}

data class LocalModel(val name: String, val url: String, val sha256: String, val sizeBytes: Long)

object LocalModelCatalog {
    // Official Qwen2.5-0.5B-Instruct-GGUF Q4_K_M artifact. Keep this out of the APK.
    val primary = LocalModel(
        "Qwen2.5-0.5B-Instruct-Q4_K_M",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
        "",
        491000000L
    )
    // ARMv7 fallback hook: a smaller GGUF can be configured here if device RAM is insufficient.
    val armv7Fallback = LocalModel(
        "TinyLlama-1.1B-Chat-v1.0-Q2_K",
        "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q2_K.gguf?download=true",
        "",
        700000000L
    )
}

class ModelDownloader(private val context: Context) {
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build()
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun ensure(model: LocalModel, progress: (Long, Long) -> Unit = { _, _ -> }): File {
        val target = File(dir, model.name + ".gguf")
        val part = File(target.path + ".part")
        var current = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(model.url).apply { if (current > 0) addHeader("Range", "bytes=$current-") }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) error("Model download HTTP ${response.code}")
            val total = current + (response.body?.contentLength() ?: -1L)
            if (response.code == 200 && current > 0) { current = 0; part.delete() }
            RandomAccessFile(part, "rw").use { file ->
                file.seek(current)
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(64 * 1024); var done = current; var read: Int
                    while (input.read(buffer).also { read = it } >= 0) { if (read == 0) continue; file.write(buffer, 0, read); done += read; progress(done, total) }
                } ?: error("Empty model response")
            }
        }
        val digest = sha256(part)
        if (model.sha256.isNotBlank() && !digest.equals(model.sha256, true)) { part.delete(); error("Model SHA-256 mismatch") }
        if (model.sizeBytes > 0 && part.length() != model.sizeBytes) error("Model size mismatch: ${part.length()} bytes; expected ${model.sizeBytes}")
        if (part.length() < 100_000_000L) error("Downloaded model is unexpectedly small")
        if (target.exists()) target.delete()
        check(part.renameTo(target)) { "Unable to finalize model download" }
        Log.i("DarkmanLocalAI", "Verified model ${target.name} sha256=$digest")
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(1024 * 1024); var n: Int; while (input.read(buffer).also { n = it } > 0) digest.update(buffer, 0, n) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
