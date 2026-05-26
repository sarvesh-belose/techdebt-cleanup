@echo off
REM Stop backend (9200) and frontend (9211) by killing whatever's listening.
REM Best-effort. Some processes may be owned by a different elevation;
REM if a kill fails with "Access is denied" the script reports it and continues.
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo  TechDebt Cleanup - stop
echo ============================================

call :kill_port 9200 Backend
call :kill_port 9211 Frontend

echo.
echo Done. Use start.bat to bring them back up.
exit /b 0

:kill_port
set "PORT=%~1"
set "NAME=%~2"
set "FOUND=0"
echo.
echo Stopping %NAME% on port %PORT% ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do (
    set "FOUND=1"
    echo   killing PID %%a
    taskkill /F /PID %%a >nul 2>&1
    if errorlevel 1 (
        echo   could not kill PID %%a ^(access denied or already gone^)
    )
)
if "!FOUND!"=="0" echo   nothing listening on %PORT%
exit /b 0
