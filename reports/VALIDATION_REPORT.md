# Darkman-AI Micro Edition — Validation Report

## Completed checks

| Check | Result | Evidence |
| --- | --- | --- |
| Android Gradle configuration | Passed | `./gradlew --no-daemon tasks --all` completed successfully. |
| Android release compilation | Passed | `./gradlew --no-daemon clean assembleRelease` completed successfully with API 35 SDK tools. |
| Kotlin compiler | Passed | The release Kotlin compilation task completed. |
| Release lint | Passed | The final target SDK adjustment removed the fatal expired-target lint issue. |
| Release APK size | Passed | Generated unsigned release APK measured 179,116 bytes in the sandbox run. |
| ARMv7 target configuration | Passed by configuration | `minSdk 21` and `abiFilters "armeabi-v7a"`; the app contains no native libraries, so its bytecode is ABI-neutral. |
| Cloud JavaScript syntax | Passed | `node --check src/server.js`. |
| Cloud smoke test | Passed | `/health`, `/v1/ai/providers`, and invalid-request validation returned expected JSON responses. |
| Shell scripts | Passed | `sh -n scripts/build.sh` and `sh -n scripts/deploy.sh`. |
| Global rebrand scan | Passed | No pre-refactor brand identifiers or legacy package paths remain in the final tree. |

## Scope note

The sandbox does not contain a physical ARMv7 Android handset. The validation therefore proves that the project compiles, has the requested minimum SDK and ABI configuration, and packages a small release artifact. A final on-device smoke test should still be run on the user's Android 5.x ARMv7 hardware after signing and installation.

The APK generated during validation is not included in the deliverables, per the project brief.
