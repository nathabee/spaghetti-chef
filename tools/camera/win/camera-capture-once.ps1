param(
    [string]$CameraName = "AUKEY Webcam",
    [string]$OutputFile = "C:\printerhub\data\camera\p1\latest.jpg",
    [int]$Width = 1280,
    [int]$Height = 720
)

$ErrorActionPreference = "Stop"

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if ($null -eq $ffmpeg) {
    Write-Error "[camera] ffmpeg is not installed or not available in PATH"
    exit 1
}

$OutputDir = Split-Path -Parent $OutputFile
$OutputName = Split-Path -Leaf $OutputFile
$TmpFile = Join-Path $OutputDir ".$OutputName.tmp.jpg"

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

if (Test-Path -LiteralPath $TmpFile) {
    Remove-Item -LiteralPath $TmpFile -Force
}

& ffmpeg -hide_banner -loglevel error -y `
    -f dshow `
    -video_size "$Width`x$Height" `
    -i "video=$CameraName" `
    -frames:v 1 `
    -update 1 `
    "$TmpFile"

Move-Item -LiteralPath $TmpFile -Destination $OutputFile -Force

Write-Host "[camera] captured $OutputFile"