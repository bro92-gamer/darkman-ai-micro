# Build Darkman-AI in Android Studio

Install a current Android Studio release with the Android SDK, Android SDK Platform 35, Android SDK Build-Tools 35.0.0, and an embedded JDK. The project uses the standard Android Gradle plugin and Kotlin plugin, so no Node.js or Expo installation is required.

## Import

Choose **Open** in Android Studio and select the delivered `android-source/` directory. Accept the Gradle sync prompt. If Android Studio asks for a Gradle JDK, select the embedded JDK or another JDK 17/21 installation. The project has no external Maven repositories beyond Google and Maven Central.

## Build

Select the `app` configuration and choose **Build > Make Project** for a debug check. For a release artifact, choose **Build > Generate Signed Bundle/APK > APK**, or run the Gradle task `app > Tasks > build > assembleRelease`.

The release build is configured as follows:

| Setting | Value |
| --- | --- |
| Application ID | `com.darkman` |
| Namespace | `com.darkman` |
| Minimum SDK | API 21 |
| Target SDK | API 35; minimum SDK remains API 21 for legacy-device compatibility |
| Native ABI | `armeabi-v7a` only |
| Shrinking | R8 and resource shrinking enabled for release |
| UI | Platform XML widgets |
| Network | OkHttp |
| Local data | SQLite |

## Signing

The delivered project does not contain a production keystore. Android Studio can create a local keystore during the signed APK wizard. Store that keystore outside the repository and back it up securely. For a public release, use a stable production signing key and never put it in Git.

## Runtime smoke test

Install the release APK on an Android 5.0+ ARMv7 device or emulator. Check that the app opens, the local response appears with no key, a memory note can be added, Files can list storage after permission approval, Apps opens the launcher list, and settings can save a provider key. Add a Groq key and use **Test** to verify provider access. Prefix a message with `cloud:` only after setting the server `BASE_URL`.

## Troubleshooting

If an Android 5.x device rejects installation, verify that the APK was built from the release variant and that its manifest reports `minSdkVersion 21`. If storage listing is empty, grant the legacy storage permission from the system settings. If provider calls fail, check the key, device time, connectivity, and the provider's current quota. The app displays a local fallback when direct AI access is unavailable.

## References

[1]: https://developer.android.com/studio/projects "Android Studio project overview"
[2]: https://developer.android.com/guide/topics/manifest/uses-sdk "Android SDK version documentation"
[3]: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization "Android app optimization"
