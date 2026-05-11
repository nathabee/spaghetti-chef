$ErrorActionPreference = 'Stop'

function Get-JavaMajorVersion {
    try {
        $out = & java -version 2>&1
        if (-not $out) {
            return $null
        }

        $firstLine = $out | Select-Object -First 1
        if ($firstLine -match '"(?<version>\d+)(\.\d+)?(\.\d+)?.*"') {
            return [int]$Matches.version
        }

        return $null
    }
    catch {
        return $null
    }
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

$javaMajor = Get-JavaMajorVersion
$appDir = 'C:\ph\app'
$jarPath = Join-Path $appDir 'printer-hub.jar'
$launcherPath = Join-Path $appDir 'printerhub.bat'
$runEnvPath = 'C:\ph\data\run.env'
$envMap = Read-RunEnv -Path $runEnvPath

$apiPort = '18080'
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe'
} | Where-Object {
    $_.CommandLine -and (
        $_.CommandLine -match 'printer-hub\.jar' -or
        $_.CommandLine -match 'printerhub\.bat'
    )
}

Write-Host "Java major version: $javaMajor"
Write-Host "App dir exists: $(Test-Path -LiteralPath $appDir)"
Write-Host "Jar exists: $(Test-Path -LiteralPath $jarPath)"
Write-Host "Launcher exists: $(Test-Path -LiteralPath $launcherPath)"
Write-Host "run.env exists: $(Test-Path -LiteralPath $runEnvPath)"
Write-Host "Configured API port: $apiPort"
Write-Host "PrinterHub process count: $($targets.Count)"

if ($targets.Count -gt 0) {
    foreach ($proc in $targets) {
        Write-Host "PID=$($proc.ProcessId) NAME=$($proc.Name)"
    }
}

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 3
    Write-Host "Health endpoint: HTTP $($resp.StatusCode)"
}
catch {
    Write-Host "Health endpoint: not reachable"
}