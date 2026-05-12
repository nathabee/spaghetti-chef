$ErrorActionPreference = 'Stop'

function Fail {
    param([string]$Message)
    Write-Error $Message
    exit 1
}

$targets = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and
    $_.CommandLine -and
    ($_.CommandLine -match 'printer-hub\.jar')
}

if (-not $targets) {
    Write-Host "No PrinterHub process found."
    exit 0
}

foreach ($proc in $targets) {
    try {
        Stop-Process -Id $proc.ProcessId -Force
        Write-Host "Stopped PrinterHub PID $($proc.ProcessId)"
    }
    catch {
        Fail "Failed to stop PrinterHub PID $($proc.ProcessId): $($_.Exception.Message)"
    }
}