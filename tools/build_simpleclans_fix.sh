#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 1 ]]; then echo 'Usage: build_simpleclans_fix.sh /absolute/path/to/original/SimpleClans.jar' >&2; exit 2; fi
sc_original=$(realpath "$1")
cd "$(dirname "$0")/.."
if [[ -d /workspace/tools/jdk-21.0.12.1 ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
sc_maven=$(command -v mvn || true)
if [[ -z "$sc_maven" ]]; then sc_maven=/workspace/tools/apache-maven-3.9.11/bin/mvn; fi
sc_args=()
if [[ "${SIMPLECLANS_FIX_OFFLINE:-0}" == 1 ]]; then sc_args+=(-o); fi
if [[ -n "${SIMPLECLANS_FIX_MAVEN_CACHE:-}" ]]; then sc_args+=("-Dmaven.repo.local=$SIMPLECLANS_FIX_MAVEN_CACHE"); fi
"$sc_maven" -B "${sc_args[@]}" "-Dsimpleclans.original=$sc_original" -f simpleclans-fix/pom.xml test
python3 tools/package_simpleclans_fix.py "$sc_original"
