@echo off
REM Start backend (port 9200) and frontend (port 9211) in separate cmd windows.
REM Each opens its own window so logs are visible; close the windows or use stop.bat.
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo  TechDebt Cleanup - start
echo ============================================

call :check_port 9200 Backend
if errorlevel 1 goto :busy
call :check_port 9211 Frontend
if errorlevel 1 goto :busy

echo.
echo Opening backend window  (Spring Boot on http://localhost:9200) ...
start "TechDebt Backend" cmd /k "cd /d %~dp0backend && echo Backend starting... && mvn spring-boot:run -Dspring-boot.run.profiles=local"

REM Give Maven a moment so its first log line doesn't interleave with the next echo.
ping -n 2 127.0.0.1 >nul

echo Opening frontend window (Vite on http://localhost:9211) ...
start "TechDebt Frontend" cmd /k "cd /d %~dp0frontend && echo Frontend starting... && npm run dev"

echo.
echo ============================================
echo  Services launching in separate windows.
echo.
echo    Frontend:   http://localhost:9211
echo    Backend:    http://localhost:9200
echo    Swagger:    http://localhost:9200/swagger-ui.html
echo    H2 console: http://localhost:9200/h2-console
echo.
echo  Use stop.bat to shut both services down.
echo ============================================
exit /b 0

:check_port
set "PORT=%~1"
set "NAME=%~2"
set "IN_USE_PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do (
    set "IN_USE_PID=%%a"
)
if defined IN_USE_PID (
    echo.
    echo Port %PORT% ^(%NAME%^) is already in use by PID !IN_USE_PID!.
    exit /b 1
)
exit /b 0

:busy
echo.
echo Run stop.bat first, then try again.
exit /b 1
