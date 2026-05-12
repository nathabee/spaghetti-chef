$ErrorActionPreference = 'Stop'

function Fail {
    param([string]$Message)
    Write-Error $Message
    exit 1
}

function Read-RunEnv {
    param([string]$Path)

    $map = @{}

    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }

    $lines = Get-Content -LiteralPath $Path
    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }
        if ($trimmed.StartsWith('#')) {
            continue
        }

        $parts = $trimmed -split '=', 2
        if ($parts.Count -eq 2) {
            $key = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"')
            $map[$key] = $value
        }
    }

    return $map
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

$envMap = Read-RunEnv -Path $runEnvPath

$databaseFile = 'printerhub.db'
$apiPort = '18080'
$serialPort = 'COM3'
$mode = 'real'
$javaCommand = ''

if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}
if ($envMap.ContainsKey('PRINTERHUB_SERIAL_PORT')) {
    $serialPort = $envMap['PRINTERHUB_SERIAL_PORT']
}
if ($envMap.ContainsKey('PRINTERHUB_MODE')) {
    $mode = $envMap['PRINTERHUB_MODE']
}
if ($envMap.ContainsKey('PRINTERHUB_JAVA')) {
    $javaCommand = $envMap['PRINTERHUB_JAVA']
}

@"
@echo off
setlocal
cd /d C:\ph\app
set PRINTERHUB_DATABASE_FILE=$databaseFile
set PRINTERHUB_JAVA=$javaCommand
call C:\ph\app\printerhub.bat $serialPort $mode $apiPort >> C:\ph\log\printerhub-out.log 2>> C:\ph\log\printerhub-err.log
"@ | Set-Content -LiteralPath $taskCmd -Encoding ASCII

schtasks /Create /F /TN $taskName /SC ONCE /ST 00:00 /RL HIGHEST /TR $taskCmd | Out-Null

Write-Host "Scheduled task '$taskName' is registered."
Write-Host "Task command: $taskCmd"