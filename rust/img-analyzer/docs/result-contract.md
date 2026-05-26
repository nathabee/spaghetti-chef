# Rust Image Analyzer Result Contract

This contract describes the stable command-line behavior for the standalone SpaghettiChef Rust image analyzer.

Version: `0.5.4`

The analyzer is still an independent CLI. Java does not call it yet.

## Command

Binary name:

```text
img-analyzer
```

Supported arguments:

| Argument | Required | Default | Description |
|----------|----------|---------|-------------|
| `--from-snapshot <path>` | yes | none | Source snapshot before the observed change. |
| `--to-snapshot <path>` | yes | none | Source snapshot after the observed change. |
| `--delta-frame <path>` | no | none | Optional existing SpaghettiChef delta frame. Accepted for dataset compatibility; not used by `delta-basic` yet. |
| `--method <name>` | no | `delta-basic` | Analysis method selector. Only `delta-basic` is currently defined. |
| `--threshold <value>` | no | `0.65` | Decision threshold from `0.0` to `1.0`, inclusive. |

Example with caller-supplied SpaghettiChef camera paths:

```bash
./target/debug/img-analyzer \
  --from-snapshot <cameraRoot>/<printerId>/snapshots/<cameraJobId>/002523_snapshot.jpg \
  --to-snapshot <cameraRoot>/<printerId>/snapshots/<cameraJobId>/002524_snapshot.jpg \
  --delta-frame <cameraRoot>/<printerId>/deltas/<cameraJobId>/<deltaSetId>/002523_002524_delta.jpg \
  --method delta-basic \
  --threshold 0.65
```

## stdout

`stdout` is reserved for machine-readable JSON only.

On success, the analyzer writes exactly one JSON result object to `stdout`.

No progress messages, diagnostics, warnings, or logs may be written to `stdout`.

## stderr

`stderr` is reserved for human-readable diagnostics.

Examples:

```text
Input file not found: ../../camera/p1/snapshots/1/002523_snapshot.jpg
```

```text
Image size mismatch: from-snapshot is (640, 480), to-snapshot is (800, 600)
```

Failure diagnostics are not part of the JSON contract yet.

## Success JSON Schema

The result object uses camelCase fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `engineName` | string | yes | Analyzer engine identifier. Current value: `RUST_CLI_DELTA`. |
| `engineVersion` | string | yes | Analyzer package version. Must match the Cargo package version, which is synced from the repository `VERSION` file. |
| `algorithmVariant` | string | yes | Algorithm identifier. Current value: `FRAME_DELTA`. |
| `confidence` | number | yes | Decision confidence from `0.0` to `1.0`. Current value is `max(changedPixelRatio, averagePixelDelta)`. |
| `suspected` | boolean | yes | `true` when the analyzer considers the frame pair suspicious. |
| `reasonCodes` | string array | yes | Stable reason identifiers explaining a suspicious result. Empty when not suspicious. |
| `message` | string | yes | Human-readable result summary. |
| `metrics` | object | yes | Numeric analyzer metrics. |

Example success output:

```json
{
  "engineName": "RUST_CLI_DELTA",
  "engineVersion": "0.5.4",
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

## Metrics

| Metric | Type | Range | Description |
|--------|------|-------|-------------|
| `changedPixelRatio` | number | `0.0` to `1.0` | Ratio of pixels whose average RGB channel delta is greater than or equal to `threshold`. |
| `averagePixelDelta` | number | `0.0` to `1.0` | Average normalized RGB delta across all compared pixels. |

All metrics are calculated from `--from-snapshot` and `--to-snapshot`.

## Reason Codes

Allowed success-result reason codes:

| Code | Meaning |
|------|---------|
| `large_delta_area` | `changedPixelRatio` is greater than or equal to `threshold`. |
| `high_average_pixel_delta` | `averagePixelDelta` is greater than or equal to `threshold`. |

Reserved diagnostic reason codes for future machine-readable failure contracts:

| Code | Meaning |
|------|---------|
| `image_size_mismatch` | Input snapshots do not have identical dimensions. |
| `input_missing` | A required input file does not exist. |
| `decode_failed` | An input image could not be decoded. |
| `analysis_failed` | Analysis could not complete after input validation. |

Failure reason codes are documented but are not emitted as JSON yet.

## Threshold Rule

`--threshold` must be between `0.0` and `1.0`, inclusive.

For `delta-basic`, a result is suspicious when:

```text
changedPixelRatio >= threshold
or
averagePixelDelta >= threshold
```

Each pixel delta is the average of normalized red, green, and blue channel differences:

```text
abs(from.red - to.red) / 255
abs(from.green - to.green) / 255
abs(from.blue - to.blue) / 255
```

Alpha is ignored by the current algorithm.

## Engine Version Rule

`engineVersion` must come from the Cargo package version at compile time:

```rust
env!("CARGO_PKG_VERSION")
```

The Cargo package version must be synced from the root `VERSION` file with:

```bash
tools/sync-version.sh
```

The Git pre-commit hook runs:

```bash
tools/check-version.sh
```

and blocks commits when `VERSION`, `pom.xml`, `Cargo.toml`, and `Cargo.lock` disagree.

## Exit Codes

| Code | Meaning | stdout | stderr |
|------|---------|--------|--------|
| `0` | Success | Result JSON | Optional diagnostics only. |
| `1` | Invalid arguments | Empty | Human-readable error. |
| `2` | Input file not found | Empty | Human-readable error. |
| `3` | Image decoding failed | Empty | Human-readable error. |
| `4` | Image size mismatch | Empty | Human-readable error. |
| `5` | Analysis failed | Empty | Human-readable error. |
| `6` | Internal error | Empty | Human-readable error. |

Example failure:

```bash
./target/debug/img-analyzer \
  --from-snapshot missing.jpg \
  --to-snapshot <cameraRoot>/<printerId>/snapshots/<cameraJobId>/002524_snapshot.jpg
```

Exit code:

```text
2
```

stderr:

```text
Input file not found: missing.jpg
```

stdout:

```text

```

## Failure Behavior

Invalid input must fail before analysis starts.

The analyzer must not create or modify image files.

The optional `--delta-frame` path must not be required. If supplied, the current Rust algorithm treats it as contract-compatible input metadata only; it does not validate or read the delta file inside the Rust algorithm.

Failure output is intentionally simple: non-zero exit code plus stderr diagnostics. Machine-readable error JSON is out of scope until a later contract revision.
