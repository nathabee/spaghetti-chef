# 0.8.x — Spaghetti Developer Workbench
 

Purpose:

 
The 0.8.x series builds developer tooling around the 0.7.x datasets and engine baselines. It prepares SpaghettiChef for ML experimentation without introducing arbitrary dashboard code execution or coupling the Java backend to one specific ML toolchain.
 

 

## What each one means

### 0.8.0 — Dataset Workbench

Dashboard/admin tooling for inspecting datasets.

Scope:

```text
- list dataset versions
- inspect label distribution
- inspect snapshot/delta counts
- validate missing files
- validate metadata
- show normal/spaghetti/unclear distribution
- show imported archive origin
```

This is not ML yet. It is dataset quality control.

### 0.8.1 — Experiment Run Registry

Create a general registry for experiments.

This should track deterministic runs, parameter tests, and later ML runs.

```text
experimentRunId
datasetId
datasetVersion
engineName
methodName
parameterJson
inputType
startedAt
finishedAt
status
resultPath
reportPath
```

This becomes the backbone for reproducibility.

### 0.8.2 — Parameter Sweep Tooling

This is very useful before ML.

Instead of manually trying one parameter set, you define a sweep:

```json
{
  "engineName": "JAVA_BASIC_DELTA",
  "methodName": "spaghetti-heuristic",
  "sweep": {
    "confidenceThreshold": [0.65, 0.75, 0.85],
    "minimumChangedPixels": [8000, 12000, 18000],
    "chaoticLocalChangeScore": [0.4, 0.6, 0.8]
  }
}
```

The tool runs all combinations against the dataset and ranks them.

This is a safe “developer tool” because it changes **parameters**, not executable code.

### 0.8.3 — Report and Comparison Dashboard

Show comparable results:

```text
- Java engine baseline
- Rust engine baseline
- parameter sweep result
- best deterministic variant
- later ML model
```

Metrics:

```text
accuracy
false positives
false negatives
average processing time
p95 processing time
memory usage, if available
dataset coverage
```

### 0.8.4 — ML Training Export Package

This prepares data for Python training, but does not train inside Java.

```text
ml-runs/{runId}/
  manifest.json
  label-map.json
  preprocessing.json
  train/
  validation/
  test/
```

Important rule:

```text
No arbitrary Python execution from the dashboard.
```

The backend creates the export. Python tools consume it.

### 0.8.5 — Model Metadata Registry

Before integrating ML, define how a trained model is registered.

```text
modelId
modelVersion
sourceDatasetVersion
trainingRunId
inputType
inputSize
labelMap
framework
modelFilePath
metrics
createdAt
```

This prepares 0.9.x cleanly.

### 0.8.6 — Inference Contract Dry Run

Define the future ML engine interface before real ML becomes active.

Example:

```bash
ml-inference-runner analyze \
  --model <model-path> \
  --input <image-path> \
  --output-json <result-json-path>
```

Expected output should match your deterministic engine result model as much as possible:

```json
{
  "engineName": "ML_SPAGHETTI_MODEL",
  "engineVersion": "0.1.0",
  "modelVersion": "2026-ml-001",
  "suspected": true,
  "confidence": 0.91,
  "score": 0.91,
  "processingTimeMs": 37,
  "labelScores": {
    "normal": 0.04,
    "spaghetti": 0.91,
    "unclear": 0.05
  }
}
```
 