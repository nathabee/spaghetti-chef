# PrinterHub Camera Capture

PrinterHub now supports direct ffmpeg webcam capture from the dashboard. The old
helper scripts are still useful for diagnostics, but they are no longer the
preferred runtime path.

## Dashboard ffmpeg Backend

Open the selected printer Camera view and configure:

Linux:

```text
Source type: ffmpeg webcam
Source value: /dev/video0
ffmpeg command: ffmpeg
ffmpeg input format: v4l2
ffmpeg video size: 640x480
```

Windows:

```text
Source type: ffmpeg webcam
Source value: video=AUKEY Webcam
ffmpeg command: ffmpeg
ffmpeg input format: dshow
ffmpeg video size: 640x480
```

For Windows, list camera names with:

```powershell
ffmpeg -list_devices true -f dshow -i dummy
```

The value usually needs the `video=` prefix, for example:

```text
video=Integrated Camera
```

## Storage Layout

PrinterHub writes captured snapshots under its camera storage directory. The
camera folder name matches the PrinterHub printer id.

Linux default:

```text
./data/camera/<printerId>/
  latest.jpg
  previous.jpg
  archive/
  snapshots/
```

Windows default:

```text
C:\printerhub\data\camera\<printerId>\
  latest.jpg
  previous.jpg
  archive\
  snapshots\
```

Missing directories are created automatically. The storage directory is a
per-printer camera setting in the dashboard Camera view. Use
`C:\printerhub\data\camera` on the Windows service install when you want the
dashboard backend to write to the same location as the diagnostic scripts.

When using the dashboard `snapshot-folder` backend, point `Source value` at a
folder containing incoming camera images, not at PrinterHub's own output folder.
PrinterHub ignores its generated `latest.*`, `previous.*`, and `delta.*` files
to avoid recapturing its own output.

## Requirements

Linux:

```bash
sudo apt install v4l-utils ffmpeg
```

Windows:

```powershell
ffmpeg must be installed and available in PATH
```

## Find the Camera

Linux:

```bash
lsusb
v4l2-ctl --list-devices
```

Example device:

```text
/dev/video0
```

Windows:

```powershell
ffmpeg -list_devices true -f dshow -i dummy
```

Example camera name:

```text
AUKEY Webcam
```

## Linux Usage

Capture one image:

```bash
CAMERA_DEVICE=/dev/video0 \
tools/camera/linux/camera-capture-once.sh ./data/camera/p1/latest.jpg
```

Run capture loop:

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_BASE_DIR=./data/camera \
CAMERA_INTERVAL_SECONDS=2 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=300 \
CAMERA_RETENTION_HOURS=24 \
tools/camera/linux/camera-capture-loop.sh p1
```

Quick test with archive every 10 seconds:

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=10 \
tools/camera/linux/camera-capture-loop.sh p1
```

## Windows Usage

Capture one image:

```powershell
.\tools\camera\win\camera-capture-once.ps1 `
  -CameraName "AUKEY Webcam" `
  -OutputFile "C:\printerhub\data\camera\p1\latest.jpg"
```

Run capture loop:

```powershell
.\tools\camera\win\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\printerhub\data\camera" `
  -IntervalSeconds 2 `
  -ArchiveIntervalSeconds 300 `
  -RetentionHours 24
```

Quick test with archive every 10 seconds:

```powershell
.\tools\camera\win\camera-capture-loop.ps1 `
  -PrinterId "p1" `
  -CameraName "AUKEY Webcam" `
  -BaseDir "C:\printerhub\data\camera" `
  -ArchiveIntervalSeconds 10
```

## Test Result

After the loop has run for 15–20 seconds, these files should exist.

Linux:

```bash
ls -lh ./data/camera/p1
ls -lh ./data/camera/p1/archive
```

Expected:

```text
latest.jpg
previous.jpg
archive/*.jpg
```

Windows:

```text
C:\printerhub\data\camera\p1\latest.jpg
C:\printerhub\data\camera\p1\previous.jpg
C:\printerhub\data\camera\p1\archive\*.jpg
```

## Notes

Use the same `printerId` as the printer created in the PrinterHub dashboard.

Example:

```text
PrinterHub printer id: p1
Camera folder:         data/camera/p1/
```

 
