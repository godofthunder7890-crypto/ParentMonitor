#!/usr/bin/env sh
APP_HOME="`pwd -P`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
die () { echo; echo "$*"; echo; exit 1; } >&2
JAVACMD="java"
set -- -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
exec "$JAVACMD" $DEFAULT_JVM_OPTS "$@"
