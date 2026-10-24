#!/bin/sh
# Minimal POSIX Gradle wrapper launcher.
DIR=$(cd "$(dirname "$0")" && pwd)
exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"