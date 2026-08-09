#!/usr/bin/env bash
set -euo pipefail

DEST="app/src/main/assets/js"
mkdir -p "$DEST"

fetch() {
  local url="$1" out="$2"
  echo "Fetching $(basename "$out")"
  curl --fail --location --silent --show-error --retry 3 --retry-delay 2 "$url" -o "$out"
  test -s "$out"
}

fetch "https://cdn.jsdelivr.net/npm/markdown-it@13.0.2/dist/markdown-it.min.js" "$DEST/markdown-it.min.js"
fetch "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/highlight.min.js" "$DEST/highlight.min.js"
fetch "https://cdn.jsdelivr.net/npm/mermaid@11.15.0/dist/mermaid.min.js" "$DEST/mermaid.min.js"

echo "Vendor renderer assets ready."
