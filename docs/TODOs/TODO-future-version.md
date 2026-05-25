# this is our planned roadmap for the future

 


## 0.5.x analysis engine architecture + Rust integration
see TODO-0.5-rust.md


## 0.6.x — Replay, Compression, And Simulation Review

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
 


## 0.7.x Upload and Simulation Hardening 

### 0.6.1 — Print Asset Transfer and Printer File Handling Hardening

status: planned

Goals:

* harden Mode 2 host-side handling of printable files used by file-backed jobs
* clarify how SpaghettiChef transfers, selects, or exposes prepared `.gcode` files to the printer
* improve validation and error reporting around missing, unreadable, or invalid print files
* make print-file handling more reviewable in dashboard and API
* avoid ambiguous failures during print activation caused by file-path or transfer problems

Focus:

* host-side printable file registry or controlled file reference handling
* validation of file existence, readability, and allowed type
* clearer distinction between:

  * job exists but file missing
  * file invalid
  * file cannot be transferred, selected, or exposed
  * printer-side print activation failed after transfer/selection
* persist file-related diagnostics in job execution history

Expected result:

* file-backed print jobs become safer and more predictable
* operators can understand whether a print failure is caused by printer behavior or by file-handling problems
* the runtime becomes more reliable for repeated real print activation

---

### 0.6.2 — Post-Print Review and Operational History Hardening

status: planned

Goals:

* improve reviewability after completed, failed, or cancelled print jobs
* strengthen operator visibility of final print outcome
* correlate print job lifecycle, printer events, and execution diagnostics more clearly
* make local troubleshooting easier after real print runs

Focus:

* better final job summaries
* clearer per-step execution history in dashboard
* stronger linkage between printer-side events and job-side state changes
* clearer operator-facing failure narratives for real print attempts

Expected result:

* local print operations become easier to review after the fact
* SpaghettiChef becomes more usable for repeated real-printer operations and troubleshooting
* audit value improves beyond raw event storage
 

---

### 0.6.3 — Simulation upload more realistic

status: planned

Goals:

* make simulated SD-card upload behavior correct enough for normal Step E validation
* ensure default simulated upload always succeeds end-to-end when no error mode is requested
* improve simulator credibility for checksum, resend, timeout, and SD-card file-list workflows
* separate deterministic simulation from fault-injection simulation
* make upload and recovery scenarios testable without real hardware

Scope:

This step strengthens the simulated printer behavior around SD-card upload and related serial protocol flows.

It does not try to emulate full firmware complexity.
It focuses on the parts needed to validate SpaghettiChef upload, recovery, and dashboard behavior with confidence.

#### Step A — make baseline simulated SD upload correct

Goals:

* support `M28` upload-open behavior in the simulator
* accept uploaded checksummed payload lines during an active simulated write session
* support `M29` upload-close behavior
* make `M20` list the uploaded file after a successful simulated upload
* keep the baseline `sim` mode deterministic and stable

Expected result:

* uploading to a normal simulated printer works from dashboard and API
* uploaded file appears in simulated SD listing
* Step E upload flow can be validated without a real printer

#### Step B — model simulated SD-card state explicitly

Goals:

* introduce an internal simulated SD-card file registry
* persist uploaded simulated files in memory for the runtime session
* allow delete/list/read-style workflows to operate on the same simulated state
* keep simulator behavior consistent across repeated commands in one session

Expected result:

* simulator behaves like a coherent fake firmware target instead of isolated command stubs
* SD upload, file listing, and deletion share the same internal model

#### Step C — separate success simulation from fault simulation

Goals:

* keep `sim` as the reliable happy-path mode
* introduce dedicated fault-oriented simulation modes for upload stress testing
* avoid mixing normal development simulation with random failure behavior

Planned modes:

* `sim` or equivalent baseline success mode
* `sim-random-good` for mostly recoverable disturbances
* `sim-random-bad` for heavy disturbance and likely upload failure

Expected result:

* developers can choose between stable validation and protocol stress testing
* upload regressions are easier to classify

#### Step D — add realistic protocol disturbance profiles

Goals:

* simulate resend requests during upload
* simulate occasional checksum or line-order errors
* simulate timeout-style degraded responses where appropriate
* keep fault probabilities bounded by the selected simulation mode

Expected result:

* Step E recovery logic can be exercised against believable simulated failures
* upload controller behavior can be validated before real-printer testing

#### Step E — harden tests around simulation-specific upload behavior

Goals:

* add focused tests for simulated `M28` / payload / `M29` / `M20`
* add tests proving uploaded simulated files appear in SD listing
* add tests for deterministic success mode
* add tests for recoverable and non-recoverable simulated upload faults
* keep simulator changes covered at both unit and integration level

Expected result:

* simulated upload behavior is no longer accidental or under-tested
* future protocol refactors are less likely to break simulation silently


#### Out of scope

Not part of this step:

* full Marlin emulation
* exact firmware-specific timing reproduction
* persistent simulated SD storage across application restarts
* complete simulation of all printer commands

#### Likely impacted areas

Main code:

* `src/main/java/spaghettichef/serial/SimulatedPrinterPort.java`
* `src/main/java/spaghettichef/runtime/PrinterRuntimeNodeFactory.java`

Tests:

* `src/test/java/spaghettichef/serial/SimulatedPrinterPortTest.java`
* `src/test/java/spaghettichef/command/SdCardUploadServiceTest.java`
* `src/test/java/spaghettichef/api/RemoteApiServerTest.java`





---


## 1.0.x — Central VPS Multi-Farm Management

Goal:

Introduce the central platform that manages and observes multiple local SpaghettiChef runtimes.

Important architectural rule:

The VPS does not communicate directly with USB printers.
It communicates with local SpaghettiChef runtimes.

---

### 1.0.0 — Central Multi-Farm Architecture

status: future

Target architecture:

```text
Central VPS Dashboard
        |
        v
Central Backend API
        |
        v
Central Database
        |
        v
Farm Runtime Connectors
        |
        v
Local SpaghettiChef Runtime A
Local SpaghettiChef Runtime B
Local SpaghettiChef Runtime C
```

Goals:

* define central system boundaries
* distinguish local runtime from central platform
* define farm identity and registration

---

### 1.0.1 — Farm Registration

status: future

Goals:

* register local SpaghettiChef runtimes in the central platform
* assign farm IDs
* store farm metadata
* track farm online/offline status

---

### 1.0.2 — Central Farm Status Aggregation

status: future

Goals:

* collect printer summaries from multiple farms
* show central overview of all farms
* separate local printer state from central aggregated state

Expected result:

```text
Farm A: 3 printers
Farm B: 8 printers
Farm C: offline
```

---

### 1.0.3 — Central Dashboard

status: future

Goals:

* build VPS dashboard for all registered farms
* show farm-level and printer-level summaries
* link to local runtime details where appropriate

---

### 1.0.4 — Farm Synchronization Protocol

status: future

Goals:

* decide whether farms push data to VPS or VPS pulls from farms
* define snapshot payloads
* define event payloads
* define retry and offline behavior

Possible models:

```text
push model: local runtime -> central VPS
pull model: central VPS -> local runtime
hybrid model
```

---

### 1.0.6.— Central Job Dispatch Concept

status: future

Goals:

* define whether jobs can be submitted centrally
* route central jobs to a selected farm
* keep local runtime responsible for actual printer execution

Important rule:

```text
central platform requests work
local SpaghettiChef runtime executes work
```

---

### 1.0.6 — Security and Authentication

status: future

Goals:

* secure farm-to-VPS communication
* authenticate local runtimes
* protect central dashboard
* define access model

---

### 1.0.7 — Multi-Farm Operational History

status: future

Goals:

* store central history
* aggregate farm events
* expose fleet-wide diagnostics
* support future reporting


---

## 2.0.x - Steamed G-Code


? is it necessary ?

### 2.0.0 — Streamed G-code Job Execution

status: planned

Purpose:

Introduce Mode 1 for local jobs where SpaghettiChef owns the command stream.

This is intentionally planned after the autonomous print path because streamed
printing turns SpaghettiChef into the real-time sender. It requires stronger flow
control, response tracking, cancellation behavior, and recovery rules than
autonomous printer-side execution.

Goals:

* support a streamed job execution mode for selected `.gcode` files or generated mini jobs
* send G-code commands sequentially through `PrintJobExecutionService`
* wait for firmware acceptance before sending the next command
* persist per-line or grouped execution diagnostics without flooding history unnecessarily
* support pause, cancel, and failure handling for a SpaghettiChef-owned stream
* coordinate streamed execution with monitoring so status polling does not corrupt the command flow
* keep streamed mode separate from autonomous printer-side print mode in API, persistence, and dashboard wording

Typical workflow scope:

```text
STREAMED_GCODE
├── validate printer enabled/reachable
├── validate no conflicting active job
├── validate selected .gcode file or mini-job command list
├── open controlled printer session
├── send next command
├── wait for ok / busy / error / timeout
├── persist grouped diagnostics
├── repeat until complete, cancelled, or failed
└── close controlled printer session
```

Expected result:

* SpaghettiChef can execute small controlled G-code streams itself
* mini jobs and future calibration workflows can be controlled line by line
* autonomous print mode remains available for normal printer-side file execution
* local 0.2.x printing supports both architecture models without moving central monitoring into scope


---
