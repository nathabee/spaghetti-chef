# ROADMAP

This roadmap describes the progressive hardening of the printer communication system from hardware discovery to CI/CD-ready automation.


This roadmap separates the PrinterHub project into three architectural stages:

- `0.0.x` — prototype foundation
- `0.1.x` — local farm runtime architecture
- `0.2.x` — local runtime administration and job management
- `1.0.x` — central VPS multi-farm management

---

<details >
  <summary>0.0.x — Prototype Foundation (done)</summary>
 

## 0.0.x — Prototype Foundation (done)

status: closed after `0.0.19`

Purpose:

Build and validate the first working foundation:

- serial communication
- simulated printer support
- polling
- REST API
- dashboard prototype
- job model prototype
- printer farm simulation
- SQLite persistence
- local runtime configuration foundation

Important note:

The `0.0.x` line is a prototype line.  
It proved the components, but it is not yet the final local farm architecture.
 
---



### 0.0.1 — Interface Discovery

status : done  
version : 0.0.1

Goals:

- identify serial interface
- read printer responses
- document supported commands
- verify firmware behavior

Output:

- `docs/interface-discovery.md`

---

### 0.0.2–0.0.3 — Java Printer Communication

status : done  
version : 0.0.2 and 0.0.3

#### 0.0.2

Goals:

- serial connection to printer
- command send/receive
- basic operational test against real hardware
- initial handling of port-access issues

#### 0.0.3

Goals:

- command send/receive logging
- repeated status polling
- basic printer initialization wait
- clean disconnect
- more stable communication flow for later extensions

---

### 0.0.4 — Automated Testing Foundation

status : done  
version : 0.0.4

Goals:

- extract serial communication behind an interface
- move polling logic into a testable service
- add JUnit tests
- add fake serial implementation
- test success case
- test no-response case
- test disconnect and error case
- test repeated polling behavior

---

### 0.0.5 — JaCoCo Coverage Baseline

status : done  
version : 0.0.5

Goals:

- add JaCoCo Maven plugin
- generate local HTML coverage report
- measure current automated test coverage
- identify uncovered or low-coverage areas

Result:

- added JaCoCo Maven plugin
- generated HTML coverage report
- established an initial coverage baseline
- identified `Main` and `SerialConnection` as key low-coverage areas

---

### 0.0.6 — Jenkins CI and Broader Automated Verification

status : done  
version : 0.0.6

Goals:

- automatically build the project on each commit or push
- run Maven verification and automated tests in Jenkins
- run JaCoCo coverage generation in CI
- archive test and coverage artifacts
- confirm that the project builds without requiring a real printer
- improve automated coverage across the main runtime paths
- add operator-facing runtime message reporting for operational review

Deliverables:

- add `Jenkinsfile`
- checkout repository in Jenkins
- run `mvn clean verify`
- publish JUnit test results
- archive JaCoCo coverage artifacts
- generate and archive:

```text
target/operator-message-report.md
```

* extend automated tests to cover four main test classes:

```text
PrinterPollerTest
SerialConnectionTest
MainIntegrationTest
MainRobustnessTest
```

* document how to run the same verification locally

Expected result:

* each Jenkins build confirms that the project compiles and tests pass
* coverage report is generated in CI
* operator-facing error scenarios are generated for review
* hardware-independent verification is available
* Jenkins provides reliable CI evidence, but not yet release preparation or deployment

---

### 0.0.7 — DevOps Pipeline and Release Automation

status : done  
version : 0.0.7


Goals:

* transform the Jenkins pipeline into a structured DevOps workflow
* validate application runtime behavior in CI using simulation
* prepare and package a reproducible release bundle
* optionally publish releases to GitHub
* preserve technical and operator-facing evidence as release artifacts

Deliverables:

Structured Jenkins pipeline stages:

```text
checkout
environment check
build and verify
simulated smoke run
prepare release bundle
package release archive
optional GitHub release publication
```

Runtime validation:

```bash
mvn exec:java \
-Dexec.mainClass="printerhub.Main" \
-Dexec.args="SIM_PORT M105 3 100 sim"
```

Release packaging:

```text
release/
├── printer-hub-<version>.jar
├── jacoco/
├── operator-message-report.md
├── README.md
├── test.md
├── devops.md
├── roadmap.md
└── version.md
```

Release archive generation:

```text
printer-hub-<version>-release.tar.gz
```

Optional GitHub publication:

```text
create GitHub release
upload release archive
generate release notes
```

Expected result:

* pipeline demonstrates full CI verification and runtime validation
* release artifacts are structured and reproducible
* CI output becomes suitable as DevOps demonstration material
* project gains a controlled release workflow
* foundation established for future CD or deployment stages

---

 
### 0.0.8 — Printer State Model

status : done  
version : 0.0.8

Goals:

- define explicit printer states
- implement state transitions during polling
- parse printer responses into a snapshot model
- expose current printer state internally

Example states:

```text
DISCONNECTED
CONNECTING
IDLE
HEATING
PRINTING
ERROR
UNKNOWN
```

Result:

* `PrinterState` added
* `PrinterSnapshot` added
* `PrinterStateTracker` added
* `PrinterPoller` updates and reports printer state
* simulated and real printer polling both show state transitions

Expected result:

* printer behavior is represented as an explicit state model
* future API and monitoring features can rely on defined snapshots
* command flow becomes easier to validate and extend

---

### 0.0.9 — Remote API Layer

status : done  
version : 0.0.9


Goals:

* add API mode to the existing Java application
* provide REST endpoints through a lightweight embedded HTTP server
* expose printer status as JSON
* prepare remote interaction without changing the CLI workflow

Example endpoints:

```text
GET  /health
GET  /printer/status
POST /printer/poll
```

Expected result:

* PrinterHub can run as a small service
* printer status is accessible through HTTP
* later dashboard and automation features can rely on stable endpoints
* project moves toward service-oriented architecture

---

  
### 0.0.10 — Continuous API Monitoring

status : done

Goals:

* keep API mode running as a monitoring service
* add background polling in simulated mode
* update printer status without requiring manual `POST /printer/poll`
* keep `/printer/status` refreshed from the latest monitoring cycle

Expected result:

* PrinterHub behaves more like a monitoring service
* printer status can update automatically while API mode is running
* later dashboard features can read live-ish status without triggering hardware access directly

---

### 0.0.11 — API Runtime and Smoke Verification

status : done

Goals:

* add automated tests for the remote API layer
* extend Jenkins API smoke verification
* verify `/health`, `/printer/status`, and `/printer/poll` in CI
* verify automatic status refresh behavior
* document API runtime validation workflow
* ensure clean startup and shutdown behavior in CI

Example local run:

```bash
mvn exec:java \
-Dprinterhub.api.port=18080 \
-Dexec.mainClass="printerhub.Main"
```

Example verification:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status

sleep 3

curl http://localhost:18080/printer/status
curl -X POST http://localhost:18080/printer/poll
```

Expected result:

* API server starts reliably
* `/health` always responds
* `/printer/status` updates automatically
* manual `/printer/poll` remains functional
* Jenkins validates API runtime behavior
* release artifacts include verified API usage documentation

---

### 0.0.12 — Failure Scenario Simulation

status : done

Goals:

* simulate printer errors
* simulate disconnected printers
* simulate timeout scenarios
* expose failures through API responses and printer status

Expected result:

* system can demonstrate operational failure handling
* `ERROR` and `DISCONNECTED` states become testable through the API
* project better represents real industrial operation

---

### 0.0.13 — Job Model Foundation

status : done

Goals:

* introduce a print job domain model
* define job lifecycle states
* prepare upload and execution workflows without real file upload yet

Example job states:

```text
CREATED
VALIDATED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Expected result:

* print jobs become explicit domain objects
* future upload, queue, and dashboard features rely on a stable model
* project moves from polling-only logic toward job orchestration

---

### 0.0.14 — Job Upload Simulation

status : done

Goals:

* add endpoint for creating a simulated print job
* validate supported file or job type
* store job metadata in memory
* connect job lifecycle to printer state

Example endpoints:

```text
POST /jobs
GET  /jobs
GET  /jobs/{id}
```

Expected result:

* users can create a job through the API
* job metadata can be inspected remotely
* the system starts resembling a centralized printer-farm backend

---

### 0.0.15 — In-Memory Printer Farm Simulation

status : done

Goals:

* support multiple logical printers
* expose all printer states through the API
* assign jobs to a selected printer
* simulate a small printer farm without requiring multiple real printers

Example endpoints:

```text
GET  /printers
GET  /printers/{id}/status
POST /printers/{id}/poll 
POST /printers/{id}/jobs
```

Expected result:

* project no longer represents only one printer
* API can expose a printer fleet
* later UI can display several printers in one dashboard

---

### 0.0.16 — Central Monitoring Dashboard

status : done

Goals:

* build a small web UI for printer farm monitoring
* display all printers and their current states
* show active jobs and last known status
* call the REST API instead of accessing printers directly
* refresh status from the API periodically

Expected result:

* users can monitor the printer farm from a central UI
* project demonstrates the full chain from UI to printer service
* the industrial simulation becomes visible and portfolio-ready

---

### 0.0.17 — Database Persistence

status : done


Goals:

* add persistent storage for jobs, printer states, and events
* keep history across application restarts
* prepare traceability for industrial-style monitoring

Possible technology:

```text
SQLite first
PostgreSQL later
```

Expected result:

* jobs and printer events are stored persistently
* printer history becomes queryable
* the project gains a foundation for reporting and audit trails

---
 
### 0.0.18 — Job Execution Simulation and Dashboard Cleanup

status : done

Goals:

* reduce snapshot noise
* show only configured/active printers in the dashboard
* remove misleading fake printer cards when only one printer is active
* move assigned jobs from ASSIGNED to RUNNING
* simulate completion and failure
* connect job state changes to printer state
* expose active job per print


Expected result:

* assigned jobs no longer remain static
* dashboard can show an active runtime job
* printer state and job state start moving together

---
 
### 0.0.19 — Local Runtime Configuration and Dashboard Administration

status : done

Goals:

* persist local printer configuration in SQLite
* administer configured printer nodes from the dashboard
* add, update, enable, and disable printer nodes without source-code changes
* support multiple local printers with different ports
* allow real and simulated printers in the same local runtime
* show only enabled configured printers in the dashboard
* store monitoring rules in the database
* administer monitoring rules from the dashboard
* support configurable snapshot storage rules:
  * store when printer state changes
  * store when temperature difference exceeds threshold
  * store when minimum interval has passed

Example configuration:

```text
printer-1 -> /dev/ttyUSB0 real
printer-2 -> /dev/ttyUSB1 real
printer-3 -> SIM_PORT sim

snapshot.minIntervalSeconds = 30
snapshot.temperatureThreshold = 1.0
snapshot.storeOnStateChange = true
```

---
 
</details>

----


<details  >
  <summary>0.1.x — Local Farm Runtime Architecture</summary>





## 0.1.x — Local Farm Runtime Architecture

Goal:

Restructure PrinterHub into the correct local farm runtime architecture.

The `0.1.x` line must first create the runtime backbone, then migrate existing features into it.

Target architecture:

```text
PrinterHub Java Runtime
├── Web server thread pool
│   └── handles REST API / dashboard HTTP requests
│
├── Monitoring scheduler
│   ├── printer-1 polling task
│   ├── printer-2 polling task
│   ├── printer-3 polling task
│   └── ...
│
├── Runtime state cache
│   └── latest known printer state per configured printer
│
├── Database access layer
│   └── persists snapshots, events, jobs, config
│
└── Serial communication layer
    ├── /dev/ttyUSB0
    ├── /dev/ttyUSB1
    └── SIM_PORT
```

Important rule:

```text
The API must not poll printers directly during normal dashboard/status reads.
The monitoring scheduler polls printers in the background.
The API reads the latest known state from the runtime state cache.
```

---

### 0.1.0 — Local Runtime Backbone

status: done

Goals:

* create a new branch in github develop where we remove the old src code in order to start from scratch
* introduce the final local PrinterHub runtime structure
* create the multi-threaded runtime backbone immediately
* run one Java process containing:

  * HTTP server thread pool
  * monitoring scheduler
  * runtime printer registry
  * runtime state cache
  * database access layer
  * serial communication layer
* support placeholders where implementation is not complete yet
* prepare migration of existing `0.0.x` code into the new structure

Expected components:

```text
PrinterHubRuntime
PrinterRuntimeNode
PrinterRegistry
PrinterRuntimeStateCache
PrinterMonitoringScheduler
PrinterMonitoringTask
RemoteApiServer
DatabaseInitializer
```

Expected runtime lifecycle:

```text
start database
load or register printers
create runtime registry
create state cache
start monitoring scheduler
start API server
```

Shutdown lifecycle:

```text
stop API server
stop monitoring scheduler
disconnect printers
close database resources
```

Expected result:

* PrinterHub becomes a structured local farm runtime
* API, monitoring, database, and serial communication are separated internally
* multiple printers can be monitored independently
* one failing printer does not block the whole runtime
* old `0.0.x` code can be reused selectively, but no longer controls the architecture

---



### 0.1.1 — Migrate Existing Monitoring into Runtime Tasks

status: done

Goals:

* move existing polling logic into `PrinterMonitoringTask`
* replace single-printer monitoring with per-printer monitoring tasks
* run independent polling cycles per configured printer
* isolate timeout, disconnect, and error handling per printer
* migrate simulated printer communication into the new runtime
* migrate real serial printer communication into the new runtime
* support polling commands such as `M105`, response parsing, timeout handling, and error state handling

Expected result:

* each printer has its own monitoring cycle
* background monitoring updates the runtime state cache
* monitoring failures are stored per printer
* simulated and real printer nodes can both be monitored
* the API remains responsive while monitoring runs
 

### 0.1.2 — Migrate Existing API and Dashboard onto Runtime Services

status: done

Goals:

* make REST API handlers call runtime services only
* remove printer orchestration from HTTP handlers
* expose local farm state from the runtime registry and state cache
* keep dashboard reads separate from hardware polling
* migrate dashboard printer cards
* migrate printer configuration API
* support adding, removing, enabling, disabling, and updating configured printers

Expected endpoints:

```text
GET    /health
GET    /printers
GET    /printers/{id}
GET    /printers/{id}/status
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
GET    /dashboard
```

Expected result:

* API becomes a clean facade over the local runtime
* dashboard reads current known state through the API
* printer cards are visible again
* printers can be configured through the runtime API/dashboard
* normal API reads do not trigger serial communication directly

Suggested execution order inside 0.1.2 :

#### Step A — Runtime REST API

Implement first:

GET    /health
GET    /printers
GET    /printers/{id}
GET    /printers/{id}/status
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable

This must use:

PrinterRegistry
PrinterRuntimeStateCache
PrinterMonitoringScheduler

No direct polling in handlers.

#### Step B — Dashboard

Then migrate:

GET /dashboard

and dashboard printer cards.

Dashboard must call the API/read cached state. It must not trigger polling.

---

### 0.1.3 — Migrate Persistence into Runtime Stores

status: done

Goals:

* isolate SQLite access behind store/repository classes
* avoid database logic inside API or monitoring code
* persist printer configuration
* persist polling snapshots
* persist printer events and monitoring failures
* keep the database schema ready for later job persistence
* prepare later replacement or extension of persistence

Implementation steps:

#### Step A — Persist Monitoring Snapshots and Events

* migrate SQLite database setup into the new runtime persistence layer
* persist polling snapshots from background monitoring
* persist printer events for successful polls, timeouts, disconnects, and errors
* keep monitoring code independent from SQL details by using stores

Expected stores:

```text
PrinterSnapshotStore
PrinterEventStore
```

#### Step B — Persist Runtime Printer Configuration

* persist configured printer nodes
* load configured printers at runtime startup
* keep bootstrap printers only as fallback/default seed data
* support dashboard/API-created printers surviving restart
* prepare global/default runtime configuration for later monitoring rules

Expected stores:

```text
PrinterConfigurationStore
```

Expected result:

* database access becomes clean and replaceable
* runtime services no longer depend directly on SQL details
* configured printers survive restart
* monitoring history survives restart
* persistence is part of the local runtime, not part of the API layer

---
 
### 0.1.4 — Runtime Verification, Error Management, and Non-Regression

status: done

Purpose:

Validate that the new `0.1.x` runtime architecture is not only structurally clean, but also operationally reliable.

This release completes the delayed hardening work from the `0.1.x` refactoring:

* centralized and interpretable error management
* per-printer failure isolation
* runtime-safe monitoring behavior
* persistence verification
* API and dashboard non-regression
* Jenkins smoke and robustness checks
* coverage reporting restored

This is not a cosmetic test release. It is the release that proves the rewritten multi-printer runtime can safely replace the migrated `0.0.x` behavior.

Goals:

* review and harden production error management before writing tests
* define centralized runtime error patterns for monitoring, API, persistence, and serial communication
* ensure every printer failure is isolated to the affected printer node
* ensure monitoring errors are visible through state cache, API, persisted events, and logs
* ensure database failures do not crash the runtime or block API reads
* verify that disabled printers are not monitored
* verify that bad printers do not block good printers
* verify simulated and real-style printer nodes can coexist
* verify API responsiveness during background monitoring
* verify dashboard/API behavior after the runtime refactoring
* verify persistence stores for printer configuration, snapshots, and events
* add unit tests for runtime, monitoring, persistence, serial simulation, and API handlers
* restore Jenkins smoke tests for normal runtime lifecycle
* add Jenkins robustness smoke tests for failure scenarios
* restore JaCoCo coverage reporting
* add non-regression tests for migrated `0.0.x` behavior

Implementation steps:

#### Step A — Production Code Review and Error-Management Hardening

Review and harden the high-risk runtime files before writing tests:

```text
PrinterMonitoringTask
PrinterMonitoringScheduler
RemoteApiServer
PrinterSnapshotStore
PrinterEventStore
PrinterConfigurationStore
PrinterHubRuntime
PrinterRuntimeNode
PrinterRuntimeStateCache
SerialConnection
SimulatedPrinterPort
```

Expected behavior:

```text
disabled printer -> no polling
bad printer -> ERROR only for that printer
good printers -> continue updating
API handlers -> read runtime/cache only
database failure -> visible but not fatal
serial failure -> converted into printer-level error
invalid API request -> clear HTTP error response
shutdown cleanup -> does not create false operational errors
```

Error-management rules:

```text
catch errors at the correct boundary
never let one printer crash the scheduler
never hide operational failures silently
store failure cause in runtime state
persist meaningful monitoring events
return interpretable API errors
avoid duplicate/noisy events for repeated identical failures
avoid database pollution during expected shutdown
centralized message for monitoring
```

#### Step B — Runtime and Monitoring Unit Tests

Add JUnit tests for the runtime backbone:

```text
PrinterRegistryTest
PrinterRuntimeStateCacheTest
PrinterRuntimeNodeFactoryTest
PrinterHubRuntimeTest
PrinterMonitoringTaskTest
PrinterMonitoringSchedulerTest
```

Verify:

```text
printer registration
printer lookup
state cache updates
enabled/disabled behavior
multi-printer monitoring
failure isolation
scheduler start/stop behavior
monitoring task error handling
```

#### Step C — Persistence Unit Tests

Add JUnit tests for SQLite-backed stores:

```text
DatabaseInitializerTest
PrinterConfigurationStoreTest
PrinterSnapshotStoreTest
PrinterEventStoreTest
```

Verify:

```text
schema creation
printer configuration insert/update/delete/load
snapshot persistence
event persistence
database file override with -Dprinterhub.databaseFile
safe behavior on invalid or unavailable database path
```

#### Step D — API and Dashboard Unit Tests

Add API-level tests:

```text
RemoteApiServerTest
```

Verify:

```text
GET /health
GET /printers
GET /printers/{id}
GET /printers/{id}/status
POST /printers
PUT /printers/{id}
DELETE /printers/{id}
POST /printers/{id}/enable
POST /printers/{id}/disable
GET /dashboard
```

Also verify API errors:

```text
invalid JSON -> HTTP 400
unknown printer -> HTTP 404
wrong method -> HTTP 405
runtime failure -> controlled HTTP 500
```

#### Step E — Serial and Simulation Non-Regression Tests

Add or restore tests for migrated `0.0.x` behavior:

```text
SerialConnectionTest
SimulatedPrinterPortTest
```

Verify:

```text
M105 simulated response
sim-error behavior
sim-timeout behavior
sim-disconnected behavior
real-style adapter error conversion
response parsing compatibility
connection cleanup
```

#### Step F — Jenkins Normal Lifecycle Smoke Test
 
Restore Jenkins smoke verification using the public runtime/API surface.

Lifecycle:

```text
remove test database
start PrinterHub runtime
verify /health
verify initial printer list
add simulated printer
verify printer appears
verify monitoring updates status
disable printer
verify monitoring stops for that printer
enable printer
verify monitoring resumes
update printer configuration
verify updated configuration
delete printer
verify printer removed
stop runtime
inspect SQLite database
restart runtime
verify persisted printers reload
```

#### Step G — Jenkins Robustness Smoke Test

Add failure scenarios:

```text
add good simulated printer
add sim-error printer
add sim-timeout printer
add sim-disconnected printer
verify API remains responsive
verify good printer continues updating
verify bad printers report ERROR
verify events are persisted with origin printer id
verify failures do not create uncontrolled database noise
verify dashboard still loads
```

HTTP robustness checks:

```text
invalid POST body -> HTTP 400
unknown printer -> HTTP 404
wrong method -> HTTP 405
missing required field -> HTTP 400
```

#### Step H — Coverage and Release Readiness

Restore CI coverage reporting:

```text
mvn clean verify
JaCoCo report generation
JUnit report publication
coverage artifact archival
operator/runtime smoke logs archived
```

Expected result:

* centralized runtime error handling is implemented and verified
* monitoring is safe under partial printer failure
* API remains responsive while monitoring runs
* persistence stores are tested and reusable
* dashboard/API behavior is verified after the refactoring
* migrated `0.0.x` behavior has non-regression coverage
* Jenkins verifies startup, API lifecycle, monitoring, persistence, and robustness
* `0.1.x` becomes ready as the foundation for later job management and administration hardening
 


---
 


 
</details>


<details open>
  <summary>0.2.x — Local Runtime Administration and Job Management</summary>
 




## 0.2.x — Local Runtime Administration and Job Management

Goal:

* configurable runtime behavior
* clearer local administration
* controlled manual printer commands
* job lifecycle support
* operational history and diagnostics
* local service-style packaging

---

### 0.2.0 — Monitoring Configuration and Dashboard Administration Basics

status: done

Goals:

* expose monitoring rules through the API
* persist monitoring intervals and thresholds
* allow runtime tuning without code changes
* improve dashboard cards for local administration use
* show whether a printer is enabled or disabled directly on the card
* distinguish more clearly between:

  * disabled printer
  * disconnected/error printer
  * real printer
  * simulated printer

Endpoints:

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Expected settings:

```text
poll interval
snapshot minimum interval
temperature delta threshold
event deduplication window
error persistence behavior
```

Dashboard expectations:

```text
show enabled / disabled status on each printer card
show real / simulated mode more clearly
make disabled state visually distinct from failure state
keep printer cards limited to configured printers
```

Expected result:

* monitoring behavior becomes configurable without source changes
* runtime tuning becomes persistent
* the dashboard becomes clearer for day-to-day local administration
* operators can immediately see whether a printer is intentionally disabled or operationally failing

---
 

### 0.2.1 — Manual Command Execution API

status: done

Goals:

* allow controlled manual printer commands
* execute commands through a dedicated command service
* keep manual command execution separate from monitoring
* persist command-related events
* support diagnostics, maintenance, and operator intervention
* start with a controlled predefined command set, not unrestricted raw command entry
* support single operator-triggered actions from the dashboard

Initial command scope:

```text
M105  read temperature
M114  read current position
M115  read firmware info
```

Possible later extensions:

```text
raw command input
movement commands beyond homing
pause/resume commands
restricted admin-only commands
```

Example endpoints:

```text
POST /printers/{id}/commands
GET  /printers/{id}/events
```

Dashboard expectations:

```text
predefined command buttons for safe read/info commands
small parameter forms for controlled commands such as target temperatures
single operator-triggered actions directly from the printer card
command result feedback visible in the dashboard
recent printer events visible for diagnostics
no direct free-text command box in the first step
```

Expected result:

* controlled operator commands become possible
* diagnostics are no longer limited to background monitoring
* monitoring and command execution remain separated internally
* dashboard-based single-command operator actions are available
* command handling creates the basis for later job execution services

---


### 0.2.2 — Job Management over Runtime Architecture
 
- step A : Foundation
- step B : Dashboard


status: done

Goals:

* connect print jobs to the runtime printer registry
* add persistent job creation and storage
* assign jobs to configured printers
* track job lifecycle through persisted state
* keep job logic out of HTTP handlers
* prepare later execution orchestration without coupling it directly to the API layer
* extend runtime nodes with execution ownership
* coordinate job execution with monitoring to avoid concurrent printer access
* expose basic job operations through the REST API
* make basic job creation and execution available through the dashboard

Expected lifecycle:

```text
CREATED
QUEUED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Initial semantic job scope:

```text
READ_TEMPERATURE
READ_POSITION
READ_FIRMWARE_INFO
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Job model note:

```text
A job is a first-class runtime object with its own lifecycle.
In this first implementation, one job maps to one guarded semantic printer action.
Manual commands from 0.2.1 remain operator-triggered actions outside the job lifecycle.
```

Expected result:

* jobs become a first-class runtime concept
* printer administration and job handling are connected
* persistence is ready for later execution logic
* basic job creation and execution are available through API and dashboard
* the runtime architecture is ready for richer execution and audit features later

 
---

</details>

### 0.2.3 — Local Audit, History Views, and Controlled Job Actions

status: done

* step A, B, C, D : done

#### step A — Audit and history visibility

status: done

Goals:

* expose printer event history
* expose snapshot history
* expose job history
* expose error history
* show job execution command and result details in dashboard and API
* make local diagnostics easier through both API and dashboard views
* make operator-triggered job outcomes reviewable after the fact

#### step B — New Dashboard UI and controlled real-printer job workflows

status: done

Goals:

* Dashboard UI with menu, navigation, component split to make it a two-level UI
* implement controlled job actions as predefined workflows, not just single raw command sends
* support multi-step preparation, validation, execution, and result interpretation for real-printer actions
* validate printer readiness before state-changing jobs are executed
* allow required pre-sequences before the main action command is sent
* make `HOME_AXES` a controlled workflow instead of only a direct `G28` send

Dashboard as two-level UI:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings
```

Selected printer navigation:

```text
Selected Printer
├── Home
├── Print
├── Prepare
├── Control
└── Info
```
---

#### step C — Correct execution diagnostics and classified outcomes

status: done

Goals:

* persist the actual printer response that led to success or failure
* distinguish clearly between:

  * successful completion
  * printer-reported failure
  * timeout / no response
  * communication failure
  * validation failure before command execution
  * workflow/orchestration failure
* ensure printer-reported failures are not rewritten as generic “no response” failures when a response exists
* persist raw and/or normalized diagnostics in job history
* persist workflow-step diagnostics, not only final job state
* show sent command, actual response, classified outcome, and failure detail in dashboard history and API responses
* support review and cleanup of completed or failed jobs, including deletion of related job diagnostics/history where implemented

Controlled job-action scope:

```text
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Note:

```text
For state-changing actions such as SET_NOZZLE_TEMPERATURE or SET_BED_TEMPERATURE,
step C classifies whether the guarded workflow command path succeeded or failed.
It does not yet prove that the requested physical target was reached and stabilized afterward.
```

Expected result:

* the local runtime becomes easier to inspect after failures
* dashboard and API become more useful for troubleshooting
* printer behavior, operator actions, and job state changes become reviewable after the fact
* controlled real-printer job actions become more operationally useful

---

#### step D — Asynchronous job execution

status : done

Goals: 

make POST /jobs/{id}/start return quickly, while the job runs in a background executor.

That step would include:

* add a bounded job executor pool
* keep one active job per printer
* return quickly from job start API
* use job state/events/diagnostics for progress
* keep dashboard behavior based on polling job state
* add tests for async start, busy printer rejection, and completed/failed background jobs


---

#### step E — File-backed print jobs and richer preparation/verification workflows

status: done

Goals:

* extend the job model so that jobs are no longer limited to one guarded semantic command
* support file-backed print jobs as a first-class runtime concept
* allow selection of an already prepared printable file stored on the PrinterHub host
* accept and validate printable file types, starting with `.gcode`
* persist print-file metadata and association with the job
* keep PrinterHub out of slicing logic:

  * no model slicing
  * no G-code editing
  * no slicer-host role in this version
* reject unsupported source formats for direct printing unless later explicitly integrated
* prepare richer controlled workflows where command acceptance and physical-effect verification are distinct concerns
* allow later workflow variants to include optional follow-up verification steps after state-changing commands


It will be done in this order:

- Add file metadata persistence. Done.
- Add `.gcode` validation and storage/registration logic. Done.
- Extend PrintJob so it can reference a print file. Done.
- Add backend API for printable files. Done.
- Add dashboard UI to select a file and create a file-backed job. Done.
- Keep actual G-code execution minimal/stubbed as “represented/prepared.” Done.
- Add tests around file validation, persistence, job creation, and unsupported file rejection. Done.


Job model note:

```text
A file-backed print job references an already prepared printable file.
PrinterHub does not generate or edit slice data in 0.2.x.
PrinterHub accepts an existing printable file, associates it with a job,
and later transfers or makes it available to the printer when execution starts.
```
 

Expected result:

* PrinterHub can represent a real print as a file-backed runtime job
* the Print area of the dashboard becomes tied to an actual printable artifact
* the runtime is prepared for real print activation using host-side stored files
* the job/workflow model is ready for richer verification-oriented actions beyond immediate command acceptance

---

Future local print execution mode split:

```text
Mode 1 — streamed job
PrinterHub owns the command stream.
PrintJobExecutionService sends G-code commands line by line,
waits for firmware acceptance, records diagnostics, and controls the full flow.

Mode 2 — autonomous printer job
PrinterHub stores or exposes a prepared file, requests print start,
monitors printer state, and persists telemetry/events while the printer firmware
owns the print execution.
```

0.2.3 continues with Mode 2 first because it is the safer first real-print path
for local PrinterHub operation. Mode 1 remains useful for mini jobs, calibration
jobs, controlled command sequences, and future streaming workflows, but it should
not block the first autonomous print activation milestone.

---


#### step F — SD-card administration and guarded host-side G-code upload

status: done

Purpose:

Make the selected-printer SD Card workflow real and usable:

```text
PrinterHub can inspect printer-side SD files,
register and manage printable targets,
store host-side .gcode files,
and copy a host-side file to a real printer SD card through
a guarded Marlin-style upload session.
```

This step does not yet start a real autonomous print from the uploaded file.

Goals:

* add a selected-printer SD Card view to inspect printer-side printable files
* move file preparation/upload responsibilities out of Print job creation and into the SD Card view
* make SD upload feedback visible in the dashboard with clear in-progress / success / failure status
* support Marlin-style host-driven SD upload only through a dedicated guarded upload session, not through the simple single-command path
* prevent conflicting access between monitoring, manual commands, and active SD upload execution
* persist SD-card listing, registration, enable/disable, and upload diagnostics

Selected-printer dashboard addition:

```text
Selected Printer
├── Home
├── Print
├── SD Card
├── Prepare
├── Control
├── Info
└── History
```

SD Card view scope:

* list printer-side files as reported by the firmware
* show the information available from the printer, such as:

  * filename
  * size when available
  * date/time when available
  * raw firmware line/details when richer parsing is not yet reliable
* refresh the SD-card file list on user demand
* automatically register newly discovered printer-side files when the SD-card list is refreshed, using the firmware-reported path as the printer-side path
* keep registration separate from print availability:

  * registered means PrinterHub knows that this printer-side path exists or existed
  * enabled means the file is allowed to appear in `PRINT_FILE` job creation
  * disabled registered files remain in persistence/history but are hidden from normal print-job selection
* register or upload a host-side `.gcode` file into the configured PrinterHub print-file storage directory
* show the relationship between host-side file metadata and printer-side firmware path when known
* allow review/edit of the host-side registered `.gcode` file before it is copied to the printer SD card, where safe
* upload/copy a registered host-side file to the printer SD card only when a reliable transfer path is confirmed
* show guarded upload status directly in the SD Card view
* after a host-side file is copied to a printer SD card, refresh/read that printer SD card so the resulting firmware path can be registered for that specific printer
* provide the registered printer-side file target that the Print page can later use for autonomous `PRINT_FILE` jobs

Print page scope after this split:

* create a print job from an enabled, already registered printer-side file target
* do not upload, edit, or register `.gcode` files directly in the job creation form
* show enough file identity for the operator to know what will be printed:

  * display name when available
  * firmware path / printer SD path
  * source host file when linked
  * size when available

Backend/API scope:

* add printer-specific SD-card/file-list operations
* add persistent mapping/registration for printer-side files discovered on SD card
* add enabled/disabled state to registered printer-side file targets and filter job creation to enabled targets
* add API support for registering an existing SD-card firmware path as a printable target
* add API support for enabling/disabling registered printer-side file targets
* keep host-side `.gcode` registration and upload APIs, but present them in the SD Card workflow rather than job creation
* add guarded API support for copying/transferring a registered host-side file to one selected printer SD card
* parse Marlin-style file-list responses as far as practical
* persist relevant file-operation diagnostics as printer events and/or job execution steps
* keep SD-card operations coordinated with monitoring and active jobs
* implement dedicated upload/session handling for firmware that requires numbered and checksummed host upload lines during `M28`/`M29` file transfer
* do not treat SD upload as a normal one-command request/response exchange

Suggested substeps:

1. Add firmware/file-list command support and parsing for SD-card listing. Done.
2. Add backend API for selected-printer SD-card file listing. Done.
3. Add the dashboard SD Card menu and read-only file list. Done.
4. Add automatic printer-side file registration for files discovered on SD-card refresh. Done.
5. Move host-side `.gcode` register/upload UI from Print job creation into the SD Card workflow. Done.
6. Persist/link host-side file metadata to printer-side firmware path when known. Done.
7. Change Print job creation so it only selects a registered printer-side printable target. Done.
8. Add enabled/disabled state for registered printer-side files and filter Print job creation to enabled targets. Done.
9. Add dashboard controls to enable/disable registered printer-side files. Done.
10. Add guarded host-file-to-printer-SD transfer entry points in backend and dashboard. Done.
11. Add dashboard-visible upload status and clearer operator error reporting for SD upload attempts. Done.
12. Real-printer verify the transfer path on the Ender-style Marlin path. Done.
13. Implement `SdCardUploadService` as a dedicated upload/session service instead of reusing the simple command path. Done.
14. Implement numbered/checksummed line streaming for SD upload, including resend handling and clean `M29` close. Done.
15. After transfer, refresh SD-card listing and link the discovered printer-side path to the host print file where possible. Done with confirmation-only linking.
16. Persist workflow steps and printer events for listing, registration, enable/disable, transfer, resend, confirmation, and failures. Done.
17. Add tests for list parsing, API responses, file registration/mapping, enable/disable filtering, upload-session behavior, checksum/resend handling, busy-printer rejection, and unsupported file operations. Done.

Expected result:

* the selected-printer dashboard can inspect printer-side SD-card files
* the SD Card page owns file preparation, host upload, SD-card registration, and guarded transfer actions
* the Print page creates jobs only from registered printer-side printable targets
* PrinterHub can copy a host-side `.gcode` file to the verified real printer SD card through a dedicated numbered/checksummed upload session
* upload confirmation depends on the printer SD listing, not on optimistic fallback registration
* Mode 2 print activation remains the next step

Note:

The upload path is currently verified and adapted against the Ender 2 Neo V3 style Marlin behavior. On that path, PrinterHub resets host line numbering, opens `M28` inside the numbered session, streams numbered/checksummed content lines, closes with numbered `M29`, and verifies with numbered `M20`.

---


#### step G — Autonomous real-printer print-start workflow and SD-card operation hardening

status: done

Purpose:

Turn the prepared SD-card/runtime model from Step F into actual autonomous
printer-side print activation, and finish the remaining SD-card administration
hardening.

Step G is about Mode 2:

```text
PrinterHub selects a registered printer-side file target,
asks the printer firmware to start printing it,
then observes the printer through monitoring, events, and job diagnostics.
```

Goals:

* implement Mode 2 controlled execution of file-backed `PRINT_FILE` jobs
* treat autonomous print start as a multi-step workflow, not as one direct command send
* validate printer readiness before print activation
* support required preparation phases before print start
* support printer-side file selection and activation as part of the job workflow
* add basic monitoring-assisted autonomous print completion handling after activation
* persist execution-step history for the print-start workflow
* add explicit guarded delete of printer-side SD files from the SD Card administration view
* track deleted printer-side files in persistence distinctly from enabled/disabled state
* add a monitoring/debug setting that enables printer wire trace logging only when operators request it
* verify and test upload behavior for `.gcode` files containing comments

Typical workflow scope:

```text
PRINT_FILE
├── validate printer enabled/reachable
├── validate no conflicting active job
├── validate fresh enough runtime state
├── optional prepare / homing / thermal checks
├── validate selected registered printer-side file target
├── inspect SD-card file state when needed
├── select firmware path / printer SD path
├── request autonomous print start
└── transition job to RUNNING
```

Suggested substeps:

1. Implement guarded printer-side file selection / print-start workflow.
2. Persist workflow steps and printer events for selection, activation, running transition, and failures.
3. Add explicit guarded delete only after real-printer command behavior is verified.
4. Extend printer-side file persistence beyond enable/disable so deleted files can remain traceable in history.
5. Add dashboard delete controls in the SD Card administration view.
6. Add a monitoring/debug flag that enables printer command/response trace logging only when requested.
7. Verify and test `.gcode` upload behavior with comment lines and representative real files.
8. Add tests for print-start workflow decisions, SD delete behavior, debug-trace flag behavior, and commented-file upload handling.
9. Add initial monitoring-assisted `RUNNING -> COMPLETED` handling for autonomous SD prints when firmware responses make completion observable.

Expected result:

* a real autonomous printer-side print can be started through the runtime as a controlled workflow
* file-backed print jobs are no longer just metadata
* print activation becomes coordinated, reviewable, and safer for real hardware use
* basic autonomous completion detection is available through printer monitoring for observable firmware responses
* SD-card administration covers enable/disable, delete, host upload, and operator-selected tracing
* Mode 1 streamed printing remains a later local-runtime capability

---


#### step H — Autonomous running print supervision and operator controls

status: done

Goals:

* deepen Mode 2 running real-printer supervision beyond the initial completion handling added in Step G
* expose running-print state and progress as far as the printer/firmware allows
* support controlled pause and cancel behavior for active print jobs
* distinguish clearly between:

  * running
  * paused
  * cancelling
  * completed
  * failed
  * cancelled
* persist terminal evidence and operator-visible outcome details
* improve dashboard visibility for active print execution

Already covered in Step G:

* autonomous print-start workflow events and execution-step diagnostics
* initial monitoring-assisted `RUNNING -> COMPLETED` handling when firmware responses expose completion

Expected result:

* autonomous real print jobs are not only startable but operable
* dashboard and API can follow running print execution more meaningfully
* completion, cancellation, and failure become properly reviewable in job history

---


#### step I — Dashboard print-job controls and recovery actions

status: done

Purpose:

Close the remaining operator-control gap in the dashboard so autonomous
printer-side jobs can be paused, resumed, cancelled, and restarted from a clear
browser workflow.

Goals:

* format dashboard timestamps for operators instead of showing raw ISO instants such as `2026-05-08T05:40:35.861517049Z`
* expose controlled pause and resume actions in the dashboard for active autonomous `PRINT_FILE` jobs
* add dedicated API routes for autonomous print pause and resume if they are not already exposed
* keep cancel behavior available for running and paused print jobs
* treat autonomous print cancel as a verified workflow: send abort, then confirm through SD print status before marking the job terminal
* prevent cancel from changing terminal jobs; `COMPLETED`, `FAILED`, and `CANCELLED` jobs must keep their final outcome
* add a cancel-request / waiting-for-printer-confirmation state when firmware reports busy or requires a physical printer-side stop confirmation
* add a restart/retry action for completed, failed, or cancelled `PRINT_FILE` jobs
* make restart create a new job attempt rather than mutating old completed history
* show which original job a restarted/retried job came from
* prevent restart when the original printer-side file target is deleted, disabled, or missing
* disable impossible controls based on current job state:

  * `RUNNING` can pause or cancel
  * `PAUSED` can resume or cancel
  * `COMPLETED`, `FAILED`, and `CANCELLED` can restart when the file target is still valid
  * `CREATED`, `QUEUED`, and `ASSIGNED` can start or cancel according to existing rules
* persist operator-control diagnostics for pause, resume, cancel, and restart
* expose job history and execution diagnostics consistently from the Print page job card and the selected-printer History page
* make job history clearly show:

  * pause command and response
  * resume command and response
  * cancel command and response
  * cancel status verification command and response
  * cancel-request / printer-busy evidence
  * restart source job
  * terminal outcome of each attempt
* make delete actions work from job cards where deletion is shown
* add filtering to the SD Card registered targets table by status, such as enabled, disabled, deleted, linked, and unlinked
* add an SD upload recovery action that can close an interrupted printer-side file-write session with a correctly numbered `M29`
* on SD upload failure after `M28` has opened the file, attempt the numbered `M29` close before reporting the upload as failed
* add a dashboard/API recovery command for operators when the printer remains in SD write mode after a failed transfer
* detect and represent USB-only versus mains-powered printer state when firmware exposes enough evidence
* gate dangerous or state-changing commands when the printer appears USB-powered only or otherwise not safely powered
* extend printer state beyond `IDLE` where useful, for example power-limited, waiting for confirmation, cancelling, and recovery-needed states
* add favicon/browser tab icon support for the dashboard
* improve dashboard wording so operators understand whether an action controls the printer firmware or only the PrinterHub job record

Dashboard expectations:

```text
Job card controls
├── Start    visible/enabled for ASSIGNED jobs
├── Pause    visible/enabled for RUNNING PRINT_FILE jobs
├── Resume   visible/enabled for PAUSED PRINT_FILE jobs
├── Cancel   visible/enabled for RUNNING or PAUSED jobs
└── Restart  visible/enabled for COMPLETED, FAILED, or CANCELLED PRINT_FILE jobs
```

```text
SD Card registered targets
├── filter by enabled / disabled
├── filter by deleted / available
├── filter by linked host file / unlinked printer-side path
└── keep upload/recovery status visible next to affected targets
```

API expectations:

```text
POST /jobs/{id}/pause
POST /jobs/{id}/resume
POST /jobs/{id}/cancel
POST /jobs/{id}/restart
POST /printers/{id}/sd-card/recovery/close-upload
```

Real-printer findings from Step I testing moved to `0.2.4`:

* Dashboard date/time values are now formatted for operators instead of raw ISO instants.
* Print page and global Jobs page job cards expose history and diagnostics consistently.
* Job-card delete controls are wired through the existing delete endpoint.
* `TURN_FAN_OFF` reports `M107 -> ok`, but the real printer fan continues running loudly.
* `SET_FAN_SPEED` with `M106 S0` reports `ok`, but the real printer fan sound does not change.
* Fan-control behavior needs hardware interpretation: distinguish controllable part-cooling fan from hotend, board, or power-supply fans that may not respond to `M106`/`M107`.
* Fan jobs currently prove command acceptance only; Step I should decide whether follow-up verification, clearer dashboard wording, or printer-specific capability notes are needed.
* `SET_NOZZLE_TEMPERATURE` and `SET_BED_TEMPERATURE` still need real-printer dashboard verification.
* Some commands work while the printer is USB-powered only, such as `M105`, but movement/heating/state-changing commands may be unsafe or firmware-hostile without mains power.
* Reliable mains-power detection is not exposed by the currently observed firmware response; later work should keep adding conservative warnings/gating as evidence becomes available.
* Failed or interrupted SD upload can leave the printer in an SD file-write session; Step I adds an operator recovery action that sends a numbered/checksummed `M29` close path.
* Cancel during autonomous print can receive repeated `busy` responses or mixed stale serial output; dashboard/backend now avoid terminal cancellation unless status verification confirms the print stopped.
* An `ok` after `M524` is not enough evidence by itself because stale serial responses can be mixed in; Step I cancellation verifies with `M27` before marking the job `CANCELLED`.
* Completed, failed, and cancelled jobs keep their terminal outcome and expose restart/retry instead of cancel.
* Dashboard includes a browser favicon/tab icon.

Expected result:

* operators can control an active autonomous print directly from the dashboard
* failed, cancelled, or completed print jobs can be retried without losing the original audit trail
* dashboard controls match job state instead of showing generic actions
* pause, resume, cancel, and restart are reviewable in job history and diagnostics
* dashboard tables and timestamps become practical for daily operator use
* real-printer anomalies are either fixed or represented honestly as firmware/hardware limitations

Expected result for 0.2.3 overall:

* audit and history views become useful for real diagnostics
* controlled printer-side actions become more robust and reviewable
* PrinterHub can manage a real print job based on an already prepared printable file
* the dashboard reflects both Ender-like printer logic and browser-native reviewability
* the runtime is ready for local real-printer print execution without becoming a slicer host

---


### 0.2.4 — Real-printer correction, SD upload hardening, and local packaging

Purpose:

Use the real-printer findings from `0.2.3` to close the remaining SD-upload and runtime-behavior gaps, then prepare the local runtime for stronger adaptive transfer behavior and later service-style packaging.

---

#### 0.2.4 — Step A — Real-printer correction and anomaly closure

status: done

Goals:

* harden completion detection for short autonomous SD prints so jobs do not remain stuck in `RUNNING` after the printer has already finished
* prevent stale active-job or stale printer-busy state from blocking restart or new print attempts when monitoring already shows the printer is idle
* improve cancel handling for printers that report `busy` or require physical confirmation before stop behavior is actually visible
* represent waiting and recovery states clearly in the dashboard, such as `CANCEL_REQUESTED`, `WAITING_FOR_PRINTER_STOP`, and recovery-needed situations
* keep cancellation evidence command-specific, so stale serial `ok` responses are not mistaken for proof that `M524` stopped the print
* strengthen SD upload recovery after interrupted `M28` write sessions
* verify `SET_NOZZLE_TEMPERATURE` and `SET_BED_TEMPERATURE` on the real printer with conservative values
* clarify whether temperature job success means command accepted, target trend observed, or target physically reached
* clarify observed fan-control behavior on the Ender-style printer, especially the distinction between controllable part-cooling fan and always-on hotend, board, or PSU fans
* detect or conservatively represent USB-only versus mains-powered state where firmware evidence allows
* gate or warn before dangerous movement, heating, or state-changing commands when safe printer power state is uncertain

Expected result:

* real-printer dashboard behavior matches observed firmware behavior
* stale `RUNNING` and stale busy states no longer block restart or new print attempts
* operators can distinguish command acceptance from verified physical effect
* the major real-printer anomalies discovered during `0.2.3` are either corrected or represented honestly as firmware or hardware limits

---

#### 0.2.4 — Step B — SD upload observability and transfer performance

status: done

Goals:

* add long SD upload progress reporting based on total upload lines and total file size known before transfer starts
* expose upload progress through backend state and dashboard UI
* show in-progress, success, and failure states clearly, including retry or resend evidence when relevant
* disable conflicting actions for the same printer while an SD upload is active, while keeping unrelated printers usable
* differentiate serial communication behavior between command-response operations and file-streaming operations
* reduce host-side polling overhead during SD transfer so upload throughput is not artificially limited by the runtime
* prepare the serial layer for later streamed print execution

Expected result:

* SD uploads are visible and reviewable during execution
* same-printer conflicting actions are blocked while upload is active
* command-response and file-streaming traffic use different serial timing behavior
* SD upload performance is improved where host-side wait behavior was part of the bottleneck
* transfer behavior is instrumented well enough to compare real-printer results at different timing and batching settings

---

#### 0.2.4 — Step C — Windowed SD upload

status: done

Goals:

* introduce a configurable SD upload batch-size setting representing the maximum number of numbered lines allowed in flight
* preserve the original per-line behavior when batch size is `1`
* allow real pipelined upload behavior when batch size is greater than `1`
* parse acknowledgement blocks correctly so multiple `ok` responses are not collapsed into a single logical response
* keep resend, upload error, and recovery-close behavior visible enough for diagnostics
* ensure `M29` closes the upload session using the correct next protocol line number rather than reusing the failed line number
* support small in-flight upload windows on real hardware and make performance testing measurable

Expected result:

* `batch=1` keeps the previous conservative behavior
* `batch>1` enables real multi-line in-flight SD transfer
* upload sequencing remains protocol-correct for numbered and checksummed lines
* failures during pipelined upload are diagnosable and recoverable
* the runtime can now measure whether small pipelined windows improve real-printer SD upload throughput

State-machine note:

```text
OPEN_UPLOAD
  -> M110 N0
  -> M28 target.gco
  -> upload file content in batches

For each batch:
  -> send whole batch in pipelined mode
  -> read responses one by one

  if all responses are ok:
      -> next batch

  if printer requests Resend: X:
      -> stop pipelined processing for this batch
      -> find X inside the current batch
      -> resend line X with normal retry logic
      -> resend X+1, X+2, ... to the end of that same batch, line-by-line
      -> when batch tail is fully accepted, continue with next batch in pipelined mode

  if X is not inside the active batch:
      -> fail upload
      -> optional recovery-close with M29
      -> stop

  if unrecoverable line error / retry exhaustion / connection failure:
      -> fail upload
      -> optional recovery-close with M29
      -> stop

After all file lines accepted:
  -> send M29
  -> list files with M20
  -> verify uploaded file exists
  -> success
```

---

#### 0.2.4 — Step D — buffered resend recovery and degraded replay stabilization

status: done

Goals:

* keep SD upload isolated from normal monitoring activity on the selected printer
* support pipelined SD upload with a configurable batch size
* retain a recent sent-line recovery history sized as `sdUploadBatchSize * sdUploadRecoveryWindowMultiplier`
* default `sdUploadRecoveryWindowMultiplier` to `2`
* allow resend recovery not only inside the active batch but also inside recently sent buffered history
* when resend targets a recoverable buffered line, replay line-by-line from that line through the end of buffered sent history
* drain pending serial input before resend replay starts
* switch the current upload into degraded single-send mode after resend instability is detected
* keep upload progress, protocol error counts, resend evidence, drain evidence, and recovery evidence visible in printer events
* fail only when recovery leaves the retained history window or when protocol instability exceeds configured limits

Expected result:

* SD upload can begin in pipelined mode for better throughput
* resend recovery works for both the current batch and recently buffered sent lines
* stale unread burst responses are drained before replay begins
* after a resend event, the upload can continue safely in degraded single-send mode instead of re-entering unstable batching immediately
* failures are diagnosable as resend outside recovery window, repeated identical resend loops, timeout, printer-side error, or cumulative protocol instability
* correctness and channel resynchronization now take priority over peak throughput once instability has been detected

Implemented behavior:

```text
OPEN_UPLOAD
  -> stop monitoring for this printer
  -> connect upload session
  -> send M110 N0
  -> send M28 target.gco
  -> initialize recent sent-line recovery history

NORMAL_UPLOAD
  -> build next outgoing window
  -> store window in recovery history
  -> send window in pipelined mode
  -> read responses one by one

if all responses are ok:
  -> continue with next unsent window

if printer requests Resend: X:
  -> if X is recoverable:
       drain pending serial input
       enable degraded single-send mode
       replay line-by-line from X through newest buffered sent line
       continue upload in single-send mode
  -> else:
       fail upload

if unrecoverable line error / retry exhaustion / connection failure:
  -> fail upload
  -> optional recovery-close with M29

if all file lines are accepted:
  -> send M29
  -> list files with M20
  -> verify uploaded file exists
  -> success
```

Note:

```text
Step D no longer assumes that a successful replay is enough evidence to return
immediately to full pipelined upload. Once resend instability is detected,
the current upload session degrades to single-send mode for safety.
```

---

 
#### 0.2.4 — Step E — transfer settings administration foundation

status: done

Goals:

* stop hardcoding SD upload transfer defaults as compile-time-only constants
* introduce persistent runtime-configurable serial transfer settings
* initialize defaults from `SerialDefaults` / protocol defaults only once
* allow editing these settings through dashboard settings
* expose transfer settings through API
* make later upload/runtime steps consume persisted settings instead of raw constants

Deliverables:

* transfer settings model and persistence store
* API endpoints to read and update transfer settings
* dashboard settings page support for transfer tuning
* runtime wiring so upload services read persisted settings

Expected result:

* monitoring-style settings management exists for serial/upload behavior too
* later adaptive logic can build on persisted operator-defined limits
* no need to change code just to tune upload behavior

---

#### 0.2.4 — Step F — Adaptive SD upload control

Status: done

Goal:

Make SD upload batch size adapt at runtime instead of staying fixed or permanently degraded after the first resend.

##### Core idea

Use a simple safe controller:

* start fast
* downgrade quickly on instability
* upgrade slowly after proven stability
* keep buffered resend recovery
* abort only when recovery is no longer safe or error limits are exceeded

##### Runtime state

* `configuredMaxBatchSize`
* `configuredMinBatchSize`
* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `recentRecoveryCount`
* `singleSendMode`

##### Tuning thresholds

* `stableLinesForUpgrade`
* `resendsBeforeDowngrade`
* `recoveryEventsBeforeSingleSend`

##### Algorithm

At upload start:

* `activeBatchSize = configuredMaxBatchSize`
* `acceptedLinesSinceLastResend = 0`
* `recentResendCount = 0`
* `recentRecoveryCount = 0`
* `singleSendMode = false`

On clean progress:

* add accepted lines to `acceptedLinesSinceLastResend`
* if stability reaches `stableLinesForUpgrade`:

  * increase `activeBatchSize` by 1
  * never exceed `configuredMaxBatchSize`
  * reset `acceptedLinesSinceLastResend`

On resend:

* drain pending input if needed
* run buffered replay recovery
* increment resend and recovery counters
* reset `acceptedLinesSinceLastResend = 0`

After resend:

* if resend count reaches `resendsBeforeDowngrade`:

  * reduce `activeBatchSize` by 1
  * never go below `configuredMinBatchSize`

If recovery keeps happening at minimum batch size:

* enable `singleSendMode`

On out-of-window resend:

* count it as a protocol anomaly
* count it against `rejectedLineCount`
* count it against `sdUploadMaxErrors`
* try resynchronization from the oldest recoverable buffered line

Abort only when:

* resend recovery falls outside retained history and cannot be resynchronized safely
* `sdUploadMaxErrors` is exceeded
* `sdUploadMaxConsecutiveIdenticalResends` is exceeded
* another hard protocol failure occurs

##### Deliverables

* adaptive batch controller in SD upload runtime state
* persisted settings for stability and downgrade thresholds
* upload events for upgrades, downgrades, resync, and single-send fallback
* API/dashboard exposure for active runtime upload metrics

##### Expected result

PrinterHub should recover from resend instability safely, climb back up after stable stretches, and settle near a practical throughput level for the real printer instead of staying permanently slow after one recovery.


---

#### 0.2.4 — Step G — Backend-to-frontend upload telemetry exposure

status: done

Goals:

* expose SD-card upload transfer telemetry through `GET /printers/{printerId}/sd-card/uploads/status`
* keep persistent `SerialTransferSettings` separate from per-upload adaptive runtime state
* publish runtime adaptive values such as configured batch limits, active batch size, resend pressure, recovery pressure, transport mode, and last adaptation reason/time
* render the returned fields in the SD-card dashboard view as raw readable telemetry for verification
* preserve final upload-session telemetry after success or error without writing runtime tuning values back to database settings

Expected result:

The browser can verify Step F adaptive upload behavior during and after an upload: progress, quality, throughput, active batch size, resend/recovery pressure, transport mode, and the last controller decision are visible from the SD-card page.

 
#### 0.2.4 — Step H — Functional two-card upload monitoring display

status: done

Goals:

* split upload monitoring into a normal operator card and a separate adaptive diagnostics card
* keep the upload status card focused on state, file, progress, lines, bytes, speed, timing, rejected lines, quality, and detail
* group adaptive diagnostics by current runtime decision, configured limits, and stability/resend pressure
* keep the display functional and readable without applying the Step I visual polish layer yet

Expected result:

The SD-card page shows upload progress as a clear operator summary while keeping adaptive controller internals available in a separate diagnostics card for verifying runtime behavior during long uploads.

 
#### 0.2.4 — Step I — Modern upload monitoring UX and operator-grade visualization

status: done

Goals:

* add a frontend transfer-health indicator for healthy, recovering, degraded, fallback, failed, complete, and idle states
* make upload progress, throughput, ETA, line/byte counters, resend count, and transfer quality readable at a glance
* add color-coded quality, resend, recovery, and stability pressure meters
* show adaptive controller decisions with a mode badge, active batch chip, configured range, and last adaptation reason/time
* keep runtime telemetry display near the SD-card upload workflow while leaving persistent transfer settings in settings

Expected result:

The SD-card page now reads like an operator monitoring panel: progress and alarms are visually prominent, adaptive changes are called out, and resend/recovery pressure can be interpreted without scanning raw logs.

 
#### 0.2.4 — Step J — Remote dashboard upload synchronization

status: done

Goals:

* add `Synchronize` and `Stop sync` controls beside the SD-card file refresh action
* reuse selected-printer upload-status polling so another browser can follow an upload started elsewhere
* poll only `GET /printers/{id}/sd-card/uploads/status`, without refreshing SD-card files or touching printer serial traffic
* keep the last visible upload card when synchronization is stopped
* show whether the selected printer is in live upload sync or manual-refresh mode

Expected result:

A second operator can open the dashboard from another PC, select the same printer, click `Synchronize`, and watch the existing upload telemetry card update live until they stop synchronization or leave the page.


---
### 0.2.5 — Global monitoring workspace and cross-printer runtime observability

status: done

Purpose:

Add a global Monitoring workspace for observing runtime activity across all configured printers without first selecting one printer.

Goals:

* add a global `Monitoring` dashboard menu entry
* add `GET /monitoring` as a backend runtime aggregation endpoint
* summarize configured, enabled, disabled, busy, and error printers
* show active/recent jobs across the local farm
* show active or last-known SD upload telemetry across printers
* expose adaptive upload diagnostics globally without replacing the selected-printer SD Card workflow
* provide follow/synchronize actions that jump from the global page to the focused selected-printer workspace

Expected result:

Operators can open one global Monitoring page to see farm runtime health, active jobs, and SD upload telemetry across printers, then follow a specific upload or job into the detailed printer page when they need deeper control.


### 0.2.6 — Runtime Recovery and Serial Device Robustness

status: done

Goals:

* improve recovery after real USB disconnect/reconnect
* reduce problems caused by unstable `/dev/ttyUSB*` device names
* make real-printer administration more robust
* improve operator visibility for serial-port failures

Minor CR / anomalies:

* README banner and dashboard screenshot path currently points to `docs/assets/media-src/...`, not a final published-media location
* dashboard.js: editing a disabled printer will re-enable it unintentionally (`enabled: true` always set even on update)

Focus:

* keep automatic retry behavior for recoverable monitoring failures
* better distinguish between:

  * disconnected device
  * invalid configured port
  * temporary communication failure
* support or document use of stable serial paths such as:

```text
/dev/serial/by-id/...
```

* improve dashboard/API error clarity for real printer connection problems

* now the job synchronization is basic : It jumps to the printer Print page and refreshes monitoring, but it does not yet start a dedicated job-status poller because there is no separate job live poller like the upload poller. So we beed a full live job polling.

#### 0.2.6.A — Serial disconnect classification and recovery behavior

status: done

Goals:

* classify serial communication failures with structured `serialFailureType` values
* distinguish device path, permission, busy/disconnected, timeout, write, protocol, temporary, and unknown serial failures
* keep monitoring retry behavior intact after a classified failure
* expose the classification through printer status, printer info, selected-printer home, and global Monitoring runtime views
* preserve the existing human-readable error message and event history behavior

#### 0.2.6.B — Stable serial path support and operator guidance

status: done

Goals:

* allow real-printer `portName` values to use stable `/dev/serial/by-id/...` paths without rewriting the configured value
* expose serial path metadata through printer and global Monitoring API responses
* warn operators when a real Linux printer uses unstable `/dev/ttyUSB*` or `/dev/ttyACM*` names
* show configured port, path type, stability, and guidance in Settings, Info, and Monitoring views
* document stable serial path discovery in install, quickstart, and dashboard docs

#### 0.2.6.C — Safer printer update behavior

status: done

Goals:

* preserve the existing enabled/disabled state when editing printer display name, mode, or port
* keep enable/disable as an explicit operator action
* make the dashboard edit form remember which printer is being edited, even if the visible ID field changes before save
* verify the API preserves the existing enabled state when a PUT update omits `enabled`

#### 0.2.6.D — Full live job synchronization from Monitoring

status: done

Goals:

* add a selected-job synchronization poller that refreshes job state, history, and execution diagnostics
* let Monitoring job synchronization jump to Selected Printer / Print and start live job follow
* show live job sync state and a Stop sync control on the selected-printer Print page
* stop synchronization automatically when a job reaches `COMPLETED`, `FAILED`, or `CANCELLED`
* keep manual job controls and existing upload synchronization behavior unchanged




Expected result:

* real printers recover more reliably after reconnect scenarios
* operators can understand whether the failure is caused by cable disconnect, changed port path, or invalid configuration
* local runtime administration becomes safer for real hardware use

---

### 0.3.0 — Local Security, Roles, and Dangerous Action Guards

status: planned

Goals:

* distinguish read-only monitoring actions from state-changing printer actions
* protect dangerous operations behind explicit confirmation
* introduce local operator/admin role separation
* prevent accidental execution of risky commands from the dashboard
* define safety wording for heating, movement, SD delete, cancel, and streamed execution
* add audit entries for all operator-triggered state-changing actions
* prepare authentication boundaries before central VPS integration

Risky action groups:

```text
heating
movement
homing
fan control
SD delete
file upload/overwrite
print start
pause/resume/cancel
emergency stop
streamed G-code execution
raw command execution
```

Expected result:

* PrinterHub becomes safer for real hardware operation
* operator actions are traceable
* central VPS integration later has a clean local permission model

#### 0.3.0.A — Local role and permission model

status: done

Goals:

* define built-in local roles: `VIEWER`, `OPERATOR`, and `ADMIN`
* define explicit backend permissions for dashboard viewing, printer visibility, monitoring, job control, SD-card/file operations, command execution, runtime configuration, and security management
* provide built-in role profiles that map each role to its default permission set
* add a lightweight `AuthorizationService` that can answer and enforce permission checks before API endpoint guards are wired in later steps
* keep the first implementation local and persistence-free so Step B can store the profiles cleanly

#### 0.3.0.B — Persist local security settings and role profiles

status: done

Goals:

* persist local security settings in SQLite with `securityEnabled`, `defaultRole`, and dangerous-action confirmation behavior
* persist built-in role profiles with permission JSON for `VIEWER`, `OPERATOR`, and `ADMIN`
* seed built-in role profiles during database initialization without changing user-modified profile permissions
* expose local security defaults through `/settings/security`, `/security/profile`, and `/security/roles`
* surface the first local security settings card and role profile summary in the dashboard Settings page

#### 0.3.0.C — Backend authorization guard for API endpoints

status: done

Goals:

* enforce persisted local role permissions in backend API handlers when local security is enabled
* keep dashboard visibility as UX only by checking permissions before endpoint handlers mutate runtime state
* resolve endpoint permissions consistently for printer configuration, settings updates, jobs, SD-card actions, print files, security settings, and command execution
* reject forbidden direct API calls with `403` and a clear permission-denied message
* support a local `X-PrinterHub-Role` override for testing/admin tooling until full user authentication exists

#### 0.3.0.D — Dangerous action confirmation model

status: done

Goals:

* add a backend dangerous-action model for heating, movement, homing, SD delete, upload overwrite, print start, print cancel, recovery close, raw command, and future streamed G-code execution
* enforce explicit `confirmed: true` request acknowledgement when `requireDangerousActionConfirmation` is enabled
* reject missing acknowledgement with HTTP `428` and a structured `confirmation_required` response containing the required dangerous action group
* keep read-only commands such as `M105`, `M114`, and `M115` outside the confirmation flow
* add dashboard confirmations and confirmation payloads for existing risky controls: SD upload, SD target delete, recovery close, job start/cancel, and dangerous manual commands

#### 0.3.0.E — Dashboard role-aware controls

status: done

Goals:

* show the current local security mode and effective dashboard role in the navigation/settings context
* add frontend permission helpers that evaluate the persisted role profile permissions already exposed by the backend
* disable job, SD-card, printer configuration, command, settings, and security controls that the current local role cannot execute
* keep disabled controls visible with role/permission hints so operators understand why an action is unavailable
* keep backend authorization as the real security boundary while improving dashboard clarity before rejected API calls happen

#### 0.3.0.F — Audit events for authorized and rejected state-changing actions

status: done

Goals:

* persist operator audit entries for state-changing API requests in SQLite
* record the local actor, effective role, resolved permission, dangerous action group, action path, target, result, failure reason, and timestamp
* write accepted audit entries when authorization and confirmation guards allow a state-changing action
* write rejected audit entries when authorization or dangerous-action confirmation blocks a request
* expose recent audit entries through `/operator-audit` and surface them in Monitoring plus selected-printer History views


---

## 0.4.x — Camera Monitoring & Visual Safety Layer

status: in progress

Purpose:

Add printer-side visual monitoring as a parallel subsystem of the local PrinterHub runtime. The camera layer observes configured printers, captures snapshots, exposes camera state through the REST API and dashboard, and later detects visual print anomalies such as spaghetti failures.

The camera subsystem must remain separate from serial communication, SD upload, and job execution internals. It may request safety actions only through controlled runtime services.
 
### 0.4.0 — Camera Monitoring Foundation

status: done

Goals:

- introduce a dedicated `printerhub.camera` package
- define `CameraDevice` abstraction
- support simulated camera and snapshot-folder camera sources
- persist camera settings per printer
- persist camera events
- capture latest snapshot per configured printer
- expose camera status and snapshot endpoints
- add a selected-printer Camera dashboard view
- keep real OpenCV/webcam support behind the abstraction and optional for later
- keep camera disabled by default

Out of scope:

- spaghetti detection
- OpenCV native dependency
- automatic print pause
- streaming video
- changes to SD upload or serial communication

---

### 0.4.1 — Cross-Platform Camera Capture Scripts

status: done

Goals:

- add dedicated camera tooling under `tools/camera/`
- keep Linux and Windows capture scripts separated:
  - `tools/camera/linux/`
  - `tools/camera/win/`
- add one-shot snapshot capture scripts
- add loop-based snapshot capture scripts
- use `ffmpeg` as the first real capture backend
- support Linux V4L2 devices such as `/dev/video0`
- support Windows DirectShow camera names such as `"AUKEY Webcam"`
- write camera files to persistent data directories
- keep `latest.jpg` overwritten on each capture cycle
- keep `previous.jpg` for future delta analysis
- optionally archive one snapshot every configured interval, for example every 5 minutes
- apply archive retention cleanup to avoid filling the data directory
- document camera discovery commands for Linux and Windows
- document the expected camera storage layout

Suggested tool structure:

```text
tools
├── camera
│   ├── linux
│   │   ├── camera-capture-once.sh
│   │   └── camera-capture-loop.sh
│   ├── win
│   │   ├── camera-capture-once.ps1
│   │   └── camera-capture-loop.ps1
│   └── README.md
└── win
    ├── r.ps1
    ├── run.env.example
    ├── s.ps1
    ├── t.ps1
    ├── u.ps1
    └── v.ps1
```

Suggested storage layout:

```text
data/camera/<printerId>/
  latest.jpg
  previous.jpg
  delta.jpg
  archive/
    20260518_180000.jpg
    20260518_180500.jpg
```

Windows runtime storage target:

```text
C:\ph\data\camera\<printerId>\
```

Linux development storage target:

```text
./data/camera/<printerId>/
```

Out of scope:

* spaghetti detection
* frame analysis
* OpenCV or JavaCV integration
* automatic printer intervention
* dashboard camera polish beyond showing existing files
* storing image blobs in SQLite

Notes:

* scripts are a bridge/proving layer, not the final product architecture
* real PrinterHub runtime should later manage capture through Java services
* image files should stay on filesystem
* SQLite should only store metadata/events later
* do not save every frame forever
* use overwritten live files plus sparse archive and retention cleanup

---

### 0.4.2 — Frame Analysis and Spaghetti Heuristic Detection

status: done

Goals:

* introduce `FrameAnalyzer`
* compare consecutive frames
* use `latest.jpg` and `previous.jpg` as the first analysis inputs
* calculate anomaly indicators
* calculate a visual delta score
* optionally generate `delta.jpg`
* introduce `SpaghettiDetectionService`
* expose confidence score and reason codes
* persist suspected visual anomalies
* show analysis state in dashboard
* change camera settings in the dashboard

Out of scope:

* automatic printer intervention
* hard stop/abort behavior
* direct serial access from camera code

---


### 0.4.3 — Camera Analysis Sessions and Trace Review

status: done

Purpose:

Introduce reviewable camera analysis sessions for a selected printer. A camera analysis session records the visual-analysis timeline independently from print jobs, so spaghetti detection can be inspected, replayed, and tuned before any automatic safety intervention is introduced.

Goals:

* introduce a dedicated camera analysis session model
* allow a camera analysis session to be started and stopped for a selected printer
* keep camera analysis sessions separate from print jobs and print-file jobs
* allow camera analysis to run in parallel with an active print job
* persist per-frame analysis results in a dedicated table
* store the visual delta score, confidence score, reason codes, frame timestamp, and related snapshot path
* keep image files on disk and store only metadata/path references in SQLite
* expose active and historical camera analysis sessions through the REST API
* show active camera analysis state in the selected-printer Camera dashboard view
* show previous camera analysis sessions for the selected printer
* visualize spaghetti detector values as a time-series graph
* allow timeline scrubbing with a slider or time selector
* when the selected time changes, show the closest related snapshot and analysis result
* support reviewing good, suspicious, and failed analysis points
* keep the implementation independent from serial communication, SD upload, and job execution internals

Pseudo specification:

```text
CameraAnalysisSession
  id
  printerId
  state: CREATED | RUNNING | COMPLETED | FAILED | CANCELLED
  startedAt
  stoppedAt
  createdAt
  updatedAt
  message

CameraAnalysisSample
  id
  sessionId
  printerId
  capturedAt
  analyzedAt
  latestSnapshotPath
  previousSnapshotPath
  deltaSnapshotPath
  deltaScore
  changedPixelRatio
  averagePixelDelta
  confidence
  suspected
  reasonCodes
  message
````

Suggested API endpoints:

```text
POST /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}
POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
```

Suggested dashboard behavior:

```text
Selected Printer -> Camera
  Active analysis session card
  Start analysis session button
  Stop analysis session button
  Recent analysis sessions list
  Analysis graph:
    x-axis = time
    y-axis = confidence / delta score
  Timeline slider:
    selecting a timestamp updates:
      latest snapshot
      delta snapshot
      reason codes
      confidence
      suspected/not suspected state
```

Out of scope:

* automatic printer pause
* automatic printer abort
* safety intervention decisions
* direct serial access from camera code
* changing print job lifecycle behavior
* storing image blobs in SQLite
* replacing the existing print job model

---

### 0.4.4 — Camera Safety Intervention

status: done

Goals:
 
* introduce a safety decision layer
* require repeated high-confidence detections before action
* persist `SPAGHETTI_SUSPECTED` and `SPAGHETTI_CONFIRMED`
* optionally pause SD print using controlled command flow
* persist safety action result
* show safety intervention in printer/job history
* keep safety actions disabled by default

Out of scope:

* automatic abort as default behavior
* direct serial access from camera code

---

### 0.4.5 — Real Webcam Backend

status: done

Goals:

* add real webcam implementation behind `CameraDevice`
* support Linux camera device paths
* support Windows camera names or indexes
* support `ffmpeg` command-based capture as the first backend
* evaluate OpenCV Java bindings or JavaCV only after ffmpeg capture is proven
* isolate native dependency handling
* document installation and troubleshooting
* keep camera backend replaceable behind the existing abstraction
* keep camera settings configurable (printerhub.config.RuntimeDefaults : intialized with constante, persistet in database, changeable in the settings of the dashbord)

Out of scope:

* making OpenCV mandatory
* streaming video
* bypassing the `CameraDevice` abstraction

---

### 0.4.6 — Camera Dashboard Job Debug

status: done

Goals:

* hardened u.ps1
* update docs/dashboard.md
* update docs/specification.md
* update docs/rest-api.md
* show camera analysis parameters and computed detector values in table form before graph polish
* show the analysis session card in the Camera view and Control view
* list archive pictures for the selected printer, with safe links to display them
* support start/stop time filters for camera archive review
* show a camera archive gallery with file list and picture previews
* keep archive browsing limited to `archive/*` files
* add dashboard-local snapshot sync using `Capture interval seconds`
* add dashboard-local automatic analysis sampling for active sessions
* make long event and analysis tables scroll inside their cards
* keep graph polish and backend/headless camera jobs for later


---

### 0.4.7 — Camera Picture And Data Management

status: done

Purpose:

Introduce the backend and admin-only structure needed to manage camera pictures, camera analysis data, and future replay/recalculation workflows. This milestone is about data ownership, cleanup, and simulation foundations before final dashboard polish.

Goals:

* add the concept of a camera archive entry linked to printer id and, when available, print job id
* make archived image filenames include date, time, and job id when a related job is known
* keep `snapshots/` as a bounded cyclic working folder
* enforce `Retained snapshots` as the maximum number of files kept in `snapshots/`
* keep `latest`, `previous`, and `delta` as working files, not gallery/archive entries
* decide and add an archive cadence setting if archiving every snapshot is too much
* persist enough metadata to delete archive pictures by job id
* add backend APIs for listing archive pictures by job id and time range
* add backend APIs for deleting archive pictures and related camera analysis data by job id
* add backend support for replaying a selected job's archived camera timeline
* add backend placeholder support for recalculating detector values with changed parameters
* add an admin-only Picture/Data Management menu placeholder
* move the current Camera files / Snapshot archive card concept toward that admin area
* add placeholder cards explaining future replay, cleanup, and recalculation workflows

Replay target:

```text
Admin -> Picture/Data Management
  Select job id
  Display ms setting
  Play
  Left: archived picture
  Middle: delta image associated with that moment
  Right: analysis values
    Captured at
    Analyzed at
    State
    Confidence
    Delta score
    Changed pixels
    Average delta
```

Recalculation target:

```text
Admin -> Picture/Data Management
  Select job id
  Edit detector parameters
  Re-run calculation placeholder
  Result table:
    archive file / delta file
    Captured at
    Analyzed at
    State
    Confidence
    Delta score
    Changed pixels
    Average delta
```

Out of scope:

* final graph polish
* replacing the camera analysis session model
* automatic printer pause/abort behavior
* allowing non-admin picture deletion
* deleting arbitrary filesystem paths

---

### 0.4.8 — Camera Dashboard Polish And Administration

status: planned

Purpose:

Connect the 0.4.7 picture/data management backend to a usable admin dashboard and polish the operator camera views.

Goals:

* make Picture/Data Management accessible only to the ADMIN profile
* connect the admin archive listing to backend picture/job metadata
* connect delete-by-job actions with confirmation
* connect replay controls to the archive timeline
* show archived picture, delta picture, and analysis values during replay
* connect recalculation placeholders enough to display computed result tables
* create graph for camera jobs to check evolution of spaghetti detection values over time
* access old archive pictures on demand depending on selected graph event/time
* improve camera cards
* show last frame age
* show camera event timeline
* show safety mode indicator
* show archive availability
* show capture backend status
* show camera storage path and retention status

Out of scope:

* changing SD upload logic
* changing serial communication ownership
* bypassing admin permissions for destructive picture/data cleanup
 
---

### 0.4.9 — Code Clean Up

- check that the code is completely following:
RuntimeDefaults.java       -> numeric/default runtime values
OperationMessages.java     -> event names, error keys, fixed message vocabulary
other java like :
CameraCaptureService.java  -> orchestration only, no duplicated event constants

- check that the code is not making saferecord when using the value of an operationmessage : if operation message not exist : code should not compile instead of writing an alternative message

- check docs updated: README.md, docs/roadmap.md, docs/dashboard.md, docs/specification.md, docs/rest-api.md

- true backend camera-job scheduler if you want it to continue without the browser open.





## 0.5.x Upload and Simulation Hardening 

### 0.5.1 — Print Asset Transfer and Printer File Handling Hardening

status: planned

Goals:

* harden Mode 2 host-side handling of printable files used by file-backed jobs
* clarify how PrinterHub transfers, selects, or exposes prepared `.gcode` files to the printer
* improve validation and error reporting around missing, unreadable, or invalid print files
* make print-file handling more reviewable in dashboard and API
* avoid ambiguous failures during print activation caused by file-path or transfer problems

Focus:

* host-side printable file registry or controlled file reference handling
* validation of file existence, readability, and allowed type
* clearer distinction between:

  * job exists but file missing
  * file invalid
  * file cannot be transferred, selected, or exposed
  * printer-side print activation failed after transfer/selection
* persist file-related diagnostics in job execution history

Expected result:

* file-backed print jobs become safer and more predictable
* operators can understand whether a print failure is caused by printer behavior or by file-handling problems
* the runtime becomes more reliable for repeated real print activation

---

### 0.5.2 — Post-Print Review and Operational History Hardening

status: planned

Goals:

* improve reviewability after completed, failed, or cancelled print jobs
* strengthen operator visibility of final print outcome
* correlate print job lifecycle, printer events, and execution diagnostics more clearly
* make local troubleshooting easier after real print runs

Focus:

* better final job summaries
* clearer per-step execution history in dashboard
* stronger linkage between printer-side events and job-side state changes
* clearer operator-facing failure narratives for real print attempts

Expected result:

* local print operations become easier to review after the fact
* PrinterHub becomes more usable for repeated real-printer operations and troubleshooting
* audit value improves beyond raw event storage
 

---

### 0.5.3 — Simulation upload more realistic

status: planned

Goals:

* make simulated SD-card upload behavior correct enough for normal Step E validation
* ensure default simulated upload always succeeds end-to-end when no error mode is requested
* improve simulator credibility for checksum, resend, timeout, and SD-card file-list workflows
* separate deterministic simulation from fault-injection simulation
* make upload and recovery scenarios testable without real hardware

Scope:

This step strengthens the simulated printer behavior around SD-card upload and related serial protocol flows.

It does not try to emulate full firmware complexity.
It focuses on the parts needed to validate PrinterHub upload, recovery, and dashboard behavior with confidence.

#### Step A — make baseline simulated SD upload correct

Goals:

* support `M28` upload-open behavior in the simulator
* accept uploaded checksummed payload lines during an active simulated write session
* support `M29` upload-close behavior
* make `M20` list the uploaded file after a successful simulated upload
* keep the baseline `sim` mode deterministic and stable

Expected result:

* uploading to a normal simulated printer works from dashboard and API
* uploaded file appears in simulated SD listing
* Step E upload flow can be validated without a real printer

#### Step B — model simulated SD-card state explicitly

Goals:

* introduce an internal simulated SD-card file registry
* persist uploaded simulated files in memory for the runtime session
* allow delete/list/read-style workflows to operate on the same simulated state
* keep simulator behavior consistent across repeated commands in one session

Expected result:

* simulator behaves like a coherent fake firmware target instead of isolated command stubs
* SD upload, file listing, and deletion share the same internal model

#### Step C — separate success simulation from fault simulation

Goals:

* keep `sim` as the reliable happy-path mode
* introduce dedicated fault-oriented simulation modes for upload stress testing
* avoid mixing normal development simulation with random failure behavior

Planned modes:

* `sim` or equivalent baseline success mode
* `sim-random-good` for mostly recoverable disturbances
* `sim-random-bad` for heavy disturbance and likely upload failure

Expected result:

* developers can choose between stable validation and protocol stress testing
* upload regressions are easier to classify

#### Step D — add realistic protocol disturbance profiles

Goals:

* simulate resend requests during upload
* simulate occasional checksum or line-order errors
* simulate timeout-style degraded responses where appropriate
* keep fault probabilities bounded by the selected simulation mode

Expected result:

* Step E recovery logic can be exercised against believable simulated failures
* upload controller behavior can be validated before real-printer testing

#### Step E — harden tests around simulation-specific upload behavior

Goals:

* add focused tests for simulated `M28` / payload / `M29` / `M20`
* add tests proving uploaded simulated files appear in SD listing
* add tests for deterministic success mode
* add tests for recoverable and non-recoverable simulated upload faults
* keep simulator changes covered at both unit and integration level

Expected result:

* simulated upload behavior is no longer accidental or under-tested
* future protocol refactors are less likely to break simulation silently


#### Out of scope

Not part of this step:

* full Marlin emulation
* exact firmware-specific timing reproduction
* persistent simulated SD storage across application restarts
* complete simulation of all printer commands

#### Likely impacted areas

Main code:

* `src/main/java/printerhub/serial/SimulatedPrinterPort.java`
* `src/main/java/printerhub/runtime/PrinterRuntimeNodeFactory.java`

Tests:

* `src/test/java/printerhub/serial/SimulatedPrinterPortTest.java`
* `src/test/java/printerhub/command/SdCardUploadServiceTest.java`
* `src/test/java/printerhub/api/RemoteApiServerTest.java`





---


## 1.0.x — Central VPS Multi-Farm Management

Goal:

Introduce the central platform that manages and observes multiple local PrinterHub runtimes.

Important architectural rule:

The VPS does not communicate directly with USB printers.
It communicates with local PrinterHub runtimes.

---

### 1.0.0 — Central Multi-Farm Architecture

status: future

Target architecture:

```text
Central VPS Dashboard
        |
        v
Central Backend API
        |
        v
Central Database
        |
        v
Farm Runtime Connectors
        |
        v
Local PrinterHub Runtime A
Local PrinterHub Runtime B
Local PrinterHub Runtime C
```

Goals:

* define central system boundaries
* distinguish local runtime from central platform
* define farm identity and registration

---

### 1.0.1 — Farm Registration

status: future

Goals:

* register local PrinterHub runtimes in the central platform
* assign farm IDs
* store farm metadata
* track farm online/offline status

---

### 1.0.2 — Central Farm Status Aggregation

status: future

Goals:

* collect printer summaries from multiple farms
* show central overview of all farms
* separate local printer state from central aggregated state

Expected result:

```text
Farm A: 3 printers
Farm B: 8 printers
Farm C: offline
```

---

### 1.0.3 — Central Dashboard

status: future

Goals:

* build VPS dashboard for all registered farms
* show farm-level and printer-level summaries
* link to local runtime details where appropriate

---

### 1.0.4 — Farm Synchronization Protocol

status: future

Goals:

* decide whether farms push data to VPS or VPS pulls from farms
* define snapshot payloads
* define event payloads
* define retry and offline behavior

Possible models:

```text
push model: local runtime -> central VPS
pull model: central VPS -> local runtime
hybrid model
```

---

### 1.0.5 — Central Job Dispatch Concept

status: future

Goals:

* define whether jobs can be submitted centrally
* route central jobs to a selected farm
* keep local runtime responsible for actual printer execution

Important rule:

```text
central platform requests work
local PrinterHub runtime executes work
```

---

### 1.0.6 — Security and Authentication

status: future

Goals:

* secure farm-to-VPS communication
* authenticate local runtimes
* protect central dashboard
* define access model

---

### 1.0.7 — Multi-Farm Operational History

status: future

Goals:

* store central history
* aggregate farm events
* expose fleet-wide diagnostics
* support future reporting


---

## 2.0.x - Steamed G-Code


? is it necessary ?

### 2.0.0 — Streamed G-code Job Execution

status: planned

Purpose:

Introduce Mode 1 for local jobs where PrinterHub owns the command stream.

This is intentionally planned after the autonomous print path because streamed
printing turns PrinterHub into the real-time sender. It requires stronger flow
control, response tracking, cancellation behavior, and recovery rules than
autonomous printer-side execution.

Goals:

* support a streamed job execution mode for selected `.gcode` files or generated mini jobs
* send G-code commands sequentially through `PrintJobExecutionService`
* wait for firmware acceptance before sending the next command
* persist per-line or grouped execution diagnostics without flooding history unnecessarily
* support pause, cancel, and failure handling for a PrinterHub-owned stream
* coordinate streamed execution with monitoring so status polling does not corrupt the command flow
* keep streamed mode separate from autonomous printer-side print mode in API, persistence, and dashboard wording

Typical workflow scope:

```text
STREAMED_GCODE
├── validate printer enabled/reachable
├── validate no conflicting active job
├── validate selected .gcode file or mini-job command list
├── open controlled printer session
├── send next command
├── wait for ok / busy / error / timeout
├── persist grouped diagnostics
├── repeat until complete, cancelled, or failed
└── close controlled printer session
```

Expected result:

* PrinterHub can execute small controlled G-code streams itself
* mini jobs and future calibration workflows can be controlled line by line
* autonomous print mode remains available for normal printer-side file execution
* local 0.2.x printing supports both architecture models without moving central monitoring into scope


---
