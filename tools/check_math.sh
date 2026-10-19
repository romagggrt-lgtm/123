#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/math-checks
javac --release 21 -d target/math-checks src/main/java/dev/shah/FlightMath.java src/test/java/dev/shah/FlightMathChecks.java
java -cp target/math-checks dev.shah.FlightMathChecks
