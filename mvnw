#!/bin/sh
set -eu
task_project=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  task_java="$JAVA_HOME/bin/java"
else
  task_java=java
fi
exec "$task_java" "-Dmaven.multiModuleProjectDirectory=$task_project" \
  -classpath "$task_project/.mvn/wrapper/maven-wrapper.jar" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
