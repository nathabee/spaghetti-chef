$ErrorActionPreference = 'Stop'

$ScriptVersion = 't.ps1 task-runtime-env-v1'
Write-Host "Running $ScriptVersion"

function Fail {
    param([string]$Message)
    Write-Error "[$ScriptVersion] $Message"
    exit 1
}

$taskName = 'PrinterHub'
$appDir = 'C:\ph\app'
$launcher = Join-Path $appDir 'printerhub.bat'
$runEnvPath = 'C:\ph\data\run.env'
$logDir = 'C:\ph\log'
$taskCmd = 'C:\ph\bin\printerhub-task.cmd'

if (-not (Test-Path -LiteralPath $launcher)) {
    Fail "Launcher not found: $launcher"
}

if (-not (Test-Path -LiteralPath $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

@"
@echo off
setlocal EnableExtensions EnableDelayedExpansion

if not exist C:\ph\data\run.env (
  echo run.env not found: C:\ph\data\run.env >> C:\ph\log\printerhub-err.log
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("C:\ph\data\run.env") do (
  set "K=%%A"
  set "V=%%B"
  if not "!K!"=="" (
    if /i not "!K:~0,1!"=="#" (
      set "!K!=!V!"
    )
  )
)

cd /d C:\ph\app
call C:\ph\app\printerhub.bat >> C:\ph\log\printerhub-out.log 2>> C:\ph\log\printerhub-err.log
"@ | Set-Content -LiteralPath $taskCmd -Encoding ASCII

$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$startTime = (Get-Date).AddMinutes(5).ToString('HH:mm')

Write-Host "Registering scheduled task '$taskName' for user $currentUser at dummy time $startTime"
Write-Host "Using runtime config from: $runEnvPath"
Write-Host "Generated task wrapper: $taskCmd"

schtasks /Create /F /TN $taskName /SC ONCE /ST $startTime /TR $taskCmd /RU $currentUser | Out-Null

Write-Host "Scheduled task '$taskName' registered successfully."