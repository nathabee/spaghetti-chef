$ErrorActionPreference = 'Stop'

$ScriptVersion = 'v.ps1 runtime-env-v1'
Write-Host "Running $ScriptVersion"

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

$runEnvPath = 'C:\printerhub\data\run.env'
$appDir = 'C:\printerhub\app'
$jarPath = Join-Path $appDir 'printer-hub.jar'
$launcherPath = Join-Path $appDir 'printerhub.bat'
$taskCmdPath = 'C:\printerhub\bin\printerhub-task.cmd'
$stdoutLog = 'C:\printerhub\log\printerhub-out.log'
$stderrLog = 'C:\printerhub\log\printerhub-err.log'
$startLog = 'C:\printerhub\log\start.log'

$envMap = Read-RunEnv -Path $runEnvPath

$apiPort = '18080'
$serialPort = 'COM3'
$mode = 'real'
$databaseFile = 'printerhub.db'
$javaCommand = ''
$cameraStorageDirectory = ''

if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}
if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}
if ($envMap.ContainsKey('PRINTERHUB_JAVA')) {
    $javaCommand = $envMap['PRINTERHUB_JAVA']
}
if ($envMap.ContainsKey('PRINTERHUB_CAMERA_STORAGE_DIRECTORY')) {
    $cameraStorageDirectory = $envMap['PRINTERHUB_CAMERA_STORAGE_DIRECTORY']
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe' -or $_.Name -eq 'cmd.exe') -and
    $_.CommandLine -and
    ($_.CommandLine -match 'printer-hub\.jar' -or $_.CommandLine -match 'printerhub\.bat' -or $_.CommandLine -match 'printerhub-task\.cmd')
}

if ($databaseFile) {
    $escapedDatabaseFile = [regex]::Escape($databaseFile)
    $filtered = $targets | Where-Object { $_.CommandLine -match $escapedDatabaseFile }
    if ($filtered.Count -gt 0) {
        $targets = $filtered
    }
}

Write-Host "run.env exists: $(Test-Path -LiteralPath $runEnvPath)"
Write-Host "App dir exists: $(Test-Path -LiteralPath $appDir)"
Write-Host "Jar exists: $(Test-Path -LiteralPath $jarPath)"
Write-Host "Launcher exists: $(Test-Path -LiteralPath $launcherPath)"
Write-Host "Task wrapper exists: $(Test-Path -LiteralPath $taskCmdPath)"
Write-Host "Configured API port: $apiPort" 
Write-Host "Configured database file: $databaseFile"
Write-Host "Configured camera storage: $cameraStorageDirectory"
Write-Host "Configured Java command: $javaCommand"
Write-Host "PrinterHub-related process count: $($targets.Count)"

if (Test-Path -LiteralPath $runEnvPath) {
    Write-Host ""
    Write-Host "--- run.env ---"
    Get-Content -LiteralPath $runEnvPath
}

try {
    $taskInfo = schtasks /Query /TN PrinterHub /FO LIST /V
    Write-Host ""
    Write-Host "--- Task Scheduler ---"
    $taskInfo
}
catch {
    Write-Host ""
    Write-Host "--- Task Scheduler ---"
    Write-Host "Scheduled task 'PrinterHub' not found."
}

if ($targets.Count -gt 0) {
    Write-Host ""
    Write-Host "--- Matching processes ---"
    foreach ($proc in $targets) {
        Write-Host "PID=$($proc.ProcessId) NAME=$($proc.Name)"
        Write-Host "CMD=$($proc.CommandLine)"
        Write-Host ""
    }
}

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 3
    Write-Host "--- Health ---"
    Write-Host "HTTP $($resp.StatusCode)"
    Write-Host $resp.Content
}
catch {
    Write-Host ""
    Write-Host "--- Health ---"
    Write-Host "not reachable"
}

if (Test-Path -LiteralPath $startLog) {
    Write-Host ""
    Write-Host "--- start.log ---"
    Get-Content -LiteralPath $startLog -Tail 40
}

if (Test-Path -LiteralPath $stdoutLog) {
    Write-Host ""
    Write-Host "--- printerhub-out.log ---"
    Get-Content -LiteralPath $stdoutLog -Tail 80
}

if (Test-Path -LiteralPath $stderrLog) {
    Write-Host ""
    Write-Host "--- printerhub-err.log ---"
    Get-Content -LiteralPath $stderrLog -Tail 80
}
