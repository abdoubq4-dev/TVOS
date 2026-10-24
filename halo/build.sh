#!/bin/bash
# Builds and signs Halo Home without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
# Set ANDROID_SDK (and JAVA_HOME for a JDK 17) if yours live elsewhere.
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_SDK:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
for f in "$BT/aapt2" "$AJ"; do [ -e "$f" ] || { echo "Missing $f - install platform 34 + build-tools 34 and set ANDROID_SDK"; exit 1; }; done
VERSION=${VERSION:-1}

rm -rf build && mkdir -p build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$AJ" --manifest AndroidManifest.xml -o build/base.apk \
    --java build/gen build/res.zip --min-sdk-version 31 --target-sdk-version 34 \
    --version-code "$VERSION" --version-name "1.$VERSION"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -d build/classes \
    $(find src build/gen -name '*.java')
"$BT/d8" --min-api 31 --release --lib "$AJ" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f halo.keystore ] || keytool -genkeypair -keystore halo.keystore -storepass android -keypass android \
    -alias halo -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Halo Home" >/dev/null 2>&1
"$BT/apksigner" sign --ks halo.keystore --ks-pass pass:android --out build/HaloHome.apk build/aligned.apk
ls -la build/HaloHome.apk
