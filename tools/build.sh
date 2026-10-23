#!/usr/bin/env bash
# Current release builds Verity only; keep the existing Shahed artifact intact.
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash tools/build_verity.sh
