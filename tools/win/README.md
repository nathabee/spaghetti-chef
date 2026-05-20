# PrinterHub Windows Tools

This directory contains Windows helper scripts for PrinterHub.

These scripts are intended to be copied into the Windows runtime `bin`
directory.

Example runtime layout:

```text
C:\printerhub\
├── app\
├── bin\
├── data\
├── log\
├── rel\
└── tmp\
````

## Runtime Scripts

```text
r.ps1              start PrinterHub through Task Scheduler
s.ps1              stop PrinterHub
t.ps1              create or refresh the PrinterHub scheduled task
u.ps1              update PrinterHub from a GitHub release ZIP
v.ps1              verify runtime configuration and status
run.env.example    example runtime environment file
```

The active runtime environment file is normally:

```text
C:\printerhub\data\run.env
```

Example:

```text
PRINTERHUB_JAVA=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe
PRINTERHUB_DATABASE_FILE=C:\printerhub\data\printerhub.db
PRINTERHUB_API_PORT=18080
```

## Camera Helpers

PrinterHub now supports direct ffmpeg webcam capture from the dashboard. The
camera helper scripts are mainly diagnostic tools and fallback utilities.

```text
tools/win/camera/
├── camera-capture-loop.ps1
└── camera-capture-once.ps1
```

In the Windows package, these scripts should be delivered under:

```text
bin\camera\
```

They should not be packaged inside `app\camera\` and should not be bundled into
`printer-hub.jar`.

## Requirements

ffmpeg must be installed and available in `PATH`.

List available DirectShow camera devices:

```powershell
ffmpeg -list_devices true -f dshow -i dummy
```

Example camera name:

```text
AUKEY Webcam
```

## Dashboard ffmpeg Backend

In the selected printer Camera view, configure:

```text
Source type: ffmpeg webcam
Source value: video=AUKEY Webcam
ffmpeg command: ffmpeg
ffmpeg input format: dshow
ffmpeg video size: 640x480
```

The Windows source value usually needs the `video=` prefix.

Examples:

```text
video=AUKEY Webcam
video=Integrated Camera
```

## Capture One Image

```powershell
.\tools\win\camera\camera-capture-once.ps1 `
  -CameraName "AUKEY Webcam" `
  -OutputFile "C:\printerhub\data\camera\p1\latest.jpg"
```

When installed on the runtime machine:

```powershell
C:\printerhub\bin\camera\camera-capture-once.ps1 `
  -CameraName "AUKEY Webcam" `
  -OutputFile "C:\printerhub\data\camera\p1\latest.jpg"
```

## Run Capture Loop

```powershell
.\tools\win\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\printerhub\data\camera" `
  -IntervalSeconds 2 `
  -ArchiveIntervalSeconds 300 `
  -RetentionHours 24
```

When installed on the runtime machine:

```powershell
C:\printerhub\bin\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\printerhub\data\camera" `
  -IntervalSeconds 2 `
  -ArchiveIntervalSeconds 300 `
  -RetentionHours 24
```

## Quick Archive Test

```powershell
C:\printerhub\bin\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\printerhub\data\camera" `
  -ArchiveIntervalSeconds 10
```

## Expected Result

After the loop has run for 15 to 20 seconds:

```text
C:\printerhub\data\camera\p1\latest.jpg
C:\printerhub\data\camera\p1\previous.jpg
C:\printerhub\data\camera\p1\archive\*.jpg
```

## Storage Layout

When the database is `C:\printerhub\data\printerhub.db`, the default camera
storage is:

```text
C:\printerhub\data\camera\<printerId>\
├── latest.jpg
├── previous.jpg
├── archive\
└── snapshots\
```

Use the same `printerId` as the printer created in the PrinterHub dashboard.
