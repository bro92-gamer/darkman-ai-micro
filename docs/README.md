# Darkman-AI Micro Edition

Darkman-AI Micro Edition is a deliberately small Android client plus an optional Node.js cloud bridge. The client is written in Kotlin with XML layouts, uses only platform widgets, SQLite, and OkHttp, and targets Android API 21 or newer. Its release configuration emits only `armeabi-v7a`, which is the 32-bit ARM ABI required for older devices.

The project was refactored from the supplied Expo/React Native source. The heavyweight JavaScript runtime and UI dependencies were removed so the client can be built directly in Android Studio or Termux. The app stores provider settings locally using an encrypted SharedPreferences implementation, while chat history and memory notes use SQLite.

## Repository layout

| Directory | Purpose |
| --- | --- |
| `android-source/` | Standalone Kotlin/XML Android Studio project. |
| `cloud-server/` | Express API for complex AI tasks and protected GitHub content commits. |
| `docs/` | Build, deployment, and API guides. |
| `scripts/` | One-command build and deployment helpers. |
| `reports/` | Implementation, validation, size, and autonomy reports. |

## Hybrid behavior

| Task | Runs locally | Uses cloud |
| --- | --- | --- |
| Chat history view and simple fallback replies | Yes | No |
| SQLite memory notes | Yes | No |
| File listing, copy, move, and delete | Yes | No |
| Installed-app launcher | Yes | No |
| Permission controls | Yes | No |
| Provider-backed chat | No | The app calls Groq, Gemini, or OpenRouter directly when a key is saved. |
| Large analysis and advanced reasoning | No | Prefix a prompt with `cloud:` and configure `BASE_URL`. |
| GitHub repository/file operations | No | Cloud API, protected by `GITHUB_TOKEN`. |

## AI providers

The Android settings screen supports Groq as the default provider, Google Gemini as a secondary provider, and OpenRouter as a tertiary provider. Keys are stored on-device in encrypted values and are never included in the source tree. The cloud server reads `GROQ_API_KEY`, `GEMINI_API_KEY`, and `OPENROUTER_API_KEY` from the deployment environment.

> Do not commit API keys, GitHub tokens, keystores, or `.env` files. Free provider accounts may impose their own rate limits and terms.

## Quick start

For Termux, run `./scripts/build.sh`. For Android Studio, open `android-source/` and sync the project. For the cloud bridge, copy `cloud-server/.env.example` to `.env`, fill at least one provider key, then run `npm install && npm start`.

The app does not require the cloud service for file browsing, memory, chat-history viewing, app launching, or permission management. Cloud access is opt-in through the `BASE_URL` field.

## References

[1]: https://developer.android.com/guide/topics/manifest/uses-sdk "Android SDK manifest documentation"
[2]: https://developer.android.com/guide/app-bundle/enable-app-optimization "Android app optimization and shrinking"
[3]: https://render.com/docs/free "Render free services documentation"
[4]: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm "Oracle Always Free resources"
[5]: https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace "GitHub Codespaces port forwarding"
