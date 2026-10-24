#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d /workspace/tools/jdk-21.0.12.1 ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
pantera_maven=$(command -v mvn || true)
if [[ -z "$pantera_maven" ]]; then pantera_maven=/workspace/tools/apache-maven-3.9.11/bin/mvn; fi
python3 tools/prepare_pantera_audio.py
python3 tools/build_pantera_pack.py
python3 tools/test_pantera_pack.py
pantera_maven_args=()
if [[ "${ZPANTERA_OFFLINE:-0}" == 1 ]]; then pantera_maven_args+=(-o); fi
if [[ -n "${ZPANTERA_MAVEN_CACHE:-}" ]]; then pantera_maven_args+=("-Dmaven.repo.local=$ZPANTERA_MAVEN_CACHE"); fi
# Remove only this module's generated output; no old plugin modules are rebuilt.
python3 - <<'PY'
from pathlib import Path
import shutil
generated = Path('pantera/target')
if generated.exists(): shutil.rmtree(generated)
PY
"$pantera_maven" -B "${pantera_maven_args[@]}" -f pantera/pom.xml verify
python3 tools/preview_pantera.py
python3 tools/package_pantera.py
