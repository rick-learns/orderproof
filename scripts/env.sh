#!/usr/bin/env bash
# Resolves the pinned toolchain. Source it: `source scripts/env.sh`.
# Override any of these by exporting them before sourcing.
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
