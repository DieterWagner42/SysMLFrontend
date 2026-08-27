@echo off
rem Double-click-friendly wrapper - the real logic lives in start-all.ps1 (PowerShell handles
rem the backend/frontend startup + readiness-polling control flow far more reliably than batch's
rem own goto/label parsing, which turned out to be too fragile for this).
rem Usage: start-all.bat [path\to\config.ini]   (passed through to backend\start.bat)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
