# CAMERA




## Installation and manual test


test  on linux :

```text
sudo apt install v4l-utils ffmpeg

lsusb
v4l2-ctl --list-devices
```

make one picture :
ffmpeg -f v4l2 -video_size 1280x720 -i /dev/video0 -frames:v 1 /tmp/aukey-test.jpg



### Example: save one image every 2 seconds:

mkdir -p ./data/camera/p1

ffmpeg \
  -f v4l2 \
  -video_size 1280x720 \
  -framerate 10 \
  -i /dev/video0 \
  -vf fps=1/2 \
  -strftime 1 \
  ./data/camera/p1/snapshot_%Y%m%d_%H%M%S.jpg




## Roadmap and Specification : 0.4.x CAMERA 


### overview

Best architecture:

```text
PrinterHub Runtime
  ├─ CameraCaptureService
  ├─ CameraDevice
  ├─ FrameAnalyzer
  ├─ SpaghettiDetectionService
  ├─ PrinterSafetyActionService
  └─ Dashboard JS view
```

For Linux/Windows compatibility, avoid OS-specific camera code at first. Use one abstraction:

```java
public interface CameraDevice {
    Optional<CameraFrame> captureFrame();
    boolean isAvailable();
    void close();
}
```

Then implementations:

```text
OpenCvCameraDevice        // later, real webcam
SnapshotFolderCameraDevice // test/dev mode
SimulatedCameraDevice      // CI tests
```

Why this is good:

* Linux camera: `/dev/video0`
* Windows camera: usually index `0`
* Java code stays the same
* dashboard only consumes REST endpoints
* CI can test without a real webcam

First version should not be “AI” yet. I would do:

```text
0.4.0 Camera monitoring foundation
- detect camera
- capture snapshot
- expose /printers/{id}/camera/snapshot
- show image in dashboard
- persist camera events

0.4.1 Spaghetti heuristic detection
- compare frames
- detect abnormal chaos/motion
- confidence score
- no automatic stop yet

0.4.2 Safety intervention
- if confidence high several times
- pause SD print with M25
- persist SPAGHETTI_DETECTED
```

For Java libraries, I would use **OpenCV Java bindings** or possibly **JavaCV**. But I would hide it behind `CameraDevice`, because native camera libraries are often the pain point on Windows.

Important: do **not** mix this into job upload/streaming. It should be a parallel monitoring task:

```text
job RUNNING
camera task observes
if anomaly → command service sends pause/abort
```

Dashboard JS only needs:

```text
GET /printers/{id}/camera/status
GET /printers/{id}/camera/snapshot
GET /printers/{id}/camera/anomalies
PUT /printers/{id}/camera/settings
```

So: Java backend for capture/detection, JS frontend for display, simulated camera for tests. That fits your current PrinterHub architecture very cleanly.


---


Yes: this should become a **separate 0.4.x feature line**, not be mixed into upload, jobs, serial transfer, or normal printer monitoring.

The cleanest direction is:

```text
0.4.x — Camera Monitoring & Visual Safety Layer
```

It should observe printers in parallel, produce visual state, persist camera events, and only later trigger printer actions through existing services.

## Core architectural decision

Do **not** make the camera part of:

```text
PrintJobExecutionService
SdCardUploadService
PrinterMonitoringTask
SerialConnection
```

Those are already critical and fragile enough.

Camera monitoring should be its own subsystem:

```text
printerhub.camera
```

with only controlled connections to:

```text
runtime
api
persistence
security
dashboard
command/job safety layer
```

The camera system should behave like this:

```text
PrinterHub Runtime
  ├─ Printer monitoring task        -> temperature / firmware / serial status
  ├─ Job execution task             -> upload / start / cancel / pause
  └─ Camera monitoring task         -> snapshot / anomaly / spaghetti suspicion
```

The camera task observes. It does **not** own printing.

---

# Recommended roadmap

## 0.4.0 — Camera monitoring foundation

Status: planned

Purpose:

Create the camera infrastructure without AI, without OpenCV dependency pain, and without automatic printer intervention.

Goals:

```text
- register camera settings per printer
- support simulated/dev camera sources
- capture snapshots
- expose camera status and latest snapshot through REST
- show snapshot in dashboard
- persist camera events
- keep camera code isolated from serial/job/upload logic
```

No spaghetti detection yet. No automatic pause. No safety action.

This version answers only:

```text
Can PrinterHub see a camera?
Can PrinterHub capture an image?
Can the dashboard show it?
Can the system work in CI without a webcam?
```

That is enough for the first mergeable step.

---

## 0.4.1 — Frame analysis and heuristic anomaly detection

Status: planned after 0.4.0

Purpose:

Introduce analysis logic, but still without printer intervention.

Goals:

```text
- compare frames over time
- calculate simple visual indicators
- persist anomaly observations
- expose confidence score
- show visual analysis state in dashboard
- avoid false automatic stops
```

Possible first heuristics:

```text
- excessive new edges in print area
- abnormal visual growth outside expected object region
- sudden chaotic filament-like lines
- movement where the print should be stable
- blob/string increase between frames
```

Output should look like:

```text
printerId=p1
cameraStatus=ACTIVE
analysisStatus=OBSERVING
spaghettiConfidence=0.62
lastAnomalyAt=...
reason=EDGE_CHAOS_INCREASE
```

Still no printer pause.

---

## 0.4.2 — Safety decision layer

Status: planned after 0.4.1

Purpose:

Turn repeated high-confidence visual anomalies into controlled safety events.

Goals:

```text
- require repeated detections before action
- introduce camera safety settings
- persist SPAGHETTI_SUSPECTED / SPAGHETTI_CONFIRMED
- optionally pause print with M25
- never hard-stop by default
- show safety action in job/printer history
```

Important: first automatic action should be **pause**, not kill.

For SD printing, likely:

```text
M25
```

For later host-streaming mode, the runtime can stop sending lines, but that is not your current priority.

---

## 0.4.3 — Real OpenCV / JavaCV camera backend

Status: planned after foundation is stable

Purpose:

Add real webcam support behind the existing abstraction.

Goals:

```text
- OpenCvCameraDevice or JavaCvCameraDevice
- support Linux /dev/video0
- support Windows camera index 0
- keep native dependency issues isolated
- document camera installation requirements
```

Do **not** start with this. Native camera libraries can waste days. Build the architecture first with simulated and folder-based snapshots.

---

## 0.4.4 — Camera dashboard polish

Status: planned

Purpose:

Make the feature useful operationally.

Goals:

```text
- live snapshot card
- camera health badge
- last frame age
- anomaly timeline
- confidence graph
- manual capture button
- camera settings panel
- safety mode indicator
```

Possible dashboard states:

```text
Camera disabled
Camera configured but unavailable
Camera active
Camera active, observing
Spaghetti suspected
Print paused by camera safety
```

---

# Packages and files I would add

## New backend package

Add:

```text
src/main/java/printerhub/camera
```

Suggested files:

```text
CameraDevice.java
CameraFrame.java
CameraStatus.java
CameraSourceType.java
CameraCaptureResult.java
CameraCaptureService.java
CameraMonitoringService.java
CameraMonitoringTask.java
CameraMonitoringScheduler.java
CameraSettings.java
CameraSettingsService.java
CameraSnapshotStore.java
CameraEventType.java
CameraEvent.java
CameraEventStore.java
FrameAnalyzer.java
FrameAnalysisResult.java
SpaghettiDetectionService.java
SpaghettiDetectionResult.java
SimulatedCameraDevice.java
SnapshotFolderCameraDevice.java
NoopCameraDevice.java
```

Later only:

```text
OpenCvCameraDevice.java
```

Do not add OpenCV in 0.4.0 unless you really want native dependency work immediately.

---

# Recommended minimal class design

## `CameraDevice`

```java
package printerhub.camera;

import java.util.Optional;

public interface CameraDevice extends AutoCloseable {

    Optional<CameraFrame> captureFrame();

    boolean isAvailable();

    @Override
    void close();
}
```

Good.

I would add one important thing:

```java
String describe();
```

So the dashboard/log can say:

```text
simulated-camera
snapshot-folder:data/camera/p1
opencv:index=0
```

Final interface:

```java
package printerhub.camera;

import java.util.Optional;

public interface CameraDevice extends AutoCloseable {

    Optional<CameraFrame> captureFrame();

    boolean isAvailable();

    String describe();

    @Override
    void close();
}
```

---

## `CameraFrame`

Keep it dumb.

```text
printerId
capturedAt
contentType
bytes
width optional
height optional
sourceDescription
```

Do not expose `BufferedImage` everywhere. Keep image decoding isolated.

---

## `CameraCaptureService`

Responsibility:

```text
- choose the right CameraDevice for a printer
- capture one frame
- store latest frame in memory
- optionally persist metadata/event
```

This should not know spaghetti logic.

---

## `FrameAnalyzer`

Responsibility:

```text
CameraFrame previous
CameraFrame current
        ↓
FrameAnalysisResult
```

This can be pure Java for 0.4.1, without OpenCV.

---

## `SpaghettiDetectionService`

Responsibility:

```text
FrameAnalysisResult + recent history + settings
        ↓
SpaghettiDetectionResult
```

It should make the decision, but not pause the printer directly.

---

## `PrinterSafetyActionService`

This can be a small bridge in 0.4.2.

Package options:

```text
printerhub.camera.PrinterSafetyActionService
```

or better:

```text
printerhub.safety.PrinterSafetyActionService
```

I prefer a new package later:

```text
src/main/java/printerhub/safety
```

because future safety features may not be camera-only.

Possible files:

```text
PrinterSafetyActionService.java
PrinterSafetyAction.java
PrinterSafetyDecision.java
PrinterSafetyMode.java
```

This service should call existing printer command/job services, not serial directly.

---

# Persistence additions

Current persistence package is already crowded, but for consistency I would keep stores there.

Add:

```text
src/main/java/printerhub/persistence/CameraSettings.java
src/main/java/printerhub/persistence/CameraSettingsStore.java
src/main/java/printerhub/persistence/CameraEvent.java
src/main/java/printerhub/persistence/CameraEventStore.java
src/main/java/printerhub/persistence/CameraSnapshotMetadata.java
src/main/java/printerhub/persistence/CameraSnapshotMetadataStore.java
```

Do **not** store full image blobs in SQLite for the first version.

Better:

```text
SQLite:
  camera settings
  camera events
  latest snapshot metadata
  anomaly metadata

Filesystem:
  actual snapshot image files
```

Example filesystem layout:

```text
data/camera/
  p1/
    latest.jpg
    snapshots/
      2026-05-18T10-42-03.123.jpg
```

Storage is a per-printer camera setting persisted in SQLite and editable from
the selected-printer Camera dashboard view.

```text
data/camera
```

---

# Database tables

## `camera_settings`

```sql
camera_settings
- printer_id TEXT PRIMARY KEY
- enabled INTEGER NOT NULL
- source_type TEXT NOT NULL
- source_value TEXT
- capture_interval_seconds INTEGER NOT NULL
- retention_snapshot_count INTEGER NOT NULL
- analysis_enabled INTEGER NOT NULL
- safety_enabled INTEGER NOT NULL
- pause_on_confirmed_spaghetti INTEGER NOT NULL
- confidence_threshold REAL NOT NULL
- confirmations_required INTEGER NOT NULL
- updated_at TEXT NOT NULL
```

For 0.4.0, many fields can exist but remain unused.

## `camera_events`

```sql
camera_events
- id INTEGER PRIMARY KEY AUTOINCREMENT
- printer_id TEXT NOT NULL
- event_type TEXT NOT NULL
- message TEXT NOT NULL
- confidence REAL
- created_at TEXT NOT NULL
```

Event types:

```text
CAMERA_ENABLED
CAMERA_DISABLED
CAMERA_AVAILABLE
CAMERA_UNAVAILABLE
CAMERA_FRAME_CAPTURED
CAMERA_CAPTURE_FAILED
CAMERA_ANALYSIS_SKIPPED
SPAGHETTI_SUSPECTED
SPAGHETTI_CONFIRMED
CAMERA_SAFETY_PAUSE_REQUESTED
CAMERA_SAFETY_PAUSE_SUCCEEDED
CAMERA_SAFETY_PAUSE_FAILED
```

## `camera_snapshot_metadata`

```sql
camera_snapshot_metadata
- id INTEGER PRIMARY KEY AUTOINCREMENT
- printer_id TEXT NOT NULL
- captured_at TEXT NOT NULL
- content_type TEXT NOT NULL
- file_path TEXT NOT NULL
- width INTEGER
- height INTEGER
- source_description TEXT
```

---

# Runtime integration: modify as little as possible

## Modify `PrinterHubRuntime.java`

Add camera services as optional subsystem.

Minimal integration:

```text
PrinterHubRuntime
  ├─ existing registry/cache/stores/services
  └─ CameraMonitoringScheduler cameraMonitoringScheduler
```

Add startup/shutdown hooks only:

```text
cameraMonitoringScheduler.start()
cameraMonitoringScheduler.stop()
```

This is the one unavoidable runtime integration point.

Keep it small.

---

## Modify `PrinterRuntimeNode.java`?

Avoid it in 0.4.0 if possible.

Camera config can be keyed by `printerId`, not embedded into the runtime node.

Later, if needed, you can add:

```java
Optional<CameraStatus> cameraStatus()
```

But for merge safety, do not touch `PrinterRuntimeNode` unless necessary.

---

## Modify `DatabaseInitializer.java`

Yes.

Add camera table creation.

This is unavoidable.

But keep it additive:

```text
CREATE TABLE IF NOT EXISTS camera_settings ...
CREATE TABLE IF NOT EXISTS camera_events ...
CREATE TABLE IF NOT EXISTS camera_snapshot_metadata ...
```

No migration of existing tables.

No changes to printer/job tables.

---

## Modify `OperationMessages.java`

Yes, but only to add constants.

Add:

```text
EVENT_CAMERA_AVAILABLE
EVENT_CAMERA_UNAVAILABLE
EVENT_CAMERA_FRAME_CAPTURED
EVENT_CAMERA_CAPTURE_FAILED
EVENT_SPAGHETTI_SUSPECTED
EVENT_SPAGHETTI_CONFIRMED
EVENT_CAMERA_SAFETY_PAUSE_REQUESTED
EVENT_CAMERA_SAFETY_PAUSE_SUCCEEDED
EVENT_CAMERA_SAFETY_PAUSE_FAILED
```

Do not reuse printer error messages. Camera events are a separate domain.

---

# API additions

Modify:

```text
src/main/java/printerhub/api/RemoteApiServer.java
```

Unfortunately this file is your central routing point, so it will need edits.

But keep them shallow. Do not put camera business logic into `RemoteApiServer`.

Add only route handlers that call a `CameraApiController`-style service if you create one.

Since the project currently seems to keep API logic mostly inside `RemoteApiServer`, I would still add a helper class to reduce future pain:

```text
src/main/java/printerhub/api/CameraApiHandler.java
```

Then `RemoteApiServer` only delegates.

Suggested endpoints:

```text
GET /printers/{id}/camera/status
GET /printers/{id}/camera/snapshot
POST /printers/{id}/camera/snapshot
GET /printers/{id}/camera/events
GET /printers/{id}/camera/settings
PUT /printers/{id}/camera/settings
```

For 0.4.0, enough:

```text
GET /printers/{id}/camera/status
GET /printers/{id}/camera/snapshot
POST /printers/{id}/camera/snapshot
GET /printers/{id}/camera/settings
PUT /printers/{id}/camera/settings
```

I would not use:

```text
GET /printers/{id}/camera/anomalies
```

yet. Better to expose anomalies as events or analysis result later:

```text
GET /printers/{id}/camera/analysis
```

---

# Dashboard additions

Add a separate view first.

## New files

```text
src/main/resources/dashboard/views/printer-camera.js
src/main/resources/dashboard/components/camera-card.js
```

Optional later:

```text
src/main/resources/dashboard/components/camera-analysis-card.js
src/main/resources/dashboard/components/camera-settings-card.js
```

## Modify existing dashboard files

Minimal required modifications:

```text
dashboard.js
nav.js
api.js
state.js
dashboard.css
```

Possibly:

```text
printer-home.js
```

Only if you want a small camera preview on the printer home page.

I would do this in two steps:

### 0.4.0 dashboard

Add a dedicated selected-printer page:

```text
Selected Printer
  Home
  Print
  Prepare
  Control
  SD Card
  Camera
  Info
  History
```

The camera page shows:

```text
- camera enabled/disabled
- source type
- source value
- availability
- last snapshot
- capture button
- last camera event
```

### 0.4.1 dashboard

Add analysis:

```text
- spaghetti confidence
- last analysis result
- reason code
- detection history
```

### 0.4.2 dashboard

Add safety:

```text
- safety mode
- pause threshold
- confirmations required
- last safety action
```

---

# Security integration

You now have:

```text
security
├── ActionPermissionResolver
├── AuthorizationService
├── DangerousActionGuard
├── Permission
```

Camera read access is low risk. Camera control and safety intervention are not.

Modify:

```text
Permission.java
ActionPermissionResolver.java
```

Add permissions such as:

```text
CAMERA_VIEW
CAMERA_CONFIGURE
CAMERA_CAPTURE
CAMERA_SAFETY_CONFIGURE
CAMERA_SAFETY_TRIGGER
```

But for 0.4.0 you can keep it simpler:

```text
CAMERA_VIEW
CAMERA_MANAGE
```

Later expand if needed.

Do not block yourself with too many permissions in the first implementation.

---

# Testing additions

Add:

```text
src/test/java/printerhub/camera
```

Suggested tests:

```text
CameraSettingsTest.java
CameraSettingsStoreTest.java
CameraEventStoreTest.java
SimulatedCameraDeviceTest.java
SnapshotFolderCameraDeviceTest.java
CameraCaptureServiceTest.java
CameraMonitoringTaskTest.java
CameraMonitoringSchedulerTest.java
SpaghettiDetectionServiceTest.java
```

Modify:

```text
DatabaseInitializerTest.java
RemoteApiServerTest.java
PrinterHubRuntimeTest.java
```

But only add camera assertions. Do not rewrite existing tests.

---

# Files I would avoid touching

Avoid touching unless absolutely necessary:

```text
SdCardUploadService.java
SerialConnection.java
SerialPortAdapter.java
JSerialCommPortAdapter.java
SimulatedPrinterPort.java
PrintJobExecutionService.java
PrintJobService.java
PrinterMonitoringTask.java
PrinterSnapshotStore.java
PrinterRuntimeNode.java
PrinterRuntimeNodeFactory.java
```

The camera feature should not destabilize those.

Especially avoid:

```text
SdCardUploadService.java
```

The SD upload logic is already complex. Camera monitoring must not enter that code path.

---

# Modified files summary

## Add

```text
src/main/java/printerhub/camera/CameraDevice.java
src/main/java/printerhub/camera/CameraFrame.java
src/main/java/printerhub/camera/CameraStatus.java
src/main/java/printerhub/camera/CameraSourceType.java
src/main/java/printerhub/camera/CameraCaptureResult.java
src/main/java/printerhub/camera/CameraCaptureService.java
src/main/java/printerhub/camera/CameraMonitoringService.java
src/main/java/printerhub/camera/CameraMonitoringTask.java
src/main/java/printerhub/camera/CameraMonitoringScheduler.java
src/main/java/printerhub/camera/CameraSettingsService.java
src/main/java/printerhub/camera/FrameAnalyzer.java
src/main/java/printerhub/camera/FrameAnalysisResult.java
src/main/java/printerhub/camera/SpaghettiDetectionService.java
src/main/java/printerhub/camera/SpaghettiDetectionResult.java
src/main/java/printerhub/camera/SimulatedCameraDevice.java
src/main/java/printerhub/camera/SnapshotFolderCameraDevice.java
src/main/java/printerhub/camera/NoopCameraDevice.java

src/main/java/printerhub/persistence/CameraSettings.java
src/main/java/printerhub/persistence/CameraSettingsStore.java
src/main/java/printerhub/persistence/CameraEvent.java
src/main/java/printerhub/persistence/CameraEventStore.java
src/main/java/printerhub/persistence/CameraSnapshotMetadata.java
src/main/java/printerhub/persistence/CameraSnapshotMetadataStore.java

src/main/java/printerhub/api/CameraApiHandler.java

src/main/resources/dashboard/views/printer-camera.js
src/main/resources/dashboard/components/camera-card.js
```

## Modify

```text
src/main/java/printerhub/runtime/PrinterHubRuntime.java
src/main/java/printerhub/api/RemoteApiServer.java
src/main/java/printerhub/persistence/DatabaseInitializer.java
src/main/java/printerhub/OperationMessages.java
src/main/java/printerhub/config/RuntimeDefaults.java
src/main/java/printerhub/security/Permission.java
src/main/java/printerhub/security/ActionPermissionResolver.java

src/main/resources/dashboard/api.js
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/state.js
src/main/resources/dashboard/components/nav.js
src/main/resources/dashboard/dashboard.css
```

Optional:

```text
src/main/resources/dashboard/views/printer-home.js
```

## Do not modify for 0.4.0

```text
src/main/java/printerhub/command/SdCardUploadService.java
src/main/java/printerhub/job/PrintJobExecutionService.java
src/main/java/printerhub/monitoring/PrinterMonitoringTask.java
src/main/java/printerhub/SerialConnection.java
```

---

# Suggested 0.4.0 implementation order

## Step A — Persistence foundation

Add:

```text
CameraSettings
CameraSettingsStore
CameraEvent
CameraEventStore
CameraSnapshotMetadata
CameraSnapshotMetadataStore
```

Modify:

```text
DatabaseInitializer
DatabaseInitializerTest
```

This gives you stable schema first.

---

## Step B — Camera abstraction

Add:

```text
CameraDevice
CameraFrame
CameraSourceType
CameraStatus
CameraCaptureResult
SimulatedCameraDevice
SnapshotFolderCameraDevice
NoopCameraDevice
```

No runtime integration yet.

---

## Step C — Capture service

Add:

```text
CameraCaptureService
CameraSettingsService
```

Tests:

```text
CameraCaptureServiceTest
CameraSettingsServiceTest
```

This validates:

```text
enabled camera -> snapshot captured
disabled camera -> skipped
missing camera -> unavailable
snapshot folder -> reads latest test image
simulated camera -> returns fixed image
```

---

## Step D — Scheduler

Add:

```text
CameraMonitoringTask
CameraMonitoringScheduler
CameraMonitoringService
```

Modify:

```text
PrinterHubRuntime
PrinterHubRuntimeTest
RuntimeDefaults
```

Add defaults:

```java
DEFAULT_CAMERA_MONITORING_INTERVAL_SECONDS = 10
DEFAULT_CAMERA_STORAGE_DIRECTORY = "data/camera"
DEFAULT_CAMERA_ENABLED = false
```

Camera should be disabled by default.

That avoids breaking existing users and CI.

---

## Step E — REST API

Add:

```text
CameraApiHandler
```

Modify:

```text
RemoteApiServer
RemoteApiServerTest
```

Endpoints:

```text
GET /printers/{id}/camera/status
GET /printers/{id}/camera/settings
PUT /printers/{id}/camera/settings
POST /printers/{id}/camera/snapshot
GET /printers/{id}/camera/snapshot
```

For snapshot image response:

```text
Content-Type: image/jpeg
Cache-Control: no-store
```

For missing snapshot:

```text
404
{
  "error": "camera_snapshot_not_available"
}
```

---

## Step F — Dashboard

Add:

```text
printer-camera.js
camera-card.js
```

Modify:

```text
api.js
dashboard.js
state.js
nav.js
dashboard.css
```

First camera dashboard should be simple:

```text
Camera
- Status
- Enabled/disabled
- Source type
- Source value
- Last frame age
- Capture now
- Latest snapshot
```

Do not add spaghetti UI in 0.4.0.

---

# Configuration model

Recommended camera source types:

```text
disabled
simulated
snapshot-folder
opencv-index
opencv-device
http-snapshot
```

For 0.4.0 implement only:

```text
simulated
snapshot-folder
disabled
```

Later:

```text
opencv-index
opencv-device
http-snapshot
```

Example settings JSON:

```json
{
  "enabled": true,
  "sourceType": "snapshot-folder",
  "sourceValue": "data/camera/p1",
  "storageDirectory": "data/camera",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20,
  "analysisEnabled": false,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3
}
```

For simulated CI:

```json
{
  "enabled": true,
  "sourceType": "simulated",
  "sourceValue": "default",
  "captureIntervalSeconds": 10
}
```

---

# Important design correction

Your draft says:

```text
CameraCaptureService
CameraDevice
FrameAnalyzer
SpaghettiDetectionService
PrinterSafetyActionService
Dashboard JS view
```

I would refine it to:

```text
CameraDevice
  low-level source abstraction

CameraCaptureService
  one snapshot capture

CameraMonitoringTask
  repeated scheduled observation

FrameAnalyzer
  visual difference/feature extraction

SpaghettiDetectionService
  decision from analysis history

PrinterSafetyActionService
  bridge from detection to printer action

CameraApiHandler
  REST layer

CameraSettingsStore / CameraEventStore / CameraSnapshotMetadataStore
  persistence
```

The missing piece in your first sketch is the **scheduler/task layer**. You need it because camera monitoring must run independently, just like printer monitoring.

---

# Safety principle

For 0.4.2, the rule should be:

```text
Detection does not equal action.
```

Instead:

```text
frame anomaly
  -> suspicion
  -> repeated suspicion
  -> confirmation
  -> safety decision
  -> command request
  -> persisted action result
```

That avoids one bad frame stopping a valid print.

Recommended default:

```text
analysisEnabled=false
safetyEnabled=false
pauseOnConfirmedSpaghetti=false
```

The user must explicitly enable safety action.

 
---
 
