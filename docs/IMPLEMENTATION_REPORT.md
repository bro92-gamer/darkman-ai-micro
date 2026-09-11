# Darkman-AI Advanced Implementation Report

## Real local AI with llama.cpp

The previous local fallback has been replaced by a real offline inference path. A pinned llama.cpp source checkout is vendored under `android-source/app/src/main/llama.cpp` and built through the Android Gradle external CMake build. `app/src/main/cpp/local_ai_jni.cpp` exposes `loadModel`, `generate`, and `freeModel` through JNI. The bridge loads a GGUF model with the llama.cpp C API, tokenizes UTF-8 input, decodes a bounded response with a sampler chain, detokenizes through the model vocabulary, and releases both context and model memory.

The target is Qwen2.5-0.5B-Instruct Q4_K_M, downloaded on first Local AI use into app-private storage rather than embedded in the APK. `ModelDownloader` resumes partial downloads with HTTP Range, writes to a `.part` file, checks SHA-256 when a catalog hash is supplied, rejects implausibly small artifacts, and atomically renames the verified file. The catalog includes an ARMv7 fallback hook; the current primary model is preferred because TinyLlama 1.1B is larger, not smaller. A smaller GGUF can be substituted in the catalog for devices that cannot hold the primary model.

The native bridge logs generated token count, elapsed seconds, tokens per second, and UTF-8 byte count through Android logcat. The Kotlin status surface reports that these metrics are logged. ARMv7 and ARM64 are both configured in Gradle; portable llama.cpp settings disable host-native instructions, OpenMP, OpenSSL, and subprocess features for Android.

## Dual Gemini routing

The provider selector now includes Google Gemini Auto and Google Gemini Flash-Lite. Auto routes short, daily prompts to `gemini-2.0-flash-lite` and sends longer or reasoning-heavy prompts to `gemini-2.0-flash`. The explicit Flash-Lite entry always selects the lite model. The cloud server implements the same policy through `modelMode=auto|lite|main`, with environment overrides `GEMINI_MODEL` and `GEMINI_LITE_MODEL`.

## Smart Memory Manager

SQLite schema version 2 adds `tags`, `summary`, and `created` fields. Short-term context is bounded to the most recent eight records. Long-term notes are explicitly tagged as facts, while chat, local, cloud, and agent outputs are also categorized. Before a provider or local prompt is built, `MemoryDb.relevant` splits the query into meaningful terms and retrieves only matching body, tag, or summary rows. This simple indexed-like keyword retrieval avoids dumping the entire transcript and works fully offline without a separate embedding model.

## Tool Calling and Agent Mode

`Tool` is the standard interface, returning a structured `ToolResult`. `FileSystemTool` reads, writes, lists, and searches inside a canonical project root. `TermuxTool` provides a constrained single-command shell hook in the app working directory and rejects shell chaining. `GodotTool` locates `project.godot`, scans `.gd` files, and reports common parse/error markers with file and line numbers. `AgentEngine` selects a tool from the prompt, executes it, displays its output, stores the result as memory, and optionally sends the result back to the selected cloud provider for a final response. This is intentionally conservative: destructive or broad shell operations are not silently executed.

## Icon integration

The supplied artwork was converted to launcher PNGs in `mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, and `mipmap-xxxhdpi`. Both `ic_launcher` and `ic_launcher_round` are present, and the manifest references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

## Scope and validation note

No APK is built in this update, as requested. Source validation checks the Kotlin/XML/CMake structure, resource presence, native API references, server syntax, and archive contents. Android Studio or GitHub Actions remains the authoritative native compilation environment because the sandbox does not include a configured Android SDK/NDK toolchain.
