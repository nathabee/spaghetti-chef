# TO DO

----


detail version 0.2.4 STEP E,F,G


 **three distinct deliverables**:

1. **adaptive upload control logic**
2. **operator telemetry / better live dashboard display**
3. **editable transfer settings in dashboard settings**
 
----

## roadmap
 
### 0.2.4 — Step E — transfer settings administration foundation

status: planned

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

### 0.2.4 — Step F — adaptive throughput control and autonomous serial tuning

status: planned

Goals:

* build on Step D buffered resend recovery instead of replacing it
* introduce a real runtime transfer controller instead of a fixed batch size plus permanent degraded latch
* separate:

  * configured maximum batch size
  * current runtime batch size
  * recovery state
  * stability evidence since last resend
  * recent resend density
* automatically reduce transfer aggressiveness after resend instability
* gradually re-enable batching after a stable stretch of accepted lines
* search for a stable high-throughput operating point instead of staying permanently in single-send mode after the first resend
* persist adaptation decisions and stability evidence in printer events
* keep real printer validation as the primary target

Planned behavior:

* start each upload at operator-configured transfer settings
* if resend appears:

  * drain pending input
  * use buffered replay recovery
  * lower the active runtime batch size
  * reset stability counters
* if upload remains stable long enough:

  * increase active runtime batch size step by step
* never exceed configured ceilings
* if instability returns:

  * reduce again
* abort only when recovery leaves retained-history or protocol thresholds are exceeded

Planned runtime state:

* configured maximum batch size
* configured minimum batch size
* current active batch size
* accepted lines since last resend
* recent resend count
* recent recovery count
* recent mode transitions
* throughput indicators for current session

Deliverables:

* adaptive batch controller inside SD upload runtime state
* configurable thresholds for increase / decrease / stability windows
* event logging for mode transitions and adaptation evidence
* API exposure for upload runtime metrics

Expected result:

* PrinterHub behaves safely under resend instability
* upload can recover upward instead of remaining permanently degraded
* runtime approximates a practical stable operating point per printer/firmware/host path

---

### 0.2.4 — Step G — upload telemetry and operator dashboard visualization

status: planned

Goals:

* make upload progress visible as a modern operator card instead of only raw text/log style state
* expose real-time session telemetry for active SD uploads
* show transfer progress, timing, recovery pressure, and active tuned parameters clearly
* make adaptive behavior understandable to the operator

Dashboard targets:

* current upload phase
* elapsed time
* estimated remaining time
* completed lines / total lines
* completed bytes / total bytes if available
* current active batch size
* configured maximum batch size
* current transfer mode
* resend count / resend threshold
* recovery count / recovery threshold
* stability streak
* transfer health indicator
* optional tiny trend bars / meter / progress segments

Expected result:

* operator can see not only that an upload is running, but how well it is running
* adaptive runtime decisions become auditable and understandable
* project demonstrates both protocol resilience and operator-grade monitoring UX

---
  

# Good algorithm tips to add into the roadmap

These are worth adding explicitly, because they will shape the design and avoid future rewrite.

## 1. Separate operator policy from runtime state

Do not store only one “batch size”.

You need two layers:

### configured policy

what the operator wants to allow

Example:

* max batch size
* min batch size
* downgrade step
* upgrade step
* stability window
* resend density threshold
* recovery abort threshold

### runtime session state

what the current upload is actually doing right now

Example:

* current active batch size
* current mode
* accepted lines since last resend
* rolling resend count
* rolling recovery count
* start time
* last mode change time

This separation is fundamental.

---

## 2. Use hysteresis, not immediate up/down oscillation

If you increase too fast and decrease too fast, the controller will flap.

Add roadmap wording like:

* use separate downgrade and upgrade criteria to avoid oscillation
* require stronger evidence to increase aggressiveness than to decrease it

Example:

* downgrade after 1 resend in sensitive mode
* upgrade only after 200 or 500 stable accepted lines

That asymmetry is good.

---

## 3. Prefer stepwise control, not “jump to optimal”

Do not try to calculate the perfect batch size mathematically at first.

Use controlled steps:

* start at configured max
* downgrade by 1 or by a percentage
* upgrade by 1 after stable window

That is much safer and simpler to test.

---

## 4. Track rolling quality windows

Not only total resend count.

Totals are useful for audit, but runtime decisions should use a recent window too:

* resends in last N accepted lines
* recoveries in last M seconds
* consecutive stable lines since last resend

Without a rolling window, late-session totals can poison decisions unfairly.

---

## 5. Distinguish mode from cause

Your UI and events should not only say “degraded”.

Better explicit states:

* `PIPELINED`
* `STABILIZING`
* `RECOVERING`
* `SINGLE_SEND`
* `ABORTING`

And separately record **why**:

* resend requested
* retained history exhausted
* timeout after replay
* repeated protocol desync
* operator ceiling reached

That makes the dashboard and event history much better.

---

## 6. ETA should be smoothed

Do not calculate remaining time from instantaneous last-line speed. It will jump around and look bad.

Use:

* moving average throughput over recent interval
* or average over whole session blended with recent window

So roadmap wording can say:

* estimated remaining time should use smoothed throughput, not raw per-line latency

---

## 7. Keep raw diagnostic data available behind the nice card

Modern UI is good, but do not throw away raw detail.

Keep:

* compact operator card by default
* expandable technical detail panel with raw counters and last recovery events

That matches your dashboard style already.

---

# Likely impacted files, grouped by theme

You asked specifically to regroup by theme so you do not scatter changes everywhere. Good approach.

---

## Theme 1 — transfer settings foundation

These are the most likely files for **Step E**.

### Config defaults

* `src/main/java/printerhub/config/SerialDefaults.java`
* possibly `src/main/java/printerhub/config/RuntimeDefaults.java`

Role:

* keep built-in initial values
* stop using them as the only source of truth after DB initialization

### Persistence

* likely new store and model, similar to monitoring settings
* possible candidates:

  * new `src/main/java/printerhub/persistence/SerialTransferSettings.java`
  * new `src/main/java/printerhub/persistence/SerialTransferSettingsStore.java`
  * `src/main/java/printerhub/persistence/DatabaseInitializer.java`
  * maybe `src/main/java/printerhub/persistence/Database.java`

Role:

* table creation
* default initialization
* read/update persisted settings

### API

* `src/main/java/printerhub/api/RemoteApiServer.java`

Role:

* GET/PUT endpoints for transfer settings
* JSON serialization / parsing

### Runtime/service consumers

* `src/main/java/printerhub/command/SdCardUploadService.java`
* maybe `src/main/java/printerhub/runtime/PrinterHubRuntime.java`
* maybe `src/main/java/printerhub/job/AutonomousPrintControlService.java`

Role:

* load persisted settings instead of compile-time constants

### Dashboard settings

* `src/main/resources/dashboard/views/settings.js`
* `src/main/resources/dashboard/api.js`
* `src/main/resources/dashboard/state.js`
* maybe `src/main/resources/dashboard/dashboard.js`
* `src/main/resources/dashboard/dashboard.css`

Role:

* settings form
* fetch/update transfer settings
* visual integration with existing settings page

### Tests

* `src/test/java/printerhub/api/RemoteApiServerTest.java`
* new store tests in `src/test/java/printerhub/persistence/`
* `src/test/java/printerhub/command/SdCardUploadServiceTest.java`

---

## Theme 2 — adaptive controller runtime

These are the most likely files for **Step F**.

### Core upload logic

* `src/main/java/printerhub/command/SdCardUploadService.java`

This is the main impacted file.

You may also want to split logic instead of bloating it:

* possible new helper class:

  * `src/main/java/printerhub/command/AdaptiveTransferController.java`
* possible new session state type:

  * `src/main/java/printerhub/command/SdUploadSessionMetrics.java`
  * or `SdUploadRuntimeState.java`

That would be cleaner than burying everything inside one large service.

### Event logging

* `src/main/java/printerhub/persistence/PrinterEventStore.java`
* maybe `src/main/java/printerhub/OperationMessages.java`

Role:

* standardized event messages for increase/decrease/recovery/stabilizing/throughput summary

### Job integration

* `src/main/java/printerhub/job/AutonomousPrintControlService.java`
* maybe `src/main/java/printerhub/job/PrintJobExecutionStep.java`
* maybe `src/main/java/printerhub/persistence/PrintJobExecutionStepStore.java`

Role:

* expose more detailed execution step telemetry for uploads started via job flow

### API exposure of live session

* `src/main/java/printerhub/api/RemoteApiServer.java`

If you want the dashboard to show active upload metrics, you likely need:

* either enrich existing printer/job endpoint payloads
* or add a dedicated upload-status endpoint

### Runtime/cache, only if you want live polling-style status

* maybe `src/main/java/printerhub/runtime/PrinterRuntimeStateCache.java`
* maybe `src/main/java/printerhub/runtime/PrinterRuntimeNode.java`

Only needed if the upload session state should be globally queryable outside the job service directly.

### Tests

* `src/test/java/printerhub/command/SdCardUploadServiceTest.java`
* maybe new tests for adaptive controller helper
* `src/test/java/printerhub/job/AutonomousPrintControlServiceTest.java`
* `src/test/java/printerhub/api/RemoteApiServerTest.java`

This theme is where most algorithm testing belongs.

---

## Theme 3 — upload telemetry and modern dashboard card

These are the most likely files for **Step G**.

### Dashboard views/components

* `src/main/resources/dashboard/views/printer-print.js`
* possibly `src/main/resources/dashboard/views/printer-home.js`
* possibly `src/main/resources/dashboard/components/job-card.js`
* possibly `src/main/resources/dashboard/components/status-panels.js`

My guess:

* `printer-print.js` should likely own the detailed upload card
* `printer-home.js` may show a compact summary
* `job-card.js` may need partial enrichment if upload is part of job display

### Frontend API wiring

* `src/main/resources/dashboard/api.js`
* `src/main/resources/dashboard/state.js`

### Styling

* `src/main/resources/dashboard/dashboard.css`

This file will definitely be touched if you want:

* segmented counters
* nice timing display
* quality meter
* active parameter chips
* progress visualization

### Server payload source

* `src/main/java/printerhub/api/RemoteApiServer.java`

Because the dashboard needs richer structured upload telemetry, not just text.

### Possibly backend aggregation helpers

* `src/main/java/printerhub/command/SdCardUploadService.java`
* or new DTO/state model exposed through API

### Tests

* mostly API tests, maybe some frontend manual testing rather than full JS unit tests if you do not currently have a frontend test harness

---

# Files most likely to become overloaded if you do not split the work

These are the danger files:

## `SdCardUploadService.java`

This can become a monster very fast.

Recommendation:

* keep transport execution here
* move adaptive policy/state into helper classes

## `RemoteApiServer.java`

This file already does a lot.

Recommendation:

* group new endpoints clearly:

  * transfer settings endpoints
  * upload telemetry endpoints

## `settings.js`

Do not also cram live upload visualization into settings.
Settings should stay settings.

## `printer-print.js`

This is a good place for upload monitoring UI, but keep it componentized if possible.

---

# Minimal architecture I would recommend

This is the clean shape.

## Backend

### persistent operator settings

* `SerialTransferSettings`
* `SerialTransferSettingsStore`

### runtime upload session state

* `SdUploadRuntimeState`

### adaptive control policy/helper

* `AdaptiveTransferController`

### executor/service

* `SdCardUploadService`

That four-part split is much healthier than dumping everything in the service.

---

# Practical implementation order

This is the safest order.

## first

settings persistence and API

## second

dashboard settings page

## third

upload service reads persisted settings

## fourth

adaptive controller helper introduced

## fifth

live telemetry model exposed through API

## sixth

rich upload card UI

That way each step leaves the codebase in a working state.

---
 