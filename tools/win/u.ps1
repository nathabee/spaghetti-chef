param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Owner = 'nathabee',
    [string]$Repo = 'printer-hub'
)

$ErrorActionPreference = 'Stop'

$ScriptVersion = 'u.ps1 runtime-env-v1'
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

$root = 'C:\ph'
$appDir = "$root\app"
$tmpDir = "$root\tmp"
$relDir = "$root\rel"
$logDir = "$root\log"
$runEnvPath = "$root\data\run.env"
$updateLog = Join-Path $logDir 'update.log'

$envMap = Read-RunEnv -Path $runEnvPath
$javaCommand = Get-JavaCommand -EnvMap $envMap
$javaMajor = Get-JavaMajorVersion -JavaCommand $javaCommand

if ($null -eq $javaCommand) {
    Fail "Java was not found. Set PRINTERHUB_JAVA in C:\ph\data\run.env"
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. javaCommand='$javaCommand' javaMajor='$javaMajor'"
}

if (-not (Test-Path -LiteralPath $logDir)) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
}
if (-not (Test-Path -LiteralPath $tmpDir)) {
    New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
}
if (-not (Test-Path -LiteralPath $relDir)) {
    New-Item -ItemType Directory -Force -Path $relDir | Out-Null
}
if (-not (Test-Path -LiteralPath 'C:\ph\bin\s.ps1')) {
    Fail "Stop script not found: C:\ph\bin\s.ps1"
}
if (-not (Test-Path -LiteralPath 'C:\ph\bin\r.ps1')) {
    Fail "Start script not found: C:\ph\bin\r.ps1"
}

$assetName = "printer-hub-$Version-windows.zip"
$tagName = "v$Version"
$zipPath = Join-Path $relDir $assetName
$extractDir = Join-Path $tmpDir ("extract-" + $Version)
$downloadUrl = "https://github.com/$Owner/$Repo/releases/download/$tagName/$assetName"

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"[$stamp] update start version=$Version tag=$tagName url=$downloadUrl java=$javaCommand" | Add-Content -LiteralPath $updateLog

Write-Host "Using run.env: $runEnvPath"
Write-Host "Detected Java command: $javaCommand"
Write-Host "Detected Java major version: $javaMajor"
Write-Host "Downloading $downloadUrl"

Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath

Write-Host "Stopping current PrinterHub"
& 'C:\ph\bin\s.ps1'

if (Test-Path -LiteralPath $extractDir) {
    Remove-Item -LiteralPath $extractDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $extractDir | Out-Null

Write-Host "Extracting $zipPath"
Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

$entries = Get-ChildItem -LiteralPath $extractDir
if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) {
    $sourceDir = $entries[0].FullName
}
else {
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

New-Item -ItemType Directory -Force -Path $appDir | Out-Null
Copy-Item -Path (Join-Path $sourceDir '*') -Destination $appDir -Recurse -Force

Write-Host "App directory content after copy:"
Get-ChildItem -LiteralPath $appDir | Select-Object Name, Length, LastWriteTime

Write-Host "Starting updated PrinterHub"
& 'C:\ph\bin\r.ps1'

"[$stamp] update success version=$Version tag=$tagName asset=$assetName" | Add-Content -LiteralPath $updateLog
Write-Host "Update complete for version $Version"