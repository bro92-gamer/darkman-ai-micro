# Darkman-AI Micro Edition — Implementation Report

## Scope

The supplied archive was a React Native/Expo application with a generated Android bridge and several JavaScript dependencies. It was not a small standalone Android Studio project. The refactor therefore created a new native Kotlin/XML client and a separate Node.js/Express server while retaining the user-visible feature intent.

## Android implementation

The Android client uses `com.darkman` as both namespace and application ID. It targets API 21, uses a platform `Activity` and XML layout, and filters the release artifact to `armeabi-v7a`. The app has no Compose, React Native, Expo, image pipeline, or large support-library dependency. SQLite stores chat history and memory notes. OkHttp handles provider and cloud requests on background threads.

The UI exposes five core paths: provider-backed chat with local fallback, memory notes, shared-storage file operations, an installed-app launcher, and permission/settings controls. The file command layer canonicalizes paths below external storage before copy, move, or recursive delete. The overlay permission is optional and is opened through system settings when available.

## Provider interface

Groq, Google Gemini, and OpenRouter are represented as one provider selector. Groq and OpenRouter use their OpenAI-compatible chat schema; Gemini uses `generateContent`. A selected key is encrypted into app-private SharedPreferences using a per-install salt and PBKDF2-derived AES/CBC key. The Android client never writes provider keys into source files.

## Cloud implementation

The server exposes `/health`, `/v1/ai/providers`, `/v1/ai/chat`, `/v1/ai/task`, and `/v1/github/operation`. It uses server-side environment variables, bounded request bodies, CORS configuration, and upstream error truncation. The GitHub operation endpoint can inspect a repository, read a file, and create/update a file through the GitHub Contents API, which produces a commit when authorized. It does not pretend to clone or push through a local working tree on an ephemeral free host.

## Deployment

Render Docker configuration, an Oracle VM path, a Codespaces devcontainer, a Docker Compose file, and a Replit-style run path are included. `BASE_URL` remains a runtime setting in the Android UI, so users can select a free provider hostname without changing code.

## Rebrand result

The final tree uses Darkman-AI naming throughout, including the `com.darkman` package, `darkman-ai-cloud` server package, `Darkman-AI` application label, environment names, and documentation. The original archive is not copied into the final deliverable, and a final case-sensitive legacy-brand scan is part of validation.
