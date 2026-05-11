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
            $value = $parts[1].Trim()
            $value = $value.Trim('"')
            $map[$key] = $value
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
$javaCommand = $null

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

$env:PRINTERHUB_DATABASE_FILE = $databaseFile
if (-not [string]::IsNullOrWhiteSpace($javaCommand)) {
    $env:PRINTERHUB_JAVA = $javaCommand
}

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"[$stamp] launcher=$launcher" | Add-Content -LiteralPath $startLog
"[$stamp] serialPort=$serialPort mode=$mode apiPort=$apiPort databaseFile=$databaseFile" | Add-Content -LiteralPath $startLog
"[$stamp] javaOverride=$javaCommand" | Add-Content -LiteralPath $startLog

$cmdArgs = "/c `"$launcher`" $serialPort $mode $apiPort"

$process = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList $cmdArgs `
    -WorkingDirectory $appDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

"[$stamp] started launcher PID=$($process.Id)" | Add-Content -LiteralPath $startLog

$healthy = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    }
    catch {
    }
}

if (-not $healthy) {
    "[$stamp] health endpoint did not become reachable on port $apiPort" | Add-Content -LiteralPath $startLog
    Fail "PrinterHub start failed. Health endpoint not reachable on port $apiPort. Check C:\ph\log\printerhub-out.log and C:\ph\log\printerhub-err.log"
}

"[$stamp] health endpoint reachable on port $apiPort" | Add-Content -LiteralPath $startLog
Write-Host "PrinterHub started successfully."
Write-Host "Serial port: $serialPort"
Write-Host "Mode: $mode"
Write-Host "API port: $apiPort"
Write-Host "Database file: $databaseFile"