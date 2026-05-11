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

if (-not (Test-Path -LiteralPath $launcher)) {
    Fail "Launcher not found: $launcher"
}

$envMap = Read-RunEnv -Path $runEnvPath

if ($envMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
    $env:PRINTERHUB_DATABASE_FILE = $envMap['PRINTERHUB_DATABASE_FILE']
}

$apiPort = $null
if ($envMap.ContainsKey('PRINTERHUB_API_PORT')) {
    $apiPort = $envMap['PRINTERHUB_API_PORT']
}

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"$stamp starting PrinterHub from $launcher" | Add-Content -LiteralPath $startLog

$argumentList = @('/c', "`"$launcher`"")
if ($apiPort) {
    $argumentList += @('COM3', 'real', $apiPort)
}

Start-Process -FilePath 'cmd.exe' `
    -ArgumentList $argumentList `
    -WorkingDirectory $appDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

Write-Host "PrinterHub start command launched."