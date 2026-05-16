Below is a cleaned, split version of your roadmap section. I separated it into:

* **Step G**: backend-to-frontend telemetry exposure
* **Step H**: functional two-card frontend display
* **Step I**: polished monitoring UX / operator-grade visualization

You can paste this into your roadmap.

---

## 0.2.4 — Upload telemetry, adaptive tuning visibility, and operator dashboard UX

Status: planned

Purpose:

Make SD-card upload behavior observable from the dashboard. The goal is not only to know that an upload is running, but also to understand how well it is running, how the adaptive controller behaves, and whether the transfer is stable, degraded, or recovering.

This work is split into three steps:

* **Step G** — expose all upload telemetry from backend to frontend
* **Step H** — render the functional two-card upload monitoring display
* **Step I** — polish the display into a modern operator-grade monitoring UX

---

# 0.2.4 — Step G — expose upload telemetry and adaptive runtime state

Status: planned

## Goal

Bring all useful SD-upload telemetry from the backend to the frontend through the REST API.

At this stage, the display does not need to be beautiful. The priority is correctness and observability: the frontend must receive the values needed to verify that Step F adaptive upload behavior is really working.

## Scope

Step G focuses on the data chain:

```text
SdCardUploadService runtime state
        ↓
UploadProgress model
        ↓
RemoteApiServer JSON
        ↓
dashboard API/state
        ↓
printer-sd-card.js raw rendering
```

## Main principle

Persistent settings and runtime adaptive state must remain separate.

### Persistent settings

These are operator configuration values stored in the database through `SerialTransferSettings`.

Examples:

* `sdUploadBatchSize`
* `sdUploadMinBatchSize`
* `sdUploadBatchUpgradeStep`
* `sdUploadBatchDowngradeStep`
* `sdUploadStableLinesForUpgrade`
* `sdUploadResendWindowLines`
* `sdUploadResendThresholdForDowngrade`
* `sdUploadRecoveryThresholdForMinBatch`
* `sdUploadRecoveryWindowMultiplier`
* `sdUploadMaxErrors`
* `sdUploadMaxConsecutiveIdenticalResends`
* `sdUploadMinPerformancePercent`
* `sdUploadMaxRetriesPerLine`
* file-streaming timing values

These values describe what the controller is allowed to do.

### Runtime adaptive state

These are live per-upload values held by the upload runtime/session state.

Examples:

* `configuredMaxBatchSize`
* `configuredMinBatchSize`
* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `recoveryCount`
* `singleSendMode`
* `transportMode`
* `lastAdaptationReason`
* `lastAdaptationAt`

These values describe what the controller is actually doing now.

## Important persistence rule

The final runtime tuning value should **not** automatically overwrite the database setting after a successful transfer.

Reason:

* DB settings represent operator intent.
* Runtime values represent one upload session.
* A single upload may be affected by transient printer noise, cable behavior, firmware timing, or SD-card response.
* Automatically saving runtime adaptation back to DB would mix configuration with temporary runtime behavior.

For now:

* `sdUploadBatchSize` in DB remains the configured maximum batch size.
* `activeBatchSize` remains runtime session state.
* After upload completion, the final runtime values may remain visible as last-session telemetry, but they should not become new persistent settings automatically.

A later feature may add:

* “Promote last stable value to settings”
* “Remember last good value per printer”
* “Adaptive profile per printer”

But that is not part of Step G.

## Backend fields expected in upload status JSON

Endpoint:

```text
GET /printers/{printerId}/sd-card/uploads/status
```

The response should expose both transfer telemetry and adaptive runtime state.

### Transfer identity

```text
printerId
printFileId
originalFilename
requestedTargetFilename
state
active
detail
startedAt
updatedAt
```

### Progress counters

```text
uploadedLineCount
totalLineCount
totalByteCount
rejectedLineCount
percent
qualityPercent
```

### Timing and speed

```text
bytesPerSecond
linesPerSecond
elapsedSeconds
estimatedSecondsRemaining
theoreticalMaxBytesPerSecond
efficiencyPercent
```

### Adaptive controller state

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
resendThresholdForDowngrade
recoveryThresholdForMinBatch
singleSendMode
transportMode
lastAdaptationReason
lastAdaptationAt
```

If some fields are not yet implemented in the backend, expose the available ones first and add the missing fields incrementally.

## Files to change

### 1. `src/main/java/printerhub/command/SdCardUploadService.java`

Add live adaptive/runtime fields to the upload progress model.

Likely work:

* extend `UploadProgress`
* populate adaptive values during upload
* keep progress updated during upload
* preserve final session telemetry after success/error
* ensure fields are updated after resend, recovery, downgrade, upgrade, and single-send transition

Expected runtime fields:

```text
configuredMaxBatchSize
configuredMinBatchSize
activeBatchSize
acceptedLinesSinceLastResend
recentResendCount
recoveryCount
singleSendMode
transportMode
lastAdaptationReason
lastAdaptationAt
```

### 2. `src/main/java/printerhub/api/RemoteApiServer.java`

Expose the new `UploadProgress` fields in:

```text
/printers/{id}/sd-card/uploads/status
```

Also keep:

```text
/settings/serial-transfer
```

returning the full persistent settings JSON.

This endpoint must return valid JSON using `Locale.ROOT` formatting for decimal numbers. The earlier comma-decimal bug must not return:

```json
"bytesPerSecond":0,0
```

It must return:

```json
"bytesPerSecond":0.00
```

### 3. `src/main/resources/dashboard/views/printer-sd-card.js`

Render all returned upload status fields in a simple readable way.

At Step G, raw visibility is enough. The goal is to confirm that the fields reach the frontend and update during a real upload.

### 4. `src/main/resources/dashboard/dashboard.js`

Only adjust if polling/state handling needs it.

Expected work:

* probably no major change
* confirm upload status polling reads the new JSON fields automatically
* confirm the values update every poll

### 5. `src/main/resources/dashboard/api.js`

Probably no change.

Current function already works:

```javascript
export async function getPrinterSdUploadStatus(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/uploads/status`);
}
```

### 6. `src/main/resources/dashboard/state.js`

Probably no change.

The status object can remain generic and store all fields returned by the backend.

### 7. `src/main/resources/dashboard/dashboard.css`

Optional in Step G.

Only add minimal readability styling if necessary. The visual polish belongs mainly to Step I.

## Expected result

At the end of Step G:

* the REST status endpoint returns valid JSON
* upload status JSON contains transfer progress, performance metrics, and adaptive runtime state
* frontend receives the fields without parsing errors
* dashboard can show raw runtime values during an upload
* Step F adaptive behavior becomes verifiable from the browser

---

# 0.2.4 — Step H — functional two-card upload monitoring display

Status: planned

## Goal

Create a clear two-card dashboard structure for SD-card upload monitoring.

Step H is about usability and layout, not visual polish. The user should be able to upload a file and immediately understand whether it is working, while still having access to detailed adaptive diagnostics when needed.

## UX principle

Do not mix normal operator progress with deep analysis.

The dashboard should support two different user intentions:

1. “I want to upload and see if it works.”
2. “I want to observe and analyse what the adaptive controller is doing.”

Therefore, use two cards.

---

## Card 1 — Upload status

This is the operator card.

It must answer quickly:

* is the upload running, successful, or failed
* how far the upload is
* how fast it is
* how much time remains
* whether resends/recovery are happening
* whether transfer quality is good or degraded

This card should always be visible when upload status exists.

### Content

Show:

```text
state
file name
confirmed lines / total lines
percent
uploaded bytes / total bytes if available
bytes per second
lines per second
elapsed time
estimated remaining time
rejected/resend count
quality percent
detail message
```

### Example labels

```text
Upload state
File
Progress
Lines
Bytes
Speed
Line rate
Elapsed
Remaining
Rejected lines
Transfer quality
Last detail
```

### Expected behavior

During upload:

* progress updates regularly
* speed updates regularly
* ETA updates regularly
* rejected/resend count changes when recovery happens
* quality percentage reflects resend pressure

After upload:

* card remains visible as last upload summary
* state becomes `success` or `error`
* progress remains inspectable
* timing and quality remain visible

---

## Card 2 — Adaptive tuning / transfer diagnostics

This is the analysis card.

It should not dominate the normal upload flow. It can be placed below the upload status card and may be collapsible.

It answers:

* what is the adaptive controller doing now
* what is configured
* what runtime value was selected
* whether the upload is stable, degraded, recovering, or in single-send mode

### Content

Show:

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
resendThresholdForDowngrade
recoveryThresholdForMinBatch
singleSendMode
transportMode
lastAdaptationReason
lastAdaptationAt
```

### Recommended grouping

#### Current runtime decision

```text
Transport mode
Active batch size
Single-send mode
Last adaptation reason
Last adaptation at
```

#### Configured limits

```text
Configured max batch size
Configured min batch size
Batch upgrade step
Batch downgrade step
```

#### Stability and resend pressure

```text
Stable lines for upgrade
Accepted lines since last resend
Recent resend window lines
Recent resend count
Resend threshold for downgrade
Recovery threshold for min batch
```

## Files to change

### Main file

```text
src/main/resources/dashboard/views/printer-sd-card.js
```

This file should own the first functional display because upload-to-SD actions already live there.

### Possible helper split later

If `printer-sd-card.js` becomes too large, extract rendering helpers later, for example:

```text
src/main/resources/dashboard/components/sd-upload-status-card.js
src/main/resources/dashboard/components/sd-upload-tuning-card.js
```

But for Step H, keeping it in `printer-sd-card.js` is acceptable if the code remains readable.

### Supporting files

```text
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/state.js
src/main/resources/dashboard/api.js
```

Expected changes are minor or none.

## Expected result

At the end of Step H:

* upload status appears as a clear operator card
* adaptive tuning appears as a separate diagnostics card
* the operator is not overwhelmed by analysis values
* technical users can still observe Step F runtime behavior during long uploads
* the display proves that adaptive fields are changing during the transfer

---

# 0.2.4 — Step I — modern upload monitoring UX and operator-grade visualization

Status: planned

## Goal

Turn the functional Step H display into a polished monitoring interface.

Step I is the dashboard design step. It should make transfer health, progress, speed, and adaptive tuning behavior understandable at a glance.

## UX goal

The operator should be able to see:

* upload is healthy
* upload is slow but stable
* upload is recovering
* upload is degraded
* upload is in single-send fallback
* adaptive batch size changed because of resend pressure
* transfer quality is acceptable or dangerous

## Card 1 — polished upload status card

Improve the operator card with visual hierarchy.

### Visual targets

* prominent state badge
* modern progress bar
* segmented counters
* ETA display
* throughput display
* quality meter
* resend warning indicator
* clear success/error state

### Suggested display elements

```text
Upload state badge
Large percent value
Progress bar
Lines uploaded / total
Bytes uploaded / total
Elapsed time
Estimated remaining time
Bytes/sec
Lines/sec
Transfer quality
Rejected/resend count
```

### Transfer health indicator

Create a simple health label:

```text
Healthy
Recovering
Degraded
Fallback
Failed
```

Possible rule:

```text
Healthy:
  active && rejectedLineCount == 0 && qualityPercent >= 99

Recovering:
  active && recentResendCount > 0

Degraded:
  active && qualityPercent < 95

Fallback:
  singleSendMode == true || transportMode == SINGLE_SEND

Failed:
  state == error
```

This rule can be frontend-only at first.

## Card 2 — polished adaptive tuning diagnostics

Improve the diagnostics card so that it becomes useful without reading raw numbers line by line.

### Visual targets

* active batch size chip
* configured max/min comparison
* mode badge
* stability progress toward next upgrade
* resend pressure indicator
* last adaptation message
* optional timeline/list of adaptation events

### Suggested sections

#### Current controller state

```text
Mode: PIPELINED / SINGLE_SEND
Active batch size: 3
Configured range: 1–5
Last decision: downgraded after resend
```

#### Stability toward upgrade

Show:

```text
acceptedLinesSinceLastResend / stableLinesForUpgrade
```

Example:

```text
138 / 200 stable lines before next upgrade
```

This can become a small progress meter.

#### Resend pressure

Show:

```text
recentResendCount / resendThresholdForDowngrade
```

Example:

```text
1 / 1 resend threshold
```

This can become orange/red when pressure is high.

#### Recovery/fallback pressure

Show:

```text
recoveryCount / recoveryThresholdForMinBatch
```

or, if the current backend uses a different name:

```text
recovery events before single-send
```

## Optional trend display

For Step I, optional small trend visualization may be useful:

* active batch size over time
* resend count over recent lines
* throughput trend
* quality trend

This should not be implemented before the basic two-card display is stable.

## Files to change

### Main visual files

```text
src/main/resources/dashboard/views/printer-sd-card.js
src/main/resources/dashboard/dashboard.css
```

### Possible extracted components

If the file becomes too large:

```text
src/main/resources/dashboard/components/sd-upload-status-card.js
src/main/resources/dashboard/components/sd-upload-tuning-card.js
```

### Possible compact display locations

Later, a compact upload summary may be shown in:

```text
src/main/resources/dashboard/views/printer-home.js
src/main/resources/dashboard/components/status-panels.js
```

But the main detailed monitoring should remain in the SD-card/upload area.

## Expected result

At the end of Step I:

* upload monitoring looks modern and readable
* the operator sees progress and health immediately
* adaptive behavior is understandable without reading logs
* resend/recovery pressure is visible
* the project demonstrates protocol resilience plus operator-grade dashboard UX

---

# Architecture notes

## Keep responsibilities separate

### `SerialTransferSettings`

Persistent operator settings.

Stores configured limits, thresholds, and file streaming timing.

### `SerialTransferSettingsStore`

Database access for persistent settings.

### `SdUploadRuntimeState`

Runtime session state for the active upload.

Should hold values like:

```text
activeBatchSize
acceptedLinesSinceLastResend
recentResendCount
recoveryCount
singleSendMode
transportMode
lastAdaptationReason
lastAdaptationAt
```

### `AdaptiveTransferController`

Policy helper for upgrade/downgrade decisions.

Should decide:

* when to increase batch size
* when to downgrade
* when to enter single-send fallback
* what adaptation reason to record

### `SdCardUploadService`

Execution service.

Should:

* stream lines
* detect resend requests
* perform recovery
* update runtime state
* ask the controller/runtime state what batch size to use
* expose `UploadProgress`

It should not become a giant class that owns every detail of policy, UI telemetry, persistence, and transport behavior.

---

# Files most likely to become overloaded

## `SdCardUploadService.java`

Risk:

* too much upload transport
* too much adaptive policy
* too much progress telemetry
* too much event logic

Recommendation:

* keep transport execution here
* move adaptive decision logic to helper classes when it grows

## `RemoteApiServer.java`

Risk:

* already handles many endpoints
* manual JSON building is error-prone

Recommendation:

* keep upload telemetry JSON grouped clearly
* use `Locale.ROOT` formatting for all decimal values
* avoid adding dashboard logic here

## `printer-sd-card.js`

Risk:

* upload actions, SD-card files, registered targets, host files, upload telemetry, and tuning diagnostics can make the file large

Recommendation:

* Step H can keep rendering here
* Step I may extract upload cards into components

## `settings.js`

Do not put live upload monitoring here.

Settings should remain for persistent operator configuration. Runtime upload diagnostics belong near the upload workflow.

---

# Final implementation order

## Step G order

```text
1. SdCardUploadService.java
2. RemoteApiServer.java
3. printer-sd-card.js
4. dashboard.js if polling needs adjustment
5. api.js only if endpoint shape changes
6. state.js only if normalization is needed
7. dashboard.css only for minimal readability
```

Core chain:

```text
SdCardUploadService.java
RemoteApiServer.java
printer-sd-card.js
```

## Step H order

```text
1. printer-sd-card.js
2. dashboard.css minimal layout if needed
3. dashboard.js if polling/render refresh needs adjustment
```

## Step I order

```text
1. decide final card layout
2. polish printer-sd-card.js or extract components
3. update dashboard.css
4. optionally add compact upload summary to printer-home.js/status-panels.js
5. manually verify with real upload
```

---

# Summary

Step G proves the data exists and reaches the browser.

Step H makes the data usable with two clear cards:

* upload status
* adaptive tuning diagnostics

Step I makes it visually strong and operator-grade.

The most important design rule remains:

```text
Persistent settings are configuration.
Runtime adaptive values are telemetry.
Do not auto-save runtime adaptation back into DB settings after one successful upload.
```
