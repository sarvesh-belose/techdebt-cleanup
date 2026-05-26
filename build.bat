@echo off
REM Build backend + frontend.
REM Usage:
REM   build.bat         - full build, runs tests
REM   build.bat fast    - skip tests
setlocal
cd /d "%~dp0"

set MVN_FLAGS=
if /i "%~1"=="fast" set MVN_FLAGS=-DskipTests

echo ============================================
echo  TechDebt Cleanup - build
echo  Tests: %MVN_FLAGS%
echo ============================================
echo.

echo === [1/2] Backend (Maven) ===
pushd backend
call mvn -B clean package %MVN_FLAGS%
if errorlevel 1 (
    echo.
    echo *** Backend build FAILED ***
    popd
    exit /b 1
)
popd
echo.

echo === [2/2] Frontend (Vite) ===
pushd frontend
if not exist node_modules (
    echo Installing npm dependencies...
    call npm install --no-audit --no-fund
    if errorlevel 1 (
        echo.
        echo *** npm install FAILED ***
        popd
        exit /b 1
    )
)
call npm run build
if errorlevel 1 (
    echo.
    echo *** Frontend build FAILED ***
    popd
    exit /b 1
)
popd
echo.

echo ============================================
echo  All builds complete.
echo  Backend jar:      backend\target\*.jar
echo  Frontend bundle:  frontend\dist\
echo ============================================
exit /b 0
