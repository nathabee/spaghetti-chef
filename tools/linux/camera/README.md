
# PrinterHub Linux Tools

This directory contains Linux helper scripts for PrinterHub.

## Camera Helpers

PrinterHub now supports direct ffmpeg webcam capture from the dashboard. The
camera helper scripts are mainly diagnostic tools and fallback utilities.

```text
tools/linux/camera/
├── camera-capture-loop.sh
└── camera-capture-once.sh
````

## Requirements

```bash
sudo apt install v4l-utils ffmpeg
```

## Find the Camera

```bash
lsusb
v4l2-ctl --list-devices
```

Example device:

```text
/dev/video0
```

## Dashboard ffmpeg Backend

In the selected printer Camera view, configure:

```text
Source type: ffmpeg webcam
Source value: /dev/video0
ffmpeg command: ffmpeg
ffmpeg input format: v4l2
ffmpeg video size: 640x480
```

## Capture One Image

```bash
CAMERA_DEVICE=/dev/video0 \
tools/linux/camera/camera-capture-once.sh ./data/camera/p1/latest.jpg
```

## Run Capture Loop

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_BASE_DIR=./data/camera \
CAMERA_INTERVAL_SECONDS=2 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=300 \
CAMERA_RETENTION_HOURS=24 \
tools/linux/camera/camera-capture-loop.sh p1
```

## Quick Archive Test

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=10 \
tools/linux/camera/camera-capture-loop.sh p1
```

## Expected Result

After the loop has run for 15 to 20 seconds:

```bash
ls -lh ./data/camera/p1
ls -lh ./data/camera/p1/archive
```

Expected files:

```text
latest.jpg
previous.jpg
archive/*.jpg
```

## Storage Layout

When the database is `./data/printerhub.db`, the default camera storage is:

```text
./data/camera/<printerId>/
├── latest.jpg
├── previous.jpg
├── archive/
└── snapshots/
```

Use the same `printerId` as the printer created in the PrinterHub dashboard.
