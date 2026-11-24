#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/share/android-sdk}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
PLATFORM_JAR="$ANDROID_HOME/platforms/android-34/android.jar"

if command -v mise >/dev/null 2>&1; then
  JAVA_HOME="${JAVA_HOME:-$(mise where java@temurin-17.0.21.1+2 2>/dev/null || mise where java 2>/dev/null || true)}"
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$BUILD_DIR/res_compiled"

echo "[1/6] Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$BUILD_DIR/res_compiled/res.zip"

echo "[2/6] Linking resources & generating R.java..."
"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/gen" \
  -o "$BUILD_DIR/unaligned.apk" \
  "$BUILD_DIR/res_compiled/res.zip"

echo "[3/6] Compiling Java sources..."
javac -source 11 -target 11 \
  -classpath "$PLATFORM_JAR" \
  -d "$BUILD_DIR/obj" \
  $(find "$PROJECT_DIR/src" "$BUILD_DIR/gen" -name "*.java")

echo "[4/6] Dexing bytecode with d8..."
"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM_JAR" \
  --min-api 26 \
  --output "$BUILD_DIR/dex" \
  $(find "$BUILD_DIR/obj" -name "*.class")

echo "[5/6] Packaging & aligning APK..."
cp "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/packaged.apk"
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/packaged.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/packaged.apk" "$BUILD_DIR/aligned.apk"

KEYSTORE="$PROJECT_DIR/essential-debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  echo "Generating signing keystore..."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass android \
    -alias essential \
    -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Essential Launcher, O=Home, C=US" >/dev/null 2>&1
fi

echo "[6/6] Signing APK..."
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias essential \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD_DIR/essential-launcher.apk" \
  "$BUILD_DIR/aligned.apk"

echo "Built: $BUILD_DIR/essential-launcher.apk ($(du -h "$BUILD_DIR/essential-launcher.apk" | cut -f1))"

if [ "${1:-}" = "--install" ]; then
  DEVICE="${2:-}"
  if [ -n "$DEVICE" ]; then
    adb -s "$DEVICE" install -r "$BUILD_DIR/essential-launcher.apk"
  else
    adb install -r "$BUILD_DIR/essential-launcher.apk"
  fi
fi
