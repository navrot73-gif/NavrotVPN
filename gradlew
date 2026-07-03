#!/usr/bin/env sh
set -e
# Lightweight wrapper runner: executes gradle-wrapper.jar if present,
# otherwise instructs how to generate the wrapper locally.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR_PATH" ]; then
  exec java -jar "$JAR_PATH" "$@"
else
  echo "Gradle wrapper JAR not found. Run sh install-wrapper.sh to generate wrapper (requires local Gradle)." 1>&2
  exit 1
fi
