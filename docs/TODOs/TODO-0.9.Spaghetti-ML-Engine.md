## TODO 0.9.x — Spaghetti ML Engine

 
The 0.9.x series focuses on machine-learning based detection, trained and validated against the datasets created during 0.7.x. 

 
# 0.9.x — Spaghetti ML Engine

## 0.9.0 — ML Dataset Preparation and First Model

### Status

Planned.

### Purpose

Prepare the 0.7.x dataset for machine-learning experiments and train the first spaghetti-detection model.

This version should focus on a minimal useful model, not on final accuracy.

### Scope

Prepare the dataset for ML training:

* split training, validation, and test data
* keep labels consistent
* avoid leaking near-identical snapshots into both training and test sets
* preserve dataset versioning
* define input image size
* define label mapping
* define model output format

Train a first model capable of classifying at least:

* normal
* spaghetti or spaghetti-like anomaly
* unclear or non-actionable frame, if useful

### Implementation Direction

Training may initially be done outside the Java backend.

Possible implementation split:

* Python for early ML training and experimentation
* Rust for model execution/inference, if suitable
* Java backend integration through a stable engine interface
* dashboard selection of the active engine

The final architecture should keep the backend independent from one specific ML experiment.

### Acceptance Criteria

* A versioned ML-ready dataset exists.
* A first model is trained.
* The model can run inference on test images.
* Results can be compared with the 0.7.x deterministic baseline.
* The model output can be represented in the same result format as deterministic algorithms.

---

## 0.9.1 — ML Engine Integration

### Status

Planned.

### Purpose

Integrate the first ML model into the SpaghettiChef runtime so that it can be selected and tested like any other detection engine.

### Scope

Add an engine abstraction that allows selecting between:

* deterministic image/delta engine
* ML inference engine
* future experimental engines

The dashboard should expose the active engine clearly.

Each inference result should be traceable:

* engine type
* engine version
* model version
* input snapshot or delta
* score or confidence
* decision
* expected label, if running against a test dataset
* processing time

### Acceptance Criteria

* The ML engine can be called from the backend.
* The dashboard can show which engine is active.
* ML results are persisted or archived.
* ML results can be compared with deterministic engine results.
* The integration does not block normal camera monitoring workflows.

---

## 0.9.2 — ML Performance Baseline

### Status

Planned.

### Purpose

Run the 0.7.1 performance and accuracy harness against the first ML engine.

### Scope

Measure:

* inference time
* CPU usage
* memory usage
* model size
* accuracy
* false positives
* false negatives
* API response time
* behavior during simulated job processing

Compare the ML engine against:

* the original deterministic engine
* the best 0.7.x deterministic variant
* manual labels from the dataset

### Acceptance Criteria

* A first ML performance report exists.
* ML results are directly comparable with 0.7.x deterministic results.
* The report shows whether ML is already better, worse, or only different.
* Bottlenecks are identified before further optimization.

---

## 0.9.3+ — ML Optimization Iterations

### Status

Planned.

### Purpose

Use separate roadmap chapters for controlled ML optimization experiments.

Each chapter should change one major variable at a time so that results remain understandable.

### Possible Optimization Topics

Future versions may test:

* different input image sizes
* raw snapshot model versus delta-image model
* combined snapshot-and-delta model
* binary classification versus multi-class classification
* lightweight model for local CPU inference
* model quantization
* cropped region-of-interest input
* bed/head masking before inference
* temporal sequence analysis
* confidence thresholds
* false-positive reduction
* job-state-aware detection

### Acceptance Criteria for Each Optimization Version

Each ML optimization version should define:

* what is being changed
* why it is being changed
* which dataset is used
* which parameters are used
* which model architecture is used
* how the model is integrated
* how results are archived
* how it compares with previous versions

Each optimization should produce a measurable report before being considered complete.
