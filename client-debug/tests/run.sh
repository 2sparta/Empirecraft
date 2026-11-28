#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
sources="$root/src/main/java/ua/empirecraft/clientdebug"
if command -v javac >/dev/null; then
    compiler=(javac --release 17)
else
    compiler=(java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17)
fi
"${compiler[@]}" -encoding UTF-8 -d "$temp/classes" \
    "$sources/Timings.java" "$sources/FlightProfile.java" "$sources/Session.java" "$root/tests/ReportProbe.java"
java -cp "$temp/classes" ua.empirecraft.clientdebug.ReportProbe "$temp/reports"
