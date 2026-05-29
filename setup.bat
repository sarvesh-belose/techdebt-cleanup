@echo off
REM First-run setup: prompts for project + tokens, writes dev.config.yml.
REM Delegates to setup.ps1 for proper interactive prompts.
setlocal
cd /d "%~dp0"

where powershell >nul 2>&1
if errorlevel 1 (
    echo PowerShell is required for setup.bat but was not found on PATH.
    echo Edit dev.config.yml manually (copy from dev.config.example.yml).
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
exit /b %errorlevel%
