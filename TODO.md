# TO DO

----


detail version 0.2.4 STEP E,F,G


 **three distinct deliverables**:

1. **adaptive upload control logic**
2. **operator telemetry / better live dashboard display**
3. **editable transfer settings in dashboard settings**
 
----

## roadmap
 

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




###########################

 In this order:

1. `src/main/java/printerhub/command/SdCardUploadService.java`

   * add the live adaptive/runtime fields to the upload progress model
   * populate/update them during upload

2. `src/main/java/printerhub/api/RemoteApiServer.java`

   * expose those new `UploadProgress` fields in `/printers/{id}/sd-card/uploads/status`
   * keep `/settings/serial-transfer` returning full settings JSON

3. `src/main/resources/dashboard/views/printer-sd-card.js`

   * render all returned upload status fields, including adaptive state and raw telemetry

4. `src/main/resources/dashboard/dashboard.js`

   * only if needed for polling/state handling adjustments
   * likely minor or none

5. `src/main/resources/dashboard/api.js`

   * probably no change, unless endpoint shape changes

6. `src/main/resources/dashboard/state.js`

   * probably no change, unless you want extra normalization/defaults

7. `src/main/resources/dashboard/dashboard.css`

   * optional now for readability
   * more important in Step G than Step F

So the real core path is:

`SdCardUploadService.java`
`RemoteApiServer.java`
`printer-sd-card.js`


---



**Card 1 — Upload status**
This is the operator card.
It must answer fast:

* is it running / success / error
* how far is it
* how fast is it
* how much time remains
* how many resend/errors happened

So this card stays simple and visible first.

Example content:

* state badge
* file name
* confirmed lines / total lines
* percent
* bytes/sec
* lines/sec
* estimated remaining time
* rejected/resend count
* quality percent

That is the “does it work?” card.

**Card 2 — Adaptive tuning / transfer diagnostics**
This is the analysis card.
It answers:

* what is the controller doing right now
* what is configured in settings
* what did runtime choose instead
* is it degrading, stable, upgrading, or forced to min/single-send

Example pairs:

* `activeBatchSize` vs `configuredMaxBatchSize`
* `configuredMinBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `stableLinesForUpgrade`
* `resendsBeforeDowngrade`
* `singleSendMode`
* maybe current mode: `PIPELINED` / `MIN_BATCH` / `SINGLE_SEND`

 


---

Exactly. What you expect is correct.

Right now you are showing mostly:

* transfer progress
* transfer speed
* quality outcome

But for Step F you also want to show the **adaptive controller state** itself:

* current active batch size
* configured min/max
* stable accepted lines counter
* recent resend count
* single-send mode or pipelined mode
* maybe last adaptation reason

That is the real Step F observability.

The important distinction is this:

* **settings** are persistent operator configuration
* **adaptive state** is runtime upload session state

So no, these values should not mainly come from `SerialTransferSettingsStore`.
They should come from the **current upload progress/runtime state** inside `SdCardUploadService`.

## What should be exposed

For your Step F controller, the status JSON should expose at least these extra fields:

```text
configuredMaxBatchSize
configuredMinBatchSize
activeBatchSize
batchUpgradeStep
batchDowngradeStep
stableLinesForUpgrade
acceptedLinesSinceLastResend
recentResendWindowLines
recentResendCount
resendsBeforeDowngrade
recoveryEventsBeforeSingleSend
singleSendMode
transportMode
lastAdaptationReason
lastAdaptationAt
```

If your current backend model does not yet track all of them, then expose the ones you already have now, and add the missing ones incrementally.

## The architecture point

You wrote:

> as i was thinking is that the backend is optimizing in real time the file management and store some value for that

Yes, but that “some value” should be split in two families.

### 1. Persistent settings

These are from `SerialTransferSettings`

Examples:

* `sdUploadBatchSize` as configured ceiling
* `sdUploadMinBatchSize`
* `sdUploadBatchUpgradeStep`
* `sdUploadBatchDowngradeStep`
* `sdUploadStableLinesForUpgrade`
* `sdUploadResendWindowLines`
* `sdUploadResendThresholdForDowngrade`
* `sdUploadRecoveryThresholdForMinBatch`

These are operator-controlled.

### 2. Runtime adaptive state

These are per-upload live values

Examples:

* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `singleSendMode`
* `transportMode`
* `lastAdaptationReason`

These should live inside upload runtime state, not in DB settings.

## What to change in backend

Your `/printers/{id}/sd-card/uploads/status` endpoint already returns `UploadProgress`.

That is the right place to add the adaptive fields.

So the real work is:

### A. extend `SdCardUploadService.UploadProgress`

Add fields like:

```java
int configuredMaxBatchSize
int configuredMinBatchSize
int activeBatchSize
int batchUpgradeStep
int batchDowngradeStep
int stableLinesForUpgrade
long acceptedLinesSinceLastResend
int recentResendWindowLines
int recentResendCount
int resendThresholdForDowngrade
int recoveryThresholdForMinBatch
boolean singleSendMode
String transportMode
String lastAdaptationReason
Instant lastAdaptationAt
```

### B. populate them from the live runtime controller state

When upload is running, update them continuously.

### C. expose them in `sdCardUploadProgressJson(...)`

Like this:

```java
+ "\"configuredMaxBatchSize\":" + progress.configuredMaxBatchSize() + ","
+ "\"configuredMinBatchSize\":" + progress.configuredMinBatchSize() + ","
+ "\"activeBatchSize\":" + progress.activeBatchSize() + ","
+ "\"batchUpgradeStep\":" + progress.batchUpgradeStep() + ","
+ "\"batchDowngradeStep\":" + progress.batchDowngradeStep() + ","
+ "\"stableLinesForUpgrade\":" + progress.stableLinesForUpgrade() + ","
+ "\"acceptedLinesSinceLastResend\":" + progress.acceptedLinesSinceLastResend() + ","
+ "\"recentResendWindowLines\":" + progress.recentResendWindowLines() + ","
+ "\"recentResendCount\":" + progress.recentResendCount() + ","
+ "\"resendThresholdForDowngrade\":" + progress.resendThresholdForDowngrade() + ","
+ "\"recoveryThresholdForMinBatch\":" + progress.recoveryThresholdForMinBatch() + ","
+ "\"singleSendMode\":" + progress.singleSendMode() + ","
+ "\"transportMode\":" + nullableString(progress.transportMode()) + ","
+ "\"lastAdaptationReason\":" + nullableString(progress.lastAdaptationReason()) + ","
+ "\"lastAdaptationAt\":" + nullableString(
        progress.lastAdaptationAt() == null ? null : progress.lastAdaptationAt().toString()
) + ","
```

## What to change in frontend

Your current frontend API is already fine:

```javascript
export async function getPrinterSdUploadStatus(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/uploads/status`);
}
```

That part does not need special adaptation. It already returns whatever JSON the backend sends.

So the real frontend work is only in:

* status rendering
* maybe settings page later

## What to add in `renderSdUploadStatus`

For Step F, I would add a second block under upload telemetry called:

* **Adaptive transfer state**

Not pretty yet. Just raw and readable.

Use this helper:

```javascript
function renderAdaptiveMetricRow(label, value) {
  return `
    <div class="info-row">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value === null || value === undefined || value === "" ? "n/a" : String(value))}</strong>
    </div>
  `;
}
```

Then inside `renderSdUploadStatus(uploadStatus)` add:

```javascript
const adaptiveHtml = `
  <details class="events-section" open>
    <summary class="events-header">Adaptive transfer state</summary>
    <div class="info-list">
      ${renderAdaptiveMetricRow("Configured max batch size", uploadStatus.configuredMaxBatchSize)}
      ${renderAdaptiveMetricRow("Configured min batch size", uploadStatus.configuredMinBatchSize)}
      ${renderAdaptiveMetricRow("Active batch size", uploadStatus.activeBatchSize)}
      ${renderAdaptiveMetricRow("Batch upgrade step", uploadStatus.batchUpgradeStep)}
      ${renderAdaptiveMetricRow("Batch downgrade step", uploadStatus.batchDowngradeStep)}
      ${renderAdaptiveMetricRow("Stable lines for upgrade", uploadStatus.stableLinesForUpgrade)}
      ${renderAdaptiveMetricRow("Accepted lines since last resend", uploadStatus.acceptedLinesSinceLastResend)}
      ${renderAdaptiveMetricRow("Recent resend window lines", uploadStatus.recentResendWindowLines)}
      ${renderAdaptiveMetricRow("Recent resend count", uploadStatus.recentResendCount)}
      ${renderAdaptiveMetricRow("Resend threshold for downgrade", uploadStatus.resendThresholdForDowngrade)}
      ${renderAdaptiveMetricRow("Recovery threshold for min batch", uploadStatus.recoveryThresholdForMinBatch)}
      ${renderAdaptiveMetricRow("Single-send mode", uploadStatus.singleSendMode)}
      ${renderAdaptiveMetricRow("Transport mode", uploadStatus.transportMode)}
      ${renderAdaptiveMetricRow("Last adaptation reason", uploadStatus.lastAdaptationReason)}
      ${renderAdaptiveMetricRow("Last adaptation at", uploadStatus.lastAdaptationAt)}
    </div>
  </details>
`;
```

And return it with the other blocks:

```javascript
return `
  <div class="empty-state">
    <div class="section-header compact">
      <div>
        <h3>${escapeHtml(title)}</h3>
        <p class="muted">${escapeHtml(uploadStatus.message || uploadStatus.detail || "")}</p>
      </div>
      <span class="badge ${badgeClass}">${escapeHtml(stateLabel.toUpperCase())}</span>
    </div>

    ${progressHtml}
    ${qualityHtml}
    ${performanceHtml}
    ${adaptiveHtml}
    <details class="events-section" open>
      <summary class="events-header">Upload telemetry</summary>
      <div class="info-list">
        ${telemetryRows}
      </div>
    </details>
  </div>
`;
```

## What you should expect to see during a real Step F upload

If the controller is really working, these values should move:

* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `singleSendMode`
* `transportMode`
* `lastAdaptationReason`
* `lastAdaptationAt`

Typical expected behavior:

### stable phase

* `acceptedLinesSinceLastResend` increases steadily
* `recentResendCount` stays low
* `activeBatchSize` may climb slowly
* `transportMode = PIPELINED`

### instability detected

* resend occurs
* `acceptedLinesSinceLastResend` resets
* `recentResendCount` increases
* `activeBatchSize` drops
* `lastAdaptationReason` becomes something like:

  * `downgraded after resend`
  * `entered single-send mode after repeated recovery at minimum batch size`

### degraded safe mode

* `singleSendMode = true`
* `transportMode = SINGLE_SEND`
* `activeBatchSize` may effectively be 1

That is exactly the kind of evidence Step F should make visible before Step G beautifies it.

## The most important correction

Do not mix this up with settings exposure alone.

You asked:

> could we already expose all our settings in the model, this would not be difficult

Yes, and that is fine too. But exposing only settings is not enough for Step F.

Because settings show:

* what the controller is allowed to do

But runtime adaptive state shows:

* what the controller is actually doing now

You need both.

## Clean recommendation

For Step F:

* expose all serial transfer settings in `/settings/serial-transfer`
* expose all runtime adaptation fields in `/printers/{id}/sd-card/uploads/status`
* show both raw in frontend
* verify during real upload that values move correctly

For Step G:

* make it pretty
* active batch chip
* green/orange/red quality badge
* mode badge `PIPELINED` / `SINGLE_SEND`
* adaptation timeline
* progress + ETA + throughput cards

So the next correct backend target is not the settings endpoint anymore. It is `SdCardUploadService.UploadProgress`.

Send me that `UploadProgress` record/class and the place where it is built in `SdCardUploadService`, and I will align the exact backend fields with the frontend rendering.















###################################
---
##########################################################

 
## The right Step F direction

You do **not** want a complicated “AI” controller first.
You want a **safe hill-climbing controller with hysteresis**.

That means:

* start from configured ceiling
* reduce quickly on instability
* increase slowly on proven stability
* never jump wildly
* never oscillate too fast

## The algorithm for STEP F

Use 5 runtime concepts:

* `configuredMaxBatchSize`
* `configuredMinBatchSize`
* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`

And 3 tuning thresholds:

* `stableLinesForUpgrade`
* `resendsBeforeDowngrade`
* `recoveryEventsBeforeSingleSend`

### Runtime behavior

At upload start:

* `activeBatchSize = configuredMaxBatchSize`
* `acceptedLinesSinceLastResend = 0`
* `recentResendCount = 0`
* mode = `PIPELINED`

When a batch succeeds cleanly:

* add accepted line count to `acceptedLinesSinceLastResend`

If `acceptedLinesSinceLastResend >= stableLinesForUpgrade`:

* if `activeBatchSize < configuredMaxBatchSize`

  * increase by `1`
* reset `acceptedLinesSinceLastResend = 0`
* record event like:

  * `SD upload adaptation: increased active batch size from 2 to 3 after 300 stable lines`

When a resend happens:

* run buffered recovery as you already do
* increment resend/recovery counters
* reset `acceptedLinesSinceLastResend = 0`

Then downgrade:

* if `activeBatchSize > configuredMinBatchSize`

  * reduce by `1`
* else stay at min
* if repeated recovery still happens at min batch:

  * enter `singleSendMode`

This is much better than immediate permanent collapse.

## Why this works better

Because it behaves like this:

* printer is stable at 5 -> stays at 5
* printer unstable at 5 but stable at 3 -> naturally settles near 3
* printer unstable everywhere -> degrades to 1 safely
* printer becomes stable again later -> climbs back slowly

So the controller searches for a practical working point instead of behaving like a panic switch.

## The important design rule

Do **not** use `singleSendMode` as the primary state anymore.

Use `activeBatchSize` as the main authority.

Then:

* `activeBatchSize == 1` means effectively single-send
* `singleSendMode()` can just be derived from that

That makes the model cleaner.

## Concrete plan for Step F

### 1. Expand `SdUploadRuntimeState`

Add fields like:

* `configuredMinBatchSize`
* `stableLinesForUpgrade`
* `resendsBeforeDowngrade`
* `recoveryCount`
* `acceptedLinesSinceLastResend`
* `successfulBatchCount`
* `lastResendLine`
* `modeTransitionCount`

Add methods like:

* `registerAcceptedLines(int count)`
* `registerRecovery()`
* `registerResend(int resendLine)`
* `shouldUpgradeBatch()`
* `upgradeBatchIfEligible()`
* `downgradeAfterRecovery()`
* `currentModeLabel()`

### 2. Let `SdCardUploadService` stop deciding policy itself

It should only:

* stream
* detect resend
* call recovery
* tell runtime state what happened
* ask runtime state what batch size to use next

### 3. Persist adaptation events

Very important for diagnostics.

Examples:

* upload entered recovery
* batch size reduced from 5 to 4
* batch size reduced from 2 to 1
* batch size increased from 1 to 2 after 250 stable lines

### 4. Only after backend behavior is stable, do Step G dashboard polish

Because fancy UI before solid adaptive metrics is backward.

## My recommended first thresholds

Keep them simple first:

* `configuredMinBatchSize = 1`
* `stableLinesForUpgrade = 200`
* `resendsBeforeDowngrade = 1`
* downgrade step = `1`
* upgrade step = `1`

That is conservative and testable.

## So, 
---
 