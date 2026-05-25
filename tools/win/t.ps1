$ErrorActionPreference = 'Stop'

$ScriptVersion = 't.ps1 task-runtime-env-v2'
Write-Host "Running $ScriptVersion"

function Fail {
    param([string]$Message)
    Write-Error "[$ScriptVersion] $Message"
    exit 1
}

$taskName = 'SpaghettiChef'
$appDir = 'C:\spaghettichef\app'
$launcher = Join-Path $appDir 'spaghettichef.bat'
$runEnvPath = 'C:\spaghettichef\data\run.env'
$logDir = 'C:\spaghettichef\log'
$taskCmd = 'C:\spaghettichef\bin\spaghettichef-task.cmd'

if (-not (Test-Path -LiteralPath $launcher)) {
    Fail "Launcher not found: $launcher"
}

if (-not (Test-Path -LiteralPath $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

@"
@echo off
setlocal EnableExtensions EnableDelayedExpansion

if not exist C:\spaghettichef\data\run.env (
  echo run.env not found: C:\spaghettichef\data\run.env >> C:\spaghettichef\log\spaghettichef-err.log
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("C:\spaghettichef\data\run.env") do (
  set "K=%%A"
  set "V=%%B"
  if not "!K!"=="" (
    if /i not "!K:~0,1!"=="#" (
      set "!K!=!V!"
    )
  )
)

cd /d C:\spaghettichef\app
call C:\spaghettichef\app\spaghettichef.bat >> C:\spaghettichef\log\spaghettichef-out.log 2>> C:\spaghettichef\log\spaghettichef-err.log
"@ | Set-Content -LiteralPath $taskCmd -Encoding ASCII

$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$startTime = (Get-Date).AddMinutes(5).ToString('HH:mm')

Write-Host "Using runtime config from: $runEnvPath"
Write-Host "Generated task wrapper: $taskCmd"

$taskExists = $false
try {
    schtasks /Query /TN $taskName | Out-Null
    $taskExists = $true
}
catch {
    $taskExists = $false
}

if ($taskExists) {
    Write-Host "Scheduled task '$taskName' already exists."
    Write-Host "Keeping existing task owner/principal unchanged."
    Write-Host "If you want a different owner, delete the task manually and re-run this script as that user."
    exit 0
}

Write-Host "Registering scheduled task '$taskName' for user $currentUser at dummy time $startTime"
schtasks /Create /F /TN $taskName /SC ONCE /ST $startTime /TR $taskCmd /RU $currentUser | Out-Null

Write-Host "Scheduled task '$taskName' registered successfully."