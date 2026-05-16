# PrinterHub Dashboard

The PrinterHub dashboard is the embedded browser UI served by the local runtime.

Open it from the same API port used to start PrinterHub:

```text
http://localhost:<printerhub.api.port>/dashboard
```

Example:

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

The dashboard uses relative API requests. It does not hardcode port `8080`; `8080` is only the backend default when no `printerhub.api.port` system property is provided.

---

## Purpose

The dashboard is the local operator console for:

* viewing the printer farm
* monitoring runtime state across all printers
* selecting and operating one printer
* creating and starting controlled jobs
* managing printer-side SD-card targets
* registering and uploading host-side `.gcode` files
* observing SD-card upload progress and adaptive transfer behavior
* reviewing job events and execution diagnostics
* managing printer configuration
* editing monitoring and serial transfer settings

PrinterHub currently targets printers that speak a Marlin-compatible G-code serial protocol. The real-printer development reference is a Creality Ender-series Marlin workflow, but the UI is not intended to be tied to one printer model.

---

## Navigation Model

The dashboard uses a two-level navigation model.

Primary navigation:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── Monitoring
├── History
└── Settings
```

Selected-printer navigation:

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

This split is intentional:

* the primary navigation handles farm-level administration and cross-printer runtime monitoring
* the selected-printer navigation follows the practical operating logic of one printer
* printer-specific operations stay close to the selected printer context
* global observability stays separate from selected-printer operation

---

## Pages

### Farm Home

Farm Home gives the runtime overview.

It shows:

* fleet summary
* printer cards
* current printer state
* hotend and bed temperatures
* last response
* current error
* recent update time

Data source:

```text
GET /printers
```

---

### Printers

Printers is the farm-level printer list and selection area.

It shows configured printers and lets the operator choose the selected printer used by the selected-printer navigation.

Data source:

```text
GET /printers
```

Printer administration itself is currently in Settings.

---

### Jobs

Jobs is the global job view.

It shows:

* all recent jobs
* job state
* assigned printer
* start, pause, resume, cancel, restart, and delete actions where supported
* expandable job history
* expandable execution diagnostics

Supported job endpoints include:

```text
POST   /jobs
GET    /jobs
GET    /jobs/{id}
POST   /jobs/{id}/start
POST   /jobs/{id}/pause
POST   /jobs/{id}/resume
POST   /jobs/{id}/cancel
POST   /jobs/{id}/restart
DELETE /jobs/{id}
GET    /jobs/{id}/events
GET    /jobs/{id}/execution-steps
```

Job start is asynchronous:

```text
POST /jobs/{id}/start
├── validates the job and printer state
├── marks or queues the job for execution
├── submits execution in the background job executor
└── returns quickly with the accepted/queued execution result
```

The dashboard then observes progress through job state, job events, and execution steps.

---

### Monitoring

Monitoring is the global cross-printer runtime observability page.

It shows:

* fleet runtime summary
* printer runtime states
* active and recent jobs
* active or last-known SD uploads
* upload health
* adaptive transfer diagnostics
* follow actions for jobs and uploads

Data source:

```text
GET /monitoring
```

The Monitoring page is not a replacement for the selected-printer SD Card page. It is a fleet-level observation workspace.

Typical use:

* open Monitoring to see what is active across the whole local runtime
* identify an active upload or job
* click a follow action
* jump into the selected printer page that owns the detailed workflow

Follow actions:

```text
upload follow -> selected printer / SD Card + upload synchronization
job follow    -> selected printer / Print
```

---

### History

History is the global review area for persisted runtime activity.

Current dashboard history support is focused on:

* printer events
* job events
* job execution diagnostics
* runtime events related to monitoring, upload, recovery, and controlled actions

Execution diagnostics show the actual workflow steps persisted by the backend, including:

* step index
* step name
* wire command
* printer response
* outcome
* success/failure
* failure reason
* failure detail

---

### Settings

Settings contains runtime configuration and printer administration.

Available today:

* monitoring rules
* serial transfer settings
* print-file storage settings
* printer create/update/delete
* printer enable/disable

Monitoring rules API:

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Serial transfer settings API:

```text
GET /settings/serial-transfer
PUT /settings/serial-transfer
```

Print-file settings API:

```text
GET /settings/print-files
PUT /settings/print-files
```

Printer administration API:

```text
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
```

Serial transfer settings are persistent operator settings. They define limits and thresholds used by the SD-card upload runtime, but they are not the same thing as the live adaptive state of one running upload.

---

## Selected Printer Pages

### Home

Selected Printer / Home is the live overview for one printer.

It shows:

* identity
* mode
* enabled/disabled state
* current runtime state
* temperatures
* last response
* current error
* latest update time

Data source:

```text
GET /printers
```

---

### Print

Selected Printer / Print is the printer-specific job area.

It shows jobs assigned to the selected printer and provides job actions such as:

* start
* pause
* resume
* cancel
* restart
* delete
* load job history
* load execution diagnostics
* show read-only `.gcode` file content for file-backed jobs

The Print page uses registered printer-side SD targets for `PRINT_FILE` jobs. A print job does not directly select an arbitrary host file; it selects a known printer-side target that can be verified, registered, enabled, and reused.

Relevant data sources:

```text
GET /jobs
GET /printer-sd-files?printerId={id}
GET /jobs/{id}/events
GET /jobs/{id}/execution-steps
```

---

### SD Card

Selected Printer / SD Card is the printer-side file and upload workspace.

It supports:

* manual refresh of the printer SD-card file list
* file names reported by firmware
* size when available from firmware
* raw firmware response review
* registration of firmware-reported files as PrinterHub printable targets
* manual registration of known printer-side paths
* enabling and disabling registered SD targets
* deleting registered SD targets
* registering host-side `.gcode` files
* uploading dashboard-selected `.gcode` files to PrinterHub storage
* guarded host-to-printer SD-card upload
* live upload progress
* transfer quality display
* upload performance metrics
* adaptive transfer diagnostics
* manual or synchronized upload-status refresh

Primary data sources:

```text
GET  /printers/{id}/sd-card/files
GET  /printer-sd-files?printerId={id}
GET  /print-files
POST /print-files
POST /print-files/uploads
POST /printer-sd-files
POST /printers/{id}/sd-card/uploads
GET  /printers/{id}/sd-card/uploads/status
POST /printers/{id}/sd-card/recovery/close-upload
```

The SD Card page separates two concepts:

```text
printer-side SD files
```

and:

```text
PrinterHub registered printable targets
```

A firmware-reported SD file becomes usable for `PRINT_FILE` jobs after it is registered as a PrinterHub SD target.

---

### SD-card Upload Monitor

The SD Card page includes an upload monitor.

It is split into operator-facing status and adaptive diagnostics.

Upload status focuses on:

* state
* file name
* progress
* confirmed lines / total lines
* confirmed bytes / total bytes
* elapsed time
* estimated remaining time
* bytes per second
* lines per second
* rejected/resend count
* transfer quality
* detail message

Adaptive diagnostics focus on:

* configured max batch size
* configured min batch size
* active batch size
* batch upgrade step
* batch downgrade step
* stable lines for upgrade
* accepted lines since last resend
* recent resend count
* resend threshold for downgrade
* recovery threshold for minimum batch
* current transport mode
* single-send/fallback state
* last adaptation reason
* last adaptation timestamp

The operator can use the first card to check whether the upload works. The second card is for deeper analysis during long or unstable transfers.

---

### Upload Synchronization

The SD Card page supports manual refresh and explicit upload synchronization.

Manual refresh checks the upload status once.

Synchronization starts periodic upload-status polling for the selected printer, useful when the dashboard is opened from another PC or browser while an upload is already running.

The synchronization control polls:

```text
GET /printers/{id}/sd-card/uploads/status
```

It does not repeatedly refresh the full SD-card file list. This avoids expensive or unsafe firmware file-list requests during upload.

Typical remote observation flow:

```text
PC remote:
  PrinterHub runs locally and is physically connected to the printer.

PC user or PC chef:
  opens http://<pc-remote-ip>:<port>/dashboard
  selects the printer
  opens SD Card
  clicks Synchronize
  watches the live upload-status feed
```

---

### Prepare

Selected Printer / Prepare is the place for preparation-oriented printer actions.

Current and intended examples:

* home axes
* set nozzle temperature
* set bed temperature
* fan control
* preparation workflows before printing

The available controlled job/action types currently include:

```text
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

---

### Control

Selected Printer / Control is the place for direct machine control.

The dashboard can send allowed manual commands through:

```text
POST /printers/{id}/commands
```

This area is intended for controlled low-level operations, not for bypassing the safer job workflows.

---

### Info

Selected Printer / Info is the read-only technical summary.

It is intended for:

* printer identity
* port
* mode
* current state
* last response
* current error
* firmware-oriented information as the backend grows

Firmware information is currently available through controlled job or command flows rather than as a dedicated profile endpoint.

---

### History

Selected Printer / History is the printer-specific review area.

It can show:

* printer events
* jobs for the selected printer
* job events loaded on demand
* job execution diagnostics loaded on demand

Data sources:

```text
GET /printers/{id}/events
GET /jobs
GET /jobs/{id}/events
GET /jobs/{id}/execution-steps
```

---

## Refresh Behavior

Dashboard refresh is split into several categories.

### Manual Refresh Now

The top-right **Refresh now** button refreshes general dashboard data and may rerender the current page.

This is explicit user action, so it is acceptable for panels to redraw.

### Automatic Background Refresh

Automatic background refresh is intentionally narrow.

It updates live printer fields only:

* printer state
* temperatures
* last response
* error message
* updated timestamp

It must not rerender whole pages such as Jobs, Print, History, Settings, or SD Card during normal idle observation.

It must not reset:

* expanded job details
* forms
* selected tabs
* scroll position
* loaded diagnostics
* selected printer context
* upload synchronization state

### Upload Status Polling

Upload status polling is separate from full dashboard refresh.

It reads only:

```text
GET /printers/{id}/sd-card/uploads/status
```

This supports live upload observation without triggering heavier printer-side actions such as SD-card file listing.

### Monitoring Refresh

The Monitoring page reads aggregated runtime state through:

```text
GET /monitoring
```

This endpoint is intended for cross-printer visibility. It aggregates runtime state, job state, and upload state without making the dashboard manually stitch together all detailed endpoints.

### Action-Based Refresh

Actions refresh the data needed for that action.

Examples:

```text
create printer -> refresh printers and rerender current page
create job -> refresh jobs and rerender current page
start job -> refresh jobs/events/diagnostics as needed
load history -> fetch job events only
load diagnostics -> fetch execution steps only
delete job -> refresh jobs and current view
upload file to SD -> start upload status polling and refresh relevant SD-card data afterward
synchronize upload -> poll upload status only
```

Job history and diagnostics panels remember their expanded/collapsed state while the page is rerendered.

---

## Frontend Files

Current dashboard files:

```text
src/main/resources/dashboard/
├── index.html
├── dashboard.css
├── dashboard.js
├── api.js
├── state.js
├── components/
│   ├── event-list.js
│   ├── job-card.js
│   ├── nav.js
│   ├── placeholder-card.js
│   ├── printer-card.js
│   └── status-panels.js
└── views/
    ├── farm-home.js
    ├── jobs.js
    ├── monitoring.js
    ├── printer-control.js
    ├── printer-history.js
    ├── printer-home.js
    ├── printer-info.js
    ├── printer-prepare.js
    ├── printer-print.js
    ├── printer-sd-card.js
    └── settings.js
```

Important notes:

* `dashboard.js` is the dashboard entrypoint
* `api.js` contains relative API calls
* `state.js` stores selected view, selected printer, cached data, loaded events, loaded diagnostics, upload status, synchronization state, and expanded job-card sections
* view modules render page-level content
* component modules render reusable cards, navigation, events, diagnostics, and status panels
* `printer-sd-card.js` owns selected-printer SD-card upload display
* `monitoring.js` owns global cross-printer runtime observability

---

## Backend/API Mapping

| Dashboard area             | API                                                                                    |
| -------------------------- | -------------------------------------------------------------------------------------- |
| Farm Home                  | `GET /printers`                                                                        |
| Printers                   | `GET /printers`                                                                        |
| Monitoring                 | `GET /monitoring`                                                                      |
| Selected Printer / Home    | `GET /printers`                                                                        |
| Selected Printer / Print   | `GET /jobs`, job action endpoints, `GET /printer-sd-files?printerId={id}`              |
| Selected Printer / SD Card | SD-card, print-file, upload, and upload-status endpoints                               |
| Selected Printer / Prepare | `POST /jobs`, `POST /jobs/{id}/start`                                                  |
| Selected Printer / Control | `POST /printers/{id}/commands`                                                         |
| Selected Printer / Info    | `GET /printers`, command/job flows for firmware info                                   |
| Selected Printer / History | `GET /printers/{id}/events`, `GET /jobs/{id}/events`, `GET /jobs/{id}/execution-steps` |
| Jobs                       | `GET /jobs`, job action endpoints                                                      |
| History                    | event and execution-step endpoints                                                     |
| Settings / Monitoring      | `GET /settings/monitoring`, `PUT /settings/monitoring`                                 |
| Settings / Serial transfer | `GET /settings/serial-transfer`, `PUT /settings/serial-transfer`                       |
| Settings / Print files     | `GET /settings/print-files`, `PUT /settings/print-files`                               |
| Settings / Printer admin   | printer CRUD and enable/disable endpoints                                              |

---

## Key API Endpoints

Printer runtime:

```text
GET /printers
GET /printers/{id}
GET /printers/{id}/status
GET /printers/{id}/events
```

Global monitoring:

```text
GET /monitoring
```

Jobs:

```text
POST   /jobs
GET    /jobs
GET    /jobs/{id}
POST   /jobs/{id}/start
POST   /jobs/{id}/pause
POST   /jobs/{id}/resume
POST   /jobs/{id}/cancel
POST   /jobs/{id}/restart
DELETE /jobs/{id}
GET    /jobs/{id}/events
GET    /jobs/{id}/execution-steps
```

SD card and files:

```text
GET    /printers/{id}/sd-card/files
POST   /printers/{id}/sd-card/uploads
GET    /printers/{id}/sd-card/uploads/status
POST   /printers/{id}/sd-card/recovery/close-upload

GET    /print-files
POST   /print-files
POST   /print-files/uploads
GET    /print-files/{id}
GET    /print-files/{id}/content

GET    /printer-sd-files
GET    /printer-sd-files?printerId={id}
POST   /printer-sd-files
GET    /printer-sd-files/{id}
POST   /printer-sd-files/{id}/enable
POST   /printer-sd-files/{id}/disable
DELETE /printer-sd-files/{id}
```

Settings:

```text
GET /settings/monitoring
PUT /settings/monitoring

GET /settings/serial-transfer
PUT /settings/serial-transfer

GET /settings/print-files
PUT /settings/print-files
```

---

## Current Limitations

The dashboard does not yet provide:

* slicer integration
* model slicing
* G-code editing
* printer-specific monitoring rules
* printer-specific serial transfer settings
* a dedicated firmware profile endpoint
* complete rich supervision of every firmware-side print phase
* production-grade multi-site orchestration

Current SD-print supervision is still early-stage. PrinterHub can start a registered printer-side file-backed print and observe completion in supported cases, but richer print progress, pause, cancel, and recovery behavior remains future work.
 