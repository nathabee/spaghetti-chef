$ErrorActionPreference = 'Stop'

$ScriptVersion = 'r.ps1 runtime-env-v2'
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

function Get-JavaCommand {
    param([hashtable]$EnvMap)

    if ($EnvMap.ContainsKey('PRINTERHUB_JAVA')) {
        $configured = $EnvMap['PRINTERHUB_JAVA']
        if (-not [string]::IsNullOrWhiteSpace($configured) -and (Test-Path -LiteralPath $configured)) {
            return $configured
        }
    }

    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $cmd) {
        if ($cmd.Source) {
            return $cmd.Source
        }
        if ($cmd.Path) {
            return $cmd.Path
        }
    }

    return $null
}

function Get-JavaMajorVersion {
    param([string]$JavaCommand)

    if ([string]::IsNullOrWhiteSpace($JavaCommand)) {
        return $null
    }

    $quotedJava = '"' + $JavaCommand + '"'
    $cmdLine = "$quotedJava -version 2>&1"
    $out = cmd /c $cmdLine
    if (-not $out) {
        return $null
    }

    $lines = @($out | ForEach-Object { "$_" })
    $firstLine = $lines | Select-Object -First 1

    if ($firstLine -match '"(?<version>\d+)(\.\d+)?(\.\d+)?.*"') {
        return [int]$Matches.version
    }

    return $null
}

$taskName = 'PrinterHub'
$runEnvPath = 'C:\printerhub\data\run.env'
$startLog = 'C:\printerhub\log\start.log'
$envMap = Read-RunEnv -Path $runEnvPath

$apiPort = '18080'
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

$databaseFile = 'C:\printerhub\data\printerhub.db'
if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $databaseFile = $envMap['PRINTERHUB_DATABASE_FILE']
}

$javaCommand = Get-JavaCommand -EnvMap $envMap
$javaMajor = Get-JavaMajorVersion -JavaCommand $javaCommand

if ($null -eq $javaCommand) {
    Fail "Java was not found. Set PRINTERHUB_JAVA in C:\printerhub\data\run.env"
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. javaCommand='$javaCommand' javaMajor='$javaMajor'"
}

if (-not (Test-Path -LiteralPath 'C:\printerhub\app\printerhub.bat')) {
    Fail "Launcher not found: C:\printerhub\app\printerhub.bat"
}

if (-not (Test-Path -LiteralPath 'C:\printerhub\log')) {
    New-Item -ItemType Directory -Force -Path 'C:\printerhub\log' | Out-Null
}

try {
    schtasks /Query /TN $taskName | Out-Null
}
catch {
    Fail "Scheduled task '$taskName' not found. Run C:\printerhub\bin\t.ps1 once as the intended task owner."
}

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"[$stamp] start requested task=$taskName apiPort=$apiPort databaseFile=$databaseFile java=$javaCommand" | Add-Content -LiteralPath $startLog

Write-Host "Starting scheduled task '$taskName'"
schtasks /Run /TN $taskName | Out-Null

$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
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
    "[$stamp] health endpoint never became reachable on port $apiPort" | Add-Content -LiteralPath $startLog
    Fail "PrinterHub start failed. Health endpoint not reachable on port $apiPort"
}

$stableChecks = 0
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$apiPort/health" -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) {
            $stableChecks++
        }
    }
    catch {
    }
}

if ($stableChecks -lt 8) {
    "[$stamp] health endpoint was transient stableChecks=$stableChecks/10 port=$apiPort" | Add-Content -LiteralPath $startLog
    Fail "PrinterHub start was not stable. Health endpoint did not remain reachable long enough on port $apiPort"
}

"[$stamp] health endpoint stable stableChecks=$stableChecks/10 port=$apiPort" | Add-Content -LiteralPath $startLog

Write-Host "PrinterHub started successfully through Task Scheduler."
Write-Host "API port: $apiPort"
Write-Host "Database file: $databaseFile"
Write-Host "Java command: $javaCommand"