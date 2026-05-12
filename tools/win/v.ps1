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
            $key = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"')
            $map[$key] = $value
        }
    }

    return $map
}

$runEnvPath = 'C:\ph\data\run.env'
$envMap = Read-RunEnv -Path $runEnvPath

$apiPort = '18080'
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

Write-Host "run.env: $runEnvPath"
Write-Host "Configured API port: $apiPort"

try {
    $taskInfo = schtasks /Query /TN PrinterHub /FO LIST /V
    Write-Host ""
    Write-Host "--- Task Scheduler ---"
    $taskInfo
}
catch {
    Write-Host "Scheduled task 'PrinterHub' not found."
}

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 3
    Write-Host ""
    Write-Host "--- Health ---"
    Write-Host "HTTP $($resp.StatusCode)"
    Write-Host $resp.Content
}
catch {
    Write-Host ""
    Write-Host "--- Health ---"
    Write-Host "not reachable"
}