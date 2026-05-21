# TO DO

This file is the short-term working specification for the active 0.4.x camera work.

The roadmap stays as the milestone overview. This file contains the concrete implementation detail needed before coding.

---

## Current Focus

### 0.4.7 — Camera Picture And Data Management

Status: implemented

Purpose:

Build the backend and admin-only UI structure needed to manage camera pictures, camera analysis data, cleanup, replay, and future spaghetti-detection recalculation.

0.4.6 made camera debugging visible in the Camera and Control pages. 0.4.7 should now separate operator monitoring from admin picture/data management.

---

## 0.4.6 Delivered

0.4.6 delivered the debug-first camera dashboard work:

* camera analysis sample table in the Camera view
* camera analysis sample table in the Control view
* start/sample/stop analysis session actions from both views
* visible detector values:
  * captured at
  * analyzed at
  * state
  * confidence
  * delta score
  * changed pixel ratio
  * average pixel delta
  * reason codes
  * message
  * latest/previous/delta paths
* safe camera archive API
* archive gallery showing only `archive/*`
* start/stop time filters for archive review
* latest snapshot sync button using `Capture interval seconds`
* dashboard-local automatic analysis sampling while a session is active
* scrollable event, analysis, and archive panels
* Windows ffmpeg documentation and dashboard settings support

Known limitation:

The sync and automatic sampling added in 0.4.6 are dashboard-local. They run while the dashboard page is open. A true backend/headless camera-job scheduler is still future work.

---

## 0.4.7 Scope

### 1. Admin Picture/Data Management Menu

Add a new admin-only dashboard area:

```text
Admin -> Picture/Data Management
```

Access rule:

```text
ADMIN profile only
```

The first 0.4.7 UI can be mostly placeholders, but the placeholders must describe the workflows clearly enough that the backend/API work has a visible home.

Initial cards:

* Archive picture browser
* Delete pictures/data by job
* Replay camera job
* Recalculate detector values
* Storage/retention status

Move or duplicate the current `Camera files / Snapshot archive` concept toward this admin area. The Camera page may keep a lightweight latest-snapshot/operator view, but picture cleanup and replay belong in Admin.

---

### 2. Camera Picture Ownership And Job Link

We need to know which pictures belong to which print job.

Backend target:

```text
CameraArchiveEntry
  id
  printerId
  jobId optional
  analysisSessionId optional
  capturedAt
  archivedAt
  archivePath
  deltaPath optional
  contentType
  sizeBytes
  sourceType
  message optional
```

Rules:

* archive entries belong to a printer
* archive entries should link to a job id when a print job is active or otherwise selected
* archive file names should include date, time, and job id when job id is known
* SQLite stores metadata and paths only
* image bytes stay on disk
* no image blobs in SQLite

Suggested filename shape:

```text
archive/{jobId}/{yyyyMMdd_HHmmss_SSS}_{jobId}.jpg
```

If no job id is known:

```text
archive/unassigned/{yyyyMMdd_HHmmss_SSS}_unassigned.jpg
```

Open question:

Whether job id should be inferred from the active print job or explicitly attached when a camera analysis session starts. Prefer explicit metadata once the active job lookup is reliable.

---

### 3. Snapshot Folder Must Be Cyclic

There is a current problem: every capture can remain in `snapshots/`, so the folder grows like an archive.

Desired behavior:

```text
latest.jpg    current working image
previous.jpg  previous working image
delta.jpg     current delta working image
snapshots/    bounded cyclic working history
archive/      long-term review pictures
```

Rules:

* captures happen every `Capture interval seconds`
* `Retained snapshots` is the maximum number of files kept in `snapshots/`
* when the snapshot count exceeds the limit, delete the oldest snapshot files
* `snapshots/` is for short working history, not permanent storage
* `archive/` is for selected review images that may be cleaned by job id later

Potential new setting:

```text
Archive every N snapshots
```

Reason:

Archiving every capture during a 5 hour job can produce too many files. We may need a setting that controls how often a capture becomes an archive entry.

Possible default:

```text
archiveEverySnapshotCount = 1
```

This preserves current behavior first, then allows reducing storage later.

---

### 4. Archive Cleanup By Job

Admin must be able to physically clean camera data from disk and related rows from SQLite.

Backend target:

```text
GET    /admin/camera/archive/jobs
GET    /admin/camera/archive/jobs/{jobId}
DELETE /admin/camera/archive/jobs/{jobId}
```

Delete behavior:

* delete archive pictures for the job from disk
* delete delta/archive generated files for the job when they are tracked
* delete or mark related camera archive metadata
* delete or mark related camera analysis samples if requested
* never delete files outside the configured camera storage
* require admin permission
* require confirmation in the frontend before destructive cleanup

Possible delete mode:

```text
DELETE /admin/camera/archive/jobs/{jobId}?includeAnalysis=true
```

Open question:

Should delete remove rows physically or mark them as deleted? For early local lab usage, physical delete may be acceptable, but the API should return a clear deletion report.

Deletion report:

```text
jobId
deletedFileCount
deletedBytes
deletedAnalysisSampleCount
failedFiles[]
message
```

---

### 5. Replay Camera Job

Admin should be able to replay archived camera data for a selected job.

UI target:

```text
Admin -> Picture/Data Management -> Replay job
  job id selector/input
  display ms setting
  Play / Pause / Stop
```

Replay frame layout:

```text
Left   archived picture at that moment
Middle delta associated with that moment
Right  spaghetti detection analysis values
```

Right panel values:

```text
Captured at
Analyzed at
State
Confidence
Delta score
Changed pixels
Average delta
Reason codes
Message
```

Backend target:

```text
GET /admin/camera/archive/jobs/{jobId}/timeline
```

Timeline response should include enough metadata to render replay without scanning arbitrary paths:

```text
frameId
capturedAt
archiveFileId
deltaFileId optional
analysisSampleId optional
confidence
deltaScore
changedPixelRatio
averagePixelDelta
suspected
reasonCodes
message
```

The frontend can play the timeline client-side using the configured `display ms`.

New setting:

```text
Replay display ms
```

Initial default suggestion:

```text
500 ms
```

---

### 6. Recalculate Detector Values Placeholder

Admin needs a future simulation tool for checking spaghetti detection parameters against real archived pictures.

0.4.7 should add backend shape and frontend placeholders. Full algorithm tuning can be later if needed.

UI target:

```text
Admin -> Picture/Data Management -> Recalculate detection
  select job id
  edit detector parameters
  run/recalculate placeholder
  results table
```

Results table:

```text
archive file
delta file
Captured at
Analyzed at
State
Confidence
Delta score
Changed pixels
Average delta
Reason codes
Message
```

Backend target placeholder:

```text
POST /admin/camera/archive/jobs/{jobId}/recalculate-preview
```

Request shape:

```text
confidenceThreshold
changedPixelRatioThreshold
averagePixelDeltaThreshold
minimumDeltaScore
other detector parameters as needed
```

Important:

This recalculation must not overwrite production analysis rows unless a later milestone explicitly adds an apply/save action.

---

### 7. Camera Archive API Rules

All picture/data management APIs must follow these constraints:

* admin-only for destructive operations
* never expose unrestricted absolute paths as stable public identifiers
* use stable file ids or metadata ids
* reject `../` path traversal
* restrict file serving/deleting to configured camera storage
* keep image bytes on disk
* keep SQLite as metadata/path storage
* log deletion/replay/recalculation actions clearly
* return useful error messages when files are missing

---

## 0.4.7 Acceptance Checklist

* Admin-only Picture/Data Management menu placeholder exists.
* Placeholder cards describe archive browsing, cleanup, replay, and recalculation.
* Camera archive entries can be associated with job id when known.
* Archive filenames include date/time and job id when known.
* `snapshots/` retention is enforced using `Retained snapshots`.
* `archive/` remains separate from `snapshots/`.
* Backend can list archive pictures by job id.
* Backend can produce a replay timeline for a job.
* Backend has a safe delete-by-job path with deletion report.
* Backend has a recalculation-preview API shape or service placeholder.
* All destructive picture/data operations require ADMIN.

---

## 0.4.8 — Camera Dashboard Polish And Administration

Status: implemented

Purpose:

Connect the picture/data management backend to a usable admin dashboard and polish the normal camera dashboard.

Candidate work:

* connect admin archive listing to real backend data
* scope the admin Picture/Data Management page by selected printer
* load only archive jobs for the selected printer
* keep admin delete/replay API calls scoped to one printer when `printerId` is supplied
* connect delete-by-job UI with confirmation
* connect replay controls to backend timeline
* display archived picture, delta image, and analysis values during replay
* connect recalculation preview enough to show result tables
* time-series graph for analysis samples
* graph point selection
* snapshot preview for selected graph point
* archive image browsing by selected event/time
* latest snapshot operator polish
* last frame age
* camera event timeline
* safety mode indicator
* capture backend status
* storage path and retention status
* non-official correction: Spaghetti trace review lists newest samples first
* non-official correction: Spaghetti trace review loads a bounded recent sample window instead of the full session history

Rule:

Do not let graph polish block picture cleanup and retention correctness.

---

## 0.4.9 — Code Cleanup

Status: planned after camera picture/data management and dashboard polish

Cleanup checks:

```text
RuntimeDefaults.java   numeric/default runtime values only
OperationMessages.java event names, error keys, fixed message vocabulary
Service classes        orchestration only, no duplicated event constants
```

Rules:

* avoid fallback string constants that hide missing `OperationMessages`
* if an operation message does not exist, code should fail at compile time
* continue moving direct console logging to `PrinterHubLog`
* keep camera code independent from serial communication internals
* keep image blobs out of SQLite
* consider a true backend camera-job scheduler if capture/sampling must continue after browser close

Docs to check:

```text
README.md
docs/roadmap.md
docs/dashboard.md
docs/specification.md
docs/rest-api.md
tools/camera/README.md
```
