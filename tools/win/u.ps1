param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Owner = 'nathabee',
    [string]$Repo = 'printer-hub'
)

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

    try {
        $out = & $JavaCommand -version 2>&1
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

$root = 'C:\ph'
$appDir = "$root\app"
$tmpDir = "$root\tmp"
$relDir = "$root\rel"
$logDir = "$root\log"
$runEnvPath = "$root\data\run.env"

$envMap = Read-RunEnv -Path $runEnvPath
$javaCommand = Get-JavaCommand -EnvMap $envMap
$javaMajor = Get-JavaMajorVersion -JavaCommand $javaCommand

if ($null -eq $javaCommand) {
    Fail "Java was not found. Set PRINTERHUB_JAVA in C:\ph\data\run.env"
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. Detected Java major version: $javaMajor"
}

$assetName = "printer-hub-$Version-windows.zip"
$zipPath = Join-Path $relDir $assetName
$extractDir = Join-Path $tmpDir ("extract-" + $Version)
$downloadUrl = "https://github.com/$Owner/$Repo/releases/download/$Version/$assetName"
$updateLog = Join-Path $logDir 'update.log'

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"$stamp update start version=$Version url=$downloadUrl java=$javaCommand" | Add-Content -LiteralPath $updateLog

if (Test-Path -LiteralPath $extractDir) {
    Remove-Item -LiteralPath $extractDir -Recurse -Force
}
New-Item -ItemType Directory -Path $extractDir | Out-Null

Write-Host "Downloading $downloadUrl"
Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath

Write-Host "Stopping current PrinterHub"
& "C:\ph\bin\s.ps1"

Write-Host "Extracting $zipPath"
Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

$entries = Get-ChildItem -LiteralPath $extractDir
if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) {
    $sourceDir = $entries[0].FullName
} else {
    $sourceDir = $extractDir
}

if (-not (Test-Path -LiteralPath (Join-Path $sourceDir 'printerhub.bat'))) {
    Fail "Extracted package does not contain printerhub.bat"
}
if (-not (Test-Path -LiteralPath (Join-Path $sourceDir 'printer-hub.jar'))) {
    Fail "Extracted package does not contain printer-hub.jar"
}

$backupDir = Join-Path $tmpDir ("app-backup-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $appDir) {
    Move-Item -LiteralPath $appDir -Destination $backupDir
}

New-Item -ItemType Directory -Path $appDir | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceDir '*') -Destination $appDir -Recurse -Force

Write-Host "Starting updated PrinterHub"
& "C:\ph\bin\r.ps1"

"$stamp update success version=$Version" | Add-Content -LiteralPath $updateLog
Write-Host "Update complete for version $Version"