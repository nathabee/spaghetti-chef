$ErrorActionPreference = 'Stop'

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
$jarPath = Join-Path $appDir 'printer-hub.jar'
$launcherPath = Join-Path $appDir 'printerhub.bat'
$runEnvPath = 'C:\ph\data\run.env'
$stdoutLog = 'C:\ph\log\printerhub-out.log'
$stderrLog = 'C:\ph\log\printerhub-err.log'
$startLog = 'C:\ph\log\start.log'

$envMap = Read-RunEnv -Path $runEnvPath

$apiPort = '18080'
$serialPort = 'COM3'
$mode = 'real'
$databaseFile = 'printerhub.db'

if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}
if ($envMap.ContainsKey('PRINTERHUB_SERIAL_PORT')) {
    $serialPort = $envMap['PRINTERHUB_SERIAL_PORT']
}
if ($envMap.ContainsKey('PRINTERHUB_MODE')) {
    $mode = $envMap['PRINTERHUB_MODE']
}
if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe' -or $_.Name -eq 'cmd.exe') -and
    $_.CommandLine -and
    ($_.CommandLine -match 'printer-hub\.jar' -or $_.CommandLine -match 'printerhub\.bat')
}

Write-Host "App dir exists: $(Test-Path -LiteralPath $appDir)"
Write-Host "Jar exists: $(Test-Path -LiteralPath $jarPath)"
Write-Host "Launcher exists: $(Test-Path -LiteralPath $launcherPath)"
Write-Host "run.env exists: $(Test-Path -LiteralPath $runEnvPath)"
Write-Host "Configured API port: $apiPort"
Write-Host "Configured serial port: $serialPort"
Write-Host "Configured mode: $mode"
Write-Host "Configured database file: $databaseFile"
Write-Host "PrinterHub-related process count: $($targets.Count)"

if ($targets.Count -gt 0) {
    foreach ($proc in $targets) {
        Write-Host "PID=$($proc.ProcessId) NAME=$($proc.Name)"
        Write-Host "CMD=$($proc.CommandLine)"
    }
}

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 3
    Write-Host "Health endpoint: HTTP $($resp.StatusCode)"
    Write-Host $resp.Content
}
catch {
    Write-Host "Health endpoint: not reachable"
}

if (Test-Path -LiteralPath $startLog) {
    Write-Host ""
    Write-Host "--- start.log ---"
    Get-Content -LiteralPath $startLog -Tail 20
}

if (Test-Path -LiteralPath $stdoutLog) {
    Write-Host ""
    Write-Host "--- printerhub-out.log ---"
    Get-Content -LiteralPath $stdoutLog -Tail 50
}

if (Test-Path -LiteralPath $stderrLog) {
    Write-Host ""
    Write-Host "--- printerhub-err.log ---"
    Get-Content -LiteralPath $stderrLog -Tail 50
}