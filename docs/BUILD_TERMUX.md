# Build Darkman-AI in Termux

This guide builds the source project on an Android device or Linux shell. The supplied project includes a small `gradlew` launcher that downloads Gradle 8.7 on first use; Android SDK command-line tools remain the responsibility of the local environment.

## 1. Install prerequisites

In Termux, update packages and install Git, OpenJDK, unzip, and curl:

```sh
pkg update -y
pkg upgrade -y
pkg install -y git openjdk-17 curl unzip
java -version
```

Install Android command-line tools or use an existing SDK. Set the SDK variables in `~/.bashrc` or `~/.profile`:

```sh
export ANDROID_HOME="$PREFIX/opt/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

Install the packages required by this project. The exact package names can vary between Termux SDK distributions, so confirm availability with `sdkmanager --list`:

```sh
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
yes | sdkmanager --licenses
```

## 2. Copy the project

Extract the delivered archive and enter the Android project:

```sh
unzip Darkman-Micro-Final.zip
cd Darkman-Micro-Final/android-source
chmod +x gradlew ../scripts/build.sh
```

## 3. Build the release APK

Run the supplied build helper from the package root:

```sh
cd ..
./scripts/build.sh
```

Or call Gradle directly:

```sh
cd android-source
./gradlew assembleRelease
```

The output is `android-source/app/build/outputs/apk/release/app-release-unsigned.apk` unless you use Android Studio's signing wizard. This is a source project; the APK is generated locally and is intentionally not included in the delivered package.

## 4. Confirm the ABI and size

The app module sets `abiFilters "armeabi-v7a"`, `minSdk 21`, R8 shrinking, and resource shrinking. Confirm the assembled APK:

```sh
apkanalyzer apk summary app/build/outputs/apk/release/app-release-unsigned.apk
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep -E 'lib/armeabi-v7a|lib/arm64-v8a|lib/x86' || true
stat -c '%s bytes' app/build/outputs/apk/release/app-release-unsigned.apk
```

This Micro Edition has no native libraries, so it is ABI-neutral and runs on ARMv7; the Gradle module still pins `armeabi-v7a` to prevent future native additions from silently expanding the target. The target is less than 25 MB. A debug APK is not a valid size comparison because it is not shrunk.

## 5. Install and run

Enable USB debugging or install directly on the device:

```sh
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

On first launch, the app requests storage and camera permissions. The overlay permission is optional. Provider keys can be entered later in the settings area.

## Low-memory notes

The build uses a single Gradle worker and no large UI framework. If Termux is killed during a build, close other apps, set `org.gradle.jvmargs=-Xmx768m` in `android-source/gradle.properties`, and retry. The app itself performs network calls on a background thread and stores only a bounded local history.

## References

[1]: https://developer.android.com/studio/command-line "Android command-line tools"
[2]: https://developer.android.com/studio/build/shrink-code "Shrink, obfuscate, and optimize an app"
[3]: https://developer.android.com/guide/practices/page-sizes "Android app size guidance"
