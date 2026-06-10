#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT"
SIGNED_KEYSTORE="${SIGNED_KEYSTORE:-$REPO_ROOT/.ci/release-keystore.jks}"
SIGNED_STORE_PASSWORD="${SIGNED_STORE_PASSWORD:-android}"
SIGNED_KEY_ALIAS="${SIGNED_KEY_ALIAS:-androiddebugkey}"
SIGNED_KEY_PASSWORD="${SIGNED_KEY_PASSWORD:-android}"
mkdir -p "$(dirname "$SIGNED_KEYSTORE")"
./tools/ci/prepare_android_env.sh
if [[ ! -f "$SIGNED_KEYSTORE" ]]; then
  keytool -genkeypair -v -storetype JKS -keystore "$SIGNED_KEYSTORE" -storepass "$SIGNED_STORE_PASSWORD" -alias "$SIGNED_KEY_ALIAS" -keypass "$SIGNED_KEY_PASSWORD" -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Vectras CI,O=Vectras,C=US"
fi
./gradlew --no-daemon clean :app:assembleRelease -Psigning_mode=unsigned -PCI_INTERNAL_VALIDATION=true
UNSIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/app-release-unsigned.snapshot.apk"
TMP_UNSIGNED_APK="$(mktemp "${TMPDIR:-/tmp}/app-release-unsigned.snapshot.XXXXXX.apk")"
UNSIGNED_SRC=""
for candidate in \
  "$REPO_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk" \
  "$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"; do
  if [[ -f "$candidate" ]]; then
    UNSIGNED_SRC="$candidate"
    break
  fi
done
[[ -n "$UNSIGNED_SRC" ]] || { echo "APK unsigned não encontrado após assembleRelease" >&2; exit 1; }
cp -f "$UNSIGNED_SRC" "$TMP_UNSIGNED_APK"
./gradlew --no-daemon :app:assembleRelease -Psigning_mode=signed -PciRelease=true \
  -Pandroid.injected.signing.store.file="$SIGNED_KEYSTORE" \
  -Pandroid.injected.signing.store.password="$SIGNED_STORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias="$SIGNED_KEY_ALIAS" \
  -Pandroid.injected.signing.key.password="$SIGNED_KEY_PASSWORD" \
  -PCI_INTERNAL_VALIDATION=true \
  -PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true
cp -f "$TMP_UNSIGNED_APK" "$UNSIGNED_APK"
rm -f "$TMP_UNSIGNED_APK"
SIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
APKSIGNER_BIN="$(command -v apksigner || true)"
if [[ -z "$APKSIGNER_BIN" ]]; then
  SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$SDK_ROOT" && -f "$REPO_ROOT/local.properties" ]]; then
    SDK_ROOT="$(sed -nE 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$REPO_ROOT/local.properties" | tail -n1)"
  fi
  if [[ -n "$SDK_ROOT" ]]; then
    APKSIGNER_BIN="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"
  fi
fi
[[ -n "$APKSIGNER_BIN" && -x "$APKSIGNER_BIN" ]] || { echo "apksigner não encontrado no PATH nem no Android SDK" >&2; exit 1; }
for apk in "$UNSIGNED_APK" "$SIGNED_APK"; do
  [[ -f "$apk" ]] || { echo "APK não encontrado: $apk" >&2; exit 1; }
  if [[ "$apk" == "$SIGNED_APK" ]]; then
    "$APKSIGNER_BIN" verify --verbose "$apk"
  else
    echo "$(basename "$apk") unsigned snapshot: assinatura ignorada por contrato"
  fi
  echo "$(basename "$apk") size=$(stat -c%s "$apk")"
  unzip -l "$apk" | awk '/lib\/arm64-v8a\// {a64=1} /lib\/armeabi-v7a\// {a32=1} END{if(!a64||!a32){exit 1}}'
done
sha256sum "$UNSIGNED_APK" "$SIGNED_APK"
python3 - <<'PY'
import os
u='app/build/outputs/apk/release/app-release-unsigned.snapshot.apk'
s='app/build/outputs/apk/release/app-release.apk'
print(f'diff_bytes={os.path.getsize(s)-os.path.getsize(u)}')
PY
