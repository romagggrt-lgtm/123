#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
MAVEN_ARGS=(-B -f lor/pom.xml)
if [[ -n "${MAVEN_REPO:-}" ]]; then MAVEN_ARGS+=("-Dmaven.repo.local=$MAVEN_REPO"); fi
if [[ "${OFFLINE:-0}" == 1 ]]; then MAVEN_ARGS+=(-o); fi
"${MAVEN_BIN:-mvn}" "${MAVEN_ARGS[@]}" verify
python3 tools/build_lor_pack.py
python3 tools/package_lor.py
