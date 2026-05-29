@echo off
REM Append one new project to dev.config.yml.
REM Use when you join a new team and want to add their service to the platform.
REM
REM Equivalent to: setup.bat add
REM
REM After this completes, restart the backend (stop.bat then start.bat)
REM so DevBootstrapRunner picks up the new project. Or register via the UI
REM at http://localhost:9211 — no restart needed.
call "%~dp0setup.bat" add
exit /b %errorlevel%
