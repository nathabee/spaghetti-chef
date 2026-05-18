# REST API


# list of endpoint 

- they are defined in the api/RemoteApiServer.java

 

## Health

```text
GET /health
```

Returns:

```json
{"status":"ok"}
```

---

## Dashboard static resources

```text
GET /dashboard
GET /dashboard/
GET /dashboard/{resourcePath}
```

Examples:

```text
GET /dashboard/index.html
GET /dashboard/dashboard.css
GET /dashboard/dashboard.js
GET /dashboard/api.js
GET /dashboard/components/nav.js
GET /dashboard/views/farm-home.js
GET /dashboard/favicon.svg
```

---

## Printers

```text
GET    /printers
POST   /printers
GET    /printers/{printerId}
PUT    /printers/{printerId}
DELETE /printers/{printerId}
POST   /printers/{printerId}/enable
POST   /printers/{printerId}/disable
GET    /printers/{printerId}/status
GET    /printers/{printerId}/events
```

Purpose:

```text
GET /printers
  List registered runtime printers.

POST /printers
  Create/register a printer.

GET /printers/{printerId}
  Get one printer.

PUT /printers/{printerId}
  Update printer configuration.

DELETE /printers/{printerId}
  Delete/unregister printer.

POST /printers/{printerId}/enable
  Enable printer.

POST /printers/{printerId}/disable
  Disable printer.

GET /printers/{printerId}/status
  Read latest cached printer snapshot.

GET /printers/{printerId}/events
  Read recent printer events.
```

---

## Printer manual commands

```text
POST /printers/{printerId}/commands
```

Purpose:

```text
Execute an allowed manual command through PrinterCommandService.
```

Current controlled examples include things like:

```text
M105
M114
M115
M104 with targetTemperature
```

---

## Printer SD card

```text
GET  /printers/{printerId}/sd-card/files
POST /printers/{printerId}/sd-card/uploads
GET  /printers/{printerId}/sd-card/uploads/status
POST /printers/{printerId}/sd-card/recovery/close-upload
```

Purpose:

```text
GET /printers/{printerId}/sd-card/files
  Ask firmware for SD card file list and register discovered files.

POST /printers/{printerId}/sd-card/uploads
  Upload a known PrintFile to the printer SD card.

GET /printers/{printerId}/sd-card/uploads/status
  Read current SD upload progress.

POST /printers/{printerId}/sd-card/recovery/close-upload
  Attempt M29 close-upload recovery for an open upload session.
```

---

## Camera monitoring, new 0.4.0 endpoints

```text
GET  /printers/{printerId}/camera/status
GET  /printers/{printerId}/camera/settings
PUT  /printers/{printerId}/camera/settings
POST /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/events
```

Purpose:

```text
GET /printers/{printerId}/camera/status
  Read camera availability/status for the printer.

GET /printers/{printerId}/camera/settings
  Read camera settings for the printer.

PUT /printers/{printerId}/camera/settings
  Save camera settings for the printer.

POST /printers/{printerId}/camera/snapshot
  Capture a new camera snapshot.

GET /printers/{printerId}/camera/snapshot
  Return the latest snapshot image.

GET /printers/{printerId}/camera/events
  Return recent camera events.
```

Example camera settings body:

```json
{
  "enabled": true,
  "sourceType": "simulated",
  "sourceValue": "default",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20
}
```

Supported camera source types for 0.4.0:

```text
disabled
simulated
snapshot-folder
```

---

## Print files

```text
GET  /print-files
POST /print-files
POST /print-files/uploads?filename={filename}
GET  /print-files/{printFileId}
GET  /print-files/{printFileId}/content
```

Purpose:

```text
GET /print-files
  List known print files.

POST /print-files
  Register an existing host-side print file by path.

POST /print-files/uploads?filename={filename}
  Upload print file content through HTTP.

GET /print-files/{printFileId}
  Get print file metadata.

GET /print-files/{printFileId}/content
  Get print file metadata plus content.
```

---

## Printer SD file registry

```text
GET    /printer-sd-files
POST   /printer-sd-files
GET    /printer-sd-files/{printerSdFileId}
POST   /printer-sd-files/{printerSdFileId}/enable
POST   /printer-sd-files/{printerSdFileId}/disable
DELETE /printer-sd-files/{printerSdFileId}
```

Query variant:

```text
GET /printer-sd-files?printerId={printerId}
```

Purpose:

```text
GET /printer-sd-files
  List all registered printer-side SD files.

GET /printer-sd-files?printerId={printerId}
  List SD files for one printer.

POST /printer-sd-files
  Manually register a printer-side SD file target.

GET /printer-sd-files/{printerSdFileId}
  Get one registered SD file.

POST /printer-sd-files/{printerSdFileId}/enable
  Enable a registered SD file for job use.

POST /printer-sd-files/{printerSdFileId}/disable
  Disable a registered SD file.

DELETE /printer-sd-files/{printerSdFileId}
  Delete/mark deleted, and if needed try firmware-side delete.
```

---

## Jobs

```text
GET    /jobs
POST   /jobs
GET    /jobs/{jobId}
DELETE /jobs/{jobId}
POST   /jobs/{jobId}/start
POST   /jobs/{jobId}/pause
POST   /jobs/{jobId}/resume
POST   /jobs/{jobId}/cancel
POST   /jobs/{jobId}/restart
GET    /jobs/{jobId}/events
GET    /jobs/{jobId}/execution-steps
```

Purpose:

```text
GET /jobs
  List recent jobs.

POST /jobs
  Create a job.

GET /jobs/{jobId}
  Get one job.

DELETE /jobs/{jobId}
  Delete one job.

POST /jobs/{jobId}/start
  Start a job asynchronously.

POST /jobs/{jobId}/pause
  Pause a running autonomous print file job.

POST /jobs/{jobId}/resume
  Resume a paused autonomous print file job.

POST /jobs/{jobId}/cancel
  Cancel a job.

POST /jobs/{jobId}/restart
  Create a new job from a terminal print-file job.

GET /jobs/{jobId}/events
  Read job-related printer events.

GET /jobs/{jobId}/execution-steps
  Read structured execution diagnostics.
```

---

## Monitoring

```text
GET /monitoring
```

Purpose:

```text
Return a global runtime monitoring snapshot:
- summary
- printers
- active jobs
- active uploads
```

---

## Monitoring settings

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Purpose:

```text
GET /settings/monitoring
  Read monitoring rules.

PUT /settings/monitoring
  Update monitoring rules and apply them to the scheduler.
```

Fields currently handled:

```text
pollIntervalSeconds
snapshotMinimumIntervalSeconds
temperatureDeltaThreshold
eventDeduplicationWindowSeconds
errorPersistenceBehavior
debugWireTracingEnabled
```

---

## Print file settings

```text
GET /settings/print-files
PUT /settings/print-files
```

Purpose:

```text
GET /settings/print-files
  Read print file storage settings.

PUT /settings/print-files
  Update print file storage directory.
```

Example body:

```json
{
  "storageDirectory": "printerhub-print-files"
}
```

---

## Serial transfer settings

```text
GET /settings/serial-transfer
PUT /settings/serial-transfer
```

Purpose:

```text
GET /settings/serial-transfer
  Read SD upload / serial transfer settings.

PUT /settings/serial-transfer
  Update SD upload / serial transfer settings.
```

Fields currently handled:

```text
sdUploadBatchSize
sdUploadMinBatchSize
sdUploadBatchUpgradeStep
sdUploadBatchDowngradeStep
sdUploadStableLinesForUpgrade
sdUploadResendWindowLines
sdUploadResendThresholdForDowngrade
sdUploadRecoveryThresholdForMinBatch
sdUploadRecoveryWindowMultiplier
sdUploadMaxErrors
sdUploadMaxConsecutiveIdenticalResends
sdUploadMinPerformancePercent
sdUploadMaxRetriesPerLine
fileStreamingReadTimeoutMs
fileStreamingQuietPeriodMs
fileStreamingReadActivitySleepMs
fileStreamingReadIdleSleepMs
fileStreamingRecoveryReplayDelayMs
```

---

## Security settings

```text
GET /settings/security
PUT /settings/security
```

Purpose:

```text
GET /settings/security
  Read local security settings.

PUT /settings/security
  Update local security settings.
```

Fields:

```text
securityEnabled
defaultRole
requireDangerousActionConfirmation
```

---

## Security profiles / roles

```text
GET /security/profile
GET /security/roles
PUT /security/roles
```

Purpose:

```text
GET /security/profile
  Return active security settings plus default role profile.

GET /security/roles
  Return role profiles.

PUT /security/roles
  Update a role profile.
```

---

## Operator audit

```text
GET /operator-audit
```

Purpose:

```text
Return recent local operator audit events.
```

---

# Condensed endpoint table

```text
GET    /health

GET    /dashboard
GET    /dashboard/
GET    /dashboard/{resourcePath}

GET    /printers
POST   /printers
GET    /printers/{printerId}
PUT    /printers/{printerId}
DELETE /printers/{printerId}
GET    /printers/{printerId}/status
POST   /printers/{printerId}/enable
POST   /printers/{printerId}/disable
POST   /printers/{printerId}/commands
GET    /printers/{printerId}/events

GET    /printers/{printerId}/sd-card/files
POST   /printers/{printerId}/sd-card/uploads
GET    /printers/{printerId}/sd-card/uploads/status
POST   /printers/{printerId}/sd-card/recovery/close-upload

GET    /printers/{printerId}/camera/status
GET    /printers/{printerId}/camera/settings
PUT    /printers/{printerId}/camera/settings
POST   /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/events

GET    /print-files
POST   /print-files
POST   /print-files/uploads?filename={filename}
GET    /print-files/{printFileId}
GET    /print-files/{printFileId}/content

GET    /printer-sd-files
GET    /printer-sd-files?printerId={printerId}
POST   /printer-sd-files
GET    /printer-sd-files/{printerSdFileId}
POST   /printer-sd-files/{printerSdFileId}/enable
POST   /printer-sd-files/{printerSdFileId}/disable
DELETE /printer-sd-files/{printerSdFileId}

GET    /jobs
POST   /jobs
GET    /jobs/{jobId}
DELETE /jobs/{jobId}
POST   /jobs/{jobId}/start
POST   /jobs/{jobId}/pause
POST   /jobs/{jobId}/resume
POST   /jobs/{jobId}/cancel
POST   /jobs/{jobId}/restart
GET    /jobs/{jobId}/events
GET    /jobs/{jobId}/execution-steps

GET    /monitoring

GET    /settings/monitoring
PUT    /settings/monitoring

GET    /settings/print-files
PUT    /settings/print-files

GET    /settings/serial-transfer
PUT    /settings/serial-transfer

GET    /settings/security
PUT    /settings/security

GET    /security/profile
GET    /security/roles
PUT    /security/roles

GET    /operator-audit
```

Current technical debt is clear: `RemoteApiServer` is doing routing, parsing, authorization, auditing, serialization, static resource serving, and domain delegation in one file. Camera is now a good proof that the next architecture cleanup should be route-handler extraction by domain.
