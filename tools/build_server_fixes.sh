#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d /workspace/tools/jdk-21.0.12.1 ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
fix_maven=$(command -v mvn || true)
if [[ -z "$fix_maven" ]]; then fix_maven=/workspace/tools/apache-maven-3.9.11/bin/mvn; fi
fix_args=()
if [[ "${SERVER_FIXES_OFFLINE:-0}" == 1 ]]; then fix_args+=(-o); fi
if [[ -n "${SERVER_FIXES_MAVEN_CACHE:-}" ]]; then fix_args+=("-Dmaven.repo.local=$SERVER_FIXES_MAVEN_CACHE"); fi
"$fix_maven" -B "${fix_args[@]}" -f gold/pom.xml verify
"$fix_maven" -B "${fix_args[@]}" -f inventorycache/pom.xml verify
python3 tools/package_server_fixes.py
