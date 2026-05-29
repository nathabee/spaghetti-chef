# SpaghettiChef REST API

This document is a practical endpoint reference for the current local SpaghettiChef API. (updated in version 0.7.0)

The API and dashboard are served from the same host and port.

Example:

```text
http://localhost:18080
```

Most endpoints return JSON. Image endpoints return image bytes.

---

## Conventions

### Base URL

```text
http://localhost:18080
```

### JSON Responses

Most endpoints return:

```http
Content-Type: application/json; charset=utf-8
```

### Image Responses

Snapshot and delta-frame file endpoints return image bytes, usually:

```http
Content-Type: image/jpeg
Cache-Control: no-store
```

### Common Error Response

General API errors use:

```json
{
  "error": "message"
}
```

Camera-specific handler errors may use:

```json
{
  "error": "camera_error_code",
  "message": "Detailed message"
}
```

### CORS

The API allows local dashboard access using:

```text
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, X-SpaghettiChef-Role
```

### Optional Role Header

When local security is enabled, requests may include:

```http
X-SpaghettiChef-Role: ADMIN
```

---

# Core Runtime

## Health and Version

```text
GET /health
GET /version
```

### GET /health

Returns:

```json
{
  "status": "ok"
}
```

### GET /version

Returns the runtime application version:

```json
{
  "version": "0.6.5"
}
```

---

## Dashboard Static Resources

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
GET /dashboard/components/camera-card.js
GET /dashboard/views/printer-camera.js
GET /dashboard/favicon.svg
```

Supported static content types include:

```text
.html  -> text/html; charset=utf-8
.css   -> text/css; charset=utf-8
.js    -> application/javascript; charset=utf-8
.svg   -> image/svg+xml
other  -> application/octet-stream
```

---

# Printers

## Printer Endpoints

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
POST   /printers/{printerId}/commands
```

---

## GET /printers

Returns all registered printers.

Response shape:

```json
{
  "printers": [
    {
      "id": "p1",
      "displayName": "Printer 1",
      "name": "Printer 1",
      "portName": "/dev/ttyUSB0",
      "mode": "serial",
      "serialPortKind": "stable-or-device-kind",
      "stableSerialPath": true,
      "serialPathWarning": null,
      "enabled": true,
      "state": "READY",
      "hotendTemperature": 205.0,
      "bedTemperature": 60.0,
      "lastResponse": "ok T:205.0 /205.0 B:60.0 /60.0",
      "errorMessage": null,
      "serialFailureType": null,
      "updatedAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## POST /printers

Registers a printer.

Request body:

```json
{
  "id": "p1",
  "displayName": "Printer 1",
  "portName": "/dev/ttyUSB0",
  "mode": "serial",
  "enabled": true
}
```

Common modes:

```text
serial
sim
sim-disconnected
sim-timeout
sim-error
```

Response:

```json
{
  "id": "p1",
  "displayName": "Printer 1",
  "name": "Printer 1",
  "portName": "/dev/ttyUSB0",
  "mode": "serial",
  "enabled": true,
  "state": "UNKNOWN"
}
```

---

## GET /printers/{printerId}

Returns one printer.

---

## PUT /printers/{printerId}

Updates a printer.

Request body:

```json
{
  "displayName": "Printer 1",
  "portName": "/dev/ttyUSB0",
  "mode": "serial",
  "enabled": true
}
```

---

## DELETE /printers/{printerId}

Deletes a printer registration.

Response:

```json
{
  "deleted": "p1"
}
```

---

## POST /printers/{printerId}/enable

Enables a printer and starts monitoring.

---

## POST /printers/{printerId}/disable

Disables a printer, stops monitoring, and closes the node.

---

## GET /printers/{printerId}/status

Returns the current runtime snapshot.

Response shape:

```json
{
  "state": "READY",
  "hotendTemperature": 205.0,
  "bedTemperature": 60.0,
  "lastResponse": "ok",
  "errorMessage": null,
  "serialFailureType": null,
  "updatedAt": "2026-05-28T12:00:00Z"
}
```

If no snapshot exists:

```json
{
  "state": "UNKNOWN",
  "hotendTemperature": null,
  "bedTemperature": null,
  "lastResponse": null,
  "errorMessage": null,
  "serialFailureType": null,
  "updatedAt": null
}
```

---

## GET /printers/{printerId}/events

Returns recent printer events.

Response shape:

```json
{
  "events": [
    {
      "id": 1,
      "printerId": "p1",
      "jobId": "job-1",
      "eventType": "JOB_STARTED",
      "message": "Job started",
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## POST /printers/{printerId}/commands

Executes a controlled manual command.

Request body:

```json
{
  "command": "M105"
}
```

Temperature command example:

```json
{
  "command": "M104",
  "targetTemperature": 200
}
```

Response shape:

```json
{
  "printerId": "p1",
  "command": "M105",
  "sentCommand": "M105",
  "response": "ok T:205.0 /205.0 B:60.0 /60.0"
}
```

If a printer execution is already in progress, the endpoint returns:

```http
409 Conflict
```

---

# Printer SD Card

## Firmware SD Card Endpoints

```text
GET  /printers/{printerId}/sd-card/files
POST /printers/{printerId}/sd-card/uploads
GET  /printers/{printerId}/sd-card/uploads/status
POST /printers/{printerId}/sd-card/recovery/close-upload
```

---

## GET /printers/{printerId}/sd-card/files

Lists files reported by the printer firmware SD card.

Response shape:

```json
{
  "printerId": "p1",
  "files": [
    {
      "filename": "cube.gco",
      "sizeBytes": 12345,
      "rawLine": "CUBE.GCO 12345"
    }
  ],
  "rawResponse": "Begin file list..."
}
```

The endpoint also registers listed files into the local printer SD file registry.

---

## POST /printers/{printerId}/sd-card/uploads

Uploads a registered print file to the printer SD card.

Request body:

```json
{
  "printFileId": "file-1",
  "targetFilename": "cube.gco"
}
```

Response shape:

```json
{
  "printerId": "p1",
  "printFileId": "file-1",
  "originalFilename": "cube.gcode",
  "requestedTargetFilename": "cube.gco",
  "linkedFirmwarePath": "/cube.gco",
  "printerSdFileId": "sd-1",
  "uploadedLineCount": 1200,
  "totalLineCount": 1200,
  "totalByteCount": 98000,
  "rejectedLineCount": 0,
  "success": true,
  "detail": null
}
```

---

## GET /printers/{printerId}/sd-card/uploads/status

Returns active upload progress.

Response when idle:

```json
{
  "active": false,
  "state": "idle"
}
```

Response during upload:

```json
{
  "printerId": "p1",
  "printFileId": "file-1",
  "originalFilename": "cube.gcode",
  "requestedTargetFilename": "cube.gco",
  "state": "RUNNING",
  "active": true,
  "uploadedLineCount": 100,
  "totalLineCount": 1200,
  "totalByteCount": 98000,
  "rejectedLineCount": 2,
  "percent": 8,
  "qualityPercent": 98,
  "configuredMaxBatchSize": 5,
  "configuredMinBatchSize": 1,
  "activeBatchSize": 3,
  "batchUpgradeStep": 1,
  "batchDowngradeStep": 1,
  "stableLinesForUpgrade": 100,
  "acceptedLinesSinceLastResend": 34,
  "recentResendWindowLines": 200,
  "recentResendCount": 1,
  "resendThresholdForDowngrade": 3,
  "recoveryThresholdForMinBatch": 2,
  "recoveryCount": 0,
  "singleSendMode": false,
  "transportMode": "BATCH",
  "lastAdaptationReason": null,
  "lastAdaptationAt": null,
  "startedAt": "2026-05-28T12:00:00Z",
  "updatedAt": "2026-05-28T12:01:00Z",
  "detail": null,
  "bytesPerSecond": 1234.56,
  "linesPerSecond": 12.34,
  "elapsedSeconds": 60,
  "estimatedSecondsRemaining": 600,
  "theoreticalMaxBytesPerSecond": 4567.89,
  "efficiencyPercent": 72.50
}
```

---

## POST /printers/{printerId}/sd-card/recovery/close-upload

Attempts to close an open SD upload session.

Response shape:

```json
{
  "printerId": "p1",
  "lineNumber": 1234,
  "attempts": 2,
  "success": true,
  "response": "ok",
  "detail": null
}
```

---

# Print Files

Print files are files known to the local runtime. They may later be uploaded to a printer SD card.

## Print File Endpoints

```text
GET  /print-files
POST /print-files
POST /print-files/uploads?filename={filename}
GET  /print-files/{printFileId}
GET  /print-files/{printFileId}/content
```

---

## GET /print-files

Returns registered print files.

Response shape:

```json
{
  "printFiles": [
    {
      "id": "file-1",
      "originalFilename": "cube.gcode",
      "path": "/home/user/models/cube.gcode",
      "sizeBytes": 123456,
      "mediaType": "text/plain",
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## POST /print-files

Registers an existing host file.

Request body:

```json
{
  "path": "/home/user/models/cube.gcode"
}
```

---

## POST /print-files/uploads?filename={filename}

Uploads raw file content into the configured print-file storage directory.

Example:

```text
POST /print-files/uploads?filename=cube.gcode
```

The request body is the file content.

---

## GET /print-files/{printFileId}

Returns one registered print file.

---

## GET /print-files/{printFileId}/content

Returns one registered print file plus its text content.

Response shape:

```json
{
  "printFile": {
    "id": "file-1",
    "originalFilename": "cube.gcode",
    "path": "/home/user/models/cube.gcode",
    "sizeBytes": 123456,
    "mediaType": "text/plain",
    "createdAt": "2026-05-28T12:00:00Z"
  },
  "content": "G28\nM105\n..."
}
```

---

# Printer SD File Registry

The printer SD file registry stores files known to exist on a printer SD card.

## Printer SD File Registry Endpoints

```text
GET    /printer-sd-files
GET    /printer-sd-files?printerId={printerId}
POST   /printer-sd-files
GET    /printer-sd-files/{printerSdFileId}
POST   /printer-sd-files/{printerSdFileId}/enable
POST   /printer-sd-files/{printerSdFileId}/disable
DELETE /printer-sd-files/{printerSdFileId}
```

---

## GET /printer-sd-files

Returns all registered printer SD files.

Optional query:

```text
GET /printer-sd-files?printerId=p1
```

---

## POST /printer-sd-files

Registers a firmware SD file manually.

Request body:

```json
{
  "printerId": "p1",
  "firmwarePath": "/cube.gco",
  "displayName": "cube.gco",
  "sizeBytes": 123456,
  "rawLine": "CUBE.GCO 123456",
  "printFileId": "file-1"
}
```

Response shape:

```json
{
  "id": "sd-1",
  "printerId": "p1",
  "firmwarePath": "/cube.gco",
  "displayName": "cube.gco",
  "sizeBytes": 123456,
  "rawLine": "CUBE.GCO 123456",
  "printFileId": "file-1",
  "enabled": true,
  "deleted": false,
  "deletedAt": null,
  "createdAt": "2026-05-28T12:00:00Z",
  "updatedAt": "2026-05-28T12:00:00Z"
}
```

---

## GET /printer-sd-files/{printerSdFileId}

Returns one printer SD file registry entry.

---

## POST /printer-sd-files/{printerSdFileId}/enable

Enables a printer SD file for job creation.

---

## POST /printer-sd-files/{printerSdFileId}/disable

Disables a printer SD file.

---

## DELETE /printer-sd-files/{printerSdFileId}

Deletes the file from printer firmware SD card when possible, then marks the registry row deleted.

Response shape:

```json
{
  "id": "sd-1",
  "printerId": "p1",
  "firmwarePath": "/cube.gco",
  "displayName": "cube.gco",
  "deleted": true,
  "deletedAt": "2026-05-28T12:00:00Z"
}
```

---

# Jobs

## Job Endpoints

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

---

## GET /jobs

Returns recent jobs.

Response shape:

```json
{
  "jobs": [
    {
      "id": "job-1",
      "name": "Print cube.gco",
      "type": "PRINT_FILE",
      "state": "CREATED",
      "printerId": "p1",
      "printFileId": "file-1",
      "printerSdFileId": "sd-1",
      "targetTemperature": null,
      "fanSpeed": null,
      "failureReason": null,
      "failureDetail": null,
      "createdAt": "2026-05-28T12:00:00Z",
      "updatedAt": "2026-05-28T12:00:00Z",
      "startedAt": null,
      "finishedAt": null
    }
  ]
}
```

---

## POST /jobs

Creates a job.

Request body for SD-card print job:

```json
{
  "type": "PRINT_FILE",
  "printerId": "p1",
  "printFileId": "file-1",
  "printerSdFileId": "sd-1",
  "targetTemperature": 205,
  "fanSpeed": 100
}
```

`printerId` may be inferred from `printerSdFileId` for `PRINT_FILE` jobs.

---

## GET /jobs/{jobId}

Returns one job.

---

## DELETE /jobs/{jobId}

Deletes a job.

Response:

```json
{
  "deleted": "job-1"
}
```

---

## POST /jobs/{jobId}/start

Starts a job asynchronously.

Response shape:

```json
{
  "job": {
    "id": "job-1",
    "state": "QUEUED"
  },
  "execution": {
    "accepted": true,
    "success": true,
    "wireCommand": null,
    "response": null,
    "outcome": "QUEUED",
    "failureReason": null,
    "failureDetail": null
  }
}
```

---

## POST /jobs/{jobId}/pause

Pauses an autonomous print job when supported.

Response shape:

```json
{
  "job": {
    "id": "job-1",
    "state": "PAUSED"
  },
  "execution": {
    "accepted": true,
    "success": true,
    "wireCommand": "M25",
    "response": "ok",
    "outcome": "SUCCESS",
    "failureReason": null,
    "failureDetail": null
  }
}
```

---

## POST /jobs/{jobId}/resume

Resumes an autonomous print job when supported.

---

## POST /jobs/{jobId}/cancel

Cancels a job. For running autonomous print jobs, cancellation is routed through autonomous print control.

---

## POST /jobs/{jobId}/restart

Creates a new print job from a completed, failed, or cancelled `PRINT_FILE` job.

Response shape:

```json
{
  "sourceJobId": "job-1",
  "job": {
    "id": "job-2",
    "name": "Restart of Print cube.gco",
    "type": "PRINT_FILE",
    "state": "CREATED"
  }
}
```

---

## GET /jobs/{jobId}/events

Returns recent printer events linked to the job.

---

## GET /jobs/{jobId}/execution-steps

Returns execution steps for the job.

Response shape:

```json
{
  "executionSteps": [
    {
      "id": 1,
      "jobId": "job-1",
      "stepIndex": 1,
      "stepName": "START_SD_PRINT",
      "wireCommand": "M23 cube.gco",
      "response": "ok",
      "outcome": "SUCCESS",
      "success": true,
      "failureReason": null,
      "failureDetail": null,
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

# Monitoring

## GET /monitoring

Returns the global runtime monitoring snapshot.

Response shape:

```json
{
  "generatedAt": "2026-05-28T12:00:00Z",
  "summary": {
    "totalPrinters": 1,
    "enabledPrinters": 1,
    "disabledPrinters": 0,
    "busyPrinters": 0,
    "errorPrinters": 0,
    "activeJobs": 0,
    "activeUploads": 0
  },
  "printers": [
    {
      "id": "p1",
      "displayName": "Printer 1",
      "name": "Printer 1",
      "portName": "/dev/ttyUSB0",
      "mode": "serial",
      "serialPortKind": "stable-or-device-kind",
      "stableSerialPath": true,
      "serialPathWarning": null,
      "enabled": true,
      "state": "READY",
      "busy": false,
      "activeJobId": null,
      "errorMessage": null,
      "serialFailureType": null,
      "updatedAt": "2026-05-28T12:00:00Z"
    }
  ],
  "activeJobs": [],
  "activeUploads": []
}
```

---

# Settings

## Monitoring Settings

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Fields:

```text
pollIntervalSeconds
snapshotMinimumIntervalSeconds
temperatureDeltaThreshold
eventDeduplicationWindowSeconds
errorPersistenceBehavior
debugWireTracingEnabled
```

Example body:

```json
{
  "pollIntervalSeconds": 5,
  "snapshotMinimumIntervalSeconds": 30,
  "temperatureDeltaThreshold": 1.0,
  "eventDeduplicationWindowSeconds": 60,
  "errorPersistenceBehavior": "DEDUPLICATED",
  "debugWireTracingEnabled": false
}
```

---

## Print File Settings

```text
GET /settings/print-files
PUT /settings/print-files
```

Example body:

```json
{
  "storageDirectory": "spaghettichef-print-files"
}
```

---

## Serial Transfer Settings

```text
GET /settings/serial-transfer
PUT /settings/serial-transfer
```

Fields:

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

Example body:

```json
{
  "sdUploadBatchSize": 5,
  "sdUploadMinBatchSize": 1,
  "sdUploadBatchUpgradeStep": 1,
  "sdUploadBatchDowngradeStep": 1,
  "sdUploadStableLinesForUpgrade": 100,
  "sdUploadResendWindowLines": 200,
  "sdUploadResendThresholdForDowngrade": 3,
  "sdUploadRecoveryThresholdForMinBatch": 2,
  "sdUploadRecoveryWindowMultiplier": 4,
  "sdUploadMaxErrors": 20,
  "sdUploadMaxConsecutiveIdenticalResends": 3,
  "sdUploadMinPerformancePercent": 70,
  "sdUploadMaxRetriesPerLine": 5,
  "fileStreamingReadTimeoutMs": 5000,
  "fileStreamingQuietPeriodMs": 10,
  "fileStreamingReadActivitySleepMs": 5,
  "fileStreamingReadIdleSleepMs": 25,
  "fileStreamingRecoveryReplayDelayMs": 50
}
```

---

## Security Settings

```text
GET /settings/security
PUT /settings/security
```

Fields:

```text
securityEnabled
defaultRole
requireDangerousActionConfirmation
```

Example body:

```json
{
  "securityEnabled": true,
  "defaultRole": "OPERATOR",
  "requireDangerousActionConfirmation": true
}
```

---

# Security

## Security Endpoints

```text
GET /security/profile
GET /security/roles
PUT /security/roles
```

---

## GET /security/profile

Returns the active default role profile and security settings.

Response shape:

```json
{
  "settings": {
    "securityEnabled": true,
    "defaultRole": "OPERATOR",
    "requireDangerousActionConfirmation": true
  },
  "roleProfile": {
    "role": "OPERATOR",
    "displayName": "Operator",
    "builtIn": true,
    "permissions": [
      "VIEW_DASHBOARD"
    ]
  }
}
```

---

## GET /security/roles

Returns all role profiles.

Response shape:

```json
{
  "roleProfiles": [
    {
      "role": "ADMIN",
      "displayName": "Admin",
      "builtIn": true,
      "permissions": [
        "VIEW_DASHBOARD",
        "MANAGE_PRINTERS"
      ]
    }
  ]
}
```

---

## PUT /security/roles

Updates a role profile.

Request body:

```json
{
  "role": "OPERATOR",
  "displayName": "Operator",
  "permissions": [
    "VIEW_DASHBOARD"
  ]
}
```

---

# Operator Audit

## GET /operator-audit

Returns recent local operator audit events.

Response shape:

```json
{
  "auditEvents": [
    {
      "id": 1,
      "actor": "local-dashboard",
      "role": "ADMIN",
      "permission": "MANAGE_PRINTERS",
      "dangerousAction": null,
      "actionType": "POST /printers",
      "targetType": "printer",
      "targetId": "p1",
      "result": "ACCEPTED",
      "failureReason": null,
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

# Camera Runtime API

Camera runtime endpoints are printer-scoped and live under:

```text
/printers/{printerId}/camera
```

## Camera Runtime Endpoints

```text
GET  /printers/{printerId}/camera/status
GET  /printers/{printerId}/camera/settings
PUT  /printers/{printerId}/camera/settings
POST /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/events

POST /printers/{printerId}/camera/jobs/start
POST /printers/{printerId}/camera/jobs/stop
GET  /printers/{printerId}/camera/jobs/active

GET  /printers/{printerId}/camera/snapshots
GET  /printers/{printerId}/camera/snapshots/{fileId}

POST /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}
POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
```

---

## GET /printers/{printerId}/camera/status

Returns camera status.

Response shape:

```json
{
  "printerId": "p1",
  "enabled": true,
  "available": true,
  "sourceType": "ffmpeg",
  "sourceValue": "/dev/video0",
  "sourceDescription": "ffmpeg /dev/video0",
  "lastCaptureAt": "2026-05-28T12:00:00Z",
  "lastError": null
}
```

---

## GET /printers/{printerId}/camera/settings

Returns camera settings.

Supported `sourceType` values:

```text
disabled
simulated
snapshot-folder
ffmpeg
```

Response shape:

```json
{
  "printerId": "p1",
  "enabled": true,
  "sourceType": "ffmpeg",
  "sourceValue": "/dev/video0",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20,
  "analysisEnabled": true,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3,
  "ffmpegCommand": "ffmpeg",
  "ffmpegInputFormat": "v4l2",
  "ffmpegVideoSize": "640x480",
  "ffmpegTimeoutMs": 5000,
  "ffmpegJpegQuality": 3,
  "storageDirectory": "camera",
  "diagnosticLoggingEnabled": false,
  "purgeAutomatically": false,
  "purgeRetentionFrequency": 10,
  "captureCropEnabled": false,
  "captureCropX1Percent": 0,
  "captureCropY1Percent": 0,
  "captureCropX2Percent": 100,
  "captureCropY2Percent": 100,
  "updatedAt": "2026-05-28T12:00:00Z"
}
```

---

## PUT /printers/{printerId}/camera/settings

Updates camera settings.

Example Linux ffmpeg body:

```json
{
  "enabled": true,
  "sourceType": "ffmpeg",
  "sourceValue": "/dev/video0",
  "storageDirectory": "camera",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20,
  "analysisEnabled": true,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3,
  "ffmpegCommand": "ffmpeg",
  "ffmpegInputFormat": "v4l2",
  "ffmpegVideoSize": "640x480",
  "ffmpegTimeoutMs": 5000,
  "ffmpegJpegQuality": 3,
  "diagnosticLoggingEnabled": false,
  "purgeAutomatically": false,
  "purgeRetentionFrequency": 10,
  "captureCropEnabled": true,
  "captureCropX1Percent": 15,
  "captureCropY1Percent": 10,
  "captureCropX2Percent": 85,
  "captureCropY2Percent": 90
}
```

Example Windows ffmpeg body:

```json
{
  "enabled": true,
  "sourceType": "ffmpeg",
  "sourceValue": "video=PC-LM1E Camera",
  "storageDirectory": "camera",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20,
  "analysisEnabled": true,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3,
  "ffmpegCommand": "ffmpeg",
  "ffmpegInputFormat": "dshow",
  "ffmpegVideoSize": "640x480",
  "ffmpegTimeoutMs": 5000,
  "ffmpegJpegQuality": 3,
  "diagnosticLoggingEnabled": false,
  "purgeAutomatically": false,
  "purgeRetentionFrequency": 10,
  "captureCropEnabled": false,
  "captureCropX1Percent": 0,
  "captureCropY1Percent": 0,
  "captureCropX2Percent": 100,
  "captureCropY2Percent": 100
}
```

---

## POST /printers/{printerId}/camera/snapshot

Captures one diagnostic snapshot.

Response shape:

```json
{
  "success": true,
  "error": null,
  "hasFrame": true,
  "message": "camera frame captured",
  "frame": {
    "printerId": "p1",
    "capturedAt": "2026-05-28T12:00:00Z",
    "contentType": "image/jpeg",
    "byteCount": 18234,
    "width": 640,
    "height": 480,
    "sourceDescription": "ffmpeg /dev/video0"
  }
}
```

Failed captures may return:

```http
409 Conflict
```

with:

```json
{
  "success": false,
  "error": "camera_capture_failed",
  "hasFrame": false,
  "message": "Camera capture failed",
  "frame": null
}
```

---

## GET /printers/{printerId}/camera/snapshot

Returns the latest camera snapshot as image bytes.

---

## GET /printers/{printerId}/camera/events

Returns recent camera events.

Response shape:

```json
[
  {
    "id": 1,
    "printerId": "p1",
    "cameraJobId": 12,
    "eventType": "CAMERA_CAPTURED",
    "message": "Snapshot captured",
    "confidence": null,
    "createdAt": "2026-05-28T12:00:00Z"
  }
]
```

---

## POST /printers/{printerId}/camera/jobs/start

Starts a camera monitoring job for the printer.

Response shape:

```json
{
  "printerId": "p1",
  "active": true,
  "monitoring": true,
  "jobId": "12",
  "state": "RUNNING",
  "linkedPrintJobId": null,
  "analysisSessionId": null,
  "startedAt": "2026-05-28T12:00:00Z",
  "stoppedAt": null,
  "captureIntervalSeconds": 10,
  "retainedSnapshots": 0,
  "sourceType": "ffmpeg",
  "sourceDescription": "/dev/video0",
  "snapshotDirectory": "camera/p1",
  "message": null,
  "latestSnapshotAvailable": true,
  "latestSnapshotId": "100",
  "latestSnapshotVersion": "100",
  "latestCaptureAt": "2026-05-28T12:00:00Z",
  "latestContentType": "image/jpeg",
  "latestWidth": 640,
  "latestHeight": 480
}
```

---

## POST /printers/{printerId}/camera/jobs/stop

Stops the active camera monitoring job.

If automatic purge is enabled in camera settings, snapshot purge may be executed after the job stops.

---

## GET /printers/{printerId}/camera/jobs/active

Returns the active camera job state.

Idle response shape:

```json
{
  "printerId": "p1",
  "active": false,
  "monitoring": false,
  "jobId": null,
  "state": "IDLE",
  "linkedPrintJobId": null,
  "analysisSessionId": null,
  "startedAt": null,
  "stoppedAt": null,
  "captureIntervalSeconds": 0,
  "retainedSnapshots": 0,
  "sourceType": null,
  "sourceDescription": null,
  "snapshotDirectory": null,
  "message": null,
  "latestSnapshotAvailable": false,
  "latestSnapshotId": null,
  "latestSnapshotVersion": null,
  "latestCaptureAt": null,
  "latestContentType": null,
  "latestWidth": 0,
  "latestHeight": 0
}
```

---

## GET /printers/{printerId}/camera/snapshots

Lists snapshot files for a printer.

Optional query parameters:

```text
from={isoInstant}
to={isoInstant}
```

Example:

```text
GET /printers/p1/camera/snapshots?from=2026-05-28T10:00:00Z&to=2026-05-28T12:00:00Z
```

Response shape:

```json
{
  "files": [
    {
      "id": "snapshot-file-id",
      "type": "snapshot",
      "fileName": "000001.jpg",
      "relativePath": "snapshots/1/000001.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 18234,
      "modifiedAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## GET /printers/{printerId}/camera/snapshots/{fileId}

Returns a snapshot file as image bytes.

---

# Camera Analysis Sessions

## POST /printers/{printerId}/camera/analysis-sessions

Starts a camera analysis session.

Response shape:

```json
{
  "id": "camera-analysis-1",
  "printerId": "p1",
  "state": "RUNNING",
  "startedAt": "2026-05-28T12:00:00Z",
  "stoppedAt": null,
  "createdAt": "2026-05-28T12:00:00Z",
  "updatedAt": "2026-05-28T12:00:00Z",
  "message": "Camera analysis session started"
}
```

---

## GET /printers/{printerId}/camera/analysis-sessions

Lists camera analysis sessions for a printer.

Response shape:

```json
{
  "sessions": [
    {
      "id": "camera-analysis-1",
      "printerId": "p1",
      "state": "RUNNING",
      "startedAt": "2026-05-28T12:00:00Z",
      "stoppedAt": null,
      "createdAt": "2026-05-28T12:00:00Z",
      "updatedAt": "2026-05-28T12:00:00Z",
      "message": "Camera analysis session started"
    }
  ]
}
```

---

## GET /printers/{printerId}/camera/analysis-sessions/{sessionId}

Returns one analysis session.

---

## POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop

Stops an analysis session.

---

## GET /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples

Returns recent analysis samples.

Optional query:

```text
limit={positiveInteger}
```

Maximum effective limit:

```text
500
```

Response shape:

```json
{
  "samples": [
    {
      "id": 1,
      "sessionId": "camera-analysis-1",
      "printerId": "p1",
      "capturedAt": "2026-05-28T12:01:00Z",
      "analyzedAt": "2026-05-28T12:01:00Z",
      "latestSnapshotPath": "camera/p1/latest.jpg",
      "previousSnapshotPath": "camera/p1/previous.jpg",
      "deltaSnapshotPath": "camera/p1/delta.jpg",
      "deltaScore": 0.12,
      "changedPixelRatio": 0.08,
      "averagePixelDelta": 0.21,
      "confidence": 0.74,
      "suspected": true,
      "reasonCodes": "[HIGH_DELTA_SCORE, HIGH_CHANGED_PIXEL_RATIO]",
      "message": "Possible spaghetti failure detected"
    }
  ]
}
```

---

## POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples

Captures and analyzes one sample for the session.

Response shape:

```json
{
  "id": 1,
  "sessionId": "camera-analysis-1",
  "printerId": "p1",
  "capturedAt": "2026-05-28T12:01:00Z",
  "analyzedAt": "2026-05-28T12:01:00Z",
  "latestSnapshotPath": "camera/p1/latest.jpg",
  "previousSnapshotPath": "camera/p1/previous.jpg",
  "deltaSnapshotPath": "camera/p1/delta.jpg",
  "deltaScore": 0.12,
  "changedPixelRatio": 0.08,
  "averagePixelDelta": 0.21,
  "confidence": 0.74,
  "suspected": true,
  "reasonCodes": "[HIGH_DELTA_SCORE, HIGH_CHANGED_PIXEL_RATIO]",
  "message": "Possible spaghetti failure detected"
}
```

---

# Camera Admin API

Camera admin endpoints are used for snapshot jobs, retained snapshots, delta sets, delta frames, calculation runs, calculation results, traceability, comparison, and cleanup.

They live under:

```text
/admin/camera
```

---

## Camera Admin Snapshot Job Endpoints

```text
GET    /admin/camera/snapshot/jobs
GET    /admin/camera/snapshot/jobs?printerId={printerId}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}
DELETE /admin/camera/snapshot/jobs/{cameraJobKey}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}/timeline
POST   /admin/camera/snapshot/jobs/{cameraJobId}/purge
GET    /admin/camera/snapshot/files/{snapshotEntryId}
POST   /admin/camera/storage/{printerId}/sync
```

---

## GET /admin/camera/snapshot/jobs

Lists camera snapshot jobs.

Optional query:

```text
printerId={printerId}
```

Response shape:

```json
{
  "jobs": [
    {
      "id": 12,
      "cameraJobId": 12,
      "cameraJobKey": "camera-job-12",
      "jobId": "camera-job-12",
      "printerId": "p1",
      "linkedPrintJobId": "job-1",
      "state": "RUNNING",
      "startedAt": "2026-05-28T12:00:00Z",
      "stoppedAt": null,
      "captureIntervalSeconds": 10,
      "retainedSnapshots": 200,
      "sourceType": "ffmpeg",
      "sourceDescription": "/dev/video0",
      "snapshotDirectory": "camera/p1/snapshots/12",
      "fileCount": 200,
      "totalBytes": 12345678,
      "firstCapturedAt": "2026-05-28T12:00:00Z",
      "lastCapturedAt": "2026-05-28T12:30:00Z"
    }
  ]
}
```

---

## GET /admin/camera/snapshot/jobs/{cameraJobKey}

Lists snapshot entries for one camera job key.

Optional query:

```text
printerId={printerId}
```

Response shape:

```json
{
  "jobId": "camera-job-12",
  "entries": [
    {
      "id": 100,
      "type": "snapshot",
      "printerId": "p1",
      "cameraJobId": 12,
      "cameraJobKey": "camera-job-12",
      "linkedPrintJobId": "job-1",
      "jobId": "job-1",
      "jobKey": "camera-job-12",
      "snapshotPath": "camera/p1/snapshots/12/000100.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 18234,
      "capturedAt": "2026-05-28T12:00:00Z",
      "retainedAt": "2026-05-28T12:00:00Z",
      "sourceType": "ffmpeg",
      "message": null,
      "fileDeleted": false,
      "deletedAt": null,
      "deletionReason": null
    }
  ]
}
```

---

## DELETE /admin/camera/snapshot/jobs/{cameraJobKey}

Deletes snapshots for one camera job key.

Optional query:

```text
printerId={printerId}
```

Response shape:

```json
{
  "jobId": "camera-job-12",
  "deletedFiles": 200,
  "deletedBytes": 12345678,
  "deletedMetadataRows": 200,
  "failedFiles": [],
  "message": "deleted"
}
```

---

## GET /admin/camera/snapshot/jobs/{cameraJobKey}/timeline

Returns timeline entries for one camera job key.

Optional query:

```text
printerId={printerId}
```

Response shape:

```json
{
  "jobId": "camera-job-12",
  "timeline": [
    {
      "id": 100,
      "type": "snapshot",
      "printerId": "p1",
      "cameraJobId": 12,
      "cameraJobKey": "camera-job-12",
      "snapshotPath": "camera/p1/snapshots/12/000100.jpg",
      "capturedAt": "2026-05-28T12:00:00Z",
      "fileDeleted": false
    }
  ]
}
```

---

## POST /admin/camera/snapshot/jobs/{cameraJobId}/purge

Purges snapshots from a camera job according to retention rules.

Optional query:

```text
printerId={printerId}
```

If `printerId` is not provided in the query, it must be provided in the body.

Request body:

```json
{
  "printerId": "p1",
  "retentionSnapshotCount": 200,
  "purgeRetentionFrequency": 10,
  "message": "manual snapshot purge"
}
```

Response shape:

```json
{
  "printerId": "p1",
  "cameraJobId": 12,
  "totalSnapshotCount": 1000,
  "keptSnapshotCount": 200,
  "purgeCandidateCount": 800,
  "deletedSnapshotCount": 800,
  "alreadyDeletedSnapshotCount": 0,
  "failedSnapshotCount": 0,
  "retentionSnapshotCount": 200,
  "purgeRetentionFrequency": 10,
  "deletedSnapshotIds": [1, 2, 3],
  "failedSnapshotIds": [],
  "message": "manual snapshot purge"
}
```

---

## GET /admin/camera/snapshot/files/{snapshotEntryId}

Returns one retained snapshot file as image bytes.

Possible errors:

```text
400 invalid_camera_snapshot_entry_id
404 camera_snapshot_entry_not_found
410 camera_snapshot_file_deleted
404 camera_snapshot_file_not_found
```

---

## POST /admin/camera/storage/{printerId}/sync

Reconciles configured camera storage files with camera database rows.

The endpoint loads camera settings for `{printerId}` from the database and scans the configured camera storage directory. It does not read dataset manifests, dataset labels, or dataset job metadata.

Request shape:

```json
{
  "layout": "runtime-camera-storage",
  "dryRun": true,
  "syncSnapshots": true,
  "syncDeltas": true,
  "deleteRowsForMissingFiles": false,
  "reactivateDeletedSnapshotRows": true,
  "createMissingCameraJobs": true,
  "createMissingDeltaSets": true,
  "requiredConfirmation": "SYNC_CAMERA_DATASET"
}
```

For real writes, set `dryRun` to `false` and include `requiredConfirmation`.

---

# Camera Admin Job Deletion

```text
DELETE /admin/camera/jobs/{cameraJobId}?printerId={printerId}
```

Deletes a camera job and its related snapshot, delta, calculation, and event data according to request flags.

Request body:

```json
{
  "deleteSnapshotFiles": true,
  "deleteSnapshotRows": true,
  "deleteDeltaFiles": true,
  "deleteDeltaRows": true,
  "deleteCalculationRuns": true,
  "deleteCameraEvents": true,
  "deleteCameraJob": true,
  "requiredConfirmation": "DELETE_CAMERA_JOB"
}
```

Response shape:

```json
{
  "printerId": "p1",
  "cameraJobId": 12,
  "deletedSnapshotFiles": 200,
  "deletedSnapshotBytes": 12345678,
  "deletedSnapshotRows": 200,
  "deletedDeltaFiles": 100,
  "deletedDeltaBytes": 2345678,
  "deletedDeltaRows": 100,
  "deletedDeltaSetRows": 1,
  "deletedCalculationRunRows": 2,
  "deletedCalculationResultRows": 200,
  "deletedCameraEventRows": 10,
  "deletedCameraJobRows": 1,
  "failedFiles": [],
  "message": "deleted"
}
```

---

# Camera Admin Delta Sets

## Delta Set Endpoints

```text
GET    /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets
POST   /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets

GET    /admin/camera/delta-sets/{deltaSetId}
DELETE /admin/camera/delta-sets/{deltaSetId}?printerId={printerId}
GET    /admin/camera/delta-sets/{deltaSetId}/frames
GET    /admin/camera/delta-sets/{deltaSetId}/calculation-runs
POST   /admin/camera/delta-sets/{deltaSetId}/calculation-runs
```

---

## GET /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets

Lists delta sets for a camera job.

Response shape:

```json
{
  "deltaSets": [
    {
      "id": 1,
      "printerId": "p1",
      "cameraJobId": 12,
      "methodName": "image-delta",
      "deltaSnapshotStep": 1,
      "sourceSnapshotCount": 200,
      "generatedDeltaCount": 199,
      "createdAt": "2026-05-28T12:00:00Z",
      "message": "step 1 delta set"
    }
  ]
}
```

---

## POST /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets

Generates a delta set from retained snapshots.

Optional query:

```text
printerId={printerId}
```

If `printerId` is not provided in the query, it must be provided in the body.

Request body:

```json
{
  "printerId": "p1",
  "deltaSnapshotStep": 1,
  "methodName": "image-delta",
  "message": "step 1 delta set"
}
```

Response shape:

```json
{
  "deltaSet": {
    "id": 1,
    "printerId": "p1",
    "cameraJobId": 12,
    "methodName": "image-delta",
    "deltaSnapshotStep": 1,
    "sourceSnapshotCount": 200,
    "generatedDeltaCount": 199,
    "createdAt": "2026-05-28T12:00:00Z",
    "message": "step 1 delta set"
  },
  "sourceSnapshotCount": 200,
  "generatedDeltaCount": 199,
  "skippedIntermediateSnapshotCount": 0
}
```

---

## GET /admin/camera/delta-sets/{deltaSetId}

Returns one delta set.

Response shape:

```json
{
  "deltaSet": {
    "id": 1,
    "printerId": "p1",
    "cameraJobId": 12,
    "methodName": "image-delta",
    "deltaSnapshotStep": 1,
    "sourceSnapshotCount": 200,
    "generatedDeltaCount": 199,
    "createdAt": "2026-05-28T12:00:00Z",
    "message": "step 1 delta set"
  }
}
```

---

## DELETE /admin/camera/delta-sets/{deltaSetId}?printerId={printerId}

Deletes a delta set and optionally its related rows/files/runs.

Request body:

```json
{
  "deleteDeltaFiles": true,
  "deleteDeltaRows": true,
  "deleteCalculationRuns": true,
  "requiredConfirmation": "DELETE_CAMERA_DELTA_SET"
}
```

Response shape:

```json
{
  "printerId": "p1",
  "cameraJobId": 12,
  "deltaSetId": 1,
  "deletedDeltaFiles": 199,
  "deletedDeltaBytes": 2345678,
  "deletedDeltaRows": 199,
  "deletedDeltaSetRows": 1,
  "deletedCalculationRunRows": 2,
  "deletedCalculationResultRows": 398,
  "failedFiles": [],
  "message": "deleted"
}
```

---

## GET /admin/camera/delta-sets/{deltaSetId}/frames

Lists delta frames for a delta set.

Response shape:

```json
{
  "deltaSetId": 1,
  "frames": [
    {
      "id": 10,
      "deltaSetId": 1,
      "printerId": "p1",
      "cameraJobId": 12,
      "fromSnapshotId": 100,
      "toSnapshotId": 101,
      "fromCapturedAt": "2026-05-28T12:00:00Z",
      "toCapturedAt": "2026-05-28T12:00:10Z",
      "deltaPath": "camera/p1/deltas/1/000100_000101_delta.jpg",
      "deltaScore": 0.12,
      "changedPixelRatio": 0.08,
      "averagePixelDelta": 0.21,
      "createdAt": "2026-05-28T12:00:10Z"
    }
  ]
}
```

---

## GET /admin/camera/delta-frames/{deltaFrameId}/file?printerId={printerId}

Returns one delta-frame file as image bytes.

Possible errors:

```text
400 printerId is required
404 camera_delta_frame_not_found
404 camera_delta_file_not_found
```

---

# Camera Admin Calculation Runs

## Calculation Run Endpoints

```text
GET  /admin/camera/delta-sets/{deltaSetId}/calculation-runs
POST /admin/camera/delta-sets/{deltaSetId}/calculation-runs

GET  /admin/camera/calculation-runs/{calculationRunId}
GET  /admin/camera/calculation-runs/{calculationRunId}/results
GET  /admin/camera/calculation-runs/{calculationRunId}/trace
GET  /admin/camera/calculation-runs/{calculationRunId}/compare?rightRunId={rightRunId}
```

---

## GET /admin/camera/delta-sets/{deltaSetId}/calculation-runs

Lists calculation runs for a delta set.

Response shape:

```json
{
  "deltaSetId": 1,
  "calculationRuns": [
    {
      "id": 20,
      "printerId": "p1",
      "cameraJobId": 12,
      "deltaSetId": 1,
      "methodName": "basic-delta",
      "engineName": "java",
      "algorithmVariant": "default",
      "engineVersion": "0.7.0",
      "executionDurationMs": 500,
      "engineStatus": "COMPLETED",
      "parameterJson": "{}",
      "createdAt": "2026-05-28T12:00:00Z",
      "resultCount": 199,
      "message": "baseline run"
    }
  ]
}
```

---

## POST /admin/camera/delta-sets/{deltaSetId}/calculation-runs

Runs a calculation over all delta frames of a delta set.

Request body:

```json
{
  "methodName": "basic-delta",
  "confidenceThreshold": 0.85,
  "parameterJson": "{\"changedPixelRatioThreshold\":0.08}",
  "message": "baseline run",
  "engineName": "java",
  "rustExecutablePath": "/home/user/spaghettichef-engine"
}
```

Response shape:

```json
{
  "calculationRun": {
    "id": 20,
    "printerId": "p1",
    "cameraJobId": 12,
    "deltaSetId": 1,
    "methodName": "basic-delta",
    "engineName": "java",
    "algorithmVariant": "default",
    "engineVersion": "0.7.0",
    "executionDurationMs": 500,
    "engineStatus": "COMPLETED",
    "parameterJson": "{}",
    "createdAt": "2026-05-28T12:00:00Z",
    "resultCount": 199,
    "message": "baseline run"
  }
}
```

---

## GET /admin/camera/calculation-runs/{calculationRunId}

Returns one calculation run.

Response shape:

```json
{
  "calculationRun": {
    "id": 20,
    "printerId": "p1",
    "cameraJobId": 12,
    "deltaSetId": 1,
    "methodName": "basic-delta",
    "engineName": "java",
    "algorithmVariant": "default",
    "engineVersion": "0.7.0",
    "executionDurationMs": 500,
    "engineStatus": "COMPLETED",
    "parameterJson": "{}",
    "createdAt": "2026-05-28T12:00:00Z",
    "resultCount": 199,
    "message": "baseline run"
  }
}
```

---

## GET /admin/camera/calculation-runs/{calculationRunId}/results

Returns calculation results for a run.

Response shape:

```json
{
  "calculationRunId": 20,
  "results": [
    {
      "id": 30,
      "calculationRunId": 20,
      "deltaFrameId": 10,
      "confidence": 0.74,
      "suspected": true,
      "reasonCodes": "[HIGH_DELTA_SCORE]",
      "message": "possible spaghetti",
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## GET /admin/camera/calculation-runs/{calculationRunId}/trace

Returns trace rows for a calculation run.

Optional query:

```text
printerId={printerId}
```

Response shape:

```json
{
  "calculationRunId": 20,
  "trace": [
    {
      "cameraJobId": 12,
      "deltaSetId": 1,
      "deltaFrameId": 10,
      "calculationRunId": 20,
      "calculationResultId": 30,
      "fromSnapshotPath": "camera/p1/snapshots/12/000100.jpg",
      "toSnapshotPath": "camera/p1/snapshots/12/000101.jpg",
      "deltaPath": "camera/p1/deltas/1/000100_000101_delta.jpg",
      "confidence": 0.74,
      "suspected": true,
      "reasonCodes": "[HIGH_DELTA_SCORE]",
      "message": "possible spaghetti",
      "createdAt": "2026-05-28T12:00:00Z"
    }
  ]
}
```

---

## GET /admin/camera/calculation-runs/{calculationRunId}/compare?rightRunId={rightRunId}

Compares two calculation runs.

Optional query:

```text
printerId={printerId}
```

Required query:

```text
rightRunId={rightCalculationRunId}
```

Response shape:

```json
{
  "left": {
    "run": {
      "id": 20,
      "methodName": "basic-delta"
    },
    "resultCount": 199,
    "suspectedCount": 20,
    "averageConfidence": 0.42,
    "resultsPerSecond": 300.0,
    "averageMillisecondsPerFrame": 3.33
  },
  "right": {
    "run": {
      "id": 21,
      "methodName": "experimental-delta"
    },
    "resultCount": 199,
    "suspectedCount": 15,
    "averageConfidence": 0.38,
    "resultsPerSecond": 250.0,
    "averageMillisecondsPerFrame": 4.0
  },
  "comparedFrameCount": 199,
  "suspectedMismatchCount": 7,
  "averageAbsoluteConfidenceDifference": 0.12,
  "frames": [
    {
      "deltaFrameId": 10,
      "leftResultId": 30,
      "rightResultId": 31,
      "leftConfidence": 0.74,
      "rightConfidence": 0.40,
      "confidenceDifference": 0.34,
      "leftSuspected": true,
      "rightSuspected": false,
      "suspectedMismatch": true,
      "leftReasonCodes": "[HIGH_DELTA_SCORE]",
      "rightReasonCodes": "[LOW_LOCAL_SCORE]"
    }
  ]
}
```

---

# Camera Admin Calculation Result Visual

```text
GET /admin/camera/calculation-results/{calculationResultId}/visual?printerId={printerId}
```

Returns a calculation result together with the related run, delta frame, source snapshots, and image URLs.

Response shape:

```json
{
  "calculationResult": {
    "id": 30,
    "calculationRunId": 20,
    "deltaFrameId": 10,
    "confidence": 0.74,
    "suspected": true,
    "reasonCodes": "[HIGH_DELTA_SCORE]",
    "message": "possible spaghetti",
    "createdAt": "2026-05-28T12:00:00Z"
  },
  "calculationRun": {
    "id": 20,
    "printerId": "p1",
    "cameraJobId": 12,
    "deltaSetId": 1,
    "methodName": "basic-delta",
    "engineName": "java",
    "algorithmVariant": "default",
    "engineVersion": "0.7.0",
    "executionDurationMs": 500,
    "engineStatus": "COMPLETED",
    "parameterJson": "{}",
    "createdAt": "2026-05-28T12:00:00Z",
    "resultCount": 199,
    "message": "baseline run"
  },
  "deltaFrame": {
    "id": 10,
    "deltaSetId": 1,
    "printerId": "p1",
    "cameraJobId": 12,
    "fromSnapshotId": 100,
    "toSnapshotId": 101,
    "fromCapturedAt": "2026-05-28T12:00:00Z",
    "toCapturedAt": "2026-05-28T12:00:10Z",
    "deltaPath": "camera/p1/deltas/1/000100_000101_delta.jpg",
    "deltaScore": 0.12,
    "changedPixelRatio": 0.08,
    "averagePixelDelta": 0.21,
    "createdAt": "2026-05-28T12:00:10Z"
  },
  "fromSnapshot": {
    "id": 100,
    "type": "snapshot",
    "printerId": "p1",
    "cameraJobId": 12,
    "cameraJobKey": "camera-job-12",
    "snapshotPath": "camera/p1/snapshots/12/000100.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 18234,
    "capturedAt": "2026-05-28T12:00:00Z",
    "retainedAt": "2026-05-28T12:00:00Z",
    "sourceType": "ffmpeg",
    "message": null,
    "fileDeleted": false,
    "deletedAt": null,
    "deletionReason": null
  },
  "toSnapshot": {
    "id": 101,
    "type": "snapshot",
    "printerId": "p1",
    "cameraJobId": 12,
    "cameraJobKey": "camera-job-12",
    "snapshotPath": "camera/p1/snapshots/12/000101.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 18234,
    "capturedAt": "2026-05-28T12:00:10Z",
    "retainedAt": "2026-05-28T12:00:10Z",
    "sourceType": "ffmpeg",
    "message": null,
    "fileDeleted": false,
    "deletedAt": null,
    "deletionReason": null
  },
  "imageUrls": {
    "fromSnapshot": "/admin/camera/snapshot/files/100",
    "toSnapshot": "/admin/camera/snapshot/files/101",
    "deltaFrame": "/admin/camera/delta-frames/10/file?printerId=p1"
  }
}
```

---

# Placeholder Endpoint

## POST /admin/camera/snapshot/jobs/{cameraJobKey}/recalculate-preview

This endpoint currently returns a placeholder response.

Response:

```json
{
  "jobId": "camera-job-12",
  "state": "placeholder",
  "message": "camera_recalculate_preview_not_implemented"
}
```

---

# Condensed Endpoint Table

```text
GET    /health
GET    /version

GET    /dashboard
GET    /dashboard/
GET    /dashboard/{resourcePath}

GET    /printers
POST   /printers
GET    /printers/{printerId}
PUT    /printers/{printerId}
DELETE /printers/{printerId}
POST   /printers/{printerId}/enable
POST   /printers/{printerId}/disable
GET    /printers/{printerId}/status
GET    /printers/{printerId}/events
POST   /printers/{printerId}/commands

GET    /printers/{printerId}/sd-card/files
POST   /printers/{printerId}/sd-card/uploads
GET    /printers/{printerId}/sd-card/uploads/status
POST   /printers/{printerId}/sd-card/recovery/close-upload

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

GET    /printers/{printerId}/camera/status
GET    /printers/{printerId}/camera/settings
PUT    /printers/{printerId}/camera/settings
POST   /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/events

POST   /printers/{printerId}/camera/jobs/start
POST   /printers/{printerId}/camera/jobs/stop
GET    /printers/{printerId}/camera/jobs/active

GET    /printers/{printerId}/camera/snapshots
GET    /printers/{printerId}/camera/snapshots/{fileId}

POST   /printers/{printerId}/camera/analysis-sessions
GET    /printers/{printerId}/camera/analysis-sessions
GET    /printers/{printerId}/camera/analysis-sessions/{sessionId}
POST   /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop
GET    /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
POST   /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples

GET    /admin/camera/snapshot/jobs
GET    /admin/camera/snapshot/jobs?printerId={printerId}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}
DELETE /admin/camera/snapshot/jobs/{cameraJobKey}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}/timeline
POST   /admin/camera/snapshot/jobs/{cameraJobId}/purge
GET    /admin/camera/snapshot/files/{snapshotEntryId}
POST   /admin/camera/storage/{printerId}/sync

DELETE /admin/camera/jobs/{cameraJobId}?printerId={printerId}

GET    /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets
POST   /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets

GET    /admin/camera/delta-sets/{deltaSetId}
DELETE /admin/camera/delta-sets/{deltaSetId}?printerId={printerId}
GET    /admin/camera/delta-sets/{deltaSetId}/frames
GET    /admin/camera/delta-sets/{deltaSetId}/calculation-runs
POST   /admin/camera/delta-sets/{deltaSetId}/calculation-runs

GET    /admin/camera/delta-frames/{deltaFrameId}/file?printerId={printerId}

GET    /admin/camera/calculation-runs/{calculationRunId}
GET    /admin/camera/calculation-runs/{calculationRunId}/results
GET    /admin/camera/calculation-runs/{calculationRunId}/trace
GET    /admin/camera/calculation-runs/{calculationRunId}/compare?rightRunId={rightRunId}

GET    /admin/camera/calculation-results/{calculationResultId}/visual?printerId={printerId}

POST   /admin/camera/snapshot/jobs/{cameraJobKey}/recalculate-preview
```
