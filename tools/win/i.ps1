$ErrorActionPreference = 'Stop'

function Fail {
    param([string]$Message)
    Write-Error $Message
    exit 1
}

function Test-Admin {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

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

if (-not (Test-Admin)) {
    Fail "i.ps1 must be run as Administrator."
}

$javaMajor = Get-JavaMajorVersion
if ($null -eq $javaMajor) {
    Fail "Java was not found in PATH. Install Java 21 first."
}
if ($javaMajor -ne 21) {
    Fail "Java 21 is required. Detected Java major version: $javaMajor"
}

$root = 'C:\ph'
$dirs = @(
    $root,
    "$root\app",
    "$root\data",
    "$root\log",
    "$root\rel",
    "$root\tmp",
    "$root\bin"
)

foreach ($dir in $dirs) {
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
}

$scriptSourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptNames = @('i.ps1', 'u.ps1', 'r.ps1', 's.ps1', 'v.ps1')

foreach ($name in $scriptNames) {
    $src = Join-Path $scriptSourceDir $name
    $dst = "C:\ph\bin\$name"
    Copy-Item -LiteralPath $src -Destination $dst -Force
}

$runEnvPath = 'C:\ph\data\run.env'
if (-not (Test-Path -LiteralPath $runEnvPath)) {
    @"
PRINTERHUB_DATABASE_FILE=printerhub.db
PRINTERHUB_API_PORT=18080
"@ | Set-Content -LiteralPath $runEnvPath -Encoding ASCII
}

Write-Host "Bootstrap complete."
Write-Host "Created C:\ph structure and copied scripts to C:\ph\bin"
Write-Host "run.env: $runEnvPath"
Write-Host "Java 21 check: OK"