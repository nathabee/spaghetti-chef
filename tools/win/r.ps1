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
        if ($cmd.Source) { return $cmd.Source }
        if ($cmd.Path) { return $cmd.Path }
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
$runEnvPath = 'C:\ph\data\run.env'
$envMap = Read-RunEnv -Path $runEnvPath
$javaCommand = Get-JavaCommand -EnvMap $envMap
$javaMajor = Get-JavaMajorVersion -JavaCommand $javaCommand

if ($null -eq $javaCommand) {
    Fail "Java was not found. Set PRINTERHUB_JAVA in C:\ph\data\run.env"
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. javaCommand='$javaCommand' javaMajor='$javaMajor'"
}

& "C:\ph\bin\t.ps1"

schtasks /Run /TN $taskName | Out-Null

$apiPort = '18080'
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

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
    Fail "PrinterHub start failed. Health endpoint not reachable on port $apiPort"
}

Write-Host "PrinterHub started successfully through Task Scheduler."