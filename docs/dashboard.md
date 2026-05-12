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
* selecting and operating one printer
* creating and starting controlled jobs
* reviewing job events and execution diagnostics
* managing printer configuration
* editing monitoring rules

PrinterHub currently targets printers that speak a Marlin-compatible G-code serial protocol. The real-printer development reference is a Creality Ender-3 V2 Neo, but the UI is not intended to be tied to that one printer model.

---

## Navigation Model

The dashboard uses a two-level navigation model.

Primary navigation:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
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

* the primary navigation handles farm-level administration
* the selected-printer navigation follows the practical operating logic of a printer
* printer-specific operations stay close to the selected printer context

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
* start/cancel/delete actions
* expandable job history
* expandable execution diagnostics

Supported job actions:

```text
POST   /jobs
GET    /jobs
POST   /jobs/{id}/start
POST   /jobs/{id}/cancel
DELETE /jobs/{id}
GET    /jobs/{id}/events
GET    /jobs/{id}/execution-steps
```

Job start is asynchronous:

```text
POST /jobs/{id}/start
├── validates the job and printer state
├── marks the job RUNNING
├── queues the execution in the background job executor
└── returns immediately with outcome QUEUED
```

The dashboard then observes progress through job state, job events, and execution steps.

---

### History

History is the global review area for persisted runtime activity.

Current dashboard history support is focused on:

* printer events
* job events
* job execution diagnostics

The execution diagnostics show the actual workflow steps persisted by the backend, including:

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
* printer create/update/delete
* printer enable/disable

Monitoring rules API:

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Printer administration API:

```text
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
```

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

It shows jobs assigned to the selected printer and provides the same job actions as the global Jobs page:

* start
* cancel
* delete
* load job history
* load execution diagnostics
* show read-only `.gcode` file content for file-backed jobs

The Print page also supports registering an already prepared host-side `.gcode` file, uploading a `.gcode` file into the configured PrinterHub print-file storage directory, and creating a `PRINT_FILE` job that references that file. In this version, the job is represented and prepared as metadata; the dashboard does not stream G-code to the printer.

---

### SD Card

Selected Printer / SD Card is the read-only printer-side file inspection area.

It currently supports:

* manual refresh of the printer SD-card file list
* file names reported by firmware
* size when available from firmware
* raw firmware response review

Data source:

```text
GET /printers/{id}/sd-card/files
```

Delete, upload-to-printer, and print-start actions are intentionally kept out of this first SD-card page until the real printer command behavior is verified.

---

### Prepare

Selected Printer / Prepare is the place for preparation-oriented printer actions.

Current and intended examples:

* home axes
* set nozzle temperature
* set bed temperature
* preparation workflows before printing

The available controlled job types currently include:

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

Dashboard refresh is split into three categories.

### Manual Refresh Now

The top-right **Refresh now** button refreshes all dashboard data and may rerender the current page.

This is explicit user action, so it is acceptable for panels to redraw.

### Automatic Background Refresh

Automatic background refresh is intentionally narrow.

It updates live printer fields only:

* printer state
* temperatures
* last response
* error message
* updated timestamp

It must not rerender whole pages such as Jobs, Print, History, or Settings.

It must not reset:

* expanded job details
* forms
* selected tabs
* scroll position
* loaded diagnostics

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
    ├── printer-control.js
    ├── printer-history.js
    ├── printer-home.js
    ├── printer-info.js
    ├── printer-prepare.js
    ├── printer-print.js
    └── settings.js
```

Important notes:

* `dashboard.js` is the dashboard entrypoint
* `api.js` contains relative API calls
* `state.js` stores selected view, selected printer, cached data, loaded events, loaded diagnostics, and expanded job-card sections
* view modules render page-level content
* component modules render reusable cards, navigation, events, diagnostics, and status panels

---

## Backend/API Mapping

| Dashboard area | API |
| --- | --- |
| Farm Home | `GET /printers` |
| Printers | `GET /printers` |
| Selected Printer / Home | `GET /printers` |
| Selected Printer / Print | `GET /jobs`, job action endpoints |
| Selected Printer / SD Card | `GET /printers/{id}/sd-card/files` |
| Selected Printer / Prepare | `POST /jobs`, `POST /jobs/{id}/start` |
| Selected Printer / Control | `POST /printers/{id}/commands` |
| Selected Printer / Info | `GET /printers`, command/job flows for firmware info |
| Selected Printer / History | `GET /printers/{id}/events`, `GET /jobs/{id}/events`, `GET /jobs/{id}/execution-steps` |
| Jobs | `GET /jobs`, job action endpoints |
| History | event and execution-step endpoints |
| Settings / Monitoring | `GET /settings/monitoring`, `PUT /settings/monitoring` |
| Settings / Printer admin | printer CRUD and enable/disable endpoints |

---

## Current Limitations

The dashboard does not yet provide:

* real print-file transfer or streaming
* slicer integration
* model slicing
* G-code editing
* printer-specific monitoring rules
* a dedicated firmware profile endpoint

These belong to later roadmap work, especially the controlled print-start workflow that follows file-backed job representation.
