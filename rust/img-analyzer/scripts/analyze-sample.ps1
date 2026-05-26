param(
    [string] $PrinterId = "p1",
    [string] $CameraJobId = "1",
    [string] $DeltaSetId = "1",
    [string] $CameraRoot = "",
    [int] $FromSequence = 1,
    [int] $ToSequence = 2,
    [string] $FromSnapshot = "",
    [string] $ToSnapshot = "",
    [string] $DeltaFrame = "",
    [switch] $NoDeltaFrame,
    [string] $Method = "delta-basic",
    [double] $Threshold = 0.65,
    [switch] $NoBuild
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $ProjectDir "../..")
$Bin = Join-Path $ProjectDir "target/debug/img-analyzer.exe"

function Resolve-SamplePath {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Require-File {
    param(
        [string] $Label,
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-Error "Missing ${Label}: ${Path}"
        $CameraRoot = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "data/camera"))
        if ($Path.StartsWith($CameraRoot)) {
            Write-Error "Expected SpaghettiChef camera data under: $CameraRoot"
            Write-Error "Capture snapshots first, or pass explicit paths to existing image files."
        }
        exit 2
    }
}

if ([string]::IsNullOrWhiteSpace($CameraRoot)) {
    $CameraRoot = Join-Path $RepoRoot "camera"
} else {
    $CameraRoot = Resolve-SamplePath $CameraRoot
}

if ([string]::IsNullOrWhiteSpace($FromSnapshot)) {
    $FromSnapshot = Join-Path $CameraRoot "$PrinterId/snapshots/$CameraJobId/$($FromSequence.ToString("000000"))_snapshot.jpg"
} else {
    $FromSnapshot = Resolve-SamplePath $FromSnapshot
}

if ([string]::IsNullOrWhiteSpace($ToSnapshot)) {
    $ToSnapshot = Join-Path $CameraRoot "$PrinterId/snapshots/$CameraJobId/$($ToSequence.ToString("000000"))_snapshot.jpg"
} else {
    $ToSnapshot = Resolve-SamplePath $ToSnapshot
}

if ($NoDeltaFrame) {
    $DeltaFrame = ""
} elseif ([string]::IsNullOrWhiteSpace($DeltaFrame)) {
    $DefaultDelta = Join-Path $CameraRoot "$PrinterId/deltas/$CameraJobId/$DeltaSetId/$($FromSequence.ToString("000000"))_$($ToSequence.ToString("000000"))_delta.jpg"
    if (Test-Path -LiteralPath $DefaultDelta -PathType Leaf) {
        $DeltaFrame = $DefaultDelta
    }
} else {
    $DeltaFrame = Resolve-SamplePath $DeltaFrame
}

Require-File "from snapshot" $FromSnapshot
Require-File "to snapshot" $ToSnapshot

if (-not [string]::IsNullOrWhiteSpace($DeltaFrame)) {
    Require-File "delta frame" $DeltaFrame
}

if (-not $NoBuild) {
    & cargo build --manifest-path (Join-Path $ProjectDir "Cargo.toml")
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path -LiteralPath $Bin -PathType Leaf)) {
    $Bin = Join-Path $ProjectDir "target/debug/img-analyzer"
}

$AnalyzerArgs = @(
    "--from-snapshot", $FromSnapshot,
    "--to-snapshot", $ToSnapshot,
    "--method", $Method,
    "--threshold", $Threshold
)

if (-not [string]::IsNullOrWhiteSpace($DeltaFrame)) {
    $AnalyzerArgs += @("--delta-frame", $DeltaFrame)
}

Write-Error "Running $Bin"
Write-Error "from=$FromSnapshot"
Write-Error "to=$ToSnapshot"
Write-Error "delta=$DeltaFrame"

& $Bin @AnalyzerArgs
