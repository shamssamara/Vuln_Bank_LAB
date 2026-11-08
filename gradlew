#!/bin/sh
# VulnBank local lab bootstrap. See gradle/wrapper/BootstrapWrapper.java for source.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
cd "$APP_HOME" || exit 1
exec "$JAVA" -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
