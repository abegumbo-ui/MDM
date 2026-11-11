@echo off
rem Double-click to remove LockGuard completely (asks for administrator permission).
rem Runs Remove-LockGuard.ps1 from the same folder. Removes only what LockGuard added.
net session >nul 2>&1
if %errorlevel% neq 0 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Remove-LockGuard.ps1"
pause
