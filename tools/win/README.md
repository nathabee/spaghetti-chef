# SpaghettiChef Windows Tools

This directory contains Windows helper scripts for SpaghettiChef.

These scripts are intended to be copied into the Windows runtime `bin`
directory.

Example runtime layout:

```text
C:\spaghettichef\
├── app\
├── bin\
├── data\
├── log\
├── rel\
└── tmp\
````

## Runtime Scripts

```text
r.ps1              start SpaghettiChef through Task Scheduler
s.ps1              stop SpaghettiChef
t.ps1              create or refresh the SpaghettiChef scheduled task
u.ps1              update SpaghettiChef from a GitHub release ZIP
v.ps1              verify runtime configuration and status
run.env.example    example runtime environment file
```

The active runtime environment file is normally:

```text
C:\spaghettichef\data\run.env
```

Example:

```text
SPAGHETTICHEF_JAVA=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe
SPAGHETTICHEF_DATABASE_FILE=C:\spaghettichef\data\spaghettichef.db
SPAGHETTICHEF_API_PORT=18080
```

## Camera Helpers

SpaghettiChef now supports direct ffmpeg webcam capture from the dashboard. The
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
`spaghetti-chef.jar`.

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
  -OutputFile "C:\spaghettichef\data\camera\p1\latest.jpg"
```

When installed on the runtime machine:

```powershell
C:\spaghettichef\bin\camera\camera-capture-once.ps1 `
  -CameraName "AUKEY Webcam" `
  -OutputFile "C:\spaghettichef\data\camera\p1\latest.jpg"
```

## Run Capture Loop

```powershell
.\tools\win\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\spaghettichef\data\camera" `
  -IntervalSeconds 2 `
  -ArchiveIntervalSeconds 300 `
  -RetentionHours 24
```

When installed on the runtime machine:

```powershell
C:\spaghettichef\bin\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\spaghettichef\data\camera" `
  -IntervalSeconds 2 `
  -ArchiveIntervalSeconds 300 `
  -RetentionHours 24
```

## Quick Archive Test

```powershell
C:\spaghettichef\bin\camera\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\spaghettichef\data\camera" `
  -ArchiveIntervalSeconds 10
```

## Expected Result

After the loop has run for 15 to 20 seconds:

```text
C:\spaghettichef\data\camera\p1\latest.jpg
C:\spaghettichef\data\camera\p1\previous.jpg
C:\spaghettichef\data\camera\p1\archive\*.jpg
```

## Storage Layout

When the database is `C:\spaghettichef\data\spaghettichef.db`, the default camera
storage is:

```text
C:\spaghettichef\data\camera\<printerId>\
├── latest.jpg
├── previous.jpg
├── archive\
└── snapshots\
```

Use the same `printerId` as the printer created in the SpaghettiChef dashboard.
