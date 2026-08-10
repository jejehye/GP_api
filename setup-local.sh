#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.11
MAVEN_DIR="$PROJECT_DIR/.tools/apache-maven-$MAVEN_VERSION"

if ! /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    echo "[ERROR] JDK 17 is required. Install Temurin or Microsoft OpenJDK 17." >&2
    exit 1
fi

if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
    ARCHIVE="${TMPDIR:-/tmp}/apache-maven-$MAVEN_VERSION-bin.tar.gz"
    mkdir -p "$PROJECT_DIR/.tools"
    echo "[INFO] Downloading Apache Maven $MAVEN_VERSION..."
    curl -fL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o "$ARCHIVE"
    tar -xzf "$ARCHIVE" -C "$PROJECT_DIR/.tools"
fi

echo "[INFO] Local build environment is ready."
"$PROJECT_DIR/mvnw" -version
