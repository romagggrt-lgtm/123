#!/usr/bin/env bash
# For this Codex workspace; installs development tools outside the checkout.
set -euo pipefail
mkdir -p /workspace/tools
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
if [[ ! -x /workspace/tools/apache-maven-3.9.11/bin/mvn ]]; then
  base=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz
  curl -fL "$base" -o "$scratch/maven.tgz"
  curl -fL "$base.sha512" -o "$scratch/maven.sha512"
  python3 - "$scratch" <<'PY'
import sys,pathlib,hashlib
p=pathlib.Path(sys.argv[1]); assert hashlib.sha512((p/'maven.tgz').read_bytes()).hexdigest()==(p/'maven.sha512').read_text().split()[0]
PY
  tar -xzf "$scratch/maven.tgz" -C /workspace/tools
fi
if [[ ! -x /workspace/tools/jdk-21.0.12.1/bin/javac ]]; then
  base=https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz
  curl -fL "$base" -o "$scratch/jdk.tgz"
  curl -fL "$base.sha256" -o "$scratch/jdk.sha256"
  python3 - "$scratch" <<'PY'
import sys,pathlib,hashlib
p=pathlib.Path(sys.argv[1]); assert hashlib.sha256((p/'jdk.tgz').read_bytes()).hexdigest()==(p/'jdk.sha256').read_text().split()[0]
PY
  # New machines may receive a newer Java 21 update. Install under the configured tool path.
  mkdir "$scratch/jdk"
  tar -xzf "$scratch/jdk.tgz" --strip-components=1 -C "$scratch/jdk"
  test ! -e /workspace/tools/jdk-21.0.12.1
  mv "$scratch/jdk" /workspace/tools/jdk-21.0.12.1
fi
/workspace/tools/jdk-21.0.12.1/bin/javac -version
/workspace/tools/apache-maven-3.9.11/bin/mvn -version
command -v python3 >/dev/null
command -v ffmpeg >/dev/null
