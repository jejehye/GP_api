@echo off
REM Windows 빌드 스크립트
REM 실행 전 PC에 JDK 17과 Maven이 설치되어 있어야 합니다.

echo ============================================
echo  GP API Agent - Windows 빌드 스크립트
echo ============================================
echo.

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven이 설치되어 있지 않거나 PATH에 없습니다.
    echo Maven 설치: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java가 설치되어 있지 않거나 PATH에 없습니다.
    echo JDK 17 설치: https://adoptium.net/
    pause
    exit /b 1
)

echo [INFO] Maven 빌드 시작...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] 빌드 실패
    pause
    exit /b 1
)

echo.
echo ============================================
echo  빌드 완료
echo ============================================
echo  - target\GpApi.exe     : 본 API 서버
echo  - target\MockGp.exe    : 테스트용 Mock GP
echo  - target\gp-api-1.0.0.jar : 실행 가능 JAR
echo ============================================
pause
