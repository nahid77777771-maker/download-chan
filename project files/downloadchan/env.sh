#!/usr/bin/env bash
# Download-Chan build environment — source this before building.
#   source env.sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"      # Gradle 8.x needs JDK 17 (not system JDK 26)
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
echo "Download-Chan build env: JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"
java -version 2>&1 | head -1
