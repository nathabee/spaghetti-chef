$ErrorActionPreference = 'Stop'

$ScriptVersion = 's.ps1 runtime-env-v1'
Write-Host "Running $ScriptVersion"

function Fail {
    param([string]$Message)
    Write-Error "[$ScriptVersion] $Message"
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

$runEnvPath = 'C:\ph\data\run.env'
$envMap = Read-RunEnv -Path $runEnvPath

$databaseFile = $null
$apiPort = '18080'

if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and
    $_.CommandLine -and
    ($_.CommandLine -match 'printer-hub\.jar')
}

if ($databaseFile) {
    $escapedDatabaseFile = [regex]::Escape($databaseFile)
    $targets = $targets | Where-Object {
        $_.CommandLine -match $escapedDatabaseFile
    }
}

if (-not $targets) {
    Write-Host "No PrinterHub process found."
    exit 0
}

foreach ($proc in $targets) {
    try {
        Stop-Process -Id $proc.ProcessId -Force
        Write-Host "Stopped PrinterHub PID $($proc.ProcessId)"
    }
    catch {
        Fail "Failed to stop PrinterHub PID $($proc.ProcessId): $($_.Exception.Message)"
    }
}

Start-Sleep -Seconds 2

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 2
    if ($resp.StatusCode -eq 200) {
        Fail "Process stop was requested, but health endpoint is still reachable on port $apiPort"
    }
}
catch {
    Write-Host "Health endpoint no longer reachable on port $apiPort"
}