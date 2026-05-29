# 0.7.x — Spaghetti Engine Basic

The 0.7.x series builds a repeatable dataset and deterministic analysis baseline for spaghetti detection.

The main design decision is that manual labeling stays minimal. The human label defines only the expected detection decision. Common motion and visual noise are not manually labeled per image; they are measured automatically by the engine.

---

## 0.7.0 — Image Dataset and Algorithm Discovery

### Purpose

Create a structured dataset of snapshots and delta images, then use it to discover deterministic image-analysis strategies for spaghetti detection.

The dataset must support archived camera files copied from another environment. After synchronization, the imported files should be usable in the performance/test environment like locally captured camera jobs, snapshots, delta sets, and delta frames, while remaining traceable as imported dataset/archive data.

### Dataset Structure

The dataset keeps archived camera files in the same structure as runtime camera storage.

Dataset metadata is stored separately under `dataset/json/` so production or development camera folders can be copied, zipped, moved, and rehydrated in another environment without renaming image files.

```text
dataset/
  README.md
  manifest.json
  scripts/
    initdataset.sh
    validate-dataset.sh

  {printerId}/
    latest.jpg
    previous.jpg
    delta.jpg
    snapshots/
      {cameraJobId}/
        {snapshotId}_snapshot.jpg
    deltas/
      {cameraJobId}/
        {deltaSetId}/
          {fromSnapshotId}_{toSnapshotId}_delta.jpg

  json/
    manifest.json
    {printerId}/
      printer.json
      camera-settings.json
      jobs/
        {datasetJobKey}/
          job.json
          label.json
```

The folder `dataset/{printerId}/` contains copied camera archive data.

The folder `dataset/json/{printerId}/` contains metadata used to import that archive into the current database.

Runtime archive ids should be preserved. For example, real folders such as `snapshots/1`, `snapshots/2`, `deltas/1/1`, and `deltas/2/1` should not be renamed to artificial folders such as `000001`, `000002`, or `step-001`.

### Dataset Manifests

The root manifest describes the whole dataset package:

```text
dataset/manifest.json
```

It should define:

* dataset id
* dataset version
* layout type
* content root
* metadata root
* included printers
* included jobs
* expected result per job
* snapshot and delta locations

The metadata manifest describes the metadata folder:

```text
dataset/json/manifest.json
```

It should define:

* metadata id
* metadata version
* layout type
* included printer metadata folders
* location of printer settings, camera settings, and job metadata

### Dataset Labels

Manual labels should stay simple:

* `normal`
* `spaghetti`
* `unclear`

Optional failure-stage metadata may be added:

* `none`
* `early`
* `clear`
* `severe`
* `unknown`

The manual label answers only this question:

> Should the engine consider this input a spaghetti/failure candidate?

### Dataset Sources

The dataset should include:

* normal print snapshots
* normal print delta images
* clear spaghetti/failure snapshots
* clear spaghetti/failure delta images
* unclear cases where the human cannot decide reliably

Controlled failure scenarios may be used, for example:

* reduced bed temperature
* weak first-layer adhesion
* imperfect Z-offset within safe hardware limits

The dataset should also support archived runtime camera data copied from another machine or environment.

### Delta Handling

The dataset may contain existing archived delta images from runtime camera storage.

Additional delta sets may be generated later from the snapshot dataset using multiple intervals:

* consecutive snapshots
* short interval deltas
* medium interval deltas
* long interval deltas

The goal is to discover which interval makes spaghetti visible while reducing noise from normal printer and bed movement.

For archived runtime data, existing delta folders should keep their original layout:

```text
dataset/{printerId}/deltas/{cameraJobId}/{deltaSetId}/
```

Generated research delta sets may be added later as additional delta-set folders or as separate generated artifacts.

### Movement and Noise Handling

Printer-head movement, bed movement, lighting changes, and normal print growth should not be manually labeled for every image.

These effects are expected in normal data and should be measured automatically by the engine.

The engine should calculate metrics such as:

* global change percentage
* local change density
* large-surface motion score
* chaotic local change score
* persistence across multiple deltas
* candidate anomaly region size
* candidate anomaly region position
* crop/region-of-interest coverage

### Algorithm Discovery

Investigate deterministic image-analysis strategies before introducing machine learning.

Candidate approaches:

* compare raw snapshots versus delta images
* evaluate different delta intervals
* ignore or reduce large-surface movement
* prioritize persistent local chaotic changes
* test configured crop/region-of-interest
* test threshold-based scoring
* test connected-component scoring
* test texture/edge-density scoring
* test multi-frame persistence scoring

### Output

Each algorithm run should produce:

* input file reference
* expected result
* actual result
* algorithm name
* algorithm version
* parameters
* detection score
* calculated motion/noise metrics
* processing time
* optional debug image

### Acceptance Criteria

* A runtime-archive dataset structure exists.
* Archived camera files can remain in runtime-compatible folders without renaming.
* Dataset metadata is stored separately under `dataset/json/`.
* A manually labeled dataset exists using `normal`, `spaghetti`, and `unclear`.
* Camera storage synchronization can rehydrate archived files into the current database without reading dataset labels or manifests.
* Delta images can be imported from archive storage.
* Additional delta sets can be generated later with multiple intervals.
* Common movement/noise is not manually labeled per image.
* The engine calculates automatic motion/noise metrics.
* Several deterministic algorithm variants can run against the same dataset.
* Results are archived in a comparable format.

 
## 0.7.1 — Engine Settings

### Purpose

Move calculation engine configuration out of hardcoded dashboard and service defaults into persisted admin settings.

The system should support two separate behaviors:

* Engine settings define how an engine normally runs in this environment.
* Per-run overrides allow one experiment to use different calculation parameters without changing the persisted defaults.

Factory defaults may live in Java constants, but only as bootstrap values. Once the database contains engine settings, the database/cache values are the source of truth.

### Configuration Model

Engine settings are persisted admin settings. They are initialized from Java factory defaults only when no row exists yet, then loaded into the settings cache and editable from the dashboard.

Engine settings should include:

* engine name, for example `JAVA_BASIC_DELTA` or `RUST_IMG_ANALYZER`
* adapter type, for example `JAVA_BASIC_DELTA` or `EXTERNAL_CLI`
* configurable engine label for dashboard display
* enabled/disabled flag
* default calculation method
* default confidence threshold
* default engine parameter JSON
* default CLI method, for external CLI engines
* executable path, for external CLI engines
* process timeout, for external CLI engines
* dashboard sort order

Per-run overrides are temporary calculation inputs. They affect only the requested run, must not update the settings cache, and must be persisted on the calculation run for reproducibility.

Per-run overrides should include:

* selected engine name
* calculation method
* confidence threshold
* parameter JSON
* CLI method, if algorithm variants are being tested

Environment and process settings should not be per-run overrides:

* Executable path belongs only to engine settings.
* Timeout should normally belong only to engine settings.
* Engine enabled/disabled and engine label belong only to engine settings.

### Current Hardcoded Values To Replace

| Value | Current location | Target behavior |
| --- | --- | --- |
| `JAVA_BASIC_DELTA` default engine | backend/dashboard | engine setting default, selectable per run |
| engine dropdown options | dashboard | loaded from enabled engine settings |
| engine display labels | dashboard | configurable engine label |
| `0.85` confidence threshold | backend/dashboard | engine setting default, overridable per run |
| calculation method defaults | backend/dashboard | engine setting default, overridable per run |
| parameter JSON default | backend/dashboard | engine setting default, overridable per run |
| CLI method `delta-basic` | external CLI adapter | engine setting default, optionally overridable per run |
| executable path | system property/dev auto-discovery | engine setting only |
| timeout `10s` | external CLI adapter | engine setting only |
| local dev Rust auto-discovery | Rust Java adapter | remove after engine settings are wired |

### Implementation Steps

#### 1. Persistence Backend

Create a calculation engine settings persistence model.

Suggested table:

```text
camera_calculation_engine_settings
```

Suggested fields:

* `engine_name`
* `engine_label`
* `enabled`
* `default_method_name`
* `default_confidence_threshold`
* `default_parameter_json`
* `default_cli_method`
* `executable_path`
* `timeout_ms`
* `sort_order`
* `created_at`
* `updated_at`

Create matching persistence classes:

* `CameraCalculationEngineSettings`
* `CameraCalculationEngineSettingsStore`
* `CameraCalculationEngineSettingsService`

The store should follow the same pattern as the other settings stores: load, save/upsert, and initialize missing rows from Java defaults.

#### 2. Settings Initialization And Cache

Add Java factory defaults for the built-in calculation engines.

The settings model must support multiple calculation engines from the beginning. Do not hardcode `rust/img-analyzer` as the only Rust engine. A Rust executable path is configuration, not engine identity.

Initial defaults should include:

* Java basic delta enabled by default
* at least one external CLI engine entry for Rust-based deterministic analysis
* stable engine names, for example `JAVA_BASIC_DELTA` and `RUST_IMG_ANALYZER`
* configurable dashboard labels for each engine
* default method name per engine
* default confidence threshold per engine
* default parameter JSON per engine
* default CLI method for external CLI engines
* executable path for external CLI engines
* timeout for external CLI engines
* dashboard sort order

The model should distinguish:

```text
engineName = which implementation or executable configuration is used
methodName = which algorithm or mode is executed inside that engine
```

Examples:

```text
engineName: JAVA_BASIC_DELTA
methodName: spaghetti-heuristic

engineName: RUST_IMG_ANALYZER
methodName: delta-basic

engineName: RUST_IMG_ANALYZER
methodName: delta-connected-components
```

On startup/database initialization:

* create the engine settings table if missing
* insert missing built-in engine rows without overwriting existing admin changes
* load settings through the settings service/cache
* preserve admin-modified values across restarts and migrations
* keep executable paths and timeouts in settings, not in per-run requests
* keep local development auto-discovery out of production behavior

A configured external engine may exist while unavailable. For example, a Rust engine can be enabled but still report unavailable if its executable path is empty or invalid.

#### 3. REST API

Add admin endpoints for calculation engine settings.

Suggested endpoints:

```text
GET /admin/camera/calculation-engine-settings
PUT /admin/camera/calculation-engine-settings/{engineName}
```

The GET response should return all configured engines, including disabled engines, so the settings page can manage them.

The PUT request should allow updating:

* engine label
* enabled flag
* default method name
* default confidence threshold
* default parameter JSON
* default CLI method
* executable path
* timeout
* sort order

State-changing writes should follow existing admin/security/confirmation conventions.

The API should use `engineName` as a stable identity. Do not use the executable path as identity. Paths may change; engine names should remain stable.

#### 4. Dashboard Settings Card

Add an admin settings card for calculation engines.

The card should allow the operator to:

* enable or disable each engine
* edit the engine label
* edit default method, threshold, and parameter JSON
* edit CLI method for external CLI engines
* edit executable path and timeout for external CLI engines
* edit dashboard sort order
* save changes and see the persisted values after reload

The recalculation panel should load enabled engines from persisted settings instead of hardcoding dropdown options.

When an engine is selected, the panel should fill the calculation fields from that engine's settings:

* default method name
* default confidence threshold
* default parameter JSON
* default CLI method, if relevant

Changing these fields in the recalculation panel should affect only the requested run.

Disabled engines should not appear as normal selectable engines in the recalculation panel.

#### 5. Calculation Request Model

Calculation requests should support engine selection and per-run overrides.

Suggested request shape:

```json
{
  "engineName": "RUST_IMG_ANALYZER",
  "methodName": "delta-basic",
  "confidenceThreshold": 0.85,
  "parameterJson": "{}",
  "cliMethod": "delta-basic",
  "message": "dataset baseline run"
}
```

If `engineName` is missing:

```text
use the configured default enabled engine
```

If `methodName` is missing:

```text
use the selected engine's default method name
```

If `confidenceThreshold` is missing:

```text
use the selected engine's default confidence threshold
```

If `parameterJson` is missing:

```text
use the selected engine's default parameter JSON
```

If `cliMethod` is missing for an external CLI engine:

```text
use the selected engine's default CLI method
```

Environment and process settings must not be per-run overrides:

* executable path is settings-only
* timeout is settings-only
* engine label is settings-only
* enabled/disabled state is settings-only

Per-run overrides must be persisted on the calculation run so experiments remain reproducible.

#### 6. Calculation Service Integration

Update the calculation service to resolve engine settings before running calculations.

For scheduled/live calculation during a camera job:

* use the configured default engine
* use method, threshold, parameter JSON, CLI method, executable path, and timeout from settings
* do not require dashboard-provided values

For on-demand recalculation:

* use the selected engine settings as defaults
* apply request overrides only for allowed per-run fields
* reject attempts to override executable path, timeout, engine label, or enabled state
* persist the final resolved values on the calculation run

The persisted run should include:

* engine name
* method name
* confidence threshold
* parameter JSON
* CLI method, if relevant
* engine version, if reported by the engine
* status
* processing time
* result score
* suspicion/detection decision
* trace or metrics data, when available

The calculation flow should receive a resolved engine configuration object instead of scattered primitive values.

#### 7. External CLI Engine Contract

External calculation engines should be integrated through a generic CLI adapter.

Do not create a dedicated Java integration for every Rust executable unless a future engine requires a genuinely different protocol.

The backend should treat external engines as configured executables that follow a common CLI contract.

A typical command shape may be:

```bash
<executable> analyze-delta \
  --input <delta-image-path> \
  --method <method-name> \
  --confidence-threshold <threshold> \
  --params-json '<json>' \
  --output-json <result-json-path>
```

Stdout JSON may also be supported if that is easier, but the contract must be explicit and consistent.

Expected result shape:

```json
{
  "engineName": "RUST_IMG_ANALYZER",
  "methodName": "delta-basic",
  "engineVersion": "0.1.0",
  "inputPath": "dataset/pex01/deltas/1/1/001298_001299_delta.jpg",
  "suspected": false,
  "confidence": 0.12,
  "score": 0.18,
  "processingTimeMs": 14,
  "metrics": {
    "changedPixelRatio": 0.031,
    "localChangeDensity": 0.22,
    "largeSurfaceMotionScore": 0.68,
    "chaoticLocalChangeScore": 0.11
  },
  "debugImagePath": null,
  "message": "ok"
}
```

The backend should validate and normalize this result before persistence.

External CLI behavior:

* use executable path from settings only
* use timeout from settings only
* use default CLI method from settings unless a per-run override is provided
* return a clear unavailable/error state when executable path is missing
* return a clear unavailable/error state when executable path is invalid
* capture stderr or failure detail for diagnostics
* enforce timeout
* never rely on local development auto-discovery as production behavior

#### 8. Engine Compatibility Check

Ensure every engine follows the same settings resolution flow.

Java basic delta must:

* run from persisted settings defaults
* accept allowed per-run threshold/method/parameter overrides
* persist resolved run values
* expose a compatible result shape

External CLI engines must:

* use executable path from settings only
* use timeout from settings only
* use CLI method from settings or allowed per-run override
* persist resolved run values
* expose a compatible result shape
* report unavailable when enabled but missing an executable path

Disabled engines must:

* not appear as normal selectable engines in the recalculation panel
* not run unless an explicit admin/test path allows it
* return a clear validation error if requested through the API

#### 9. Default Engine Selection

When no `engineName` is provided, the backend should select a default engine deterministically.

Initial rule:

```text
1. Pick the enabled engine with the lowest sort order.
2. If no enabled engine is available, fall back to JAVA_BASIC_DELTA if available.
3. If no usable engine exists, fail with a clear validation error.
```

A future explicit default-engine setting may be added later, but it is not required for this step.

#### 10. Verification Workflow

Test the settings lifecycle:

* fresh database creates default engine settings
* loading settings returns Java factory defaults before any admin edit
* admin edit persists to the database
* edited settings survive service reload/restart
* all configured engines are returned by the settings API
* disabled engines are visible in settings but hidden from normal recalculation selection
* recalculation UI displays enabled engines and labels from settings
* recalculation UI uses settings defaults when no per-run overrides are changed
* per-run overrides affect only the created calculation run
* per-run overrides do not update stored settings
* calculation runs persist final resolved values
* Java engine uses the same settings resolution flow as external engines
* external CLI engine reports unavailable when enabled without an executable path
* external CLI engine reports unavailable when executable path is invalid
* external CLI engine runs successfully when a valid executable path is saved
* live/job-triggered calculation uses settings defaults
* on-demand recalculation uses settings defaults plus operator overrides

### Acceptance Criteria

* Engine settings are persisted in the database and loaded through a settings service/cache.
* Built-in engine settings are initialized from Java defaults only when missing.
* Admin changes overwrite factory defaults and survive restart.
* Engine names are stable identities and are not derived from executable paths.
* Multiple engines can be configured from the same settings model.
* Engine labels are configurable and used by the dashboard.
* The dashboard exposes a calculation engine settings card.
* The recalculation panel loads enabled engines from settings.
* The recalculation request can select an engine with `engineName`.
* If no engine is selected, the backend chooses a deterministic default.
* Method, threshold, parameter JSON, and CLI method can be overridden for one run without changing settings.
* Executable path and timeout are settings-only values.
* Calculation runs persist the final resolved values used for reproducibility.
* Java and external CLI engines both use the same settings resolution flow.
* External CLI engines use a common command/result contract.
* Missing or invalid external executables produce clear unavailable/error results.
* Local Rust development auto-discovery is removed or restricted so production behavior depends on persisted settings.

---

## 0.7.2 — Performance and Accuracy Test Harness

### Purpose

Create repeatable scripts and reports to measure deterministic engine behavior against the dataset.

### Scope

The harness should measure:

* disk usage
* memory usage
* CPU usage
* processing time
* throughput
* backend API response time
* detection accuracy
* false positives
* false negatives

The harness should support:

* single snapshot analysis
* single delta analysis
* batch dataset analysis
* simulated job-sequence analysis
* backend API analysis call
* longer-running load scenario

### Report Data

Each result row should include:

* test run ID
* dataset version
* input file
* input type
* expected result
* actual result
* pass/fail result
* engine name
* method name
* engine version
* parameters
* processing time
* detection score
* automatic motion/noise metrics
* generated debug output, if available

### Tooling Direction

Prefer open-source, portfolio-friendly tools:

* shell scripts for orchestration
* Rust CLI for engine execution
* Python for report generation, if useful
* `hyperfine` for command benchmarking
* `/usr/bin/time` or equivalent for process metrics
* CSV, JSON, Markdown, or HTML reports

### Acceptance Criteria

* A repeatable benchmark script exists.
* The script runs against the 0.7.0 dataset.
* Results are persisted in a structured format.
* Reports include both accuracy and technical metrics.
* Multiple engine/method variants can be compared.
* The harness can compare Java and external CLI engines through the same result model.

---

## 0.7.3 — Existing Engine Baseline

### Purpose

Run the existing deterministic engine against the dataset and establish the first measurable baseline.

### Scope

Use the 0.7.2 harness to evaluate the current engine.

The baseline should show:

* how often the existing engine detects spaghetti correctly
* how often it creates false positives
* how often it misses spaghetti
* whether false positives correlate with large global movement
* whether false negatives correlate with weak local spaghetti signal
* whether delta interval selection matters
* whether crop/region-of-interest improves detection

### Acceptance Criteria

* The existing engine has a documented baseline.
* At least one baseline report is archived.
* False positives and false negatives can be inspected.
* Reports include automatic motion/noise metrics.
* The baseline can later be compared with 0.8.x ML results.
