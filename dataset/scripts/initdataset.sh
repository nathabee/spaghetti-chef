#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:18080}"
DATASET_ROOT="${DATASET_ROOT:-dataset}"
PRINTER_ID="${PRINTER_ID:-pex01}"
DRY_RUN="${DRY_RUN:-false}"

DATASET_CONTENT_ROOT="${DATASET_CONTENT_ROOT:-${DATASET_ROOT}/${PRINTER_ID}}"
DATASET_JSON_ROOT="${DATASET_JSON_ROOT:-${DATASET_ROOT}/json}"
DATASET_JSON_PRINTER_ROOT="${DATASET_JSON_PRINTER_ROOT:-${DATASET_JSON_ROOT}/${PRINTER_ID}}"

PRINTER_FILE="${DATASET_JSON_PRINTER_ROOT}/printer.json"
CAMERA_SETTINGS_FILE="${DATASET_JSON_PRINTER_ROOT}/camera-settings.json"

RESPONSE_FILE="/tmp/spaghettichef-initdataset-response.json"

need_cmd() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${command_name}" >&2
    exit 1
  fi
}

http_code() {
  local method="$1"
  local url="$2"
  local body="${3:-}"

  if [[ -n "$body" ]]; then
    curl -sS -o "$RESPONSE_FILE" \
      -w "%{http_code}" \
      -X "$method" \
      -H "Content-Type: application/json" \
      --data "$body" \
      "$url"
  else
    curl -sS -o "$RESPONSE_FILE" \
      -w "%{http_code}" \
      -X "$method" \
      "$url"
  fi
}

require_success() {
  local code="$1"
  local operation="$2"

  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "ERROR: ${operation} failed with HTTP ${code}" >&2
    echo "Response:" >&2
    cat "$RESPONSE_FILE" >&2 || true
    echo >&2
    exit 1
  fi
}

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "ERROR: required file not found: ${path}" >&2
    exit 1
  fi
}

require_dir() {
  local path="$1"
  if [[ ! -d "$path" ]]; then
    echo "ERROR: required directory not found: ${path}" >&2
    exit 1
  fi
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])'
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

json_without_id() {
  local path="$1"

  python3 - "$path" <<'PY'
import json
import sys

path = sys.argv[1]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

data.pop("id", None)

print(json.dumps(data, separators=(",", ":")))
PY
}

main() {
  need_cmd curl
  need_cmd python3

  require_dir "$DATASET_ROOT"
  require_dir "$DATASET_CONTENT_ROOT"
  require_dir "$DATASET_JSON_ROOT"
  require_dir "$DATASET_JSON_PRINTER_ROOT"
  require_file "$PRINTER_FILE"
  require_file "$CAMERA_SETTINGS_FILE"

  echo "Checking SpaghettiChef API at ${API_BASE} ..."
  code="$(http_code GET "${API_BASE}/health")"
  require_success "$code" "health check"

  echo "Runtime health:"
  cat "$RESPONSE_FILE"
  echo

  printer_json_id="$(json_value "$PRINTER_FILE" "id")"
  if [[ "$printer_json_id" != "$PRINTER_ID" ]]; then
    echo "ERROR: printer.json id '${printer_json_id}' does not match PRINTER_ID '${PRINTER_ID}'" >&2
    exit 1
  fi

  echo "Creating printer ${PRINTER_ID} if missing ..."
  create_printer_body="$(cat "$PRINTER_FILE")"

  code="$(http_code POST "${API_BASE}/printers" "$create_printer_body")"

  if [[ "$code" == "200" || "$code" == "201" ]]; then
    echo "Printer created."
  elif [[ "$code" == "400" || "$code" == "409" || "$code" == "500" ]]; then
    echo "Printer may already exist; trying update ..."
    update_printer_body="$(json_without_id "$PRINTER_FILE")"
    code="$(http_code PUT "${API_BASE}/printers/${PRINTER_ID}" "$update_printer_body")"
    require_success "$code" "printer update"
    echo "Printer updated."
  else
    require_success "$code" "printer create"
  fi

  echo "Configuring camera for ${PRINTER_ID} ..."
  camera_settings_body="$(cat "$CAMERA_SETTINGS_FILE")"
  code="$(http_code PUT "${API_BASE}/printers/${PRINTER_ID}/camera/settings" "$camera_settings_body")"
  require_success "$code" "camera settings update"
  echo "Camera configured."

  echo "Running camera storage sync dryRun=${DRY_RUN} ..."

  sync_body="$(
    cat <<JSON
{
  "layout": "runtime-camera-storage",
  "dryRun": ${DRY_RUN},
  "syncSnapshots": true,
  "syncDeltas": true,
  "deleteRowsForMissingFiles": true,
  "reactivateDeletedSnapshotRows": true,
  "createMissingCameraJobs": true,
  "createMissingDeltaSets": true,
  "requiredConfirmation": "SYNC_CAMERA_DATASET"
}
JSON
  )"

  code="$(http_code POST "${API_BASE}/admin/camera/storage/${PRINTER_ID}/sync" "$sync_body")"

  if [[ "$code" == "404" ]]; then
    echo "ERROR: camera storage sync endpoint is not implemented." >&2
    echo "Expected endpoint:" >&2
    echo "  POST /admin/camera/storage/${PRINTER_ID}/sync" >&2
    echo "Response:" >&2
    cat "$RESPONSE_FILE" >&2 || true
    echo >&2
    exit 2
  fi

  require_success "$code" "camera storage sync"

  echo "Camera storage sync result:"
  cat "$RESPONSE_FILE"
  echo

  echo "Verifying camera jobs ..."
  code="$(http_code GET "${API_BASE}/admin/camera/snapshot/jobs?printerId=${PRINTER_ID}")"
  require_success "$code" "camera job verification"
  cat "$RESPONSE_FILE"
  echo

  echo "Dataset initialization complete."
}

main "$@"
