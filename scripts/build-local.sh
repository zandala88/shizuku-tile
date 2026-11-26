#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
shizuku_tools="${SHIZUKU_BUILD_TOOLS:-$HOME/.cache/my-funds-tools}"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$shizuku_tools/jdk/Contents/Home" ]; then
  export JAVA_HOME="$shizuku_tools/jdk/Contents/Home"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -x "$shizuku_tools/gradle-8.9/bin/gradle" ]; then
  exec "$shizuku_tools/gradle-8.9/bin/gradle" "$@"
fi
exec ./gradlew "$@"
