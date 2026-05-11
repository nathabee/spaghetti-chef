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
            $map[$parts[0].Trim()] = $parts[1].Trim()
        }
    }

    return $map
}

$appDir = 'C:\ph\app'
$launcher = Join-Path $appDir 'printerhub.bat'
$runEnvPath = 'C:\ph\data\run.env'
$logDir = 'C:\ph\log'
$stdoutLog = Join-Path $logDir 'printerhub-out.log'
$stderrLog = Join-Path $logDir 'printerhub-err.log'
$startLog = Join-Path $logDir 'start.log'

if (-not (Test-Path -LiteralPath $appDir)) {
    Fail "App directory not found: $appDir"
}
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
    $env:PRINTERHUB_JAVA = $envMap['PRINTERHUB_JAVA']
}

$env:PRINTERHUB_DATABASE_FILE = $databaseFile

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"[$stamp] launcher=$launcher" | Add-Content -LiteralPath $startLog
"[$stamp] serialPort=$serialPort mode=$mode apiPort=$apiPort databaseFile=$databaseFile" | Add-Content -LiteralPath $startLog
"[$stamp] javaOverride=$($env:PRINTERHUB_JAVA)" | Add-Content -LiteralPath $startLog

$process = Start-Process -FilePath $launcher `
    -ArgumentList @($serialPort, $mode, $apiPort) `
    -WorkingDirectory $appDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Start-Sleep -Seconds 3

if ($process.HasExited) {
    "[$stamp] process exited early with code $($process.ExitCode)" | Add-Content -LiteralPath $startLog
    Fail "PrinterHub exited immediately. Check C:\ph\log\printerhub-out.log and C:\ph\log\printerhub-err.log"
}

"[$stamp] started PID=$($process.Id)" | Add-Content -LiteralPath $startLog
Write-Host "PrinterHub started. PID=$($process.Id)"
Write-Host "Serial port: $serialPort"
Write-Host "Mode: $mode"
Write-Host "API port: $apiPort"
Write-Host "Database file: $databaseFile"