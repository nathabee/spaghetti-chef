# SpaghettiChef Dataset

This folder contains archived camera data and dataset metadata for SpaghettiChef image-analysis and spaghetti-detection experiments.

The dataset is used to build repeatable tests for the 0.7.x engine work:

* import archived camera snapshots and delta images
* rehydrate dataset files into a local SpaghettiChef database
* run deterministic image-analysis algorithms
* compare algorithm variants
* later run performance and accuracy scripts

The dataset is not a live camera feed. It is imported filesystem data that simulates camera jobs which already happened.

---

## Purpose

Create a structured dataset of snapshots and delta images, then use it to discover deterministic image-analysis strategies for spaghetti detection.

The dataset helps answer questions such as:

* whether snapshots or deltas are better for detection
* which delta interval is useful
* how normal printer movement affects image differences
* how normal print growth differs from spaghetti formation
* how an algorithm behaves against known normal and spaghetti examples
* how fast an engine can process a dataset

---

## Dataset Layout

The dataset keeps real camera files in the same structure as runtime camera storage.

Metadata is stored separately under `dataset/json/`.

```text
dataset/
  README.md
  manifest.json

  pex01/
    delta.jpg
    latest.jpg
    previous.jpg
    snapshots/
      1/
        001298_snapshot.jpg
        001299_snapshot.jpg
        ...
      2/
        007548_snapshot.jpg
        ...
    deltas/
      1/
        1/
          001298_001299_delta.jpg
          ...
      2/
        1/
          007548_007549_delta.jpg
          ...

  json/
    manifest.json
    pex01/
      printer.json
      camera-settings.json
      jobs/
        1/
          job.json
          label.json
        2/
          job.json
          label.json

  scripts/
    initdataset.sh
    validate-dataset.sh
```

---

## Why Files And JSON Are Separated

The folder:

```text
dataset/pex01/
```

contains the copied camera archive.

It should stay close to the real runtime folder layout so files can be copied from another machine without renaming them.

The folder:

```text
dataset/json/pex01/
```

contains import metadata.

This makes it possible to:

* copy camera files from a production or development machine
* unzip them in a performance/test environment
* adjust only the JSON metadata
* run the sync script
* recreate the database rows in the new environment

After sync, the imported data should be usable like locally captured camera data, while remaining traceable as imported dataset/archive data.

---

## Runtime Camera File Layout

Snapshot files are stored under:

```text
dataset/{printerId}/snapshots/{cameraJobId}/
```

Example:

```text
dataset/pex01/snapshots/1/001298_snapshot.jpg
dataset/pex01/snapshots/1/001299_snapshot.jpg
```

Delta files are stored under:

```text
dataset/{printerId}/deltas/{cameraJobId}/{deltaSetId}/
```

Example:

```text
dataset/pex01/deltas/1/1/001298_001299_delta.jpg
dataset/pex01/deltas/1/1/001299_001300_delta.jpg
```

The job ids and delta-set ids use the real runtime folder names. They are not padded.

Valid job folders:

```text
snapshots/1/
snapshots/2/
deltas/1/1/
deltas/2/1/
```

Do not rename them to:

```text
000001
000002
step-001
```

---

## File Naming Rules

### Snapshots

Snapshot files must use this format:

```text
{snapshotId}_snapshot.jpg
```

Example:

```text
001298_snapshot.jpg
```

### Deltas

Delta files must use this format:

```text
{fromSnapshotId}_{toSnapshotId}_delta.jpg
```

Example:

```text
001298_001299_delta.jpg
```

The validator checks that the referenced source and target snapshot files exist.

---

## Metadata Layout

Printer metadata lives under:

```text
dataset/json/{printerId}/
```

Example:

```text
dataset/json/pex01/printer.json
dataset/json/pex01/camera-settings.json
```

Job metadata lives under:

```text
dataset/json/{printerId}/jobs/{cameraJobId}/
```

Example:

```text
dataset/json/pex01/jobs/1/job.json
dataset/json/pex01/jobs/1/label.json
```

---

## printer.json

Example:

```json
{
  "id": "pex01",
  "displayName": "Printer Example 01",
  "portName": "dataset://pex01",
  "mode": "sim",
  "enabled": true
}
```

The printer id is the target fake/performance printer id.

It does not need to be the same as the original production printer id.

---

## camera-settings.json

Example:

```json
{
  "enabled": true,
  "sourceType": "snapshot-folder",
  "sourceValue": "dataset/pex01",
  "storageDirectory": "dataset/pex01",
  "captureIntervalSeconds": 5,
  "retentionSnapshotCount": 100,
  "analysisEnabled": true,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3,
  "ffmpegCommand": "ffmpeg",
  "ffmpegInputFormat": "",
  "ffmpegVideoSize": "640x480",
  "ffmpegTimeoutMs": 5000,
  "ffmpegJpegQuality": 3,
  "diagnosticLoggingEnabled": true,
  "purgeAutomatically": false,
  "purgeRetentionFrequency": 10,
  "captureCropEnabled": false,
  "captureCropX1Percent": 0,
  "captureCropY1Percent": 0,
  "captureCropX2Percent": 100,
  "captureCropY2Percent": 100
}
```

---

## job.json

Example for archived job `1`:

```json
{
  "datasetJobKey": "1",
  "printerId": "pex01",
  "originalPrinterId": "p3",
  "label": "spaghetti",
  "expectedResult": "spaghetti",
  "failureStage": "clear",
  "snapshotIntervalSeconds": 5,
  "startedAt": "2026-01-01T10:00:00Z",
  "message": "Imported archived runtime camera job 1",
  "source": {
    "layout": "runtime-archive",
    "snapshotDirectory": "snapshots/1"
  },
  "deltaSets": [
    {
      "datasetDeltaSetKey": "1",
      "deltaSnapshotStep": 1,
      "methodName": "dataset-image-delta",
      "deltaDirectory": "deltas/1/1"
    }
  ]
}
```

The important fields are:

```text
datasetJobKey
printerId
expectedResult
failureStage
source.layout
source.snapshotDirectory
deltaSets[].deltaDirectory
```

---

## label.json

Example:

```json
{
  "expectedResult": "spaghetti",
  "failureStage": "clear",
  "comment": "Imported archived runtime camera job 1"
}
```

Manual labels should stay simple.

Recommended `expectedResult` values:

```text
normal
spaghetti
unclear
```

Recommended `failureStage` values:

```text
none
early
clear
severe
unknown
```

Do not manually label head movement, bed movement, or normal print growth for every file. Those are expected noise sources and should be measured by the engine.

---

## Install Dataset From Release Zip

Download the dataset zip from the GitHub release and unzip it from the repository root.

Example:

```bash
unzip spaghetti-chef-0.7.0-dataset-example-pex01.zip -d .
```

Expected result:

```text
dataset/
```

---

## Initialize Dataset In SpaghettiChef

Start the SpaghettiChef API if necessary:

```bash
mvn exec:java \
  -Dspaghettichef.databaseFile=spaghetti.db \
  -Dspaghettichef.api.port=18080 \
  -Dexec.mainClass="spaghettichef.Main"
```

From the repository root:

```bash
chmod +x dataset/scripts/initdataset.sh
chmod +x dataset/scripts/validate-dataset.sh
```

Validate first:

```bash
DATASET_ROOT=dataset PRINTER_ID=pex01 ./dataset/scripts/validate-dataset.sh --structure-only
```

Run a dry run:

```bash
API_BASE=http://localhost:18080 DATASET_ROOT=dataset PRINTER_ID=pex01 DRY_RUN=true ./dataset/scripts/initdataset.sh
```

Run the real import:

```bash
API_BASE=http://localhost:18080 DATASET_ROOT=dataset PRINTER_ID=pex01 DRY_RUN=false ./dataset/scripts/initdataset.sh
```

---

## What initdataset.sh Does

The script prepares the local runtime for dataset-based tests.

Expected behavior:

1. Check that SpaghettiChef is running.
2. Create the fake printer if it does not exist.
3. Update the fake printer if it already exists.
4. Configure the fake camera for the printer.
5. Synchronize configured camera storage files into the database.
6. Verify that camera jobs are visible through the admin API.

The script does not start real camera monitoring.

It does not call `ffmpeg`.

It does not capture live snapshots.

It bootstraps printer/camera settings from dataset JSON, then reconciles configured camera storage files with the local database.

---

## Camera Storage Database Synchronization

Expected endpoint:

```text
POST /admin/camera/storage/{printerId}/sync
```

Expected request shape:

```json
{
  "layout": "runtime-camera-storage",
  "dryRun": true,
  "syncSnapshots": true,
  "syncDeltas": true,
  "deleteRowsForMissingFiles": true,
  "reactivateDeletedSnapshotRows": true,
  "createMissingCameraJobs": true,
  "createMissingDeltaSets": true,
  "requiredConfirmation": "SYNC_CAMERA_DATASET"
}
```

Synchronization rules:

* load camera settings for `{printerId}` from the database
* use configured `cameraSettings.storageDirectory` as the source of truth
* scan runtime storage folders such as `snapshots/{cameraJobId}` and `deltas/{cameraJobId}/{deltaSetId}`
* create missing camera jobs from discovered storage folders
* create missing snapshot rows for existing snapshot files
* reactivate snapshot rows marked as deleted if the file exists again
* delete snapshot rows for missing files when requested
* create missing delta sets from discovered delta folders
* create missing delta frame rows for existing delta files
* delete delta frame rows for missing files when requested
* keep repeated sync idempotent
* never call `ffmpeg`
* never start camera monitoring
* never require the original production database
* never read dataset JSON, manifests, or labels

---

## Validate Dataset Structure

Run:

```bash
./dataset/scripts/validate-dataset.sh --structure-only
```

The validator checks:

* required content folders exist
* required metadata folders exist
* metadata files contain valid JSON
* snapshot names follow `{snapshotId}_snapshot.jpg`
* delta names follow `{from}_{to}_delta.jpg`
* delta source and target snapshots exist
* labels use accepted values

For real image validation:

```bash
./dataset/scripts/validate-dataset.sh --strict-images
```

`--strict-images` additionally checks that `.jpg` files are non-empty and start with a JPEG header.

---

## Use Dataset For Algorithm Tests

Typical future flow:

1. Import the dataset.
2. Verify delta sets.
3. Run an algorithm calculation over a delta set.
4. Store the calculation run.
5. Compare calculation result with expected labels.
6. Produce a performance and accuracy report.

Future scripts may look like:

```bash
./dataset/scripts/initdataset.sh
./dataset/scripts/validate-dataset.sh --strict-images
./tools/camera/run-dataset-baseline.sh
./tools/camera/report-dataset-results.sh
```

---

## Important Notes

Empty `.jpg` files are acceptable only for structure tests.

Real image analysis requires valid image files.

The dataset import does not depend on live printer hardware.

The fake printer uses simulation mode.

The fake camera uses imported snapshot folders.

The dataset is meant to be portable, deterministic, and repeatable.
