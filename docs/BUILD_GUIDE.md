# Darkman-AI Advanced Build Guide

## Android Studio and GitHub Actions

Open `android-source/` in Android Studio with SDK Platform 35, Build Tools 35, JDK 17 or newer, and an Android NDK installed through SDK Manager. The Gradle project invokes CMake and builds the vendored llama.cpp source for `armeabi-v7a` and `arm64-v8a`. Use the normal debug or release Gradle task when you are ready to build; this source-only update does not include an APK.

The native build uses portable settings (`GGML_NATIVE=OFF`, OpenMP disabled, OpenSSL disabled, and subprocess support disabled). Do not remove the CMake external-native-build configuration or the `llama.cpp` directory from the source archive.

## Local AI test after building

Install the debug build on a device with adequate free storage and RAM. Select **Local AI (Qwen)**, send a short Arabic and English prompt, and wait for the one-time model download. Stop and restart the app during a download to verify resume behavior. Confirm that a `.gguf` model appears under the app's private `files/models` directory, that a response is generated without network access after download, and that `adb logcat -s DarkmanLocalAI` shows token count, seconds, tokens per second, and UTF-8 byte metrics. If the device cannot load the model, use the catalog fallback hook in `LocalModelCatalog` and document the selected GGUF's size and SHA-256.

## Permissions and tools

Tap **Permissions** to request legacy camera/storage permissions on supported Android versions and to open the optional overlay settings. Android 13 and newer should use the Storage Access Framework for a user-selected project directory; broad `MANAGE_EXTERNAL_STORAGE` access is declared only as a compatibility hook and should not be granted unless the deployment policy permits it. File and Godot tools operate within a canonical project root. Termux commands are intentionally limited to one command without shell chaining.

## Gemini environment variables

For the optional cloud server, copy `.env.example` and configure the server-side key. Use `GEMINI_API_KEY` for the API key, `GEMINI_MODEL` for the main model (default `gemini-2.0-flash`), and `GEMINI_LITE_MODEL` for the economical model (default `gemini-2.0-flash-lite`). The Android app stores the user-provided Gemini key locally and routes **Google Gemini Auto** using the same short-prompt heuristic. **Google Gemini Flash-Lite** forces the economical model. Provider quotas and model availability are controlled by Google and may change; update the environment values rather than hard-coding a new model in the UI.

## Source validation checklist

Before merging, run Kotlin/Gradle lint or assemble in Android Studio/GitHub Actions, verify both native ABIs are produced, check the manifest icon references, run the local model smoke test, verify Arabic output, test memory retrieval with an older note, and exercise each agent tool against a temporary project directory. Do not commit model weights, API keys, keystores, or generated APKs.
