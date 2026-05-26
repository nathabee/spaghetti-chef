# Camera

This document is the current camera user manual and implementation overview for SpaghettiChef.

Historical planning belongs in:

```text
docs/TODOs/TODO-0.4-camera.md
docs/TODOs/TODO-0.5-rust.md
docs/TODOs/TODO-0.6-replay.md
```

## User Manual

Camera monitoring starts from a printer entry.

In SpaghettiChef, a printer can represent:

```text
a real printer connected by serial/USB
a simulated printer for tests
a placeholder printer used only to monitor a camera
```

That means you can create a fake or placeholder printer when you only want SpaghettiChef to monitor a camera connected to the PC. The camera settings still need a printer id because camera files, jobs, snapshots, delta sets, and calculation runs are all grouped by printer.

### 1. Create Or Select A Printer

Open the dashboard and create a printer first.

For a real printer, configure the usual printer connection settings.

For a camera-only setup, create a printer entry with a clear id such as:

```text
cam-pc-1
webcam-lab
printer-stand-camera
```

Use a simulated or disabled printer connection if you do not want SpaghettiChef to send printer commands. The camera can still be configured for that selected printer.

### 2. Configure The Camera For The Selected Printer

Select the printer in the dashboard, then open the camera/settings area.

Enable the camera and configure:

```text
source type
source value
input format
video size
capture interval seconds
retained snapshots
camera storage directory
analysis enabled
safety enabled
```

Common camera source examples:

```text
Linux webcam:
  source type: ffmpeg
  input format: v4l2
  source value: /dev/video0

Windows webcam:
  source type: ffmpeg
  input format: dshow
  source value: video=<camera name>

Development/test:
  source type: simulated or snapshot folder
```

If the camera root is left at the application default, SpaghettiChef stores camera files relative to the database directory. If you set a custom camera storage directory, the camera writes there instead.

### 3. Test One Capture

Use `Capture now` to test the camera.

This is a manual capture. It is useful for checking that the camera works, but it is not the same as a camera job.

Expected result:

```text
the latest preview image updates
a camera event is recorded
latest.jpg may be refreshed
```

Manual capture must not create or reuse a camera job id.

### 4. Start A Camera Job

Start a camera job from the selected-printer camera or analysis-session area.

When a camera job starts:

```text
SpaghettiChef creates one cameraJobId.
The scheduler receives that exact cameraJobId.
Scheduled captures write source snapshots into that camera job.
The dashboard can show the latest preview while persisted files are collected.
```

The capture interval controls how often pictures are taken.

Example:

```text
capture interval seconds = 2
```

means the camera job will sequence pictures roughly every two seconds while the job is running.

### 5. Stop The Camera Job

Stop the camera job when monitoring is finished.

Stopping the job:

```text
marks the camera job complete
stops the scheduler
keeps the persisted source snapshots
keeps generated delta frames and calculation results
```

A later scheduler tick for a completed camera job must not silently create a new job.

### 6. Review The Captured Data

Use the admin camera data area to inspect persisted data.

Typical workflow:

```text
select printer
select camera job
review source snapshots
generate or select a delta set
run a calculation
select Java or Rust calculation engine
compare calculation runs
```

The admin view is the right place to inspect historical camera data. The current camera preview is only a live/operator convenience.

## Dashboard Overview

Camera features are split between operator views and admin views.

Operator-facing views show the current camera state:

```text
selected printer camera settings
latest camera preview
camera job start/stop controls
current camera job status
live analysis/session status
recent trace rows
camera events
```

Admin-facing views manage retained data:

```text
camera jobs
source snapshots
delta sets
calculation runs
engine selection
manual recalculation
future replay/compression tools
```

The operator view is for watching the printer. The admin view is for inspecting and recalculating persisted camera data.

## Camera Settings

Camera settings are stored per printer.

The important settings are:

```text
enabled
source type
source value
input format
video size
capture interval seconds
retained snapshots
storage directory / camera root
analysis enabled
safety enabled
confidence threshold
confirmations required
```

The camera root is configurable. Examples in the documentation use `<cameraRoot>` to make that explicit.

Example layout:

```text
<cameraRoot>/<printerId>/
```

The configured camera root is resolved by the Java backend. The dashboard should not assume a fixed `data/camera` path.

## Current File Model

The current rule is:

```text
Persisted camera-job files are the source of truth.
latest.jpg, previous.jpg, and delta.jpg are volatile preview files only.
```

SpaghettiChef stores camera-job data directly at its final persisted path.

Source snapshots for a camera job:

```text
<cameraRoot>/<printerId>/snapshots/<cameraJobId>/<snapshotId>_snapshot.jpg
```

Delta frames for a camera job and delta set:

```text
<cameraRoot>/<printerId>/deltas/<cameraJobId>/<deltaSetId>/<from>_<to>_delta.jpg
```

The database stores the stable metadata and file references:

```text
camera_jobs
camera_snapshot_entries
camera_delta_sets
camera_delta_frames
camera_calculation_runs
camera_calculation_results
```

Replay, recalculation, comparison, and benchmarking must use these persisted rows and named files.

## Preview Files

Each printer may also have these files:

```text
<cameraRoot>/<printerId>/latest.jpg
<cameraRoot>/<printerId>/previous.jpg
<cameraRoot>/<printerId>/delta.jpg
```

They exist so the dashboard always has something simple to display.

Meaning:

```text
latest.jpg    current preview image
previous.jpg  previous preview image
delta.jpg     current preview delta image
```

They may be overwritten at every capture cycle.

They must not be used as historical references for:

```text
camera jobs
delta sets
calculation runs
replay
benchmarking
compression decisions
```

The capture flow writes the persisted end file first, then may copy/update these preview files. This avoids parallel jobs or late captures creating history that points to the wrong job.

## Camera Job Lifecycle

A camera job owns the retained source snapshots created while it is active.

The intended lifecycle is:

```text
Start job creates the cameraJobId.
The scheduler receives that exact cameraJobId.
Each scheduled capture writes only into that cameraJobId.
Stop job completes that cameraJobId and stops the scheduler.
A late scheduler tick for a completed cameraJobId fails or records an error.
It must not create a new job.
```

Manual capture is different:

```text
Manual capture may update the preview image.
Manual capture must not create, read, or reuse a cameraJobId.
Manual capture must not accidentally attach files to a running or completed camera job.
```

This separation prevents incoherent capture history when a manual capture and a scheduled camera job happen near the same time.

## Capture Flow

For a scheduled camera-job capture:

```text
1. Load camera settings for the printer.
2. Capture one frame from the configured camera source.
3. Resolve the active cameraJobId supplied by the scheduler.
4. Write the source snapshot directly to its persisted job path.
5. Store camera_snapshot_entries metadata.
6. Update latest.jpg / previous.jpg preview files.
7. Generate/update live delta data if live analysis is enabled.
8. Store camera events and diagnostic messages.
```

For a manual capture:

```text
1. Load camera settings for the printer.
2. Capture one frame.
3. Update preview files.
4. Store a camera event.
5. Do not attach the capture to a camera job.
```

## Delta Sets

A delta set is a persisted group of delta frames for one camera job.

It is scoped by:

```text
printerId
cameraJobId
deltaSetId
```

Each delta frame references:

```text
from snapshot id
to snapshot id
delta image path
```

Several delta sets can exist for the same camera job. For example:

```text
delta set 1: compare every adjacent snapshot
delta set 2: compare every tenth snapshot
delta set 3: regenerated later with different settings
```

Delta frames must reference persisted source snapshots. They must not reference `latest.jpg`, `previous.jpg`, or `delta.jpg`.

## Calculation Runs

A calculation run analyzes one delta set.

Current calculation engines:

```text
JAVA_BASIC_DELTA
RUST_CLI_DELTA
```

Each calculation run stores:

```text
engine name
algorithm variant
engine version
execution duration
engine status
result count
message
```

Each calculation result stores per-frame analysis data such as:

```text
delta frame id
confidence
suspected
reason codes
message
changed pixel ratio
average pixel delta
```

Java and Rust runs can coexist for the same printer, camera job, and delta set. No calculation run should overwrite another run.

## Rust Analyzer Track

The 0.5.x track adds Rust as an optional image-analysis engine.

Current scope:

```text
0.5.0 standalone Rust CLI analyzer
0.5.1 validation against real SpaghettiChef camera files
0.5.2 stable CLI/result contract
0.5.3 Java process adapter
0.5.4 configurable calculation engine architecture
0.5.5 Rust recalculation integration
0.5.6 engine comparison and benchmarking
```

Rust analysis uses persisted source snapshots and persisted delta frames. It does not read SpaghettiChef live preview files as history.

## Engine Comparison And Benchmarking

The 0.5.6 comparison work should compare calculation runs for the same:

```text
printerId
cameraJobId
deltaSetId
```

The useful comparison fields are:

```text
engine name
algorithm variant
engine version
engine status
execution duration
result count
suspected count
average confidence
per-frame confidence difference
per-frame suspected mismatch
reason code differences
```

This is benchmarking and comparison only. It should not create new snapshots, delta frames, or calculation results unless the admin explicitly starts a recalculation first.

## Replay And Compression Track

The 0.6.x replay work should use persisted data only.

Replay reads:

```text
camera_snapshot_entries
camera_delta_sets
camera_delta_frames
camera_calculation_runs
camera_calculation_results
```

Replay must not read historical data from:

```text
latest.jpg
previous.jpg
delta.jpg
```

Compression/delete actions are explicit admin actions. They must be scoped to one selected printer and one selected camera job.

Compression/delete must never touch:

```text
<cameraRoot>/<printerId>/latest.jpg
<cameraRoot>/<printerId>/previous.jpg
<cameraRoot>/<printerId>/delta.jpg
```

If source snapshots are deleted or moved to a non-readable archive, dependent delta sets and calculation runs must be deleted or marked invalid.

## REST And Dashboard References

Important dashboard/API concepts:

```text
GET /printers/{printerId}/camera/status
GET /printers/{printerId}/camera/snapshot
PUT /printers/{printerId}/camera/settings
POST /printers/{printerId}/camera/jobs/start
POST /printers/{printerId}/camera/jobs/stop
admin camera job list
admin snapshot timeline
admin delta set generation
admin calculation run creation
admin calculation run comparison
```

The snapshot endpoint may return the current preview image. Admin history views must use persisted job data.

## Manual Camera Test

Linux packages:

```bash
sudo apt install v4l-utils ffmpeg
```

Inspect connected cameras:

```bash
lsusb
v4l2-ctl --list-devices
```

Capture one test picture:

```bash
ffmpeg -f v4l2 -video_size 1280x720 -i /dev/video0 -frames:v 1 /tmp/spaghettichef-camera-test.jpg
```

Example continuous capture outside SpaghettiChef:

```bash
mkdir -p ./camera/p1/manual-test

ffmpeg \
  -f v4l2 \
  -video_size 1280x720 \
  -framerate 10 \
  -i /dev/video0 \
  -vf fps=1/2 \
  -strftime 1 \
  ./camera/p1/manual-test/snapshot_%Y%m%d_%H%M%S.jpg
```
