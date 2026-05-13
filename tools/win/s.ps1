$ErrorActionPreference = 'Stop'

$ScriptVersion = 's.ps1 runtime-env-v2'
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

$taskName = 'PrinterHub'
$runEnvPath = 'C:\printerhub\data\run.env'
$envMap = Read-RunEnv -Path $runEnvPath

$databaseFile = $null
$apiPort = '18080'

if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

try {
    schtasks /End /TN $taskName | Out-Null
    Write-Host "Requested Task Scheduler stop for '$taskName'"
}
catch {
    Write-Host "Scheduled task '$taskName' was not running or could not be ended through schtasks."
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    $_.CommandLine -and (
        $_.CommandLine -match 'printer-hub\.jar' -or
        $_.CommandLine -match 'printerhub\.bat' -or
        $_.CommandLine -match 'printerhub-task\.cmd'
    )
}

if ($databaseFile) {
    $escapedDatabaseFile = [regex]::Escape($databaseFile)
    $filtered = $targets | Where-Object { $_.CommandLine -match $escapedDatabaseFile }
    if ($filtered.Count -gt 0) {
        $targets = $filtered
    }
}

if (-not $targets) {
    Write-Host "No PrinterHub process found."
}
else {
    foreach ($proc in $targets) {
        try {
            Stop-Process -Id $proc.ProcessId -Force
            Write-Host "Stopped PrinterHub PID $($proc.ProcessId)"
        }
        catch {
            Fail "Failed to stop PrinterHub PID $($proc.ProcessId): $($_.Exception.Message)"
        }
    }
}

$stopped = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1

    $remaining = Get-CimInstance Win32_Process | Where-Object {
        $_.CommandLine -and (
            $_.CommandLine -match 'printer-hub\.jar' -or
            $_.CommandLine -match 'printerhub\.bat' -or
            $_.CommandLine -match 'printerhub-task\.cmd'
        )
    }

    if ($databaseFile) {
        $escapedDatabaseFile = [regex]::Escape($databaseFile)
        $filteredRemaining = $remaining | Where-Object { $_.CommandLine -match $escapedDatabaseFile }
        if ($filteredRemaining.Count -gt 0) {
            $remaining = $filteredRemaining
        }
    }

    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 2
        $healthStillUp = ($resp.StatusCode -eq 200)
    }
    catch {
        $healthStillUp = $false
    }

    if (($remaining.Count -eq 0) -and (-not $healthStillUp)) {
        $stopped = $true
        break
    }
}

if (-not $stopped) {
    Fail "PrinterHub stop was requested, but runtime still appears active or locked."
}

Write-Host "Health endpoint no longer reachable on port $apiPort"
Write-Host "PrinterHub stopped successfully."