#!/bin/sh
set -e
# Generate Gradle wrapper files locally. This requires a local Gradle installation.
# Run: sh install-wrapper.sh
gradle wrapper --gradle-version 8.5
echo "Gradle wrapper generated. Now you can run ./gradlew assembleDebug"
