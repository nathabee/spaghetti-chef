# TODO — 0.5.x Rust Image Analysis Track

Goal: add Rust to PrinterHub as an independent image-analysis track first, without coupling it too early to the existing Java backend.

 
---

# 0.5.0 — Standalone Rust Image Analyzer Prototype

## Status

Planned.

## Purpose

Create a standalone Rust command-line tool that analyzes existing image files produced by PrinterHub camera jobs.

This version does **not** modify the Java backend.

The tool must be usable manually from the terminal.

## Architecture rule

PrinterHub remains unchanged.

```text
Rust does not call Java.
Java does not call Rust.
No REST API changes.
No database changes.
No dashboard changes.
```

Communication is only through files and stdout:

```text
input image files
        ↓
Rust CLI analyzer
        ↓
JSON result printed to stdout
```

## Target folder

```text
rust/img-analyzer/
  Cargo.toml
  README.md
  src/
    main.rs
    analyzer.rs
    cli.rs
    errors.rs
    image_io.rs
    result.rs
```

## Command target

```bash
cargo run -- \
  --from-snapshot <path>
  --to-snapshot <path>
  --delta-frame <path> \
  --method delta-basic \
  --threshold 0.65
```

`--delta-frame` should be optional.

## Initial algorithm

Keep it simple:

```text
1. Load from-snapshot image.
2. Load to-snapshot image.
3. Resize validation only, no automatic resizing yet.
4. Compare pixels.
5. Count changed pixels.
6. Calculate changedPixelRatio.
7. Calculate averagePixelDelta.
8. Compare result with threshold.
9. Print JSON result.
```

No OpenCV.

No machine learning.

No WASM.

No Java integration.

In 0.5.0, `--delta-frame` is accepted as an optional argument but is not yet used by the initial algorithm.

## Output JSON

```json
{
  "engineName": "RUST_CLI_DELTA",
  "engineVersion": "0.5.0",
  "algorithmVariant": "FRAME_DELTA",
  "confidence": 0.78,
  "suspected": true,
  "reasonCodes": [
    "large_delta_area"
  ],
  "message": "Large visual difference detected between snapshots.",
  "metrics": {
    "changedPixelRatio": 0.34,
    "averagePixelDelta": 0.27
  }
}
```

## Acceptance checklist

* Rust project builds with `cargo build`.
* Tool accepts command-line file paths.
* Tool reads two source snapshot files.
* Tool handles missing optional delta frame.
* Tool prints valid JSON to stdout.
* Tool prints diagnostics to stderr.
* Tool exits with code `0` on success.
* Tool exits non-zero on invalid input.
* At least one unit test exists.
* No Java code is changed.

---

# 0.5.1 — Rust Analyzer Real PrinterHub Dataset Workflow

## Status

Planned.

## Purpose

Use real PrinterHub camera files as manual test data.

This proves that the Rust analyzer works with the real 0.4.x storage model.

## Input folders

Use real files from:

```text
data/camera/<printerId>/snapshots/<cameraJobId>/
data/camera/<printerId>/deltas/<cameraJobId>/<deltaSetId>/
```

## Manual workflow

```text
1. Start PrinterHub normally.
2. Capture camera snapshots.
3. Let 0.4.x create source snapshots and delta frames.
4. Pick two snapshot files from one camera job.
5. Run Rust analyzer manually.
6. Compare result with existing Java calculation behavior.
```

## Helper scripts

Linux:

```text
rust/img-analyzer/scripts/analyze-sample.sh
```

Windows:

```text
rust/img-analyzer/scripts/analyze-sample.ps1
```

## Sample command

```bash
./target/debug/img-analyzer \
  --from-snapshot ../../data/camera/p1/snapshots/1/1_snapshot.jpg \
  --to-snapshot ../../data/camera/p1/snapshots/1/2_snapshot.jpg \
  --delta-frame ../../data/camera/p1/deltas/1/1/000001_000002_delta.jpg \
  --method delta-basic \
  --threshold 0.65
```

## Acceptance checklist

* Analyzer runs against real PrinterHub snapshot files.
* Analyzer runs with and without delta frame input.
* At least one normal case is tested.
* At least one suspicious/high-delta case is tested.
* Manual result is readable.
* No Java code is changed.

---

# 0.5.2 — Rust Result Contract Stabilization

## Status

Planned.

## Purpose

Stabilize the CLI contract before Java integration.

This is the bridge between independent Rust work and future PrinterHub integration.

## Contract file

Add:

```text
rust/img-analyzer/docs/result-contract.md
```

## Contract must define

```text
command-line arguments
stdout JSON schema
stderr behavior
exit codes
reason codes
metrics fields
engine version rule
threshold rule
failure behavior
```

## Exit codes

```text
0 = success
1 = invalid arguments
2 = input file not found
3 = image decoding failed
4 = image size mismatch
5 = analysis failed
6 = internal error
```

## stdout rule

stdout is reserved for machine-readable JSON only.

## stderr rule

stderr is reserved for human-readable diagnostics.

## Reason codes

Initial allowed values:

```text
large_delta_area
high_average_pixel_delta
image_size_mismatch
input_missing
decode_failed
analysis_failed
```

## Acceptance checklist

* Result contract is documented.
* Exit codes are documented.
* Reason codes are documented.
* Metrics are documented.
* Example success output exists.
* Example failure output exists.
* No Java code is changed.

---

# 0.5.3 — Java Rust CLI Adapter Spike

## Status

Planned after 0.5.0–0.5.2 are stable.

## Purpose

Add the smallest possible Java adapter that can call the Rust executable.

This is only a process adapter.

It must not change the live camera pipeline yet.

## Architecture rule

```text
No live camera job changes.
No dashboard changes yet.
No calculation-engine selection yet.
No Rust REST backend.
```

## New Java package

```text
src/main/java/printerhub/camera/analysis/
```

## New Java files

```text
RustCliAnalyzerProcess.java
RustCliAnalyzerRequest.java
RustCliAnalyzerResponse.java
RustCliAnalyzerException.java
RustCliAnalyzerExitCode.java
```

## Responsibilities

```text
build command safely
start process
apply timeout
capture stdout
capture stderr
parse JSON
map exit code
return response object
```

## Test strategy

Use a fake script instead of real Rust executable.

Linux fake:

```text
src/test/resources/fake-rust-analyzer-success.sh
```

Windows fake later if needed.

## Acceptance checklist

* Java can call a fake analyzer process.
* Java parses valid JSON.
* Java handles timeout.
* Java handles non-zero exit.
* Java handles invalid JSON.
* Java captures stderr.
* Unit tests pass.
* No live camera behavior changes.

---

# 0.5.4 — Configurable Calculation Engine Architecture

## Status

Planned.

## Purpose

Introduce the real PrinterHub calculation-engine abstraction.

This makes Rust selectable later without disturbing the camera pipeline.

## New engine model

```text
SpaghettiCalculationEngine
  JavaBasicDeltaCalculationEngine
  RustCliCalculationEngine
```

## Engine values

```text
DISABLED
JAVA_BASIC_DELTA
RUST_CLI_DELTA
```

Postpone:

```text
JAVA_ENHANCED_DELTA
EDGE_DENSITY
FOREGROUND_CHAOS
COMBINED_SCORE
```

until the basic engine model works.

## Database extension

Extend:

```text
camera_calculation_runs
```

Add:

```text
engine_name TEXT NOT NULL
algorithm_variant TEXT NULL
engine_version TEXT NULL
execution_duration_ms INTEGER NULL
engine_status TEXT NOT NULL
```

Suggested `engine_status` values:

```text
SUCCESS
FAILED
TIMEOUT
UNAVAILABLE
INVALID_RESPONSE
```

## Java files likely touched

```text
src/main/java/printerhub/camera/CameraCalculationRunService.java
src/main/java/printerhub/persistence/CameraCalculationRun.java
src/main/java/printerhub/persistence/CameraCalculationRunStore.java
src/main/java/printerhub/persistence/DatabaseInitializer.java
```

## Acceptance checklist

* Java basic engine remains default.
* Existing calculation runs still work.
* Calculation runs persist engine metadata.
* Rust engine can exist as an optional type.
* Missing Rust executable does not crash PrinterHub.
* `mvn test` passes.

---

# 0.5.5 — Rust Engine Integration In Recalculation Workflow

## Status

Planned.

## Purpose

Allow admin recalculation to use the Rust CLI analyzer.

This integration must use persisted data only.

## Flow

```text
Admin selects printer.
Admin selects camera job.
Admin selects delta set.
Admin selects calculation engine = RUST_CLI_DELTA.
Admin runs calculation.
Java calls Rust executable for each delta frame.
Java persists CameraCalculationResult.
```

## Safety rule

Rust failure must not crash:

```text
camera job
dashboard
Java REST API
PrinterHub runtime
```

Failure must be visible as diagnostic data.

## Java files likely touched

```text
src/main/java/printerhub/camera/CameraCalculationRunService.java
src/main/java/printerhub/api/CameraApiHandler.java
src/main/resources/dashboard/views/admin-camera-data.js
src/main/resources/dashboard/api.js
```

## Acceptance checklist

* Admin can select Rust calculation engine.
* Rust result is persisted as CameraCalculationResult.
* Java and Rust calculation runs can coexist for the same delta set.
* Failed Rust calls are visible.
* Existing Java calculation still works.
* `mvn test` passes.

---

# 0.5.6 — Engine Comparison And Benchmarking

## Status

Planned.

## Purpose

Compare Java and Rust calculation runs.

## Dashboard target

For selected camera job / delta set, show:

```text
calculation run id
engine name
algorithm variant
engine version
result count
suspected count
average confidence
execution duration
status
```

## Comparison target

Allow comparison between:

```text
JAVA_BASIC_DELTA run
RUST_CLI_DELTA run
```

for the same:

```text
printerId
cameraJobId
deltaSetId
```

## Acceptance checklist

* Admin can compare Java and Rust runs.
* Runtime duration is visible.
* Result differences are visible.
* Engine metadata is visible.
* No calculation run overwrites another.
* `mvn test` passes.

--- 