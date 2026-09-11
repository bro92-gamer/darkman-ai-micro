# Darkman-AI Features

## Offline AI: Qwen2.5-0.5B

Darkman-AI can run locally with the Qwen2.5-0.5B-Instruct Q4_K_M GGUF model through llama.cpp. The model is downloaded only when **Local AI (Qwen)** is selected for the first time, can resume after interruption, and is stored outside the APK. It supports ARMv7 and ARM64 builds. Performance depends on device RAM, thermal state, and CPU; expect a small instruct model intended for short assistant answers rather than cloud-model parity. Logcat records memory-adjacent inference timing, generated tokens, tokens per second, and UTF-8 byte counts.

## Smart Memory

The memory manager retains a bounded short-term window and stores long-term notes with tags, summaries, and timestamps. Relevant memories are retrieved by keyword before a prompt is sent, which reduces context size, API usage, and local latency. Arabic and other UTF-8 text is retained as Unicode throughout the Kotlin and native inference paths.

## Agent and Tool Mode

Agent mode recognizes tasks that benefit from tools. It can list, read, write, and search files inside a safe project root; scan Godot projects and GDScript files for common error patterns; and invoke a constrained Termux/local shell hook for an explicitly requested single command. Tool output is shown in the UI, saved as agent memory, and can be passed to a cloud model for a final explanation or proposed fix. The architecture is extensible through the `Tool` interface.

## Cloud efficiency

Gemini Auto sends simple daily requests to Flash-Lite and reserves the main Flash model for complex prompts. Users can also select Flash-Lite directly. Groq and OpenRouter remain available through their existing OpenAI-compatible endpoints.

## Device access and privacy

Legacy storage permissions are requested only when needed. Android 13+ installations should prefer the Storage Access Framework for user-selected folders; the manifest also declares the legacy and broad-storage hooks needed by older devices. API keys remain in app-private settings, and local model files remain in app-private storage.
