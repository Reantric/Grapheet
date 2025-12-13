#!/usr/bin/env sh
set -eu

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Missing Gradle wrapper jar: $WRAPPER_JAR" >&2
  exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
