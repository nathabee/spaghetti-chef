#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd -- "${PROJECT_DIR}/../.." && pwd)"
BIN="${PROJECT_DIR}/target/debug/img-analyzer"

PRINTER_ID="p1"
CAMERA_JOB_ID="1"
DELTA_SET_ID="1"
CAMERA_ROOT="${REPO_ROOT}/camera"
FROM_SEQUENCE="1"
TO_SEQUENCE="2"
FROM_SNAPSHOT=""
TO_SNAPSHOT=""
DELTA_FRAME=""
METHOD="delta-basic"
THRESHOLD="0.65"
BUILD=1

usage() {
  cat <<'USAGE'
Usage:
  analyze-sample.sh [options]

Options:
  --printer-id <id>       Printer id used for conventional camera paths. Default: p1
  --camera-job-id <id>    Camera job id used for conventional camera paths. Default: 1
  --delta-set-id <id>     Delta set id used for conventional camera paths. Default: 1
  --camera-root <path>    Configured camera storage directory. Default: <repo>/camera
  --from-sequence <n>     Snapshot entry id for default from path. Default: 1
  --to-sequence <n>       Snapshot entry id for default to path. Default: 2
  --from-snapshot <path>  First snapshot image.
  --to-snapshot <path>    Second snapshot image.
  --delta-frame <path>    Optional delta frame.
  --no-delta-frame        Run without a delta frame argument.
  --method <name>         Analyzer method. Default: delta-basic
  --threshold <value>     Threshold between 0.0 and 1.0. Default: 0.65
  --no-build              Do not run cargo build before analyzing.
  --help                  Show this help.

Examples:
  ./scripts/analyze-sample.sh --printer-id p1 --camera-job-id 1 --from-sequence 2523 --to-sequence 2524
  ./scripts/analyze-sample.sh --camera-root ../../camera --printer-id p1 --camera-job-id 1 --from-sequence 2523 --to-sequence 2524
  ./scripts/analyze-sample.sh --from-snapshot ../../camera/p1/snapshots/1/002523_snapshot.jpg --to-snapshot ../../camera/p1/snapshots/1/002524_snapshot.jpg --no-delta-frame
USAGE
}

resolve_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    realpath -m -- "${path}"
  else
    realpath -m -- "${PWD}/${path}"
  fi
}

require_file() {
  local label="$1"
  local path="$2"
  if [[ ! -f "${path}" ]]; then
    echo "Missing ${label}: ${path}" >&2
    if [[ "${path}" == "${REPO_ROOT}/data/camera/"* ]]; then
      echo "Expected SpaghettiChef camera data under: ${REPO_ROOT}/data/camera" >&2
      echo "Capture snapshots first, or pass explicit paths to existing image files." >&2
    fi
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --printer-id)
      PRINTER_ID="${2:?--printer-id requires a value}"
      shift 2
      ;;
    --camera-job-id)
      CAMERA_JOB_ID="${2:?--camera-job-id requires a value}"
      shift 2
      ;;
    --delta-set-id)
      DELTA_SET_ID="${2:?--delta-set-id requires a value}"
      shift 2
      ;;
    --camera-root)
      CAMERA_ROOT="$(resolve_path "${2:?--camera-root requires a value}")"
      shift 2
      ;;
    --from-sequence)
      FROM_SEQUENCE="${2:?--from-sequence requires a value}"
      shift 2
      ;;
    --to-sequence)
      TO_SEQUENCE="${2:?--to-sequence requires a value}"
      shift 2
      ;;
    --from-snapshot)
      FROM_SNAPSHOT="$(resolve_path "${2:?--from-snapshot requires a value}")"
      shift 2
      ;;
    --to-snapshot)
      TO_SNAPSHOT="$(resolve_path "${2:?--to-snapshot requires a value}")"
      shift 2
      ;;
    --delta-frame)
      DELTA_FRAME="$(resolve_path "${2:?--delta-frame requires a value}")"
      shift 2
      ;;
    --no-delta-frame)
      DELTA_FRAME="__none__"
      shift
      ;;
    --method)
      METHOD="${2:?--method requires a value}"
      shift 2
      ;;
    --threshold)
      THRESHOLD="${2:?--threshold requires a value}"
      shift 2
      ;;
    --no-build)
      BUILD=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${FROM_SNAPSHOT}" ]]; then
  FROM_SNAPSHOT="${CAMERA_ROOT}/${PRINTER_ID}/snapshots/${CAMERA_JOB_ID}/$(printf '%06d' "${FROM_SEQUENCE}")_snapshot.jpg"
fi

if [[ -z "${TO_SNAPSHOT}" ]]; then
  TO_SNAPSHOT="${CAMERA_ROOT}/${PRINTER_ID}/snapshots/${CAMERA_JOB_ID}/$(printf '%06d' "${TO_SEQUENCE}")_snapshot.jpg"
fi

if [[ -z "${DELTA_FRAME}" ]]; then
  DEFAULT_DELTA="${CAMERA_ROOT}/${PRINTER_ID}/deltas/${CAMERA_JOB_ID}/${DELTA_SET_ID}/$(printf '%06d' "${FROM_SEQUENCE}")_$(printf '%06d' "${TO_SEQUENCE}")_delta.jpg"
  if [[ -f "${DEFAULT_DELTA}" ]]; then
    DELTA_FRAME="${DEFAULT_DELTA}"
  else
    DELTA_FRAME="__none__"
  fi
fi

require_file "from snapshot" "${FROM_SNAPSHOT}"
require_file "to snapshot" "${TO_SNAPSHOT}"

if [[ "${DELTA_FRAME}" != "__none__" ]]; then
  require_file "delta frame" "${DELTA_FRAME}"
fi

if [[ "${BUILD}" -eq 1 ]]; then
  cargo build --manifest-path "${PROJECT_DIR}/Cargo.toml" >&2
fi

ARGS=(
  --from-snapshot "${FROM_SNAPSHOT}"
  --to-snapshot "${TO_SNAPSHOT}"
  --method "${METHOD}"
  --threshold "${THRESHOLD}"
)

if [[ "${DELTA_FRAME}" != "__none__" ]]; then
  ARGS+=(--delta-frame "${DELTA_FRAME}")
fi

echo "Running ${BIN}" >&2
echo "from=${FROM_SNAPSHOT}" >&2
echo "to=${TO_SNAPSHOT}" >&2
echo "delta=${DELTA_FRAME}" >&2

"${BIN}" "${ARGS[@]}"
