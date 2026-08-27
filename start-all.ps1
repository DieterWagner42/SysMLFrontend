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
# npm is only ever touched in the second (staticDir-unset) mode, and even there this script checks
# it actually exists first - found live: forgetting to set staticDir on a machine with no Node/npm
# at all made this script try to launch npm.cmd anyway ("das gibt es auf dem Zielrechner nicht!"),
# failing with a cryptic native error instead of a clear one. Now it fails fast with an actionable
# message instead.
#
# Usage: powershell -File start-all.ps1 [path\to\config.ini]   (passed through to backend\start.bat)

param(
    [string]$ConfigPath
)

$ErrorActionPreference = "Stop"

$RootDir = $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$NodeDirFallback = "C:\Program Files\nodejs"

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

# Resolves npm.cmd via PATH first (same order as backend\start.bat's own `where java` check), then
# falls back to the hardcoded dev-machine install location. Returns $null if neither has it - the
# caller must check for that instead of assuming npm is always there.
function Find-Npm {
    $onPath = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $fallback = Join-Path $NodeDirFallback "npm.cmd"
    if (Test-Path $fallback) { return $fallback }
    return $null
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

# ── Frontend (Vite dev server - normal dev-machine workflow, needs Node/npm) ────────────

$npmPath = Find-Npm
if (-not $npmPath) {
    Write-Host "ERROR: npm not found (checked PATH and '$NodeDirFallback')," -ForegroundColor Red
    Write-Host "and the backend isn't configured to serve the frontend itself either."
    Write-Host ""
    Write-Host "This machine has no usable Node/npm, so the frontend dev server can't be started"
    Write-Host "this way. Instead: on a machine that DOES have Node, run 'npm run build' in"
    Write-Host "frontend\ to produce frontend\dist, copy that folder here, then set"
    Write-Host "[Server] staticDir in backend\config.ini to point at it (see its own comment there)"
    Write-Host "and run this script again - the backend will then serve the frontend itself."
    Read-Host "Press Enter to close"
    exit 1
}
$NodeDir = Split-Path $npmPath -Parent

$nodeModules = Join-Path $FrontendDir "node_modules"
if (-not (Test-Path $nodeModules)) {
    Write-Host "Installing frontend dependencies (first run only)..."
    Push-Location $FrontendDir
    $env:PATH = "$NodeDir;$env:PATH"
    & $npmPath install
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
