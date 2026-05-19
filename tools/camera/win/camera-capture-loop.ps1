param(
    [string]$PrinterId = "p1",
    [string]$CameraName = "PC-LM1E Camera",
    [string]$BaseDir = "C:\printerhub\data\camera",
    [int]$Width = 1280,
    [int]$Height = 720,
    [int]$IntervalSeconds = 2,
    [int]$ArchiveIntervalSeconds = 300,
    [int]$RetentionHours = 24
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CaptureOnce = Join-Path $ScriptDir "camera-capture-once.ps1"

if (-not (Test-Path -LiteralPath $CaptureOnce)) {
    Write-Error "[camera] missing capture script: $CaptureOnce"
    exit 1
}

$PrinterDir = Join-Path $BaseDir $PrinterId
$ArchiveDir = Join-Path $PrinterDir "archive"

$Latest = Join-Path $PrinterDir "latest.jpg"
$Previous = Join-Path $PrinterDir "previous.jpg"

New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

$LastArchive = Get-Date "1970-01-01T00:00:00"

Write-Host "[camera] loop started"
Write-Host "[camera] printer=$PrinterId"
Write-Host "[camera] cameraName=$CameraName"
Write-Host "[camera] baseDir=$BaseDir"
Write-Host "[camera] latest=$Latest"
Write-Host "[camera] previous=$Previous"
Write-Host "[camera] archiveDir=$ArchiveDir"
Write-Host "[camera] intervalSeconds=$IntervalSeconds"
Write-Host "[camera] archiveIntervalSeconds=$ArchiveIntervalSeconds"
Write-Host "[camera] retentionHours=$RetentionHours"
Write-Host "[camera] size=${Width}x${Height}"

while ($true) {
    $Now = Get-Date

    if (Test-Path -LiteralPath $Latest) {
        Copy-Item -LiteralPath $Latest -Destination $Previous -Force
    }

    & powershell -ExecutionPolicy Bypass -File $CaptureOnce `
        -CameraName $CameraName `
        -OutputFile $Latest `
        -Width $Width `
        -Height $Height

    $SecondsSinceArchive = ($Now - $LastArchive).TotalSeconds
    if ($SecondsSinceArchive -ge $ArchiveIntervalSeconds) {
        $Stamp = $Now.ToString("yyyyMMdd_HHmmss")
        $ArchiveFile = Join-Path $ArchiveDir "$Stamp.jpg"

        Copy-Item -LiteralPath $Latest -Destination $ArchiveFile -Force
        $LastArchive = $Now

        Write-Host "[camera] archived $ArchiveFile"
    }

    $RetentionLimit = (Get-Date).AddHours(-1 * $RetentionHours)
    Get-ChildItem -LiteralPath $ArchiveDir -Filter "*.jpg" -File |
        Where-Object { $_.LastWriteTime -lt $RetentionLimit } |
        Remove-Item -Force

    Start-Sleep -Seconds $IntervalSeconds
}