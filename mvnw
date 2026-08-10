#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_HOME="$PROJECT_DIR/.tools/apache-maven-3.9.11"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 25)
        export JAVA_HOME
    fi
fi

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
    echo "[ERROR] Project-local Maven was not found: $MAVEN_HOME" >&2
    echo "Run ./setup-local.sh first." >&2
    exit 1
fi

exec "$MAVEN_HOME/bin/mvn" -Dmaven.repo.local="$PROJECT_DIR/.m2/repository" "$@"
