# PrinterHub REST API

This document is a practical endpoint reference for the current local PrinterHub API.

The API and dashboard are served from the same host and port.

Example:

```text
http://localhost:18080
```

Most endpoints return JSON. Snapshot image endpoints return image bytes.

---

## Health And Version

```text
GET /health
GET /version
```

`GET /health` returns:

```json
{"status":"ok"}
```

`GET /version` returns the runtime application version:

```json
{"version":"0.4.5"}
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
POST   /printers/{printerId}/commands
```

Example create/update body:

```json
{
  "id": "p1",
  "displayName": "Printer 1",
  "portName": "COM3",
  "mode": "serial",
  "enabled": true
}
```

Manual command example:

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

---

## Printer SD Card

```text
GET  /printers/{printerId}/sd-card/files
POST /printers/{printerId}/sd-card/uploads
GET  /printers/{printerId}/sd-card/uploads/status
POST /printers/{printerId}/sd-card/recovery/close-upload
```

Upload body:

```json
{
  "printFileId": "file-1",
  "targetPath": "/model.gco"
}
```

The upload status endpoint reports active progress and transfer diagnostics.

---

## Camera Status, Settings, Snapshot, Events

```text
GET  /printers/{printerId}/camera/status
GET  /printers/{printerId}/camera/settings
PUT  /printers/{printerId}/camera/settings
POST /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/snapshot
GET  /printers/{printerId}/camera/events
GET  /printers/{printerId}/camera/archive?from={isoInstant}&to={isoInstant}
GET  /printers/{printerId}/camera/archive/{fileId}
```

Supported `sourceType` values:

```text
disabled
simulated
snapshot-folder
ffmpeg
```

Example simulated settings:

```json
{
  "enabled": true,
  "sourceType": "simulated",
  "sourceValue": "default",
  "storageDirectory": "camera",
  "captureIntervalSeconds": 10,
  "retentionSnapshotCount": 20,
  "analysisEnabled": false,
  "safetyEnabled": false,
  "pauseOnConfirmedSpaghetti": false,
  "confidenceThreshold": 0.85,
  "confirmationsRequired": 3,
  "ffmpegCommand": "ffmpeg",
  "ffmpegInputFormat": "",
  "ffmpegVideoSize": "640x480",
  "ffmpegTimeoutMs": 5000,
  "ffmpegJpegQuality": 3
}
```

Example Windows ffmpeg settings:

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
  "ffmpegJpegQuality": 3
}
```

Example Linux ffmpeg settings:

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
  "ffmpegJpegQuality": 3
}
```

Capture response example:

```json
{
  "success": true,
  "hasFrame": true,
  "message": "camera frame captured",
  "contentType": "image/jpeg",
  "width": 320,
  "height": 240
}
```

If capture fails, the response and camera events include the failure detail.

Archive listing response example:

```json
{
  "files": [
    {
      "id": "YXJjaGl2ZS8yMDI2LTA1LTIwVDExLTMxLTI1Wi5qcGc",
      "type": "archive",
      "fileName": "2026-05-20T11-31-25Z.jpg",
      "relativePath": "archive/2026-05-20T11-31-25Z.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 18234,
      "modifiedAt": "2026-05-20T11:31:25Z"
    }
  ]
}
```

The archive endpoint only lists files under the selected printer camera directory. Use the returned `id` with `/camera/archive/{fileId}` to open an image without exposing raw absolute paths.

---

## Camera Analysis Sessions

```text
POST /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}
POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop
GET  /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
POST /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
```

Start response example:

```json
{
  "id": "camera-analysis-...",
  "printerId": "p1",
  "state": "RUNNING",
  "startedAt": "2026-05-20T10:00:00Z",
  "stoppedAt": null,
  "createdAt": "2026-05-20T10:00:00Z",
  "updatedAt": "2026-05-20T10:00:00Z",
  "message": "Camera analysis session started"
}
```

Sample response example:

```json
{
  "id": 1,
  "sessionId": "camera-analysis-...",
  "printerId": "p1",
  "capturedAt": "2026-05-20T10:01:00Z",
  "analyzedAt": "2026-05-20T10:01:00Z",
  "latestSnapshotPath": "C:\\printerhub\\data\\camera\\p1\\latest.jpg",
  "previousSnapshotPath": "C:\\printerhub\\data\\camera\\p1\\previous.jpg",
  "deltaSnapshotPath": "C:\\printerhub\\data\\camera\\p1\\delta.jpg",
  "deltaScore": 0.12,
  "changedPixelRatio": 0.08,
  "averagePixelDelta": 0.21,
  "confidence": 0.74,
  "suspected": true,
  "reasonCodes": "[HIGH_DELTA_SCORE, HIGH_CHANGED_PIXEL_RATIO]",
  "message": "Possible spaghetti failure detected"
}
```

The dashboard currently displays samples as a table. The future graph X axis is `capturedAt`; useful Y series are `confidence`, `deltaScore`, `changedPixelRatio`, and `averagePixelDelta`.

---

## Print Files

```text
GET  /print-files
POST /print-files
POST /print-files/uploads?filename={filename}
GET  /print-files/{printFileId}
GET  /print-files/{printFileId}/content
```

Register existing file body:

```json
{
  "path": "/home/user/models/cube.gcode"
}
```

Upload content with:

```text
POST /print-files/uploads?filename=cube.gcode
```

The request body is the file content.

---

## Printer SD File Registry

```text
GET    /printer-sd-files
GET    /printer-sd-files?printerId={printerId}
POST   /printer-sd-files
GET    /printer-sd-files/{printerSdFileId}
POST   /printer-sd-files/{printerSdFileId}/enable
POST   /printer-sd-files/{printerSdFileId}/disable
DELETE /printer-sd-files/{printerSdFileId}
```

Create body:

```json
{
  "printerId": "p1",
  "path": "/cube.gco",
  "displayName": "cube.gco",
  "enabled": true
}
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

Create body example:

```json
{
  "printerId": "p1",
  "type": "PRINT_FILE",
  "printFileId": "file-1",
  "printerSdFileId": "sd-1"
}
```

Start is asynchronous. Inspect job state, events, and execution steps for progress.

---

## Monitoring

```text
GET /monitoring
```

Returns a global runtime snapshot:

* fleet summary
* printer snapshots
* active jobs
* active upload status

---

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

---

## Print File Settings

```text
GET /settings/print-files
PUT /settings/print-files
```

Example body:

```json
{
  "storageDirectory": "printerhub-print-files"
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

---

## Security

```text
GET /settings/security
PUT /settings/security
GET /security/profile
GET /security/roles
PUT /security/roles
```

Security settings fields:

```text
securityEnabled
defaultRole
requireDangerousActionConfirmation
```

---

## Operator Audit

```text
GET /operator-audit
```

Returns recent local operator audit events.

---

## Condensed Endpoint Table

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

GET    /printers/{printerId}/camera/status
GET    /printers/{printerId}/camera/settings
PUT    /printers/{printerId}/camera/settings
POST   /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/snapshot
GET    /printers/{printerId}/camera/events
GET    /printers/{printerId}/camera/archive
GET    /printers/{printerId}/camera/archive/{fileId}
POST   /printers/{printerId}/camera/analysis-sessions
GET    /printers/{printerId}/camera/analysis-sessions
GET    /printers/{printerId}/camera/analysis-sessions/{sessionId}
POST   /printers/{printerId}/camera/analysis-sessions/{sessionId}/stop
GET    /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples
POST   /printers/{printerId}/camera/analysis-sessions/{sessionId}/samples

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
