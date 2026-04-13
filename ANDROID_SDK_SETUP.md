# Android SDK setup for local and CI builds

This project auto-detects the Android SDK in `./gradlew` and writes `local.properties` when possible.

## Supported auto-detect locations
- `$HOME/Android/Sdk`
- `$HOME/android-sdk`
- `/opt/android-sdk`
- `/opt/android/sdk`
- `/usr/lib/android-sdk`

## Manual setup (if auto-detect fails)
Create `local.properties` in the repository root:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Or export environment variables before running Gradle:

```bash
export ANDROID_HOME=/absolute/path/to/Android/Sdk
export ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk
./gradlew assembleDebug
```
