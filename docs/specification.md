# PrinterHub Specification

This document describes the current PrinterHub solution.

It is intentionally limited to what exists now. It does not describe roadmap work or planned future features.

---

## Runtime Summary

PrinterHub is a local Java runtime for monitoring and controlling Marlin-compatible 3D printers over serial communication.

The runtime provides:

* embedded REST API
* embedded dashboard
* SQLite persistence
* background printer monitoring
* asynchronous job execution
* controlled printer commands and job workflows
* simulated printer ports for development and tests

The real-printer development reference is a Creality Ender-3 V2 Neo.

---

## Start Command

Run verification:

```bash
mvn clean verify
```

Start the local runtime:

```bash
mvn exec:java \
  -Dprinterhub.databaseFile="printerhub.db" \
  -Dprinterhub.api.port=18080 \
  -Dexec.mainClass="printerhub.Main"
```

Open the dashboard:

```text
http://localhost:18080/dashboard
```

Runtime properties:

| Property | Default | Purpose |
| --- | --- | --- |
| `printerhub.api.port` | `8080` | REST API and dashboard port |
| `printerhub.databaseFile` | `printerhub.db` | SQLite database file |
| `printerhub.monitoring.intervalSeconds` | `5` | monitoring poll interval |

---

## High-Level Architecture

```mermaid
flowchart TB
    main["Main"]
    runtime["PrinterHubRuntime"]

    api["RemoteApiServer<br/>HTTP API + Dashboard"]
    registry["PrinterRegistry<br/>configured runtime nodes"]
    cache["PrinterRuntimeStateCache<br/>latest printer snapshots"]
    monitor["PrinterMonitoringScheduler<br/>background polling"]
    jobs["AsyncPrintJobExecutor<br/>background job workers"]
    jobExec["PrintJobExecutionService<br/>workflow execution"]
    commands["PrinterCommandService<br/>manual command API"]
    persistence["SQLite persistence<br/>configuration, rules, snapshots, events, jobs, steps"]
    ports["PrinterPort<br/>SerialConnection or SimulatedPrinterPort"]
    dashboard["Dashboard resources<br/>HTML/CSS/JS modules"]

    main --> runtime
    runtime --> api
    runtime --> registry
    runtime --> cache
    runtime --> monitor

    api --> dashboard
    api --> commands
    api --> jobs
    api --> persistence
    api --> cache
    api --> registry

    monitor --> registry
    monitor --> ports
    monitor --> cache
    monitor --> persistence

    jobs --> jobExec
    jobExec --> registry
    jobExec --> ports
    jobExec --> persistence
    jobExec --> monitor

    commands --> ports
    commands --> persistence
```

---

## Startup Flow

```mermaid
sequenceDiagram
    participant Main
    participant Runtime as PrinterHubRuntime
    participant DB as SQLite
    participant Registry as PrinterRegistry
    participant Monitor as PrinterMonitoringScheduler
    participant API as RemoteApiServer

    Main->>Main: read system properties
    Main->>Runtime: build runtime services
    Main->>Runtime: start()
    Runtime->>DB: initialize schema
    Runtime->>DB: load monitoring rules
    Runtime->>Monitor: update monitoring rules
    Runtime->>DB: load configured printers
    Runtime->>Registry: register printer nodes
    Runtime->>Registry: initialize
    Runtime->>Monitor: start monitoring
    Runtime->>API: start HTTP server
```

Shutdown calls:

```text
RemoteApiServer.stop()
PrinterMonitoringScheduler.stop()
PrinterRegistry.close()
AsyncPrintJobExecutor.close()
```

---

## Backend

The backend is a Java application using:

* Java `HttpServer` for the REST API and dashboard resources
* SQLite through JDBC
* jSerialComm for real serial ports
* in-process simulated printer ports for tests and local development
* Maven for build and test execution

The API server uses a fixed request thread pool:

```text
DEFAULT_API_THREAD_POOL_SIZE = 8
```

The dashboard is served from:

```text
/dashboard
```

The main API areas are:

```text
GET    /health
GET    /printers
POST   /printers
GET    /printers/{id}
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
GET    /printers/{id}/status
GET    /printers/{id}/events
POST   /printers/{id}/commands
GET    /printers/{id}/sd-card/files

GET    /print-files
POST   /print-files
GET    /print-files/{id}

GET    /jobs
POST   /jobs
GET    /jobs/{id}
DELETE /jobs/{id}
POST   /jobs/{id}/start
POST   /jobs/{id}/cancel
GET    /jobs/{id}/events
GET    /jobs/{id}/execution-steps

GET    /settings/monitoring
PUT    /settings/monitoring
```

---

## Persistence

PrinterHub persists local runtime data in SQLite.

Persisted data includes:

* printer configuration
* monitoring rules
* printer snapshots
* printer events
* print-file metadata
* print jobs
* print job execution steps

The database file is selected by:

```text
-Dprinterhub.databaseFile=<file>
```

If no property is provided, the default file is:

```text
printerhub.db
```

---

## Threading Model

PrinterHub uses separate thread pools for separate responsibilities.

```mermaid
flowchart TB
    apiPool["API thread pool<br/>default 8"]
    monitoringPool["Monitoring scheduler pool<br/>runtime-sized, lazy default 8"]
    jobPool["Job executor pool<br/>default 8"]

    apiReq["HTTP requests"]
    monitorTasks["Scheduled monitoring tasks"]
    jobTasks["Background job tasks"]

    apiReq --> apiPool
    monitorTasks --> monitoringPool
    jobTasks --> jobPool

    apiPool --> apiWork["short API work<br/>CRUD, reads, enqueue job"]
    monitoringPool --> pollWork["poll printers with M105"]
    jobPool --> jobWork["execute controlled workflows"]
```

Default limits:

```text
API request thread pool: 8
Job executor pool:      8
Monitoring lazy pool:   8
Monitoring start pool:  max(1, configuredPrinterCount + 4)
```

Important behavior:

* API requests do not execute long jobs directly.
* `POST /jobs/{id}/start` queues a background job and returns quickly.
* The background job executor performs the long-running workflow.
* Each printer can have only one active job at a time.
* Monitoring is stopped for a printer while a job is executing on that printer, then restarted afterward if the printer is still enabled.

---

## Printer Monitoring

Enabled printers are monitored by `PrinterMonitoringScheduler`.

The scheduler creates a repeated `PrinterMonitoringTask` for each enabled printer.

```mermaid
sequenceDiagram
    participant Scheduler as MonitoringScheduler
    participant Task as MonitoringTask
    participant Port as PrinterPort
    participant Cache as RuntimeStateCache
    participant DB as SQLite

    Scheduler->>Task: scheduleWithFixedDelay
    Task->>Cache: set CONNECTING
    Task->>Port: connect()
    Task->>Port: sendCommand("M105")
    Port-->>Task: printer response
    Task->>Task: classify state
    Task->>Cache: update latest snapshot
    Task->>DB: persist snapshot
    Task->>DB: persist event when policy allows
```

Monitoring uses the configured polling interval.

The default status command is:

```text
M105
```

The dashboard reads the latest cached state. Normal dashboard reads do not poll printers directly.

---

## Printer State Machine

The observable printer state is stored in `PrinterRuntimeStateCache` as the latest `PrinterSnapshot`.

```mermaid
flowchart TB
    A["Configured printer"] --> B{"Enabled?"}

    B -- "No" --> C["DISCONNECTED"]
    B -- "Yes" --> D["CONNECTING"]

    D --> E{"Poll result"}

    E -- "timeout / disconnect / exception" --> F["ERROR"]
    E -- "error / kill / halted response" --> F
    E -- "busy / printing response" --> G["PRINTING"]
    E -- "hotend above threshold" --> H["HEATING"]
    E -- "ok / T: response" --> I["IDLE"]
    E -- "unclassifiable response" --> J["UNKNOWN"]

    F --> D
    G --> D
    H --> D
    I --> D
    J --> D
```

States:

```text
DISCONNECTED
CONNECTING
IDLE
HEATING
PRINTING
ERROR
UNKNOWN
```

Response classification:

```text
contains "error", "kill", or "halted" -> ERROR
contains "busy" or "printing"         -> PRINTING
hotend above heating threshold        -> HEATING
contains "ok" or "t:"                 -> IDLE
otherwise                             -> UNKNOWN
```

---

## Serial Access

Printer communication goes through the `PrinterPort` interface.

Implementations:

```text
SerialConnection        real serial port
SimulatedPrinterPort    simulated printer behavior
```

`SerialConnection` methods are synchronized:

```text
connect()
sendCommand(command)
disconnect()
isConnected()
```

This protects one `SerialConnection` instance from concurrent access.

Manual command execution also synchronizes on the printer port:

```text
synchronized (node.printerPort()) {
    connect
    send command
    disconnect
}
```

Job execution marks the printer node as busy with:

```text
PrinterRuntimeNode.beginJobExecution(jobId)
PrinterRuntimeNode.endJobExecution()
```

This prevents two jobs from executing on the same printer at the same time.

---

## Asynchronous Job Execution

Job start is asynchronous.

```mermaid
sequenceDiagram
    participant UI as Dashboard/API client
    participant API as RemoteApiServer
    participant Async as AsyncPrintJobExecutor
    participant Jobs as PrintJobService
    participant Worker as Job worker thread
    participant Exec as PrintJobExecutionService
    participant Port as PrinterPort
    participant DB as SQLite

    UI->>API: POST /jobs/{id}/start
    API->>Async: start(jobId)
    Async->>Jobs: load job
    Async->>Async: validate assigned state and printer readiness
    Async->>Jobs: mark RUNNING
    Async->>DB: record JOB_EXECUTION_QUEUED
    Async->>Worker: submit execution task
    API-->>UI: 200, job RUNNING, outcome QUEUED

    Worker->>Exec: executeStartedJob(jobId)
    Exec->>Port: connect()
    Exec->>Port: send workflow commands
    Port-->>Exec: responses
    Exec->>DB: persist execution steps and events
    Exec->>Jobs: mark COMPLETED or FAILED
    Exec->>Port: disconnect()
```

Job executor default:

```text
DEFAULT_JOB_EXECUTOR_POOL_SIZE = 8
```

Job states:

```text
CREATED
QUEUED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Current start flow:

```text
ASSIGNED -> RUNNING -> COMPLETED
ASSIGNED -> RUNNING -> FAILED
ASSIGNED -> FAILED when rejected before background submission
ASSIGNED/RUNNING -> CANCELLED when cancelled through API
```

The `QUEUED` enum exists, but the current start flow marks the job `RUNNING` before submitting it to the background executor and records the queue moment as a `JOB_EXECUTION_QUEUED` event.

---

## Job Execution State Machine

```mermaid
flowchart TB
    A["ASSIGNED"] --> B{"Start requested"}
    B --> C{"Validation passes?"}
    C -- "No" --> F["FAILED"]
    C -- "Yes" --> D["RUNNING"]
    D --> E{"Workflow result"}
    E -- "success" --> G["COMPLETED"]
    E -- "failure / exception" --> F
    A --> H["CANCELLED"]
    D --> H
```

Execution events include:

```text
JOB_CREATED
JOB_ASSIGNED
JOB_STARTED
JOB_EXECUTION_QUEUED
JOB_EXECUTION_STARTED
JOB_EXECUTION_IN_PROGRESS
JOB_EXECUTION_SUCCEEDED
JOB_EXECUTION_FAILED
JOB_COMPLETED
JOB_FAILED
JOB_CANCELLED
```

Execution diagnostics are persisted as workflow steps.

Each execution step can store:

* job id
* step index
* step name
* wire command
* response
* outcome
* success flag
* failure reason
* failure detail

---

## Controlled Job Actions

Current controlled job types:

```text
READ_TEMPERATURE
READ_POSITION
READ_FIRMWARE_INFO
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
PRINT_FILE
```

The job workflow layer maps semantic job types to wire commands.

Examples:

```text
READ_FIRMWARE_INFO -> M115
READ_TEMPERATURE   -> M105
READ_POSITION      -> M114
HOME_AXES          -> M114, then G28
PRINT_FILE         -> represented/prepared metadata step, no G-code sent
```

Responses are classified into success or failure by the job response classifier.

For long-running commands such as `G28`, the serial read timeout is longer than normal commands. Busy responses such as `echo:busy: processing` are treated as in-progress information, not as final success by themselves.

---

## Dashboard Solution

The dashboard is served by the backend from:

```text
/dashboard
```

Current frontend files:

```text
src/main/resources/dashboard/
├── index.html
├── dashboard.css
├── dashboard.js
├── api.js
├── state.js
├── components/
└── views/
```

`dashboard.js` is the entrypoint.

`api.js` uses relative URLs such as:

```text
/printers
/jobs
/settings/monitoring
```

Because of this, the dashboard follows the API port that served it.

Dashboard navigation:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings

Selected Printer
├── Home
├── Print
├── SD Card
├── Prepare
├── Control
├── Info
└── History
```

Dashboard refresh behavior:

```mermaid
flowchart TB
    manual["Manual Refresh Now"] --> all["Fetch printers, jobs, monitoring rules<br/>rerender current page"]
    auto["Automatic background refresh"] --> live["Fetch printers only<br/>update live printer fields"]
    action["Action-based refresh"] --> scoped["Refresh data needed by that action"]

    live --> keep["Keep forms, expanded diagnostics,<br/>selected view, and loaded history"]
```

Automatic refresh does not rerender the whole dashboard. It updates live printer fields only:

* state
* temperatures
* last response
* error message
* updated timestamp

---

## Dashboard/API Data Flow

```mermaid
sequenceDiagram
    participant Dashboard
    participant API as RemoteApiServer
    participant Cache as RuntimeStateCache
    participant DB as SQLite
    participant Jobs as AsyncPrintJobExecutor

    Dashboard->>API: GET /printers
    API->>Cache: read latest printer states
    API-->>Dashboard: printer list and current states

    Dashboard->>API: POST /jobs/{id}/start
    API->>Jobs: queue job
    API-->>Dashboard: job RUNNING, outcome QUEUED

    Dashboard->>API: GET /jobs/{id}/events
    API->>DB: load persisted events
    API-->>Dashboard: events

    Dashboard->>API: GET /jobs/{id}/execution-steps
    API->>DB: load persisted steps
    API-->>Dashboard: execution diagnostics
```

---

## Current Boundaries

PrinterHub currently does not perform slicing, model conversion, or G-code editing.

Current jobs are controlled command/workflow jobs and represented file-backed `PRINT_FILE` jobs.

The dashboard can register existing host-side `.gcode` paths, upload `.gcode` files into the configured print-file storage directory, select print files for `PRINT_FILE` jobs, and show the file content read-only from a job card. The runtime does not transfer or stream those files to a printer in this version.

All persistence is local SQLite.

The current runtime is local-first; there is no central server/farm federation in the implementation described here.
