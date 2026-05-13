pipeline {
    agent any

    parameters {
        string(
            name: 'GIT_BRANCH',
            defaultValue: 'develop',
            description: 'Git branch to build, for example develop or main.'
        )
        string(
            name: 'JAVA_HOME_OVERRIDE',
            defaultValue: '',
            description: 'Optional JAVA_HOME override. Leave empty to use the agent default.'
        )
        string(
            name: 'API_SMOKE_PORT',
            defaultValue: '18090',
            description: 'Port used for the local runtime smoke test.'
        )
        string(
            name: 'RELEASE_VERSION',
            defaultValue: '',
            description: 'Optional release version, for example 0.1.3. Leave empty for CI-only runs.'
        )
        booleanParam(
            name: 'PUBLISH_GITHUB_RELEASE',
            defaultValue: false,
            description: 'Publish the prepared release bundle to GitHub Releases. Only use for stable main releases.'
        )
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
    }

    environment {
        MAVEN_OPTS = '-Djava.awt.headless=true'
        GITHUB_REPO = 'nathabee/printer-hub'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.GIT_BRANCH}"]],
                    userRemoteConfigs: [[
                        url: 'https://github.com/nathabee/printer-hub.git'
                    ]]
                ])
            }
        }

        stage('Environment') {
            steps {
                script {
                    if (params.JAVA_HOME_OVERRIDE?.trim()) {
                        env.JAVA_HOME = params.JAVA_HOME_OVERRIDE.trim()
                        env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
                    }
                }

                sh '''
                    echo "JAVA_HOME=${JAVA_HOME:-<not-set>}"
                    which java || true
                    which javac || true
                    which mvn || true
                    which sqlite3 || true
                    which curl || true
                    which python3 || true
                    java -version
                    javac -version
                    mvn -version
                '''
            }
        }

        stage('Verify') {
            steps {
                sh '''
                    set -eu

                    rm -rf target
                    rm -f printerhub.db printerhub-real.db printerhub-test.db printerhub-ci.db

                    mvn -B -ntp clean verify
                '''
            }
        }

        stage('Local Runtime Smoke Test') {
            steps {
                sh '''
                    set -eu

                    API_PORT="${API_SMOKE_PORT:-18090}"
                    DB_FILE="printerhub-ci.db"

                    mkdir -p target
                    rm -f "${DB_FILE}"

                    start_runtime() {
                      echo "Starting PrinterHub local runtime on port ${API_PORT}"
                      echo "Using database file ${DB_FILE}"

                      mvn -B -ntp exec:java \
                        -Dexec.mainClass="printerhub.Main" \
                        -Dprinterhub.api.port="${API_PORT}" \
                        -Dprinterhub.monitoring.intervalSeconds=1 \
                        -Dprinterhub.databaseFile="${DB_FILE}" \
                        > target/runtime-smoke.log 2>&1 &

                      APP_PID=$!
                      export APP_PID

                      for i in $(seq 1 30); do
                        if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null; then
                          return 0
                        fi
                        sleep 1
                      done

                      echo "Runtime did not become healthy in time"
                      cat target/runtime-smoke.log || true
                      return 1
                    }

                    stop_runtime() {
                      if [ -n "${APP_PID:-}" ]; then
                        kill "${APP_PID}" >/dev/null 2>&1 || true
                        wait "${APP_PID}" >/dev/null 2>&1 || true
                        unset APP_PID
                      fi
                    }

                    cleanup() {
                      stop_runtime
                    }
                    trap cleanup EXIT

                    json_field() {
                      FILE_PATH="$1"
                      FIELD_NAME="$2"
                      python3 - "$FILE_PATH" "$FIELD_NAME" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    value = value[part]

if value is None:
    print("null")
else:
    print(value)
PY
                    }

                    start_runtime

                    curl -fsS "http://localhost:${API_PORT}/health" > target/health.json
                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-initial.json

                    grep -q '"status":"ok"' target/health.json
                    grep -q '"printers":\\[\\]' target/printers-initial.json

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-1",
                        "displayName": "CI Simulated Printer 1",
                        "portName": "SIM_PORT_1",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/printer-created.json

                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-after-create.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-1-after-monitoring.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1/status" \
                      > target/printer-1-status-after-monitoring.json

                    grep -q '"printer-1"' target/printer-created.json
                    grep -q '"displayName":"CI Simulated Printer 1"' target/printer-created.json
                    grep -q '"printer-1"' target/printers-after-create.json
                    grep -q '"state":"IDLE"' target/printer-1-after-monitoring.json
                    grep -q '"hotendTemperature":21.80' target/printer-1-after-monitoring.json
                    grep -q '"bedTemperature":21.52' target/printer-1-after-monitoring.json
                    grep -q '"updatedAt"' target/printer-1-after-monitoring.json

                    json_field target/printer-1-after-monitoring.json updatedAt \
                      > target/printer-1-updated-before-disable.txt

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers/printer-1/disable" \
                      > target/printer-disabled.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-disable.json

                    grep -q '"enabled":false' target/printer-disabled.json
                    grep -q '"enabled":false' target/printer-after-disable.json
                    grep -q '"state":"DISCONNECTED"' target/printer-after-disable.json

                    json_field target/printer-after-disable.json updatedAt \
                      > target/printer-1-disabled-updated-before.txt

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-disable-wait.json

                    json_field target/printer-after-disable-wait.json updatedAt \
                      > target/printer-1-disabled-updated-after.txt

                    cmp -s target/printer-1-disabled-updated-before.txt target/printer-1-disabled-updated-after.txt

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers/printer-1/enable" \
                      > target/printer-enabled.json

                    grep -q '"enabled":true' target/printer-enabled.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-enable.json

                    grep -q '"enabled":true' target/printer-after-enable.json
                    grep -q '"state":"IDLE"' target/printer-after-enable.json

                    json_field target/printer-enabled.json updatedAt \
                      > target/printer-1-enabled-updated-before.txt || true

                    json_field target/printer-after-enable.json updatedAt \
                      > target/printer-1-enabled-updated-after.txt

                    if cmp -s target/printer-1-disabled-updated-after.txt target/printer-1-enabled-updated-after.txt; then
                      echo "updatedAt did not change after enable"
                      exit 1
                    fi

                    curl -fsS -X PUT "http://localhost:${API_PORT}/printers/printer-1" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "displayName": "CI Simulated Printer Updated",
                        "portName": "SIM_PORT_2",
                        "mode": "sim-error",
                        "enabled": true
                      }' > target/printer-updated.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-update.json

                    grep -q '"displayName":"CI Simulated Printer Updated"' target/printer-updated.json
                    grep -q '"portName":"SIM_PORT_2"' target/printer-updated.json
                    grep -q '"mode":"sim-error"' target/printer-updated.json
                    grep -q '"displayName":"CI Simulated Printer Updated"' target/printer-after-update.json
                    grep -q '"portName":"SIM_PORT_2"' target/printer-after-update.json
                    grep -q '"mode":"sim-error"' target/printer-after-update.json

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-2",
                        "displayName": "CI Persistent Printer",
                        "portName": "SIM_PORT_PERSIST",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/printer-2-created.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-before-delete.json

                    grep -q '"printer-2"' target/printers-before-delete.json

                    curl -fsS -X DELETE "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-deleted.json

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-after-delete.json

                    grep -q '"deleted":"printer-1"' target/printer-deleted.json
                    if grep -q '"printer-1"' target/printers-after-delete.json; then
                      echo "printer-1 still present after delete"
                      exit 1
                    fi
                    grep -q '"printer-2"' target/printers-after-delete.json

                    curl -fsS "http://localhost:${API_PORT}/dashboard" > target/dashboard.html
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.css" > target/dashboard.css
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.js" > target/dashboard.js
                    curl -fsS "http://localhost:${API_PORT}/dashboard/api.js" > target/dashboard-api.js
                    curl -fsS "http://localhost:${API_PORT}/dashboard/views/farm-home.js" > target/dashboard-view-farm-home.js
                    curl -fsS "http://localhost:${API_PORT}/dashboard/components/nav.js" > target/dashboard-component-nav.js

                    grep -q 'PrinterHub' target/dashboard.html
                    grep -q 'app-shell' target/dashboard.css
                    grep -q 'renderFarmHome' target/dashboard.js
                    grep -q 'export async function getPrinters' target/dashboard-api.js
                    grep -q 'export function renderFarmHome' target/dashboard-view-farm-home.js
                    grep -q 'export function renderNav' target/dashboard-component-nav.js

                    stop_runtime

                    sqlite3 "${DB_FILE}" '.tables' > target/db-tables.txt
                    sqlite3 "${DB_FILE}" 'select id,name,port_name,mode,enabled from configured_printers order by id;' \
                      > target/configured-printers.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,state,created_at from printer_snapshots order by id desc limit 20;' \
                      > target/printer-snapshots.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,event_type,message,created_at from printer_events order by id desc limit 20;' \
                      > target/printer-events.txt

                    grep -q 'configured_printers' target/db-tables.txt
                    grep -q 'printer_snapshots' target/db-tables.txt
                    grep -q 'printer_events' target/db-tables.txt

                    grep -q 'printer-2' target/configured-printers.txt
                    grep -q 'printer-2' target/printer-snapshots.txt

                    start_runtime

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-after-restart.json

                    grep -q '"printer-2"' target/printers-after-restart.json
                    grep -q '"CI Persistent Printer"' target/printers-after-restart.json

                    stop_runtime
                    trap - EXIT

                    echo "Health:"
                    cat target/health.json

                    echo
                    echo "Initial printers:"
                    cat target/printers-initial.json

                    echo
                    echo "Created printer-1:"
                    cat target/printer-created.json

                    echo
                    echo "Printer-1 after monitoring:"
                    cat target/printer-1-after-monitoring.json

                    echo
                    echo "Printer-1 after disable:"
                    cat target/printer-after-disable.json

                    echo
                    echo "Printer-1 after enable:"
                    cat target/printer-after-enable.json

                    echo
                    echo "Printer-1 after update:"
                    cat target/printer-after-update.json

                    echo
                    echo "Printers after delete:"
                    cat target/printers-after-delete.json

                    echo
                    echo "Printers after restart:"
                    cat target/printers-after-restart.json

                    echo
                    echo "Database tables:"
                    cat target/db-tables.txt

                    echo
                    echo "Configured printers:"
                    cat target/configured-printers.txt

                    echo
                    echo "Printer snapshots:"
                    cat target/printer-snapshots.txt

                    echo
                    echo "Printer events:"
                    cat target/printer-events.txt

                    echo
                    echo "Runtime smoke log:"
                    cat target/runtime-smoke.log
                '''
            }
        }

        stage('Robustness Smoke Test') {
            steps {
                sh '''
                    set -eu

                    API_PORT="${API_SMOKE_PORT:-18090}"
                    ROBUST_PORT=$((API_PORT + 1))
                    DB_FILE="printerhub-robustness-ci.db"

                    mkdir -p target
                    rm -f "${DB_FILE}"

                    start_runtime() {
                      echo "Starting PrinterHub robustness runtime on port ${ROBUST_PORT}"
                      echo "Using database file ${DB_FILE}"

                      mvn -B -ntp exec:java \
                        -Dexec.mainClass="printerhub.Main" \
                        -Dprinterhub.api.port="${ROBUST_PORT}" \
                        -Dprinterhub.monitoring.intervalSeconds=1 \
                        -Dprinterhub.databaseFile="${DB_FILE}" \
                        > target/runtime-robustness.log 2>&1 &

                      APP_PID=$!
                      export APP_PID

                      for i in $(seq 1 30); do
                        if curl -fsS "http://localhost:${ROBUST_PORT}/health" >/dev/null; then
                          return 0
                        fi
                        sleep 1
                      done

                      echo "Robustness runtime did not become healthy in time"
                      cat target/runtime-robustness.log || true
                      return 1
                    }

                    stop_runtime() {
                      if [ -n "${APP_PID:-}" ]; then
                        kill "${APP_PID}" >/dev/null 2>&1 || true
                        wait "${APP_PID}" >/dev/null 2>&1 || true
                        unset APP_PID
                      fi
                    }

                    cleanup() {
                      stop_runtime
                    }
                    trap cleanup EXIT

                    json_field() {
                      FILE_PATH="$1"
                      FIELD_NAME="$2"
                      python3 - "$FILE_PATH" "$FIELD_NAME" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    value = value[part]

if value is None:
    print("null")
else:
    print(value)
PY
                    }

                    start_runtime

                    curl -fsS "http://localhost:${ROBUST_PORT}/health" \
                      > target/robust-health-initial.json

                    curl -fsS -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-good",
                        "displayName": "CI Good Printer",
                        "portName": "SIM_PORT_GOOD",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/robust-printer-good-created.json

                    curl -fsS -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-error",
                        "displayName": "CI Error Printer",
                        "portName": "SIM_PORT_ERROR",
                        "mode": "sim-error",
                        "enabled": true
                      }' > target/robust-printer-error-created.json

                    curl -fsS -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-timeout",
                        "displayName": "CI Timeout Printer",
                        "portName": "SIM_PORT_TIMEOUT",
                        "mode": "sim-timeout",
                        "enabled": true
                      }' > target/robust-printer-timeout-created.json

                    curl -fsS -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-disconnected",
                        "displayName": "CI Disconnected Printer",
                        "portName": "SIM_PORT_DISCONNECTED",
                        "mode": "sim-disconnected",
                        "enabled": true
                      }' > target/robust-printer-disconnected-created.json

                    curl -fsS "http://localhost:${ROBUST_PORT}/printers" \
                      > target/robust-printers-after-create.json

                    sleep 4

                    curl -fsS "http://localhost:${ROBUST_PORT}/health" \
                      > target/robust-health-after-failures.json

                    curl -fsS "http://localhost:${ROBUST_PORT}/printers" \
                      > target/robust-printers-after-monitoring.json

                    curl -fsS "http://localhost:${ROBUST_PORT}/printers/printer-good" \
                      > target/robust-printer-good.json
                    curl -fsS "http://localhost:${ROBUST_PORT}/printers/printer-error" \
                      > target/robust-printer-error.json
                    curl -fsS "http://localhost:${ROBUST_PORT}/printers/printer-timeout" \
                      > target/robust-printer-timeout.json
                    curl -fsS "http://localhost:${ROBUST_PORT}/printers/printer-disconnected" \
                      > target/robust-printer-disconnected.json

                    grep -q '"status":"ok"' target/robust-health-initial.json
                    grep -q '"status":"ok"' target/robust-health-after-failures.json

                    grep -q '"printer-good"' target/robust-printers-after-monitoring.json
                    grep -q '"printer-error"' target/robust-printers-after-monitoring.json
                    grep -q '"printer-timeout"' target/robust-printers-after-monitoring.json
                    grep -q '"printer-disconnected"' target/robust-printers-after-monitoring.json

                    grep -q '"state":"IDLE"' target/robust-printer-good.json
                    grep -q '"hotendTemperature":21.80' target/robust-printer-good.json
                    grep -q '"bedTemperature":21.52' target/robust-printer-good.json

                    grep -q '"state":"ERROR"' target/robust-printer-error.json
                    grep -q '"state":"ERROR"' target/robust-printer-timeout.json
                    grep -q '"state":"ERROR"' target/robust-printer-disconnected.json

                    json_field target/robust-printer-good.json updatedAt \
                      > target/robust-good-updated-before.txt

                    sleep 3

                    curl -fsS "http://localhost:${ROBUST_PORT}/health" \
                      > target/robust-health-during-runtime.json
                    curl -fsS "http://localhost:${ROBUST_PORT}/printers/printer-good" \
                      > target/robust-printer-good-later.json

                    grep -q '"status":"ok"' target/robust-health-during-runtime.json
                    grep -q '"state":"IDLE"' target/robust-printer-good-later.json

                    json_field target/robust-printer-good-later.json updatedAt \
                      > target/robust-good-updated-after.txt

                    if cmp -s target/robust-good-updated-before.txt target/robust-good-updated-after.txt; then
                      echo "Good printer did not continue updating while bad printers were failing"
                      exit 1
                    fi

                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard" \
                      > target/robust-dashboard.html
                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard/dashboard.css" \
                      > target/robust-dashboard.css
                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard/dashboard.js" \
                      > target/robust-dashboard.js
                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard/api.js" \
                      > target/robust-dashboard-api.js
                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard/views/farm-home.js" \
                      > target/robust-dashboard-view-farm-home.js
                    curl -fsS "http://localhost:${ROBUST_PORT}/dashboard/components/nav.js" \
                      > target/robust-dashboard-component-nav.js

                    grep -q 'PrinterHub' target/robust-dashboard.html
                    grep -q 'app-shell' target/robust-dashboard.css
                    grep -q 'renderFarmHome' target/robust-dashboard.js
                    grep -q 'export async function getPrinters' target/robust-dashboard-api.js
                    grep -q 'export function renderFarmHome' target/robust-dashboard-view-farm-home.js
                    grep -q 'export function renderNav' target/robust-dashboard-component-nav.js

                    HTTP_400_BODY=$(mktemp)
                    HTTP_404_BODY=$(mktemp)
                    HTTP_405_BODY=$(mktemp)
                    HTTP_400_MISSING_BODY=$(mktemp)

                    HTTP_400_STATUS=$(curl -sS -o "${HTTP_400_BODY}" -w "%{http_code}" \
                      -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{"id":"broken","displayName":"Broken","portName":"SIM_PORT","mode":"sim')

                    HTTP_404_STATUS=$(curl -sS -o "${HTTP_404_BODY}" -w "%{http_code}" \
                      "http://localhost:${ROBUST_PORT}/printers/unknown-printer")

                    HTTP_405_STATUS=$(curl -sS -o "${HTTP_405_BODY}" -w "%{http_code}" \
                      -X POST "http://localhost:${ROBUST_PORT}/health")

                    HTTP_400_MISSING_STATUS=$(curl -sS -o "${HTTP_400_MISSING_BODY}" -w "%{http_code}" \
                      -X POST "http://localhost:${ROBUST_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{"id":"missing-fields","portName":"SIM_PORT","mode":"sim"}')

                    echo "${HTTP_400_STATUS}" > target/robust-http-400-status.txt
                    echo "${HTTP_404_STATUS}" > target/robust-http-404-status.txt
                    echo "${HTTP_405_STATUS}" > target/robust-http-405-status.txt
                    echo "${HTTP_400_MISSING_STATUS}" > target/robust-http-400-missing-status.txt

                    cp "${HTTP_400_BODY}" target/robust-http-400-body.json
                    cp "${HTTP_404_BODY}" target/robust-http-404-body.json
                    cp "${HTTP_405_BODY}" target/robust-http-405-body.json
                    cp "${HTTP_400_MISSING_BODY}" target/robust-http-400-missing-body.json

                    test "${HTTP_400_STATUS}" = "400"
                    test "${HTTP_404_STATUS}" = "404"
                    test "${HTTP_405_STATUS}" = "405"
                    test "${HTTP_400_MISSING_STATUS}" = "400"

                    grep -q '"error"' target/robust-http-400-body.json
                    grep -q '"printer_not_found"' target/robust-http-404-body.json
                    grep -q '"method_not_allowed"' target/robust-http-405-body.json
                    grep -q '"displayName must not be blank"' target/robust-http-400-missing-body.json

                    stop_runtime

                    sqlite3 "${DB_FILE}" '.tables' > target/robust-db-tables.txt
                    sqlite3 "${DB_FILE}" 'select id,name,port_name,mode,enabled from configured_printers order by id;' \
                      > target/robust-configured-printers.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,state,created_at from printer_snapshots order by id desc limit 50;' \
                      > target/robust-printer-snapshots.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,event_type,message,created_at from printer_events order by id desc limit 50;' \
                      > target/robust-printer-events.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,count(*) from printer_events group by printer_id order by printer_id;' \
                      > target/robust-printer-event-counts.txt

                    grep -q 'printer-good' target/robust-configured-printers.txt
                    grep -q 'printer-error' target/robust-configured-printers.txt
                    grep -q 'printer-timeout' target/robust-configured-printers.txt
                    grep -q 'printer-disconnected' target/robust-configured-printers.txt

                    grep -q 'printer-good' target/robust-printer-snapshots.txt
                    grep -q 'printer-error' target/robust-printer-snapshots.txt
                    grep -q 'printer-timeout' target/robust-printer-snapshots.txt
                    grep -q 'printer-disconnected' target/robust-printer-snapshots.txt

                    grep -q 'printer-error' target/robust-printer-events.txt
                    grep -q 'printer-timeout' target/robust-printer-events.txt
                    grep -q 'printer-disconnected' target/robust-printer-events.txt

                    grep -q 'PRINTER_ERROR\\|PRINTER_TIMEOUT\\|PRINTER_DISCONNECTED' target/robust-printer-events.txt

                    python3 - <<'PY'
from pathlib import Path
counts = {}
for line in Path("target/robust-printer-event-counts.txt").read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    printer_id, count = line.split("|")
    counts[printer_id] = int(count)

for printer_id in ("printer-error", "printer-timeout", "printer-disconnected"):
    if printer_id not in counts:
        raise SystemExit(f"missing event count for {printer_id}")
    if counts[printer_id] > 10:
        raise SystemExit(f"too many persisted events for {printer_id}: {counts[printer_id]}")
PY

                    cat > target/operator-message-report.md <<'EOF'
# Operator message report

## Scenario: normal lifecycle smoke
The normal lifecycle smoke test verified startup, health, printer creation,
monitoring updates, disable/enable behavior, update, delete, persistence
inspection, and restart reload.

## Scenario: robustness smoke
The robustness smoke test verified that one healthy printer remained operational
while sim-error, sim-timeout, and sim-disconnected printers failed independently.

Observed operational evidence:
- API remained responsive through /health and /printers during mixed failures.
- Good printer remained IDLE and continued updating updatedAt.
- Bad printers transitioned to ERROR.
- Failure events were persisted with origin printer ids.
- Event growth stayed bounded during repeated monitoring cycles.
- Dashboard resources remained available.
- HTTP robustness responses remained controlled:
  - invalid POST body -> 400
  - unknown printer -> 404
  - wrong method -> 405
  - missing required field -> 400
EOF

                    echo "Robustness health initial:"
                    cat target/robust-health-initial.json

                    echo
                    echo "Robustness printers after monitoring:"
                    cat target/robust-printers-after-monitoring.json

                    echo
                    echo "Good printer later:"
                    cat target/robust-printer-good-later.json

                    echo
                    echo "Robustness event counts:"
                    cat target/robust-printer-event-counts.txt

                    echo
                    echo "Robustness printer events:"
                    cat target/robust-printer-events.txt

                    echo
                    echo "Robustness runtime log:"
                    cat target/runtime-robustness.log
                '''
            }
        }


        stage('Prepare Release Bundle') {
            when {
                expression {
                    return params.RELEASE_VERSION?.trim()
                }
            }
            steps {
                sh '''
                    set -eu

                    rm -rf release
                    mkdir -p release

                    if ls target/*-all.jar >/dev/null 2>&1; then
                      cp target/*-all.jar release/
                    fi

                    if [ -f target/operator-message-report.md ]; then
                      cp target/operator-message-report.md release/
                    fi

                    if [ -d target/site/jacoco ]; then
                      mkdir -p release/jacoco
                      cp -r target/site/jacoco/* release/jacoco/
                    fi

                    if [ -f README.md ]; then
                      cp README.md release/
                    fi

                    if [ -f docs/test.md ]; then
                      cp docs/test.md release/
                    fi

                    if [ -f docs/devops.md ]; then
                      cp docs/devops.md release/
                    else
                      echo "docs/devops.md is not available in this branch." > release/devops-notes.md
                    fi

                    if [ -f docs/roadmap.md ]; then
                      cp docs/roadmap.md release/
                    fi

                    if [ -f docs/version.md ]; then
                      cp docs/version.md release/
                    fi

                    if [ -f docs/industrial-bio-printer-simulation.md ]; then
                      cp docs/industrial-bio-printer-simulation.md release/
                    fi

                    if [ -f docs/install-remote.md ]; then
                      cp docs/install-remote.md release/
                    fi


                    if [ -d target ]; then
                      mkdir -p release/smoke
                      cp target/runtime-smoke.log release/smoke/ 2>/dev/null || true
                      cp target/health.json release/smoke/ 2>/dev/null || true
                      cp target/printers-initial.json release/smoke/ 2>/dev/null || true
                      cp target/printer-created.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-create.json release/smoke/ 2>/dev/null || true
                      cp target/printer-1-after-monitoring.json release/smoke/ 2>/dev/null || true
                      cp target/printer-1-status-after-monitoring.json release/smoke/ 2>/dev/null || true
                      cp target/printer-disabled.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-disable.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-disable-wait.json release/smoke/ 2>/dev/null || true
                      cp target/printer-enabled.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-enable.json release/smoke/ 2>/dev/null || true
                      cp target/printer-updated.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-update.json release/smoke/ 2>/dev/null || true
                      cp target/printer-2-created.json release/smoke/ 2>/dev/null || true
                      cp target/printer-deleted.json release/smoke/ 2>/dev/null || true
                      cp target/printers-before-delete.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-delete.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-restart.json release/smoke/ 2>/dev/null || true
                      cp target/dashboard.html release/smoke/ 2>/dev/null || true
                      cp target/dashboard.css release/smoke/ 2>/dev/null || true
                      cp target/dashboard.js release/smoke/ 2>/dev/null || true
                      cp target/dashboard-api.js release/smoke/ 2>/dev/null || true
                      cp target/db-tables.txt release/smoke/ 2>/dev/null || true
                      cp target/configured-printers.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-snapshots.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-events.txt release/smoke/ 2>/dev/null || true
                    fi
                '''
            }
        }

stage('Package Expert Distributions') {
    when {
        expression {
            return params.RELEASE_VERSION?.trim()
        }
    }
    steps {
        script {
            def versionName = params.RELEASE_VERSION.trim()
            env.RELEASE_ARCHIVE = "printer-hub-${versionName}-release.tar.gz"
            env.LINUX_PACKAGE = "printer-hub-${versionName}-linux.tar.gz"
            env.WINDOWS_PACKAGE = "printer-hub-${versionName}-windows.zip"
            env.ADMIN_PACKAGE = "printer-hub-${versionName}-admin.zip"
        }

        sh '''
            set -eu

            rm -rf dist package
            mkdir -p dist package/linux package/windows package/admin

            JAR_FILE=$(find target -maxdepth 1 -name 'printer-hub-*-all.jar' | sort | tail -n 1)
            test -n "${JAR_FILE}"

            cp "${JAR_FILE}" package/linux/printer-hub.jar
            cp "${JAR_FILE}" package/windows/printer-hub.jar

            cp README.md package/linux/README.md
            cp README.md package/windows/README.md
            cp docs/install.md package/linux/INSTALL.md
            cp docs/install.md package/windows/INSTALL.md
            cp docs/quickstart.md package/linux/QUICKSTART.md
            cp docs/quickstart.md package/windows/QUICKSTART.md

            cat > package/linux/printerhub.sh <<'EOF'
#!/usr/bin/env sh
set -eu
 
API_PORT="${1:-18080}"
DATABASE_FILE="${PRINTERHUB_DATABASE_FILE:-printerhub.db}"

exec java -Dprinterhub.databaseFile="${DATABASE_FILE}" -Dprinterhub.api.port="${API_PORT}" -jar printer-hub.jar
EOF
            chmod +x package/linux/printerhub.sh
cat > package/windows/printerhub.bat <<'EOF'
@echo off
setlocal

set API_PORT=%1
set API_PORT_SOURCE=arg
if "%API_PORT%"=="" (
  set API_PORT=%PRINTERHUB_API_PORT%
  set API_PORT_SOURCE=env
)
if "%API_PORT%"=="" (
  set API_PORT=18080
  set API_PORT_SOURCE=default
)

set DATABASE_FILE=%PRINTERHUB_DATABASE_FILE%
set DATABASE_FILE_SOURCE=env
if "%DATABASE_FILE%"=="" (
  if exist "C:\printerhub\data" (
    set "DATABASE_FILE=C:\printerhub\data\printerhub.db"
    set "DATABASE_FILE_SOURCE=managed-default"
  ) else (
    set "DATABASE_FILE=printerhub.db"
    set "DATABASE_FILE_SOURCE=local-default"
  )
)

set JAVA_CMD=%PRINTERHUB_JAVA%
set JAVA_CMD_SOURCE=env
if "%JAVA_CMD%"=="" (
  set JAVA_CMD=java
  set JAVA_CMD_SOURCE=default
)

echo PrinterHub launcher configuration
echo   java: %JAVA_CMD% [source=%JAVA_CMD_SOURCE%]
echo   api port: %API_PORT% [source=%API_PORT_SOURCE%]
echo   database file: %DATABASE_FILE% [source=%DATABASE_FILE_SOURCE%]

"%JAVA_CMD%" -Dprinterhub.databaseFile="%DATABASE_FILE%" -Dprinterhub.api.port="%API_PORT%" -jar printer-hub.jar
EOF

cp tools/win/run.env.example package/admin/
cp tools/win/t.ps1 package/admin/
cp tools/win/u.ps1 package/admin/
cp tools/win/r.ps1 package/admin/
cp tools/win/s.ps1 package/admin/
cp tools/win/v.ps1 package/admin/
cp docs/install-remote.md package/admin/INSTALL-REMOTE.md

cat > package/admin/README.txt <<'EOF'
PrinterHub Windows remote administration bootstrap package.

Contents:
- run.env.example : example local runtime configuration for the PowerShell helper layer
- t.ps1 : register or refresh the PrinterHub scheduled task
- u.ps1 : remote update script
- r.ps1 : start PrinterHub through Task Scheduler
- s.ps1 : stop PrinterHub
- v.ps1 : status and health check
- INSTALL-REMOTE.md : setup instructions

Copy the PowerShell scripts to C:\\printerhub\\bin on the Windows host.
Copy run.env.example to C:\\printerhub\\data\\run.env and adjust values if needed.
EOF

            tar -C package -czf "dist/${LINUX_PACKAGE}" linux
            (cd package/windows && jar --create --file "../../dist/${WINDOWS_PACKAGE}" .)
            (cd package/admin && jar --create --file "../../dist/${ADMIN_PACKAGE}" .)
            tar -czf "${RELEASE_ARCHIVE}" release

            ls -lh dist
            ls -lh "${RELEASE_ARCHIVE}"
        '''
    }
}

        stage('Publish GitHub Release') {
            when {
                expression {
                    return params.PUBLISH_GITHUB_RELEASE && params.RELEASE_VERSION?.trim()
                }
            }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                    sh '''
                        set -eu

                        TAG_NAME="v${RELEASE_VERSION}"
                        RELEASE_NAME="PrinterHub ${RELEASE_VERSION}"

                        API_JSON=$(mktemp)

                        cat > "${API_JSON}" <<EOF
{
  "tag_name": "${TAG_NAME}",
  "name": "${RELEASE_NAME}",
  "draft": false,
  "prerelease": false,
  "generate_release_notes": true
}
EOF

                        curl -sS -X POST \
                          -H "Accept: application/vnd.github+json" \
                          -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                          https://api.github.com/repos/${GITHUB_REPO}/releases \
                          -d @"${API_JSON}" \
                          > github-release-response.json

                        UPLOAD_URL=$(python3 - <<'PY'
import json
with open("github-release-response.json", "r", encoding="utf-8") as f:
    data = json.load(f)
url = data.get("upload_url", "")
print(url.split("{")[0])
PY
)

                        test -n "${UPLOAD_URL}"

                        for ARTIFACT in "${RELEASE_ARCHIVE}" dist/*; do
                          CONTENT_TYPE="application/octet-stream"
                          case "${ARTIFACT}" in
                            *.tar.gz) CONTENT_TYPE="application/gzip" ;;
                            *.zip) CONTENT_TYPE="application/zip" ;;
                          esac

                          ARTIFACT_NAME=$(basename "${ARTIFACT}")
                          curl -sS -X POST \
                            -H "Accept: application/vnd.github+json" \
                            -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                            -H "Content-Type: ${CONTENT_TYPE}" \
                            "${UPLOAD_URL}?name=${ARTIFACT_NAME}" \
                            --data-binary @"${ARTIFACT}"
                        done
                    '''
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'

            archiveArtifacts artifacts: 'target/surefire-reports/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/operator-message-report.md', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/runtime-smoke.log', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/health.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-initial.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-create.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-1-after-monitoring.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-1-status-after-monitoring.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/printer-disabled.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-disable.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-disable-wait.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-enabled.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-enable.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/printer-updated.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-update.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-2-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-deleted.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-before-delete.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-delete.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-restart.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/dashboard.html', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard.css', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard-api.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard-view-farm-home.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard-component-nav.js', allowEmptyArchive: true


            archiveArtifacts artifacts: 'target/db-tables.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/configured-printers.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-snapshots.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-events.txt', allowEmptyArchive: true

            archiveArtifacts artifacts: 'release/**', allowEmptyArchive: true
            archiveArtifacts artifacts: '*.tar.gz', allowEmptyArchive: true
            archiveArtifacts artifacts: 'github-release-response.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/runtime-robustness.log', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-health-initial.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-health-after-failures.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-health-during-runtime.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-printer-good-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-error-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-timeout-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-disconnected-created.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-printers-after-create.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printers-after-monitoring.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-printer-good.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-error.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-timeout.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-disconnected.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-good-later.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-dashboard.html', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-dashboard.css', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-dashboard.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-dashboard-api.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-dashboard-view-farm-home.js', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-dashboard-component-nav.js', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-http-400-status.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-404-status.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-405-status.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-400-missing-status.txt', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-http-400-body.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-404-body.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-405-body.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-http-400-missing-body.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/robust-db-tables.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-configured-printers.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-snapshots.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-events.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/robust-printer-event-counts.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'dist/**', allowEmptyArchive: true
        }

        success {
            echo 'PrinterHub verification completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check Java version, Maven output, runtime smoke log, archived API responses, and SQLite smoke artifacts.'
        }
    }
}
