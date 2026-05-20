# TO DO

This file is the short-term working specification for the active 0.4.x work.

The roadmap stays as the milestone overview. This file contains the concrete implementation detail needed before coding.

---

## Current Focus

### 0.4.6 — Camera Dashboard Job Debug

Status: in progress

Purpose:

Make camera analysis sessions debug-friendly before polishing graphs. The operator should be able to start an analysis session, capture samples, see every computed value in plain table form, inspect the related files, and understand why a point is good or suspicious.

This milestone is about observability and debugging, not final visual polish.

---

## 0.4.6 Scope

### 1. Dashboard Analysis Sample Table

The Camera view should show camera analysis samples as plain data first.

Required columns:

```text
capturedAt
analyzedAt
state good/suspicious
confidence
deltaScore
changedPixelRatio
averagePixelDelta
reasonCodes
message
latestSnapshotPath
previousSnapshotPath
deltaSnapshotPath
```

Purpose:

* make the detector values visible without graph interpretation
* confirm the backend is producing useful numeric values
* define the future graph axes clearly

Future graph mapping:

```text
x-axis = capturedAt
y-axis series = confidence
y-axis series = deltaScore
y-axis series = changedPixelRatio
y-axis series = averagePixelDelta
point state = good/suspicious
point detail = reasonCodes + message + snapshot paths
```

Current decision:

Do not spend time polishing the graph until this table is useful and trusted.

---

### 2. Camera Analysis Session Card Reuse

The same camera analysis session card should be visible in:

```text
Selected Printer -> Camera
Selected Printer -> Control
```

Reason:

During real printer testing, the operator may need printer controls and camera analysis close together.

Expected behavior:

* Start session from either page
* Capture sample from either page
* Stop session from either page
* Refresh data after each action
* Keep camera code independent from serial internals

---

### 3. Archive And Snapshot File Review

Need a reviewable file list for the selected printer camera storage.

Desired UI:

```text
Camera archive card
  start time
  stop time
  refresh/list button
  table of files
    file name
    captured/modified time
    type: latest / previous / delta / archive / snapshot
    size
    link/open action
```

Default time range:

```text
start = active print job start time when available
stop  = active print job stop/end time when available, otherwise now
```

Manual override:

```text
operator can edit start and stop date/time
```

Backend need:

The current API exposes latest snapshot and analysis sample paths, but it does not yet expose a safe browsable archive index. Add an API that lists files under the selected printer camera directory without allowing arbitrary filesystem browsing.

Possible endpoints:

```text
GET /printers/{printerId}/camera/archive?from={isoInstant}&to={isoInstant}
GET /printers/{printerId}/camera/archive/{fileId}
```

Important constraints:

* keep image bytes on disk
* do not store image blobs in SQLite
* do not allow `../` path traversal
* only expose files belonging to the selected printer camera storage
* include file metadata in JSON
* use stable file ids or safe relative paths, not raw unrestricted absolute paths

---

### 4. Camera Job Session Concept

For dashboard/debug wording, a "camera job session" means:

```text
camera analysis session for a selected printer,
optionally aligned with an active print job time window
```

It is not a replacement for the existing print job model.

The implementation should keep:

```text
PrintJob
CameraAnalysisSession
CameraAnalysisSample
CameraSnapshotMetadata
```

as separate concepts.

Possible later link:

```text
CameraAnalysisSession may optionally store relatedJobId
```

Do not add that until the archive/time-window debugging proves the need.

---

### 5. Spaghetti Detection Tuning

The table should reveal whether detector values are useful.

Tune only after checking real samples from Linux and Windows captures.

Values to inspect:

```text
deltaScore
changedPixelRatio
averagePixelDelta
confidence
suspected
reasonCodes
```

Useful debug questions:

* Are good frames producing low confidence?
* Are suspicious frames producing visibly higher delta values?
* Are lighting/camera noise changes causing false positives?
* Does `changedPixelRatio` react too strongly to small camera movement?
* Does `averagePixelDelta` need a clearer scale or threshold?
* Do reason codes explain the decision enough for an operator?

Out of scope for 0.4.6:

* automatic pause tuning
* automatic abort
* final graph styling
* OpenCV/JavaCV migration

---

### 6. Windows ffmpeg Debug Support

Keep the documentation and UI examples clear for Windows.

Known important setting:

```text
ffmpeg input format = dshow
source value        = video=<exact camera name>
```

Example:

```text
video=PC-LM1E Camera
```

Common bad value:

```text
PC-LM1E Camera
```

Expected failure if missing `video=`:

```text
Malformed dshow input string
```

Potential future improvement:

If `ffmpegInputFormat=dshow` and `sourceValue` does not start with `video=` or `audio=`, the dashboard could warn the user, or the backend could normalize it.

Prefer warning first, because automatic normalization may hide operator mistakes.

---

### 7. Documentation For 0.4.6

Already updated or to keep aligned:

```text
docs/dashboard.md      user manual
docs/specification.md  technical architecture/spec
docs/rest-api.md       endpoint reference
tools/camera/README.md camera command examples
```

Documentation must stay clear about:

* storage directory is a camera setting, not `run.env`
* relative camera storage resolves from the database directory
* PrinterHub adds the printer id to the camera storage base
* Windows dshow source names need `video=`
* image files are stored on disk, SQLite stores metadata/paths only

---

## 0.4.6 Acceptance Checklist

* Camera analysis sample table is visible in Camera view.
* Camera analysis sample table is visible in Control view.
* Start/sample/stop works from both Camera and Control.
* Table contains all numeric detector values.
* Table contains reason codes and message.
* Table contains latest/previous/delta snapshot paths.
* Archive/file listing API is designed before implementation.
* Archive/file listing UI can filter by start and stop time.
* Windows ffmpeg setup is documented with `video=` example.
* The dashboard remains usable on small laptop screens.

---

## 0.4.7 — Camera Dashboard Polish

Status: planned after 0.4.6

Purpose:

Convert trusted debug data into a better operator experience.

Candidate work:

* time-series graph for analysis samples
* graph point selection
* snapshot preview for selected graph point
* archive image browsing by selected event/time
* latest snapshot auto-refresh
* last frame age
* camera event timeline
* safety mode indicator
* capture backend status
* storage path and retention status

Rule:

Do not polish graphs before 0.4.6 table/debug data is working.

---

## 0.4.8 — Code Cleanup

Status: planned after camera dashboard debugging/polish

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


+ 
* true backend camera-job scheduler if you want it to continue without the browser open.


Docs to check:

```text
README.md
docs/roadmap.md
docs/dashboard.md
docs/specification.md
docs/rest-api.md
tools/camera/README.md
```
