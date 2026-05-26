
# TODO 0.6.x replay

This doc is [/docs/TODOs/TODO-0.6-replay.md]



## 0.6.x — Replay, Compression, And Simulation Review


### 0.6.0 replay in dashboard
status: planned



because replay/compression benefits from having several engines and calculation methods already existing and defined in the 0.5.x.

We want replay to compare:

```text
JavaBasicDelta
vs
JavaEnhancedDelta
vs
RustCliDelta
```


Purpose:

Add admin review tools for replaying camera jobs, source snapshots, generated delta sets, and calculation results after the analysis-session and recalculation model is stable.

This step is intentionally after the live/persisted analysis correction. Replay must use real persisted data, not volatile working files such as `latest.jpg`, `previous.jpg`, or `delta.jpg`.

Goals:

* replay retained source snapshots as an accelerated image sequence
* replay delta frames from a selected delta set
* replay calculation results over time
* inspect selected source snapshot metadata
* inspect selected delta frame metadata
* inspect selected calculation result metadata
* support play, pause, stop, and replay speed controls
* allow frame-by-frame manual review
* show selected frame preview
* show selected metadata panel
* introduce explicit admin compression/archive behavior
* prevent compression from touching live working files
* clearly warn when compression deletes source data
* invalidate or remove dependent delta sets and calculation runs when source snapshots are deleted
* keep replay read-only unless the admin explicitly starts a compression/delete action

Replay modes:

```text
1. Snapshot replay
   Shows raw source snapshots from one selected camera job.

2. Delta replay
   Shows visual difference evolution from one selected delta set.

3. Calculation replay
   Shows delta frames together with persisted spaghetti-detection results.

4. Comparison replay
   Optional later mode.
   Compares two calculation runs for the same camera job or delta set.
```

Replay source rules:

```text
Snapshot replay reads:
  camera_snapshot_entries

Delta replay reads:
  camera_delta_sets
  camera_delta_frames

Calculation replay reads:
  camera_calculation_runs
  camera_calculation_results
  camera_delta_frames

Replay must not read historical data from:
  latest.jpg
  previous.jpg
  delta.jpg
```

Replay controls:

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

Replay selection:

```text
Printer
Camera job
Replay mode
Delta set, if replaying deltas or calculations
Calculation run, if replaying calculation results
Replay speed
```

Compression rule:

Compression is an explicit admin action.

It is not capture-time behavior.

It must never run automatically during camera capture, live analysis, delta generation, or calculation.

Live working files must never be compressed or deleted by this feature:

```text
latest.jpg
previous.jpg
delta.jpg
```

Source data deletion rule:

If source snapshots are compressed, deleted, or moved to a non-readable archive location, all dependent data must be deleted or marked invalid:

```text
camera_delta_sets
camera_delta_frames
camera_calculation_runs
camera_calculation_results
analysis-session references
```

Admin warning:

```text
This operation deletes or compresses source snapshots and may invalidate delta sets,
calculation runs, and analysis-session history.

Deleted source snapshots cannot be reconstructed.
```

Filesystem safety rule:

Compression/delete actions must be scoped to one selected printer and one selected camera job.

Allowed target:

```text
data/camera/<printerId>/snapshots/<cameraJobId>/
```

Allowed derived-data target:

```text
data/camera/<printerId>/deltas/<cameraJobId>/
```

Forbidden targets:

```text
data/camera/<printerId>/latest.jpg
data/camera/<printerId>/previous.jpg
data/camera/<printerId>/delta.jpg
data/camera/<otherPrinterId>/
any path outside data/camera/
```

API direction:

```text
GET /admin/camera/replay/jobs?printerId=<printerId>
  List replayable camera jobs.

GET /admin/camera/replay/jobs/{cameraJobId}/snapshots?printerId=<printerId>
  List replayable source snapshots.

GET /admin/camera/replay/delta-sets/{deltaSetId}/frames?printerId=<printerId>
  List replayable delta frames.

GET /admin/camera/replay/calculation-runs/{calculationRunId}/results?printerId=<printerId>
  List replayable calculation results.

POST /admin/camera/compression/jobs/{cameraJobId}?printerId=<printerId>
  Compress or delete selected camera job source data after confirmation.
```

Implementation notes:

* keep replay read-only
* do not mix replay with live capture scheduling
* do not create new delta frames during replay
* do not create new calculation results during replay
* replay only displays existing persisted data
* compression/delete requires explicit admin confirmation
* compression/delete must be audited
* deletion must clean database metadata and filesystem data consistently
* failed compression/delete must leave the database and filesystem in a recoverable state

Acceptance checklist:

* admin can replay source snapshots for one selected camera job
* admin can replay delta frames for one selected delta set
* admin can replay calculation results for one selected calculation run
* replay display ms controls playback speed
* admin can pause replay
* admin can step to previous and next frame
* selected frame preview displays the correct persisted file
* selected metadata panel shows persisted IDs and file paths
* replay never uses `latest.jpg`, `previous.jpg`, or `delta.jpg` as history
* compression requires confirmation
* compression is limited to the selected printer and selected camera job
* compression never touches `latest.jpg`, `previous.jpg`, or `delta.jpg`
* compression never deletes files outside the selected printer/camera-job storage
* deleting source snapshots invalidates or deletes dependent delta sets and calculation runs
* compression/delete actions are recorded in the operator audit/history
* `mvn test` passes

Out of scope:

* automatic printer pause
* automatic printer abort
* model training
* replacing the current image-delta heuristic
* cloud archive upload
* video encoding
* long-term ML dataset management
 
