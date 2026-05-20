param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Owner = 'nathabee',
    [string]$Repo = 'printer-hub'
)

$ErrorActionPreference = 'Stop'

$ScriptVersion = 'u.ps1 runtime-env-v3'
Write-Host "Running $ScriptVersion"

function Fail {
    param([string]$Message)
    Write-Error "[$ScriptVersion] $Message"
    exit 1
}

function Write-UpdateLog {
    param(
        [string]$Path,
        [string]$Message
    )

    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "[$stamp] $Message" | Add-Content -LiteralPath $Path
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

function Get-ConfiguredDatabaseFile {
    param(
        [hashtable]$EnvMap,
        [string]$Root
    )

    if ($EnvMap.ContainsKey('PRINTERHUB_DATABASE_FILE')) {
        $configured = $EnvMap['PRINTERHUB_DATABASE_FILE']
        if (-not [string]::IsNullOrWhiteSpace($configured)) {
            return $configured
        }
    }

    return Join-Path (Join-Path $Root 'data') 'printerhub.db'
}

function Assert-PersistentRuntimePaths {
    param(
        [string]$DatabaseFile,
        [string]$AppDir,
        [string]$DataDir
    )

    if ([string]::IsNullOrWhiteSpace($DatabaseFile)) {
        Fail "PRINTERHUB_DATABASE_FILE is empty. Refusing update because persistence location is undefined."
    }

    if (-not [System.IO.Path]::IsPathRooted($DatabaseFile)) {
        Fail "PRINTERHUB_DATABASE_FILE must be an absolute path. Current value: '$DatabaseFile'"
    }

    $dbFullPath = [System.IO.Path]::GetFullPath($DatabaseFile)
    $appFullPath = [System.IO.Path]::GetFullPath($AppDir)
    $dataFullPath = [System.IO.Path]::GetFullPath($DataDir)

    if ($dbFullPath.StartsWith($appFullPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        Fail "Database is inside the replaceable app directory: '$dbFullPath'. Move it to '$dataFullPath\printerhub.db' before updating."
    }

    if (-not $dbFullPath.StartsWith($dataFullPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        Write-Warning "Database is outside the managed data directory: '$dbFullPath'. This is allowed, but not recommended."
    }

    $dbDir = Split-Path -Parent $dbFullPath
    if (-not (Test-Path -LiteralPath $dbDir)) {
        New-Item -ItemType Directory -Force -Path $dbDir | Out-Null
    }

    Write-Host "Persistent database file: $dbFullPath"
}

function Assert-ExtractedWindowsAppPackage {
    param([string]$SourceDir)

    $requiredFiles = @(
        'printerhub.bat',
        'printer-hub.jar'
    )

    foreach ($fileName in $requiredFiles) {
        $path = Join-Path $SourceDir $fileName
        if (-not (Test-Path -LiteralPath $path)) {
            Fail "Extracted Windows package does not contain required file: $fileName"
        }
    }

    $unexpectedPowerShellScripts = Get-ChildItem -LiteralPath $SourceDir -Recurse -File -Filter '*.ps1' -ErrorAction SilentlyContinue
    if ($unexpectedPowerShellScripts.Count -gt 0) {
        Write-Host "Unexpected PowerShell scripts found in Windows app package:"
        $unexpectedPowerShellScripts | Select-Object FullName | Format-Table -AutoSize
        Fail "Windows app package must not contain PowerShell helper scripts. Put scripts in the admin package instead."
    }

    $unexpectedCameraHelpers = Get-ChildItem -LiteralPath $SourceDir -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -like 'camera-capture-*'
        }

    if ($unexpectedCameraHelpers.Count -gt 0) {
        Write-Host "Unexpected camera helper files found in Windows app package:"
        $unexpectedCameraHelpers | Select-Object FullName | Format-Table -AutoSize
        Fail "Windows app package must not contain camera helper scripts. Put them under admin package camera/."
    }
}

function Copy-LightweightDataBackup {
    param(
        [string]$DataDir,
        [string]$BackupDir
    )

    if (-not (Test-Path -LiteralPath $DataDir)) {
        return
    }

    Write-Host "Creating lightweight data backup: $BackupDir"
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

    Get-ChildItem -LiteralPath $DataDir -File | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $BackupDir -Force
    }
}

function Copy-AppBackup {
    param(
        [string]$AppDir,
        [string]$BackupDir
    )

    if (-not (Test-Path -LiteralPath $AppDir)) {
        Write-Host "No existing app directory found. Fresh app directory will be created."
        return
    }

    Write-Host "Creating app backup copy: $BackupDir"
    Copy-Item -LiteralPath $AppDir -Destination $BackupDir -Recurse -Force
}

function Remove-AppOwnedItems {
    param([string]$AppDir)

    New-Item -ItemType Directory -Force -Path $AppDir | Out-Null

    $appOwnedItems = @(
        'printer-hub.jar',
        'printerhub.bat',
        'printerhub-task.cmd',
        'META-INF',
        'dashboard',
        'camera',
        'INSTALL.md',
        'QUICKSTART.md',
        'README.md'
    )

    foreach ($itemName in $appOwnedItems) {
        $target = Join-Path $AppDir $itemName

        if (Test-Path -LiteralPath $target) {
            try {
                Remove-Item -LiteralPath $target -Recurse -Force
                Write-Host "Removed old app-owned item: $target"
            }
            catch {
                Fail "Could not remove old app-owned item '$target': $($_.Exception.Message)"
            }
        }
    }
}

function Copy-AppFiles {
    param(
        [string]$SourceDir,
        [string]$AppDir
    )

    New-Item -ItemType Directory -Force -Path $AppDir | Out-Null

    Write-Host "Copying new app files into: $AppDir"
    Copy-Item -Path (Join-Path $SourceDir '*') -Destination $AppDir -Recurse -Force
}

function Assert-InstalledJarMatchesSource {
    param(
        [string]$SourceDir,
        [string]$AppDir
    )

    $sourceJar = Join-Path $SourceDir 'printer-hub.jar'
    $targetJar = Join-Path $AppDir 'printer-hub.jar'

    if (-not (Test-Path -LiteralPath $sourceJar)) {
        Fail "Source package does not contain printer-hub.jar: $sourceJar"
    }

    if (-not (Test-Path -LiteralPath $targetJar)) {
        Fail "Installed app does not contain printer-hub.jar after copy: $targetJar"
    }

    $sourceJarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceJar).Hash
    $targetJarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetJar).Hash

    Write-Host "Source JAR SHA256: $sourceJarHash"
    Write-Host "Target JAR SHA256: $targetJarHash"

    if ($sourceJarHash -ne $targetJarHash) {
        Fail "Installed JAR hash does not match extracted package. source=$sourceJarHash target=$targetJarHash"
    }
}

$root = 'C:\printerhub'
$appDir = "$root\app"
$tmpDir = "$root\tmp"
$relDir = "$root\rel"
$logDir = "$root\log"
$dataDir = "$root\data"
$runEnvPath = "$root\data\run.env"
$updateLog = Join-Path $logDir 'update.log'

foreach ($dir in @($logDir, $tmpDir, $relDir, $dataDir)) {
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
}

$envMap = Read-RunEnv -Path $runEnvPath
$javaCommand = Get-JavaCommand -EnvMap $envMap
$javaMajor = Get-JavaMajorVersion -JavaCommand $javaCommand
$databaseFile = Get-ConfiguredDatabaseFile -EnvMap $envMap -Root $root

if ($null -eq $javaCommand) {
    Fail "Java was not found. Set PRINTERHUB_JAVA in C:\printerhub\data\run.env"
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. javaCommand='$javaCommand' javaMajor='$javaMajor'"
}

Assert-PersistentRuntimePaths -DatabaseFile $databaseFile -AppDir $appDir -DataDir $dataDir

if (-not (Test-Path -LiteralPath 'C:\printerhub\bin\s.ps1')) {
    Fail "Stop script not found: C:\printerhub\bin\s.ps1"
}
if (-not (Test-Path -LiteralPath 'C:\printerhub\bin\r.ps1')) {
    Fail "Start script not found: C:\printerhub\bin\r.ps1"
}

$assetName = "printer-hub-$Version-windows.zip"
$tagName = "v$Version"
$zipPath = Join-Path $relDir $assetName
$extractDir = Join-Path $tmpDir ("extract-" + $Version)
$appBackupDir = Join-Path $tmpDir ("app-backup-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$dataBackupDir = Join-Path $tmpDir ("data-backup-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$downloadUrl = "https://github.com/$Owner/$Repo/releases/download/$tagName/$assetName"

Write-UpdateLog -Path $updateLog -Message "update start version=$Version tag=$tagName url=$downloadUrl java=$javaCommand"

Write-Host "Using run.env: $runEnvPath"
Write-Host "Detected Java command: $javaCommand"
Write-Host "Detected Java major version: $javaMajor"
Write-Host "Downloading $downloadUrl"

Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath

Copy-LightweightDataBackup -DataDir $dataDir -BackupDir $dataBackupDir

Write-Host "Stopping current PrinterHub"
& 'C:\printerhub\bin\s.ps1'

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

Write-Host "Extracted source directory: $sourceDir"
Assert-ExtractedWindowsAppPackage -SourceDir $sourceDir

Copy-AppBackup -AppDir $appDir -BackupDir $appBackupDir
Remove-AppOwnedItems -AppDir $appDir
Copy-AppFiles -SourceDir $sourceDir -AppDir $appDir
Assert-InstalledJarMatchesSource -SourceDir $sourceDir -AppDir $appDir

Write-Host "App directory content after copy:"
Get-ChildItem -LiteralPath $appDir | Select-Object Name, Length, LastWriteTime

Write-Host "Starting updated PrinterHub"
& 'C:\printerhub\bin\r.ps1'

Write-UpdateLog -Path $updateLog -Message "update success version=$Version tag=$tagName asset=$assetName"
Write-Host "Update complete for version $Version"