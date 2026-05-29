#!/usr/bin/env bash
set -euo pipefail

MODE="default"
DATASET_ROOT="${DATASET_ROOT:-dataset}"
PRINTER_ID="${PRINTER_ID:-pex01}"

DATASET_CONTENT_ROOT="${DATASET_CONTENT_ROOT:-${DATASET_ROOT}/${PRINTER_ID}}"
DATASET_JSON_ROOT="${DATASET_JSON_ROOT:-${DATASET_ROOT}/json}"
DATASET_JSON_PRINTER_ROOT="${DATASET_JSON_PRINTER_ROOT:-${DATASET_JSON_ROOT}/${PRINTER_ID}}"

usage() {
  cat <<EOF
Usage:
  $0 [--structure-only|--strict-images]

Environment:
  DATASET_ROOT                Dataset root directory. Default: dataset
  PRINTER_ID                  Target dataset printer id. Default: pex01
  DATASET_CONTENT_ROOT        Runtime archive content root. Default: dataset/{PRINTER_ID}
  DATASET_JSON_ROOT           Metadata root. Default: dataset/json
  DATASET_JSON_PRINTER_ROOT   Printer metadata root. Default: dataset/json/{PRINTER_ID}

Modes:
  --structure-only   Validate folder structure, JSON metadata, filenames, and references.
                     Does not require image files to contain valid JPEG data.

  --strict-images    Also validate that .jpg files are non-empty and start with a JPEG header.

Default:
  Same as --structure-only.
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "$*"
}

require_cmd() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || fail "required command not found: ${command_name}"
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "required file not found: ${path}"
}

require_dir() {
  local path="$1"
  [[ -d "$path" ]] || fail "required directory not found: ${path}"
}

validate_json_file() {
  local path="$1"

  require_file "$path"

  python3 - "$path" <<'PY'
import json
import sys

path = sys.argv[1]
try:
    with open(path, "r", encoding="utf-8") as f:
        json.load(f)
except Exception as exc:
    raise SystemExit(f"invalid JSON file: {path}: {exc}")
PY
}

json_value() {
  local path="$1"
  local field="$2"

  python3 - "$path" "$field" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    if not isinstance(value, dict) or part not in value:
        print("")
        raise SystemExit(0)
    value = value[part]

if value is None:
    print("")
else:
    print(value)
PY
}

json_array_length() {
  local path="$1"
  local field="$2"

  python3 - "$path" "$field" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    if not isinstance(value, dict) or part not in value:
        print(0)
        raise SystemExit(0)
    value = value[part]

if isinstance(value, list):
    print(len(value))
else:
    print(0)
PY
}

json_array_item_value() {
  local path="$1"
  local field="$2"
  local index="$3"
  local item_field="$4"

  python3 - "$path" "$field" "$index" "$item_field" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]
index = int(sys.argv[3])
item_field = sys.argv[4]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    if not isinstance(value, dict) or part not in value:
        print("")
        raise SystemExit(0)
    value = value[part]

if not isinstance(value, list) or index >= len(value):
    print("")
    raise SystemExit(0)

item = value[index]
if not isinstance(item, dict):
    print("")
    raise SystemExit(0)

result = item.get(item_field)
if result is None:
    print("")
else:
    print(result)
PY
}

count_files() {
  local path="$1"
  local pattern="$2"

  if [[ ! -d "$path" ]]; then
    echo 0
    return
  fi

  find "$path" -maxdepth 1 -type f -name "$pattern" | wc -l | tr -d ' '
}

validate_snapshot_name() {
  local file_name="$1"
  [[ "$file_name" =~ ^[0-9]{6}_snapshot\.jpg$ ]] || fail "invalid runtime snapshot filename: ${file_name}; expected 001298_snapshot.jpg"
}

validate_delta_name() {
  local file_name="$1"
  [[ "$file_name" =~ ^[0-9]{6}_[0-9]{6}_delta\.jpg$ ]] || fail "invalid runtime delta filename: ${file_name}; expected 001298_001299_delta.jpg"
}

validate_jpeg_if_strict() {
  local path="$1"

  if [[ "$MODE" != "strict-images" ]]; then
    return
  fi

  [[ -s "$path" ]] || fail "image file is empty: ${path}"

  python3 - "$path" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
with path.open("rb") as f:
    header = f.read(2)

if header != b"\xff\xd8":
    raise SystemExit(f"not a JPEG file or missing JPEG header: {path}")
PY
}

validate_label_value() {
  local label_file="$1"
  local expected_result
  local failure_stage

  expected_result="$(json_value "$label_file" "expectedResult")"
  failure_stage="$(json_value "$label_file" "failureStage")"

  case "$expected_result" in
    normal|spaghetti|unclear)
      ;;
    "")
      fail "label file missing expectedResult: ${label_file}"
      ;;
    *)
      fail "invalid expectedResult '${expected_result}' in ${label_file}; expected normal, spaghetti, or unclear"
      ;;
  esac

  case "$failure_stage" in
    none|early|clear|severe|unknown)
      ;;
    "")
      fail "label file missing failureStage: ${label_file}"
      ;;
    *)
      fail "invalid failureStage '${failure_stage}' in ${label_file}; expected none, early, clear, severe, or unknown"
      ;;
  esac
}

validate_delta_references() {
  local snapshot_dir="$1"
  local delta_file="$2"

  local file_name
  local from_id
  local to_id

  file_name="$(basename "$delta_file")"

  validate_delta_name "$file_name"

  from_id="${file_name:0:6}"
  to_id="${file_name:7:6}"

  [[ -f "${snapshot_dir}/${from_id}_snapshot.jpg" ]] || fail "delta references missing source snapshot: ${delta_file} -> ${from_id}_snapshot.jpg"
  [[ -f "${snapshot_dir}/${to_id}_snapshot.jpg" ]] || fail "delta references missing target snapshot: ${delta_file} -> ${to_id}_snapshot.jpg"

  validate_jpeg_if_strict "$delta_file"
}

validate_runtime_archive_job() {
  local job_metadata_dir="$1"
  local dataset_job_key
  local job_json
  local label_json
  local declared_job_key
  local declared_printer_id
  local source_layout
  local snapshot_directory
  local snapshot_dir
  local snapshot_count
  local delta_set_count

  dataset_job_key="$(basename "$job_metadata_dir")"

  [[ "$dataset_job_key" =~ ^[0-9]+$ ]] || fail "invalid job metadata folder: ${job_metadata_dir}; expected runtime numeric id such as 1 or 2"

  job_json="${job_metadata_dir}/job.json"
  label_json="${job_metadata_dir}/label.json"

  validate_json_file "$job_json"
  validate_json_file "$label_json"
  validate_label_value "$label_json"

  declared_job_key="$(json_value "$job_json" "datasetJobKey")"
  declared_printer_id="$(json_value "$job_json" "printerId")"
  source_layout="$(json_value "$job_json" "source.layout")"
  snapshot_directory="$(json_value "$job_json" "source.snapshotDirectory")"

  [[ "$declared_job_key" == "$dataset_job_key" ]] || fail "job.json datasetJobKey '${declared_job_key}' does not match folder '${dataset_job_key}' in ${job_json}"
  [[ "$declared_printer_id" == "$PRINTER_ID" ]] || fail "job.json printerId '${declared_printer_id}' does not match PRINTER_ID '${PRINTER_ID}' in ${job_json}"
  [[ "$source_layout" == "runtime-archive" ]] || fail "job.json source.layout must be runtime-archive in ${job_json}"
  [[ -n "$snapshot_directory" ]] || fail "job.json missing source.snapshotDirectory in ${job_json}"

  snapshot_dir="${DATASET_CONTENT_ROOT}/${snapshot_directory}"
  require_dir "$snapshot_dir"

  snapshot_count="$(count_files "$snapshot_dir" "*_snapshot.jpg")"
  [[ "$snapshot_count" -gt 0 ]] || fail "no snapshot files found in ${snapshot_dir}"

  while IFS= read -r snapshot_file; do
    validate_snapshot_name "$(basename "$snapshot_file")"
    validate_jpeg_if_strict "$snapshot_file"
  done < <(find "$snapshot_dir" -maxdepth 1 -type f -name "*_snapshot.jpg" | sort)

  delta_set_count="$(json_array_length "$job_json" "deltaSets")"
  [[ "$delta_set_count" -gt 0 ]] || fail "job.json must contain at least one deltaSets entry in ${job_json}"

  for index in $(seq 0 $((delta_set_count - 1))); do
    local delta_directory
    local delta_snapshot_step
    local delta_dir
    local delta_count

    delta_directory="$(json_array_item_value "$job_json" "deltaSets" "$index" "deltaDirectory")"
    delta_snapshot_step="$(json_array_item_value "$job_json" "deltaSets" "$index" "deltaSnapshotStep")"

    [[ -n "$delta_directory" ]] || fail "deltaSets[${index}] missing deltaDirectory in ${job_json}"
    [[ "$delta_snapshot_step" =~ ^[0-9]+$ ]] || fail "deltaSets[${index}] deltaSnapshotStep must be numeric in ${job_json}"

    delta_dir="${DATASET_CONTENT_ROOT}/${delta_directory}"
    require_dir "$delta_dir"

    delta_count="$(count_files "$delta_dir" "*_delta.jpg")"
    [[ "$delta_count" -gt 0 ]] || fail "no delta files found in ${delta_dir}"

    while IFS= read -r delta_file; do
      validate_delta_references "$snapshot_dir" "$delta_file"
    done < <(find "$delta_dir" -maxdepth 1 -type f -name "*_delta.jpg" | sort)

    info "OK job ${dataset_job_key}, delta set ${index}: ${delta_count} delta files from ${delta_directory}"
  done

  info "OK job ${dataset_job_key}: ${snapshot_count} snapshots from ${snapshot_directory}"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --structure-only)
        MODE="structure-only"
        shift
        ;;
      --strict-images)
        MODE="strict-images"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "unknown argument: $1"
        ;;
    esac
  done

  if [[ "$MODE" == "default" ]]; then
    MODE="structure-only"
  fi
}

main() {
  parse_args "$@"

  require_cmd python3
  require_cmd find
  require_cmd wc

  local metadata_jobs_root
  local root_manifest
  local metadata_manifest

  metadata_jobs_root="${DATASET_JSON_PRINTER_ROOT}/jobs"
  root_manifest="${DATASET_ROOT}/manifest.json"
  metadata_manifest="${DATASET_JSON_ROOT}/manifest.json"

  info "Validating SpaghettiChef runtime-archive dataset"
  info "  mode: ${MODE}"
  info "  dataset root: ${DATASET_ROOT}"
  info "  content root: ${DATASET_CONTENT_ROOT}"
  info "  metadata root: ${DATASET_JSON_PRINTER_ROOT}"
  info "  printer id: ${PRINTER_ID}"
  info

  require_dir "$DATASET_ROOT"
  require_file "${DATASET_ROOT}/README.md"

  if [[ -f "$root_manifest" ]]; then
    validate_json_file "$root_manifest"
  fi

  require_dir "$DATASET_CONTENT_ROOT"
  require_dir "$DATASET_JSON_ROOT"

  if [[ -f "$metadata_manifest" ]]; then
    validate_json_file "$metadata_manifest"
  fi

  require_dir "$DATASET_JSON_PRINTER_ROOT"
  validate_json_file "${DATASET_JSON_PRINTER_ROOT}/printer.json"
  validate_json_file "${DATASET_JSON_PRINTER_ROOT}/camera-settings.json"
  require_dir "$metadata_jobs_root"

  local printer_json_id
  printer_json_id="$(json_value "${DATASET_JSON_PRINTER_ROOT}/printer.json" "id")"
  [[ "$printer_json_id" == "$PRINTER_ID" ]] || fail "printer.json id '${printer_json_id}' does not match PRINTER_ID '${PRINTER_ID}'"

  local job_count
  job_count="$(find "$metadata_jobs_root" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')"

  if [[ "$job_count" -eq 0 ]]; then
    fail "no job metadata folders found in ${metadata_jobs_root}"
  fi

  while IFS= read -r job_metadata_dir; do
    validate_runtime_archive_job "$job_metadata_dir"
  done < <(find "$metadata_jobs_root" -mindepth 1 -maxdepth 1 -type d | sort)

  info
  info "Dataset validation completed successfully."
  info "  jobs: ${job_count}"
}

main "$@"