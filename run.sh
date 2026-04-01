#!/usr/bin/env sh
set -eu

printf '\n'
printf '%s\n' '================================================'
printf '%s\n' '       DATAOPS PLATFORM - LAUNCHING MONOLITH'
printf '%s\n' '================================================'
printf '\n'

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

JAR_PATH="dataops-platform-monolith/target/dataops-platform-monolith-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
  printf '%s\n' '[INFO] JAR not found - building the project...'

  if [ -x "./mvnw" ]; then
    ./mvnw clean install -DskipTests
  elif command -v mvn >/dev/null 2>&1; then
    mvn clean install -DskipTests
  else
    printf '%s\n' '[ERROR] Neither ./mvnw nor mvn was found.'
    printf '%s\n' '[ERROR] Install Maven or add the Maven Wrapper to this repository.'
    exit 1
  fi

  printf '%s\n' '[INFO] Build completed successfully.'
  printf '\n'
fi

printf '%s\n' '[INFO] Starting DataOps Platform Monolith...'
printf '%s\n' '[INFO] Swagger UI : http://localhost:8080/swagger-ui.html'
printf '%s\n' '[INFO] Actuator   : http://localhost:8080/actuator'
printf '\n'

exec java -Xmx2g -XX:+UseG1GC -jar "$JAR_PATH"
