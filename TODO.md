


## 0.2.5 — Global monitoring workspace and cross-printer runtime observability

status: planned

Purpose:

Add a global Monitoring area to the dashboard for observing runtime activity across all configured printers, without first selecting one printer.

This version builds on the 0.2.4 upload telemetry work, but must not regress the existing selected-printer SD upload card. The selected-printer SD Card page remains the focused upload workflow. The new Monitoring page is an aggregated operational view for the whole local farm.


One correction: for 0.2.5, avoid making it “upload only”. It should be a global Monitoring workspace, where upload monitoring is one section. That prevents the dashboard from becoming fragmented later



---

## Goals

* add a global `Monitoring` menu entry in the dashboard
* show current runtime activity across all printers
* show currently running or recently active jobs across all printers
* expose upload/runtime telemetry in a global view
* make it possible to refresh global monitoring state manually
* allow the operator to synchronize/follow one active printer/job from the global view
* keep the existing selected-printer upload status card working unchanged
* prepare the architecture for later historical monitoring and trend views

---

## Scope

### In scope

* new global dashboard menu item: `Monitoring`
* new dashboard view for cross-printer runtime monitoring
* backend endpoint returning current monitoring/activity state
* global list of active or recently active jobs
* global list of active SD uploads, if any
* reuse of upload telemetry already exposed by `/printers/{id}/sd-card/uploads/status`
* manual refresh button for global monitoring state
* “Synchronize” action to focus/follow a specific printer or active job
* clear separation between:
  * selected-printer SD upload workflow
  * global farm monitoring overview

### Out of scope for first 0.2.5 version

* full historical upload tracing after process restart
* permanent upload telemetry history
* charts over long time ranges
* advanced alerting
* WebSocket/live push
* central VPS monitoring
* replacing the selected-printer SD Card upload card

---

## Design principle

The selected-printer upload card answers:

> What is happening for this printer upload right now?

The global Monitoring page answers:

> What is happening across the whole local farm right now?

The new page must aggregate data. It must not duplicate workflow buttons too aggressively.

---

## Dashboard structure

Add a new global menu item:

```text
Farm Home
Printers
Jobs
Monitoring
History
Settings
````

`Monitoring` is a global page, not a selected-printer page.

---

## Global Monitoring page layout

### Card 1 — Fleet runtime summary

Purpose:

Show the operator whether the local farm is idle, busy, degraded, or failing.

Suggested fields:

* total configured printers
* enabled printers
* disabled printers
* printers currently busy
* printers in error/disconnected state
* active jobs
* active SD uploads
* last refresh time

Expected behavior:

* refreshes with the global dashboard refresh
* also has a page-level Refresh button
* does not trigger direct printer polling
* reads runtime/cache/backend aggregation only

---

### Card 2 — Active jobs across all printers

Purpose:

Show all jobs currently relevant across the farm.

Suggested fields:

* job id
* job name
* job type
* printer id / printer name
* state
* started at
* updated at
* failure reason/detail if any
* quick action: open job details
* quick action: synchronize/follow

First version should probably show:

```text
QUEUED
RUNNING
PAUSED
```

Optionally also show the most recent:

```text
COMPLETED
FAILED
CANCELLED
```

with a small limit, for example last 10 or last 20.

---

### Card 3 — Active SD upload telemetry

Purpose:

Show active upload telemetry across all printers without selecting each printer manually.

Suggested fields:

* printer id / printer name
* target filename
* state
* active
* uploaded lines / total lines
* percent
* bytes/sec
* lines/sec
* ETA
* rejected/resend count
* quality percent
* active batch size
* configured max batch size
* transport mode
* single-send mode
* last adaptation reason

Important:

This card should only show **current/last known upload progress**. If uploads are not persisted after completion, then old upload details may disappear or only show the last in-memory state until restart. That is acceptable for 0.2.5 if documented clearly.

---

### Card 4 — Adaptive transfer diagnostics

Purpose:

Show controller behavior across printers in a compact way.

This is the global equivalent of the selected-printer “Adaptive tuning” diagnostic card.

Suggested fields per active upload:

* printer id
* active batch size
* configured min batch size
* configured max batch size
* accepted lines since last resend
* recent resend count
* resend threshold for downgrade
* recovery threshold for min batch
* stable lines for upgrade
* transport mode
* last adaptation timestamp
* last adaptation reason

Display rule:

* keep this card secondary
* make it collapsible or visually lower priority
* do not bury the basic upload progress behind diagnostics

---

## Synchronize / follow behavior

The Monitoring page should support a `Synchronize` action.

Initial meaning:

When the operator clicks `Synchronize` on an active job or upload:

* select the related printer in dashboard state
* optionally switch to the selected-printer SD Card page or Print page
* load the latest upload status/job details
* keep polling that printer/job while the operator watches

Recommended first behavior:

```text
Synchronize upload -> Selected Printer → SD Card
Synchronize job    -> Selected Printer → Print or Jobs detail
```

This keeps the global page simple and lets the existing detailed pages remain the focused workspace.

---

## Backend requirements

A new backend endpoint is likely needed.

### Recommended endpoint

```text
GET /monitoring
```

Purpose:

Return a single aggregated snapshot for the global Monitoring page.

Suggested JSON shape:

```json
{
  "generatedAt": "2026-05-16T10:00:00Z",
  "summary": {
    "totalPrinters": 3,
    "enabledPrinters": 2,
    "disabledPrinters": 1,
    "busyPrinters": 1,
    "errorPrinters": 0,
    "activeJobs": 1,
    "activeUploads": 1
  },
  "printers": [],
  "activeJobs": [],
  "activeUploads": []
}
```

The endpoint should aggregate from existing runtime services:

* `PrinterRegistry`
* `PrinterRuntimeStateCache`
* `PrintJobService`
* `SdCardUploadService`
* maybe `PrinterEventStore` later

Important invariant:

The endpoint must not directly poll printers. It must read cached/runtime state only.

---

## Backend implementation notes

### New or changed files

```text
src/main/java/printerhub/api/RemoteApiServer.java
src/main/java/printerhub/monitoring/GlobalMonitoringSnapshot.java       optional
src/main/java/printerhub/monitoring/GlobalMonitoringService.java        recommended
src/main/java/printerhub/command/SdCardUploadService.java               maybe minor
src/test/java/printerhub/api/RemoteApiServerTest.java
```

### Recommended backend split

Avoid putting all aggregation logic directly into `RemoteApiServer`.

Better shape:

```text
GlobalMonitoringService
  reads PrinterRegistry
  reads PrinterRuntimeStateCache
  reads PrintJobService
  reads SdCardUploadService
  returns GlobalMonitoringSnapshot
```

Then `RemoteApiServer` only serializes the result.

This keeps `RemoteApiServer` from becoming even bigger.

---

## Frontend requirements

### New or changed files

```text
src/main/resources/dashboard/state.js
src/main/resources/dashboard/api.js
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/components/nav.js
src/main/resources/dashboard/views/monitoring.js
src/main/resources/dashboard/dashboard.css
```

### Frontend API

Add:

```javascript
export async function getMonitoringOverview() {
  return requestJson("/monitoring");
}
```

### Frontend state

Add:

```javascript
monitoringOverview: null
```

and setter:

```javascript
export function setMonitoringOverview(overview) {
  state.monitoringOverview = overview;
}
```

### Navigation

Add global menu entry:

```text
Monitoring
```

### Dashboard router

Add new primary view:

```javascript
MONITORING: "monitoring"
```

and route it to:

```javascript
renderMonitoringPage()
```

---

## Non-regression rule

0.2.5 must not break:

* selected-printer SD Card page
* upload to SD button
* upload progress card
* upload status polling
* adaptive tuning values shown for the selected printer
* `/printers/{id}/sd-card/uploads/status`
* `/settings/serial-transfer`

The new global Monitoring page should **reuse** existing status data where possible, not replace it.

---

## Persistence decision

For 0.2.5, upload telemetry can remain runtime-only.

That means:

* active upload state is visible while upload is running
* last upload state may be visible while the process keeps it in memory
* after restart, upload telemetry can disappear
* historical analysis is not guaranteed yet

Do not auto-save adaptive runtime values into `SerialTransferSettings`.

Reason:

* settings represent operator configuration
* adaptive values represent session behavior
* mixing them would make debugging harder

A later version can add:

```text
upload telemetry history
last session report
promote tuned value to settings
```

but this should not be part of the first 0.2.5 implementation.

---

## Suggested implementation order

### Step A — backend aggregation

Files:

```text
GlobalMonitoringService.java
RemoteApiServer.java
RemoteApiServerTest.java
```

Tasks:

* create global monitoring snapshot model/service
* add `GET /monitoring`
* include summary, active jobs, active uploads
* verify with curl

Example verification:

```bash
curl -s http://localhost:8080/monitoring | jq
```

---

### Step B — frontend navigation and page shell

Files:

```text
state.js
api.js
dashboard.js
components/nav.js
views/monitoring.js
```

Tasks:

* add `MONITORING` primary view id
* add nav entry
* add API call
* add state holder
* render basic Monitoring page shell
* add Refresh button behavior

---

### Step C — display active jobs and uploads

Files:

```text
views/monitoring.js
dashboard.css
```

Tasks:

* render fleet summary card
* render active jobs table/card
* render active uploads table/card
* show raw adaptive fields first
* keep layout readable but not final-polished

---

### Step D — synchronize/follow action

Files:

```text
dashboard.js
views/monitoring.js
state.js
```

Tasks:

* add `data-monitoring-sync-printer`
* on click:

  * set selected printer
  * switch to selected-printer SD Card or Print page
  * refresh relevant status/details
* make the operator land on the detailed page with current data visible

---

## Expected result

After 0.2.5:

* the operator has a global Monitoring menu
* active jobs across all printers are visible in one place
* active SD uploads across all printers are visible in one place
* adaptive upload behavior can be observed globally
* one click can synchronize/follow a specific printer/job
* the selected-printer upload card remains the detailed workflow view
* the system is prepared for later historical monitoring and trend analysis

````

I would name this version exactly:

```text
0.2.5 — Global monitoring workspace and cross-printer runtime observability
````

It is clearer than calling it only “monitoring menu”, because the real architectural addition is the **global runtime aggregation endpoint plus dashboard workspace**.
