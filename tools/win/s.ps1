$ErrorActionPreference = 'Stop'

function Fail {
    param([string]$Message)
    Write-Error $Message
    exit 1
}

$logFile = 'C:\ph\log\stop.log'
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

$targets = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe'
} | Where-Object {
    $_.CommandLine -and (
        $_.CommandLine -match 'printer-hub\.jar' -or
        $_.CommandLine -match 'printerhub\.bat'
    )
}

if (-not $targets) {
    "$stamp no PrinterHub java process found" | Add-Content -LiteralPath $logFile
    Write-Host "No PrinterHub process found."
    exit 0
}

foreach ($proc in $targets) {
    try {
        Stop-Process -Id $proc.ProcessId -Force
        "$stamp stopped PID $($proc.ProcessId)" | Add-Content -LiteralPath $logFile
        Write-Host "Stopped PrinterHub PID $($proc.ProcessId)"
    }
    catch {
        "$stamp failed to stop PID $($proc.ProcessId): $($_.Exception.Message)" | Add-Content -LiteralPath $logFile
        Fail "Failed to stop PrinterHub PID $($proc.ProcessId): $($_.Exception.Message)"
    }
}