# TODO — 0.6.x Camera Replay, Purge, And Data Review

This doc is [/docs/TODOs/TODO-0.6-replay.md].

Goal: make persisted camera data easier to review and manage after the 0.4 camera storage model and 0.5 calculation-engine work.

0.6.x is not about changing capture. It is about what happens after camera jobs have produced persisted data.

## Current Storage Model

The camera root is configurable in camera settings.

Example layout:

```text
<cameraRoot>/
└── <printerId>/
    ├── latest.jpg
    ├── previous.jpg
    ├── delta.jpg
    ├── snapshots/
    │   └── <cameraJobId>/
    │       ├── 002543_snapshot.jpg
    │       ├── 002544_snapshot.jpg
    │       └── 002545_snapshot.jpg
    └── deltas/
        └── <cameraJobId>/
            └── <deltaSetId>/
                ├── 002543_002544_delta.jpg
                └── 002544_002545_delta.jpg
```

Source of truth:

```text
camera_jobs
camera_snapshot_entries
camera_delta_sets
camera_delta_frames
camera_calculation_runs
camera_calculation_results
```

Preview files are not history:

```text
latest.jpg
previous.jpg
delta.jpg
```

0.6.x features must not use these preview files as replay, purge, delete, or comparison references.

## Existing Data Tables

Implementation should reuse the existing model first:

```text
camera_settings
camera_jobs
camera_snapshot_entries
camera_delta_sets
camera_delta_frames
camera_calculation_runs
camera_calculation_results
camera_analysis_sessions
camera_analysis_samples
camera_events
operator_audit_events
```

Likely schema additions:

```text
camera_settings
  purge_automatically INTEGER NOT NULL DEFAULT 0
  purge_retention_frequency INTEGER NOT NULL DEFAULT 5

camera_snapshot_entries
  file_deleted INTEGER NOT NULL DEFAULT 0
  deleted_at TEXT NULL
  deletion_reason TEXT NULL
```

Naming note:

Use `purgeRetentionFrequency` in Java/JSON/dashboard code. Avoid the misspelled form `frequence retention`.

## Global Rules

* Replay is read-only.
* Purge removes selected physical snapshot files but keeps database metadata.
* Delete removes selected physical files and database data according to explicit admin choices.
* Compression/archive is future work unless explicitly selected in a later 0.6.x slice.
* No 0.6.x operation may touch `latest.jpg`, `previous.jpg`, or `delta.jpg`.
* No 0.6.x operation may operate outside the configured camera root for the selected printer.
* Filesystem operations must be scoped to one selected `printerId` and one selected `cameraJobId`.
* Deleting source snapshots must invalidate or delete dependent delta sets and calculation runs.
* Failed filesystem/database operations must leave a recoverable state and visible diagnostic message.

---

# 0.6.0 — Snapshot Purge

## Status

Done.

## Purpose

Free disk space by deleting some persisted source snapshot image files while keeping enough images for review and retaining metadata in the database.

Purge is different from delete:

```text
purge:
  removes selected physical snapshot files
  keeps camera_snapshot_entries rows
  marks deleted/missing files in database
  does not delete delta frames
  does not delete calculation runs

delete:
  removes selected job data more deeply
  may delete snapshots, deltas, calculations, and database rows
```

## Settings

Add camera settings:

```text
purgeAutomatically
purgeRetentionFrequency
```

Defaults:

```text
purgeAutomatically = false
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Existing setting reused:

```text
retentionSnapshotCount
```

Dashboard camera settings must display and persist all three values.

## Retention Rule

For one selected camera job:

```text
Keep all snapshots that are among the latest retentionSnapshotCount snapshots.
Also keep every purgeRetentionFrequency-th snapshot before that latest window.
Delete other physical snapshot files.
Mark deleted snapshots in camera_snapshot_entries.
```

If `purgeRetentionFrequency <= 0`, reject the settings or request.

If `retentionSnapshotCount <= 0`, keep only frequency-retained snapshots.

Purge must be idempotent. Running it twice with the same inputs must not fail and must not produce new changes after the first run.

## Example 1 — Manual Purge

Settings:

```text
purgeAutomatically = false
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Camera job creates snapshots `000001` through `001000`.

Before manual purge:

```text
1000 physical source snapshots
999 delta frames, if generated
```

After admin runs purge for that camera job:

```text
kept snapshots:
  000001, 000006, 000011, ..., 000976, 000981, 000982, ..., 001000

deleted physical snapshots:
  all non-kept snapshots

delta files:
  unchanged

camera_snapshot_entries:
  rows remain
  deleted files are marked as fileDeleted/deletedAt/deletionReason
```

## Example 2 — Automatic Purge

Settings:

```text
purgeAutomatically = true
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Camera job creates snapshots `000001` through `001000`.

At the end of the job, or during scheduled capture after safe thresholds:

```text
kept snapshots:
  000001, 000006, 000011, ..., 000976, 000981, 000982, ..., 001000
```

The implementation may run automatic purge incrementally, but it must preserve the same retention rule.

## Backend Work

Likely Java files:

```text
spaghettichef.camera.CameraSnapshotPurgeService
spaghettichef.camera.CameraSnapshotPurgeReport
spaghettichef.persistence.CameraSettings
spaghettichef.persistence.CameraSettingsStore
spaghettichef.persistence.CameraSnapshotEntry
spaghettichef.persistence.CameraSnapshotEntryStore
spaghettichef.persistence.DatabaseInitializer
spaghettichef.api.RemoteApiServer
```

Purge service inputs:

```text
printerId
cameraJobId
retentionSnapshotCount
purgeRetentionFrequency
reason/message
```

Purge report:

```text
printerId
cameraJobId
candidateCount
keptCount
deletedCount
alreadyDeletedCount
failedCount
deletedSnapshotIds
failedSnapshotIds
message
```

## API Direction

```text
POST /admin/camera/snapshot/jobs/{cameraJobId}/purge?printerId=<printerId>
```

Request JSON:

```json
{
  "retentionSnapshotCount": 20,
  "purgeRetentionFrequency": 5,
  "message": "admin manual purge"
}
```

Response JSON:

```json
{
  "printerId": "p1",
  "cameraJobId": 2,
  "candidateCount": 1000,
  "keptCount": 216,
  "deletedCount": 784,
  "alreadyDeletedCount": 0,
  "failedCount": 0,
  "message": "Snapshot purge complete."
}
```

## Dashboard Work

Camera settings:

```text
Purge automatically
Purge retention frequency
Retained snapshots
```

Admin Picture/Data Management:

```text
select printer
select camera job
show snapshot count / deleted count
run snapshot purge
show purge report
hide or disable View button for deleted snapshot files
show deleted marker for purged snapshots
```

## Acceptance Checklist

* [x] Camera settings persist `purgeAutomatically`.
* [x] Camera settings persist `purgeRetentionFrequency`.
* [x] Manual purge deletes only selected snapshot files for one selected printer/job.
* [x] Manual purge marks deleted snapshot entries in database.
* [x] Manual purge keeps latest `retentionSnapshotCount` snapshots.
* [x] Manual purge keeps every `purgeRetentionFrequency`-th older snapshot.
* [x] Manual purge is idempotent.
* [x] Purge never deletes delta files.
* [x] Purge never deletes calculation runs.
* [x] Purge never touches `latest.jpg`, `previous.jpg`, or `delta.jpg`.
* [x] Dashboard does not show View button for purged snapshot files.
* [x] `mvn test` passes.

---

# 0.6.1 — Camera Job Delete

## Status

Done.

## Purpose

Delete data associated with one selected camera job using explicit admin choices.

This is stronger than purge.

## Delete Options

Admin can choose what to remove:

```text
snapshot physical files
remove database rows for deleted files (snapshots or delta)
snapshot database rows (all)
delta physical files 
delta database rows
calculation runs/results
camera events for this job, if reliably linked
camera job row
```

Recommendation for first implementation:

```text
safe default:
  delete physical snapshots
  delete physical delta files
  delete delta sets and delta frames
  delete calculation runs and results
  delete snapshot entries
  mark camera job as deleted or delete camera job row

do not delete broad camera_events unless they have a reliable cameraJobId link
```

If there is no reliable `cameraJobId` on `camera_events`, leave events untouched and say so in the report.

## Safety Rules

* Requires admin confirmation.
* Requires selected `printerId` and `cameraJobId`.
* Must validate all target paths are under the selected printer camera root.
* Must never delete `latest.jpg`, `previous.jpg`, or `delta.jpg`.
* Must never delete another printer's files.
* Must produce a report.

## Backend Work

Likely Java files:

```text
spaghettichef.camera.CameraJobDeletionService
spaghettichef.camera.CameraJobDeletionRequest
spaghettichef.camera.CameraJobDeletionReport
spaghettichef.persistence.CameraJobStore
spaghettichef.persistence.CameraSnapshotEntryStore
spaghettichef.persistence.CameraDeltaSetStore
spaghettichef.persistence.CameraDeltaFrameStore
spaghettichef.persistence.CameraCalculationRunStore
spaghettichef.persistence.CameraCalculationResultStore
spaghettichef.api.RemoteApiServer
```

## API Direction

```text
DELETE /admin/camera/jobs/{cameraJobId}?printerId=<printerId>
```

Request JSON:

```json
{
  "deleteSnapshotFiles": true,
  "deleteSnapshotRows": true,
  "deleteDeltaFiles": true,
  "deleteDeltaRows": true,
  "deleteCalculationRuns": true,
  "deleteCameraJob": true,
  "requiredConfirmation": "DELETE_CAMERA_JOB"
}
```

## Dashboard Work

Admin Picture/Data Management:

```text
Delete camera job dialog
checkboxes for delete options
warning with affected counts
confirmation field/button
report after delete
reload job list after success
```

## Acceptance Checklist

* [x] Admin can delete selected camera job data with confirmation.
* [x] Delete report shows removed snapshots, deltas, calculation runs, and failures.
* [x] Delete validates selected printer/job ownership.
* [x] Delete never touches preview files.
* [x] Delete never touches another printer's files.
* [x] Delete handles already-missing files.
* [x] Delete removes camera events with a reliable `cameraJobId` link.
* [x] `mvn test` passes.

---

# 0.6.2 — Delta Set Delete

## Status

Done.

## Purpose

Delete one selected delta set and its physical delta files.

This is useful when a delta set was generated with the wrong step or method.

## Scope

For selected:

```text
printerId
cameraJobId
deltaSetId
```

Delete:

```text
camera_delta_frames rows
camera_delta_sets row
physical delta files under <cameraRoot>/<printerId>/deltas/<cameraJobId>/<deltaSetId>/
dependent camera_calculation_runs
dependent camera_calculation_results
```

Do not delete:

```text
source snapshots
camera job
preview files
other delta sets for the same job
```

## API Direction

```text
DELETE /admin/camera/delta-sets/{deltaSetId}?printerId=<printerId>
```

Request JSON:

```json
{
  "deleteCalculationRuns": true,
  "requiredConfirmation": "DELETE_DELTA_SET"
}
```

## Acceptance Checklist

* [x] Admin can delete one selected delta set.
* [x] Physical delta files for that delta set are deleted.
* [x] Calculation runs/results depending on that delta set are deleted or marked invalid.
* [x] Source snapshots remain untouched.
* [x] Other delta sets remain untouched.
* [x] Preview files remain untouched.
* [x] `mvn test` passes.

---

# 0.6.3 — Calculation Result Visual Inspector

## Status

Done.

## Purpose

Let the user understand one calculation result visually.

Show the three persisted images that explain a calculation result:

```text
from snapshot
to snapshot
delta frame
```

Also show calculation metadata:

```text
calculation run id
engine name
algorithm variant
engine version
confidence
suspected
reason codes
message
delta frame id
source snapshot ids
file paths
created at
```

## Opened From

The inspector should be reusable from:

```text
Admin Picture/Data Management:
  calculation trace row View button

Selected printer Camera view:
  Spaghetti trace review row View button

Running camera job:
  optional live-fed inspector for the newest calculation result
```

First implementation should be manual/open-on-demand. Automatic live feed can wait until the card works.

## Backend Work

Prefer one read-only endpoint that returns all metadata and URLs/ids needed by the dashboard.

API direction:

```text
GET /admin/camera/calculation-results/{calculationResultId}/visual?printerId=<printerId>
```

Response shape:

```json
{
  "calculationResult": {},
  "calculationRun": {},
  "deltaFrame": {},
  "fromSnapshot": {},
  "toSnapshot": {},
  "imageUrls": {
    "fromSnapshot": "...",
    "toSnapshot": "...",
    "deltaFrame": "..."
  }
}
```

Image-serving endpoints must refuse deleted/missing files with a clear 404 JSON or image fallback behavior.

## Dashboard Work

New reusable card/component:

```text
Calculation result inspector
```

Layout:

```text
three image previews
metadata panel
engine/result summary
missing/deleted file warning
```

## Acceptance Checklist

* [x] Trace rows have a View button.
* [x] Inspector shows from snapshot, to snapshot, and delta image.
* [x] Inspector shows calculation metadata.
* [x] Inspector handles purged/deleted snapshot files.
* [x] Inspector never uses preview files as history.
* [x] `mvn test` passes.

---

# 0.6.4 — Replay In Dashboard

## Status

Done.

## Purpose

Add admin review tools for replaying persisted camera jobs, source snapshots, delta sets, and calculation results.

Replay is read-only.

CR Dashboard CSS :
- Lists must be put in scrollable. See cards : Camera snapshot jobs, Replay timeline, Spaghetti trace review. The same way it was done in the camera menu : card events and analyse session

## Replay Modes

```text
Snapshot replay:
  source snapshots from one camera job

Delta replay:
  delta frames from one delta set

Calculation replay:
  delta frames plus one calculation run's results

Comparison replay:
  optional later mode using 0.5.6 comparison data
```

## Replay Source Rules

Snapshot replay reads:

```text
camera_snapshot_entries
```

Delta replay reads:

```text
camera_delta_sets
camera_delta_frames
```

Calculation replay reads:

```text
camera_calculation_runs
camera_calculation_results
camera_delta_frames
```

Replay must not read history from:

```text
latest.jpg
previous.jpg
delta.jpg
```

## Replay Controls

```text
Play
Pause
Stop
Previous frame
Next frame
Replay display ms
Frame counter
Selected frame preview
Selected metadata panel
Selected source snapshot pair
Selected delta frame
Selected calculation result
```

## Replay Selection

```text
Printer
Camera job
Replay mode
Delta set, if replaying deltas or calculations
Calculation run, if replaying calculation results
Replay speed
```

## API Direction

Prefer reusing existing admin list endpoints where possible.

Add only missing read-only helpers:

```text
GET /admin/camera/replay/jobs?printerId=<printerId>
GET /admin/camera/replay/jobs/{cameraJobId}/snapshots?printerId=<printerId>
GET /admin/camera/replay/delta-sets/{deltaSetId}/frames?printerId=<printerId>
GET /admin/camera/replay/calculation-runs/{calculationRunId}/results?printerId=<printerId>
```

If existing endpoints already provide this data, document the reuse and avoid duplicate API routes.

## Dashboard Work

Admin Picture/Data Management:

```text
Replay panel
mode selector
play/pause/stop controls
previous/next frame controls
speed input
image preview
metadata panel
missing/deleted file indicators
```

## Acceptance Checklist

* [x] Admin can replay source snapshots for one selected camera job.
* [x] Admin can replay delta frames for one selected delta set.
* [x] Admin can replay calculation results for one selected calculation run.
* [x] Replay display ms controls playback speed.
* [x] Admin can pause replay.
* [x] Admin can step to previous and next frame.
* [x] Selected frame preview displays the correct persisted file.
* [x] Selected metadata panel shows persisted IDs and file paths.
* [x] Replay marks purged/deleted files clearly.
* [x] Replay never uses `latest.jpg`, `previous.jpg`, or `delta.jpg` as history.
* [x] `mvn test` passes.

---




## 0.6.5 — Capture Crop Region

## Status

Planned.

## Purpose

Add a configurable capture crop region so that camera snapshots can focus on the relevant printer area instead of storing and analyzing the full raw camera frame.

At the moment, the camera snapshot uses the complete frame provided by ffmpeg:

```text
(0, 0) to (Xmax, Ymax)
```

For a wide webcam image, this may include irrelevant areas such as space under the bed, too much left side, too much right side, or background around the printer. This wastes storage and sends unnecessary pixels into the frame analysis pipeline.

The goal is to define a rectangular region of interest inside the raw camera frame and store only that cropped region as the snapshot.

The crop region is configured as percentages of the raw frame dimensions, not as absolute pixels.

Default crop region:

```text
P1 = (0%, 0%)
P2 = (100%, 100%)
```

This means that, by default, the full camera frame is used and existing behavior is preserved.

Example crop region:

```text
P1 = (20%, 10%)
P2 = (80%, 90%)
```

This means:

* start the snapshot at 20% of the raw frame width
* start the snapshot at 10% of the raw frame height
* end the snapshot at 80% of the raw frame width
* end the snapshot at 90% of the raw frame height

The crop settings are only needed at capture time. After the cropped snapshot has been stored, later processing such as delta generation, frame analysis, replay, and dashboard display should use the stored snapshot file directly.

## Required Settings

Add camera settings for the capture crop region.

Recommended names:

```text
captureCropEnabled = false
captureCropX1Percent = 0
captureCropY1Percent = 0
captureCropX2Percent = 100
captureCropY2Percent = 100
```

Validation rules:

* `captureCropX1Percent` must be between `0` and `100`
* `captureCropY1Percent` must be between `0` and `100`
* `captureCropX2Percent` must be between `0` and `100`
* `captureCropY2Percent` must be between `0` and `100`
* `captureCropX1Percent` must be smaller than `captureCropX2Percent`
* `captureCropY1Percent` must be smaller than `captureCropY2Percent`
* default values must preserve the full raw frame

## Backend Requirements

The backend must support the new crop settings in the camera configuration model.

Required backend behavior:

* add default values for the crop settings
* persist the crop settings in the database
* expose the crop settings through the camera settings API
* cache or load the crop settings consistently with the existing camera settings mechanism
* apply the crop before storing the snapshot file
* keep existing behavior unchanged when the crop region is disabled or set to the full frame
* validate invalid crop values before saving or before applying them
* avoid applying the crop later in the delta, analysis, or replay pipeline

The crop must happen during snapshot creation, after the raw ffmpeg frame is available and before the final snapshot file is stored.

## Frontend Requirements

The frontend must expose the crop settings in the camera configuration UI.

The user must be able to configure the crop region in two ways:

1. by editing percentage values directly in the camera settings card
2. by graphically selecting the crop region on the displayed snapshot

## Snapshot Card Requirements

Add a button to the snapshot card:

```text
Define crop region
```

The button must only be enabled when no camera job is active.

When the user clicks the button, the displayed snapshot enters a selection mode where the user can graphically select the rectangular crop region.

During selection mode, the button label changes to:

```text
Validate crop region
```

When the user confirms the selection, the selected rectangle is converted into percentage values and written to the camera settings form.

The user can then save the camera settings using the normal settings save button.

The graphical selection is a helper for editing the same crop settings. It must not introduce a second hidden configuration mechanism.

## Camera Settings Card Requirements

The camera settings card should be reorganized into clearer sections.

The current layout is difficult to read because unrelated settings are displayed together.

Recommended sections:

### Camera behavior

```text
Enable camera monitoring
Enable frame analysis
Enable safety decisions
Pause on confirmed spaghetti
Enable camera diagnostic logs
```

### Camera source

```text
Source type
Storage directory
Source value
ffmpeg command
ffmpeg input format
ffmpeg video size
ffmpeg timeout ms
ffmpeg JPEG quality
Capture interval seconds
```

### Capture crop region

```text
Enable capture crop region
Crop X1 %
Crop Y1 %
Crop X2 %
Crop Y2 %
```

Default values:

```text
Enable capture crop region = false
Crop X1 % = 0
Crop Y1 % = 0
Crop X2 % = 100
Crop Y2 % = 100
```

### Snapshot purge

```text
Retained snapshots
Purge frequency
Purge automatically
```

The `Purge automatically` checkbox must use the same visual design as the other checkboxes.

### Analysis thresholds

```text
Confidence threshold
Confirmations required
```

### Save action

```text
Save camera settings
```

## Naming Requirements

Use `crop` or `crop region` in the roadmap, code, and UI.

Preferred wording:

```text
Capture crop region
Define crop region
Validate crop region
Enable capture crop region
```

Avoid using `zoom` for this feature.

Reason:

* this feature does not control optical camera zoom
* it does not necessarily scale the image
* it selects a rectangle from the raw camera frame
* `crop region` is more technically accurate
* `region of interest` is also correct, but less user-friendly for the dashboard

The term `region of interest` may be used internally or in documentation, but the dashboard should preferably use `crop region`.

## Goals

* reduce irrelevant image area in stored snapshots
* reduce irrelevant pixels sent to frame analysis
* allow wide webcams to focus on the printer bed and print area
* preserve existing behavior by default
* make crop configuration understandable in the dashboard
* allow graphical crop selection from the snapshot card
* keep crop logic limited to the snapshot capture phase
* avoid changing delta, replay, and analysis logic unnecessarily

## Acceptance Checklist

* Camera settings include persisted crop region values.
* Default crop values preserve the full raw camera frame.
* Backend validates crop percentage values.
* Backend applies the crop before storing the snapshot file.
* Backend does not require crop values after the snapshot has been stored.
* Delta generation works from the already-cropped stored snapshots.
* Frame analysis works from the already-cropped stored snapshots.
* Replay/dashboard image display works from the already-cropped stored snapshots.
* Camera settings API exposes the crop settings.
* Camera settings UI allows direct editing of crop percentage values.
* Snapshot card provides a graphical crop selection mode.
* `Define crop region` is disabled while a camera job is active.
* During graphical selection, the button label changes to `Validate crop region`.
* Validating the graphical selection updates the crop percentage settings.
* `Purge automatically` checkbox uses the same design as the other checkboxes.
* Camera settings card is grouped into meaningful sections.
* The roadmap and UI do not call this feature `zoom`.

---

# 0.6.6 — Simulation Review

## Status

Planned.

## Purpose

Make simulated camera/printer setups easier to inspect and validate.

This is useful because a fake printer can intentionally be used as a camera-only monitor for a PC-connected camera.

## Goals

```text
show whether printer is real/simulated/placeholder
show whether camera source is real/simulated/snapshot-folder
show camera root and source path
show generated camera jobs for simulated setups
support replay of simulated camera data exactly like real camera data
```

## Acceptance Checklist

* [ ] Dashboard clearly distinguishes simulated camera/printer data.
* [ ] Replay works for simulated camera jobs.
* [ ] Purge/delete safety rules are identical for real and simulated data.
* [ ] `mvn test` passes.

---

# Out Of Scope For 0.6.x

* automatic printer pause
* automatic printer abort
* model training
* replacing the current image-delta heuristic
* cloud archive upload
* video encoding
* long-term ML dataset management
* changing camera capture ownership rules
* using `latest.jpg`, `previous.jpg`, or `delta.jpg` as history
