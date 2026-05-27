# img-analyzer

Standalone Rust command-line image analyzer for SpaghettiChef camera experiments.

## Purpose

`img-analyzer` is an experimental Rust tool developed alongside SpaghettiChef.

The goal is to explore Rust-based image analysis without coupling the first development steps to the Java backend.

The tool reads existing image files produced by SpaghettiChef camera jobs and performs simple visual-difference analysis.

Current scope:

```text
source snapshot A
source snapshot B
optional delta frame
threshold-based comparison
JSON result output
```

The tool does not currently use:

```text
Java integration
REST API
database access
OpenCV
machine learning
WASM
```

---

## Build

Go to the Rust project:

```bash
cd rust/img-analyzer
```

Format:

```bash
cargo fmt
```

Run tests:

```bash
cargo test
```

Build:

```bash
cargo build
```

Build optimized release version:

```bash
cargo build --release
```

---

## Run

Example:

```bash
cargo run -- \
  --from-snapshot ../../camera/p1/snapshots/1/002523_snapshot.jpg \
  --to-snapshot ../../camera/p1/snapshots/1/002524_snapshot.jpg \
  --method delta-basic \
  --threshold 0.20
```

Example with optional delta frame:

```bash
cargo run -- \
  --from-snapshot ../../camera/p1/snapshots/1/002523_snapshot.jpg \
  --to-snapshot ../../camera/p1/snapshots/1/002524_snapshot.jpg \
  --delta-frame ../../camera/p1/deltas/1/1/002523_002524_delta.jpg \
  --method delta-basic \
  --threshold 0.20
```

## Run Against SpaghettiChef Camera Data

0.5.1 adds helper scripts for manual analysis of real files captured by the Java application:

```text
<cameraRoot>/<printerId>/snapshots/<cameraJobId>/
<cameraRoot>/<printerId>/deltas/<cameraJobId>/<deltaSetId>/
```

The camera root is configurable in SpaghettiChef camera settings. The Java default storage directory is `camera`, resolved from the database directory. Use `--camera-root` when your captured files live somewhere else.

Linux:

```bash
./scripts/analyze-sample.sh --printer-id p1 --camera-job-id 1 --from-sequence 2523 --to-sequence 2524
```

Windows:

```ps1
.\scripts\analyze-sample.ps1 -PrinterId p1 -CameraJobId 1 -FromSequence 2523 -ToSequence 2524
```
 

---

## Command arguments

| Argument | Required | Description |
|----------|----------|----------|
| `--from-snapshot` | yes | first source image |
| `--to-snapshot` | yes | second source image |
| `--delta-frame` | no | optional existing delta image |
| `--method` | no | analysis method |
| `--threshold` | no | threshold between `0.0` and `1.0` |

---

## Output

The analyzer prints machine-readable JSON to stdout.

Example:

```json
{
  "engineName": "RUST_CLI_DELTA",
  "engineVersion": "0.6.2",
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

Diagnostics and error details are written to stderr.

The stable CLI and JSON behavior is documented in [docs/result-contract.md](docs/result-contract.md).

---

## Exit codes

```text
0  success
1  invalid arguments
2  input file not found
3  image decoding failed
4  image size mismatch
5  analysis failed
6  internal error
```

---

## Development status

Current version:

```text
0.6.2
Standalone Rust prototype.
No Java integration yet.
```
