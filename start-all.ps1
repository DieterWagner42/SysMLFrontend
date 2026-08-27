# Starts the whole app. Two modes, auto-detected from the backend's own [Server] staticDir
# setting (see backend\config.ini) once it's up:
#   - staticDir unset (normal dev machine): also starts the frontend's Vite dev server on :5173
#     in its own console window, and opens that.
#   - staticDir set to a built frontend/dist (see backend\CLAUDE.md / config.ini's own comment) —
#     the backend serves the frontend itself at :4567, no Node/npm needed on this machine at all;
#     the frontend section below is skipped entirely and :4567 is opened directly. Requested live:
#     "ich kann auf dem Zielrechner nicht npm laufen lassen".
# Either way the backend gets its own console window, so its "type stop and press Enter" prompt
# stays visible and independently closable.
#
# Usage: powershell -File start-all.ps1 [path\to\config.ini]   (passed through to backend\start.bat)

param(
    [string]$ConfigPath
)

$ErrorActionPreference = "Stop"

$RootDir = $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$NodeDir = "C:\Program Files\nodejs"

function Test-Up([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 1 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-Up([string]$Url, [string]$Label, [int]$MaxRetries = 30) {
    for ($i = 0; $i -lt $MaxRetries; $i++) {
        if (Test-Up $Url) { return $true }
        Start-Sleep -Seconds 1
    }
    Write-Warning "$Label did not respond within $MaxRetries s - check its console window for errors."
    return $false
}

# ── Backend ──────────────────────────────────────────────────────────

$backendClasses = Join-Path $BackendDir "out\com\sysmlfrontend\backend\BootstrapApp.class"
if (-not (Test-Path $backendClasses)) {
    Write-Host "ERROR: $BackendDir\out is missing compiled classes." -ForegroundColor Red
    Write-Host "Compile the backend first - see backend\CLAUDE.md for the javac command."
    Read-Host "Press Enter to close"
    exit 1
}

if (Test-Up "http://localhost:4567/api/status") {
    Write-Host "Backend already running at http://localhost:4567 - skipping."
} else {
    Write-Host "Starting backend..."
    $backendArgs = if ($ConfigPath) { $ConfigPath } else { "" }
    Start-Process -WorkingDirectory $BackendDir -FilePath "cmd.exe" `
        -ArgumentList "/k", "start.bat $backendArgs" `
        -WindowStyle Normal

    Write-Host "Waiting for backend to come online..."
    if (Wait-Up "http://localhost:4567/api/status" "Backend") {
        Write-Host "Backend is up."
    }
}

# ── Does the backend already serve the frontend itself? ────────────────

$frontendServedByBackend = $false
try {
    $status = Invoke-WebRequest -Uri "http://localhost:4567/api/status" -UseBasicParsing -TimeoutSec 3 | ConvertFrom-Json
    $frontendServedByBackend = [bool]$status.frontendServed
} catch {
    # Backend didn't come up (already warned above) - fall through to the normal dev-server path.
}

if ($frontendServedByBackend) {
    Write-Host "Backend is serving the frontend directly (config.ini [Server] staticDir) - no separate frontend process needed."
    Start-Process "http://localhost:4567/"
    exit 0
}

# ── Frontend (Vite dev server - normal dev-machine workflow) ───────────

$nodeModules = Join-Path $FrontendDir "node_modules"
if (-not (Test-Path $nodeModules)) {
    Write-Host "Installing frontend dependencies (first run only)..."
    Push-Location $FrontendDir
    $env:PATH = "$NodeDir;$env:PATH"
    & "$NodeDir\npm.cmd" install
    Pop-Location
}

if (Test-Up "http://localhost:5173") {
    Write-Host "Frontend already running at http://localhost:5173 - skipping."
} else {
    Write-Host "Starting frontend..."
    Start-Process -WorkingDirectory $FrontendDir -FilePath "cmd.exe" `
        -ArgumentList "/k", "set PATH=$NodeDir;%PATH% && npm.cmd run dev" `
        -WindowStyle Normal

    Write-Host "Waiting for frontend dev server to come online..."
    if (Wait-Up "http://localhost:5173" "Frontend") {
        Write-Host "Frontend is up."
    }
}

Start-Process "http://localhost:5173"
