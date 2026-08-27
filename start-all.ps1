# Starts the whole app: backend (Java, http://localhost:4567) in its own console
# window, then the frontend (Vite dev server, http://localhost:5173) in another,
# then opens the browser. Each half keeps its own window so the backend's own
# "type stop and press Enter" console and the frontend's dev-server log both
# stay visible and independently closable.
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

# ── Frontend ─────────────────────────────────────────────────────────

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
