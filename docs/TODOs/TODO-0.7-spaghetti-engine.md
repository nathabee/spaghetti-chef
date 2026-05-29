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

 
## 0.7.1 — Performance and Accuracy Test Harness

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
* algorithm name
* algorithm version
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
* Multiple algorithm variants can be compared.

---

## 0.7.2 — Existing Engine Baseline

### Purpose

Run the existing deterministic engine against the dataset and establish the first measurable baseline.

### Scope

Use the 0.7.1 harness to evaluate the current engine.

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
 
