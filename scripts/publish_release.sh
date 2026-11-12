#!/usr/bin/env bash
set -euo pipefail

# Publica uma release do EuPlantei no GitHub com o APK e o update.json que o
# app consulta. Uso: scripts/publish_release.sh "notas da versão"
#
# O token é lido de GH_TOKEN ou de ~/.config/opencode/gh_token. Ele nunca é
# gravado no repositório nem no .git/config.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GH="${GH:-gh}"
command -v "$GH" >/dev/null || { echo "gh CLI não encontrado" >&2; exit 1; }

TOKEN="${GH_TOKEN:-}"
if [[ -z "$TOKEN" && -f "$HOME/.config/opencode/gh_token" ]]; then
  TOKEN="$(cat "$HOME/.config/opencode/gh_token")"
fi
[[ -n "$TOKEN" ]] || { echo "Defina GH_TOKEN" >&2; exit 1; }
export GH_TOKEN="$TOKEN"

OWNER="${EUPLANTEI_OWNER:-JabsDev}"
REPO="${EUPLANTEI_REPO:-EuPlantei}"
VERSION_CODE="$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts | head -1)"
VERSION_NAME="$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -1)"
TAG="v$VERSION_NAME"
NOTES="${1:-EuPlantei $VERSION_NAME}"

[[ -n "$VERSION_CODE" && -n "$VERSION_NAME" ]] || {
  echo "Não consegui ler versionCode/versionName" >&2
  exit 1
}

echo "Publicando $TAG (versionCode $VERSION_CODE)..."
JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk}" ./gradlew assembleRelease --console=plain

APK="app/build/outputs/apk/release/app-release.apk"
ASSET="EuPlantei-$VERSION_NAME.apk"
[[ -f "$APK" ]] || { echo "APK de release não encontrado" >&2; exit 1; }

SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
APK_URL="https://github.com/$OWNER/$REPO/releases/download/$TAG/$ASSET"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp "$APK" "$TMP/$ASSET"
NOTES="$NOTES" VERSION_CODE="$VERSION_CODE" VERSION_NAME="$VERSION_NAME" \
  APK_URL="$APK_URL" SHA="$SHA" python3 - "$TMP/update.json" <<'PY'
import json, os, sys
payload = {
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA"],
    "notes": os.environ["NOTES"],
}
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

if "$GH" release view "$TAG" --repo "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "Release $TAG já existe; atualizando assets..."
  "$GH" release upload "$TAG" "$TMP/$ASSET" "$TMP/update.json" \
    --repo "$OWNER/$REPO" --clobber
  "$GH" release edit "$TAG" --repo "$OWNER/$REPO" --notes "$NOTES"
else
  "$GH" release create "$TAG" "$TMP/$ASSET" "$TMP/update.json" \
    --repo "$OWNER/$REPO" --title "EuPlantei $VERSION_NAME" --notes "$NOTES"
fi

echo "OK: $APK_URL"
echo "SHA-256: $SHA"
