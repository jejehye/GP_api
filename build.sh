#!/bin/bash
# Linux/Mac 빌드 스크립트
# 실행 전 PC에 JDK 25가 설치되어 있어야 합니다.
# 참고: launch4j는 .exe를 만들지만, Linux/Mac에서도 EXE 생성은 가능합니다.

set -e

echo "============================================"
echo " GP API Agent - 빌드 스크립트"
echo "============================================"
echo

if ! command -v java &> /dev/null; then
    echo "[ERROR] Java가 설치되어 있지 않습니다."
    echo "  https://adoptium.net/"
    exit 1
fi

if [ ! -x ./.tools/apache-maven-3.9.11/bin/mvn ]; then
    ./setup-local.sh
fi

echo "[INFO] Maven 빌드 시작..."
./mvnw clean package -DskipTests \
    -Dexec.skip=true \
    -Dlaunch4j.skip=true \
    -Dassembly.skipAssembly=true

echo
echo "============================================"
echo " 빌드 완료"
echo "============================================"
echo " - target/gp-api-1.0.0.jar : 실행 가능 JAR (모든 OS)"
echo " - Windows EXE/portable ZIP은 Windows에서 build.bat로 생성"
echo "============================================"
