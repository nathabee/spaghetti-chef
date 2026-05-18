<p align="center">
  <img src="docs/assets/media/banner-1544x500.png" alt="PrinterHub banner">
</p>

# PrinterHub

**PrinterHub** is a Java-based local runtime for monitoring and controlling 3D printers through an embedded dashboard, REST API, persistent runtime state, and controlled printer workflows.

The project is currently in the **0.4.x camera and visual safety phase**. The active development focus is camera snapshot capture, frame comparison, and the foundation for later spaghetti detection.

PrinterHub remains local-first: one PrinterHub runtime controls and observes a local printer farm through USB-connected or simulated printers. The current real-printer reference is a **Creality Ender-series Marlin-compatible printer**.

For the detailed version roadmap, see:

* [`docs/roadmap.md`](docs/roadmap.md)

---

## Current Focus

PrinterHub is currently developing the camera monitoring layer:

* per-printer camera configuration
* latest snapshot capture and display
* filesystem-based camera snapshot storage
* provisional Linux and Windows camera capture helpers
* preparation for frame delta analysis
* preparation for spaghetti detection heuristics

The camera work builds on the existing local runtime, dashboard, REST API, persistence, printer monitoring, job execution, SD-card handling, local security, and audit foundations.

---

## Current Runtime Capabilities

Implemented local runtime capabilities include:

* local multi-printer dashboard
* REST API and SQLite-backed runtime state
* background printer monitoring
* simulated and USB-connected printer support
* selected-printer workspaces for status, jobs, SD-card files, control, info, history, and camera views
* guarded job execution and printer-side `PRINT_FILE` starts
* host-to-printer SD-card upload with telemetry and diagnostics
* role-aware local security for `VIEWER`, `OPERATOR`, and `ADMIN`
* dangerous-action confirmation for heating, movement, print start/cancel, SD delete, upload overwrite, and raw command paths
* operator audit records for accepted and rejected state-changing actions
* remote Windows bootstrap and versioned update via OpenSSH and PowerShell helper scripts

---

## Roadmap

The detailed roadmap is maintained separately:

* [`docs/roadmap.md`](docs/roadmap.md)

Near-term direction:

* `0.4.x` — camera monitoring, frame analysis, and visual safety
* `1.0.x` — central VPS multi-farm management

---

## Real-printer Note

PrinterHub is tested against physical USB-connected Marlin-style 3D printers. The SD-card upload path is real serial recovery work: checksummed upload sessions, pipelined transfer, resend recovery, adaptive batch sizing, live quality metrics, and browser-visible diagnostics are shaped by real printer behavior.

Simulation remains important for automated tests, but the project is grounded in hardware-facing runtime behavior.

---

## Running locally

Build and verify:

```bash
mvn clean verify
```

Start the local runtime with an explicit database file and API port:

```bash
mvn exec:java \
  -Dprinterhub.databaseFile="printerhub.db" \
  -Dprinterhub.api.port=18080 \
  -Dexec.mainClass="printerhub.Main"
```

Then open:

```text
http://localhost:18080/dashboard
```

The dashboard uses relative API requests, so it follows the port used to serve the dashboard. Port `8080` is only the backend default when no `printerhub.api.port` property is provided.

---

## Monitoring and runtime settings

PrinterHub supports runtime-global monitoring rules and serial transfer settings.

Monitoring settings include:

```text
poll interval
snapshot minimum interval
temperature delta threshold
event deduplication window
error persistence behavior
debug wire tracing
```

Serial transfer settings include upload and file-streaming parameters used by the SD-card upload path, including batch-size limits, recovery thresholds, retry limits, and read timing values.

These settings are currently global to the runtime and not yet printer-specific.

The dashboard auto-refresh is intentionally selective. Lightweight live fields can refresh automatically, while heavier actions such as SD-card file listing remain explicit user actions.

---

## Dashboard

PrinterHub includes an embedded dashboard as part of the local runtime.

The dashboard uses global navigation plus a selected-printer workspace.

### Primary navigation

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── Monitoring
├── History
└── Settings
```

### Selected printer navigation

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

The structure separates fleet-level administration from selected-printer operation.

### Global Monitoring workspace

The **Monitoring** page provides cross-printer runtime visibility.

It shows:

* fleet runtime summary
* printer runtime states
* active and recent jobs
* active or last-known SD uploads
* upload health
* adaptive transfer diagnostics
* follow actions for jobs and uploads

From Monitoring, an operator can follow an active upload or job and jump into the relevant selected-printer workspace.

### Selected-printer SD Card workspace

The **SD Card** view owns:

* printer-side SD file listing
* registration of printer-side printable targets
* enable / disable of registered printable targets
* host-side `.gcode` registration and upload
* guarded copy of a host-side `.gcode` file to the selected printer SD card
* upload progress
* upload quality
* transfer performance
* adaptive transfer diagnostics
* manual or synchronized upload-status refresh

### Selected-printer Print workspace

The **Print** view creates `PRINT_FILE` jobs from already registered printer-side file targets.

The dashboard is part of the local runtime architecture and reads through the API layer.

---

## Dashboard screenshots

<table>
  <tr>
    <td align="center">
      <sub>Farm Home</sub><br>
      <img src="docs/assets/media-src/screenshot-printerhub-dashboard-01.png" alt="PrinterHub dashboard farm home" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center">
      <sub>Selected Printer → Print</sub><br>
      <img src="docs/assets/media-src/screenshot-printerhub-dashboard-02.png" alt="PrinterHub print view" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center">
      <sub>SD card management</sub><br>
      <img src="docs/assets/media-src/screenshot-printerhub-dashboard-03.png" alt="SD card management" width="100%">
    </td>
  </tr>
</table>

---

## Jobs and controlled actions

PrinterHub uses the backend term **job** consistently across API, persistence, and dashboard.

At the current stage, jobs are operational control records. They are not yet the full future production workflow of slicing, queueing, printing, supervising, and completing a manufactured part.

What is already available:

* job creation and listing
* printer assignment
* asynchronous controlled job start
* job cancellation and deletion
* job event visibility
* job execution result visibility
* structured execution-step diagnostics
* host-side `.gcode` print-file registration
* dashboard upload of host-side `.gcode` files
* printer-side SD file discovery, registration, and enable/disable management
* guarded host-to-printer SD-card `.gcode` upload
* file-backed `PRINT_FILE` jobs created from registered printer-side SD targets
* autonomous printer-side `PRINT_FILE` activation from registered SD targets
* controlled real-printer action workflows for selected action types

Current controlled action scope:

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

`PRINT_FILE` jobs reference a registered printer-side SD target. PrinterHub can register an existing host path or save a dashboard-uploaded file into the configured print-file storage directory, copy that host-side file to the selected printer SD card through a guarded upload session, and then request a firmware-side print start.

PrinterHub validates and persists file metadata, but it does not slice, edit, or line-stream a full print from the host in this version.

Current limitation:

* autonomous SD-print supervision is still early-stage
* PrinterHub can start a printer-side file-backed print and detect completion in observable cases
* richer pause, cancel, and print-progress controls remain future work

Job start behavior:

```text
POST /jobs/{id}/start
├── validates the job and printer state
├── marks the job RUNNING
├── submits execution to the background job executor
└── returns immediately with execution outcome QUEUED
```

The dashboard and API then use job state, events, and execution steps to observe progress:

```text
GET /jobs/{id}
GET /jobs/{id}/events
GET /jobs/{id}/execution-steps
```

For autonomous SD-backed `PRINT_FILE` jobs, PrinterHub also uses monitoring to help determine when a started printer-side file has completed on the firmware side.

---

## SD-card upload observability

Host-to-printer SD-card upload is one of the main real-printer verification paths.

PrinterHub exposes upload visibility through:

```text
POST /printers/{id}/sd-card/uploads
GET  /printers/{id}/sd-card/uploads/status
```

The dashboard displays:

* upload state
* file name
* confirmed lines / total lines
* confirmed bytes / total bytes
* elapsed time
* estimated remaining time
* bytes per second
* lines per second
* rejected/resend count
* transfer quality
* current transfer mode
* configured and active batch sizes
* stability and recovery pressure
* last adaptation reason

The upload monitor is split into operator-facing progress and deeper adaptive diagnostics, so the user can either simply check that the upload works or inspect controller behavior during long transfers.

---

## Audit and diagnostics

PrinterHub exposes and persists operational information that makes local troubleshooting easier.

Available diagnostic visibility includes:

* printer event history
* job history
* job event history
* monitoring-related runtime events
* upload recovery and adaptation events
* execution command and result details
* workflow-step response, outcome, and failure detail records
* dashboard and API review of operator-triggered actions

This makes local runtime behavior easier to inspect after failures and during hardware tests.

---

## Printer state machine

Each monitored printer node follows the same runtime state model.

```mermaid
flowchart TB
    A["Configured printer"] --> B{"Enabled?"}

    B -- "No" --> C["DISCONNECTED"]

    B -- "Yes" --> D["CONNECTING"]
    D --> E{"Poll outcome"}

    E -- "timeout / disconnect / failure" --> F["ERROR"]
    E -- "busy / printing" --> G["PRINTING"]
    E -- "hotend above threshold" --> H["HEATING"]
    E -- "ok / T:" --> I["IDLE"]
    E -- "unclassifiable response" --> J["UNKNOWN"]

    F --> D
    G --> D
    H --> D
    I --> D
    J --> D
```

Defined states:

```text
DISCONNECTED
CONNECTING
IDLE
HEATING
PRINTING
ERROR
UNKNOWN
```

---

## Industrial context

PrinterHub is not just a single-printer control exercise.

It models the transition from:

```text
single USB-connected printer
```

toward:

```text
structured multi-printer runtime monitoring and administration
```

and later:

```text
centralized multi-site printer management
```

Related background:

* [`docs/industrial-bio-printer-simulation.md`](docs/industrial-bio-printer-simulation.md)

---

## Target architecture direction

The longer-term direction goes beyond a local runtime and moves toward centralized orchestration.

```mermaid
flowchart TB
    ui["Central web UI"]
    api["Backend API"]
    db["Database"]
    runtime["Printer runtime services"]
    device["Device communication layer"]
    printers["Printer devices / printer farms"]

    ui <--> api
    api <--> db
    api <--> runtime
    runtime <--> db
    runtime --> device
    device --> printers
```

---

## DevOps and verification

PrinterHub uses Jenkins-based CI.

The current pipeline verifies:

* Maven build and test execution
* runtime and API smoke lifecycle
* robustness scenarios with mixed healthy and failing printers
* JaCoCo coverage reporting
* release bundle preparation

Details:

* [`docs/devops.md`](docs/devops.md)

Useful local verification commands:

```bash
mvn test
mvn clean verify
mvn -Dtest=RemoteApiServerTest test
mvn -Dtest=SdCardUploadServiceTest test
mvn -Dtest=AsyncPrintJobExecutorTest,PrintJobExecutionServiceTest test
```

---

## Repository structure

```text
printer-hub/
├── README.md
├── Jenkinsfile
├── docs/
│   ├── roadmap.md
│   ├── quickstart.md
│   ├── install.md
│   ├── developer.md
│   ├── devops.md
│   ├── version.md
│   └── ...
├── src/
│   ├── main/
│   │   ├── java/printerhub/
│   │   │   ├── api/
│   │   │   ├── command/
│   │   │   ├── config/
│   │   │   ├── job/
│   │   │   ├── monitoring/
│   │   │   ├── persistence/
│   │   │   ├── runtime/
│   │   │   ├── serial/
│   │   │   └── ...
│   │   └── resources/
│   │       └── dashboard/
│   │           ├── components/
│   │           ├── views/
│   │           └── ...
│   └── test/
│       └── java/printerhub/
│           └── ...
├── ops/
├── tools/
│   └── win/
└── pom.xml
```

---

## Documentation

* Setup and prerequisites: [`docs/install.md`](docs/install.md)
* Local usage: [`docs/quickstart.md`](docs/quickstart.md)
* Developer reference: [`docs/developer.md`](docs/developer.md)
* CI and release workflow: [`docs/devops.md`](docs/devops.md)
* Planned evolution: [`docs/roadmap.md`](docs/roadmap.md)

---

## License

MIT License

* [`LICENSE`](LICENSE)
 
