#!/usr/bin/env sh
set -e

choose_java17() {
  for CANDIDATE in \
    "/root/.local/share/mise/installs/java/17.0.2" \
    "/root/.local/share/mise/installs/java/17.0" \
    "/root/.local/share/mise/installs/java/17" \
    "/usr/lib/jvm/temurin-17-jdk-amd64" \
    "/usr/lib/jvm/java-17-openjdk-amd64" \
    "/usr/lib/jvm/java-17-openjdk" \
    "$JAVA_HOME"
  do
    if [ -n "$CANDIDATE" ] && [ -x "$CANDIDATE/bin/java" ]; then
      VERSION=$($CANDIDATE/bin/java -version 2>&1 | head -n 1)
      echo "$VERSION" | grep -q 'version "17' || continue
      export JAVA_HOME="$CANDIDATE"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done
  return 1
}

choose_java17 || echo "[gradlew] WARNING: JDK 17 not found, using system Java"

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  for SDK_CANDIDATE in \
    "$HOME/Android/Sdk" \
    "$HOME/android-sdk" \
    "/opt/android-sdk" \
    "/opt/android/sdk" \
    "/usr/lib/android-sdk"
  do
    if [ -d "$SDK_CANDIDATE/platforms" ] || [ -d "$SDK_CANDIDATE/cmdline-tools" ]; then
      export ANDROID_HOME="$SDK_CANDIDATE"
      export ANDROID_SDK_ROOT="$SDK_CANDIDATE"
      break
    fi
  done
fi

if [ ! -f "local.properties" ]; then
  SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ]; then
    ESCAPED=$(printf '%s' "$SDK_DIR" | sed 's/\\/\\\\/g')
    printf 'sdk.dir=%s\n' "$ESCAPED" > local.properties
    echo "[gradlew] Generated local.properties with sdk.dir=$SDK_DIR"
  fi
fi

exec gradle "$@"
