# Darkman-AI Micro Edition — Autonomy Report

The project brief explicitly delegated architecture and implementation decisions. The following choices were made to meet the constraints without requesting clarification.

| Decision | Rationale |
| --- | --- |
| Replace Expo/React Native with native Kotlin/XML | The requested API 21, ARMv7, under-25-MB, low-RAM target is better served by a platform-only client. |
| Use `com.darkman` | This applies the requested package rebrand without an ambiguous suffix. |
| Keep target SDK 28 while compiling with SDK 35 | It permits the requested legacy storage behavior on older devices while allowing a current build toolchain. |
| Use direct provider calls in the client | The brief requires adding a Groq key and receiving a response. Direct calls make that path testable without cloud setup. |
| Keep cloud calls opt-in via `cloud:` | Offline features remain independent of server availability. |
| Use environment-only cloud secrets | This prevents credentials from entering the APK or Git repository. |
| Implement GitHub Contents API file commits | Free cloud hosts are often ephemeral; API commits are more reliable than assuming a local Git binary and persistent disk. |
| Include Render, Oracle, Codespaces, and Replit instructions | The brief names multiple free deployment and development targets, so all are documented. |
| Do not include an APK | The brief explicitly requests source code only. |

The final package intentionally does not claim that external provider quotas, hosting uptime, or cloud free-tier capacity are guaranteed. Those are controlled by the respective providers and can change.
