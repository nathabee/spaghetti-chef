import { escapeHtml, formatDateTime } from "../utils/format.js";
import { cameraSnapshotFileUrl, cameraSnapshotUrl } from "../api.js";

function formatBoolean(value) {
  return value ? "Yes" : "No";
}

function formatNullable(value) {
  if (value === null || value === undefined || value === "") {
    return "—";
  }

  return escapeHtml(value);
}

function cameraBadge(status) {
  if (!status || !status.enabled) {
    return '<span class="badge badge-disabled">Camera disabled</span>';
  }

  if (status.available) {
    return '<span class="badge badge-enabled">Camera active</span>';
  }

  return '<span class="badge status-error">Camera unavailable</span>';
}

export function renderCameraStatusCard(status) {
  return `
    <article class="section-card camera-status-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Camera</div>
          <h3>Visual monitoring status</h3>
        </div>
        ${cameraBadge(status)}
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">Enabled</span>
          <strong>${formatBoolean(status?.enabled)}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Available</span>
          <strong>${formatBoolean(status?.available)}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Source type</span>
          <strong>${formatNullable(status?.sourceType)}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Last capture</span>
          <strong>${status?.lastCaptureAt ? escapeHtml(formatDateTime(status.lastCaptureAt)) : "—"}</strong>
        </div>
      </div>

      <dl class="detail-list">
        <div>
          <dt>Source value</dt>
          <dd>${formatNullable(status?.sourceValue)}</dd>
        </div>
        <div>
          <dt>Source description</dt>
          <dd>${formatNullable(status?.sourceDescription)}</dd>
        </div>
      </dl>

      ${
        status?.lastError
          ? `<p class="error-text">${escapeHtml(status.lastError)}</p>`
          : ""
      }
    </article>
  `;
}

export function renderCameraSettingsCard(settings) {
  const sourceType = settings?.sourceType || "disabled";

  return `
    <article class="section-card camera-settings-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Settings</div>
          <h3>Camera configuration</h3>
          <p class="muted">Configure this printer's camera source and optional 0.4.2 frame analysis.</p>
        </div>
      </div>

      <form id="cameraSettingsForm" class="form-grid">
        <label class="checkbox-label">
          <input id="cameraEnabledInput" name="enabled" type="checkbox" ${settings?.enabled ? "checked" : ""}>
          Enable camera monitoring
        </label>

        <label class="checkbox-label">
          <input id="cameraAnalysisEnabledInput" name="analysisEnabled" type="checkbox" ${settings?.analysisEnabled ? "checked" : ""}>
          Enable frame analysis
        </label>

        <label class="checkbox-label">
          <input id="cameraSafetyEnabledInput" name="safetyEnabled" type="checkbox" ${settings?.safetyEnabled ? "checked" : ""}>
          Enable safety decisions
        </label>

        <label class="checkbox-label">
          <input id="cameraPauseOnConfirmedInput" name="pauseOnConfirmedSpaghetti" type="checkbox" ${settings?.pauseOnConfirmedSpaghetti ? "checked" : ""}>
          Pause on confirmed spaghetti
        </label>

        <label>
          Source type
          <select id="cameraSourceTypeInput" name="sourceType">
            <option value="disabled" ${sourceType === "disabled" ? "selected" : ""}>disabled</option>
            <option value="simulated" ${sourceType === "simulated" ? "selected" : ""}>simulated</option>
            <option value="snapshot-folder" ${sourceType === "snapshot-folder" ? "selected" : ""}>snapshot-folder</option>
            <option value="ffmpeg" ${sourceType === "ffmpeg" ? "selected" : ""}>ffmpeg webcam</option>
          </select>
        </label>

        <label>
          Storage directory
          <input
            id="cameraStorageDirectoryInput"
            name="storageDirectory"
            type="text"
            value="${escapeHtml(settings?.storageDirectory || "camera")}"
            placeholder="camera or C:\\printerhub\\data\\camera">
        </label>

        <label>
          Source value
          <input
            id="cameraSourceValueInput"
            name="sourceValue"
            type="text"
            value="${escapeHtml(settings?.sourceValue || "")}"
            placeholder="/dev/video0 or video=Integrated Camera">
        </label>

        <label>
          ffmpeg command
          <input
            id="cameraFfmpegCommandInput"
            name="ffmpegCommand"
            type="text"
            value="${escapeHtml(settings?.ffmpegCommand || "ffmpeg")}"
            placeholder="ffmpeg">
        </label>

        <label>
          ffmpeg input format
          <input
            id="cameraFfmpegInputFormatInput"
            name="ffmpegInputFormat"
            type="text"
            value="${escapeHtml(settings?.ffmpegInputFormat || "")}"
            placeholder="v4l2 or dshow">
        </label>

        <label>
          ffmpeg video size
          <input
            id="cameraFfmpegVideoSizeInput"
            name="ffmpegVideoSize"
            type="text"
            value="${escapeHtml(settings?.ffmpegVideoSize || "640x480")}"
            placeholder="640x480">
        </label>

        <label>
          ffmpeg timeout ms
          <input
            id="cameraFfmpegTimeoutMsInput"
            name="ffmpegTimeoutMs"
            type="number"
            step="100"
            min="100"
            value="${escapeHtml(settings?.ffmpegTimeoutMs ?? 5000)}">
        </label>

        <label>
          ffmpeg JPEG quality
          <input
            id="cameraFfmpegJpegQualityInput"
            name="ffmpegJpegQuality"
            type="number"
            step="1"
            min="1"
            value="${escapeHtml(settings?.ffmpegJpegQuality ?? 3)}">
        </label>

        <label>
          Capture interval seconds
          <input
            id="cameraCaptureIntervalSecondsInput"
            name="captureIntervalSeconds"
            type="number"
            step="1"
            min="1"
            value="${escapeHtml(settings?.captureIntervalSeconds ?? 10)}">
        </label>

        <label>
          Retained snapshots
          <input
            id="cameraRetentionSnapshotCountInput"
            name="retentionSnapshotCount"
            type="number"
            step="1"
            min="1"
            value="${escapeHtml(settings?.retentionSnapshotCount ?? 20)}">
        </label>

        <label>
          Confidence threshold
          <input
            id="cameraConfidenceThresholdInput"
            name="confidenceThreshold"
            type="number"
            step="0.01"
            min="0.01"
            max="1"
            value="${escapeHtml(settings?.confidenceThreshold ?? 0.85)}">
        </label>

        <label>
          Confirmations required
          <input
            id="cameraConfirmationsRequiredInput"
            name="confirmationsRequired"
            type="number"
            step="1"
            min="1"
            value="${escapeHtml(settings?.confirmationsRequired ?? 3)}">
        </label>

        <div class="form-actions">
          <button type="submit">Save camera settings</button>
        </div>
      </form>
    </article>
  `;
}

export function renderCameraSnapshotCard(printerId, status, settings) {
  const captureIntervalSeconds = positiveInteger(settings?.captureIntervalSeconds, 10);

  return `
    <article class="section-card camera-snapshot-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Snapshot</div>
          <h3>Latest camera frame</h3>
        </div>
        <div class="action-row">
          <button type="button" data-camera-capture="${escapeHtml(printerId)}">Capture now</button>
          <button type="button" class="secondary-button" data-camera-refresh="${escapeHtml(printerId)}">Refresh</button>
          <button type="button" class="secondary-button" data-camera-sync-start="${escapeHtml(printerId)}" data-camera-capture-interval="${captureIntervalSeconds}">Sync</button>
          <button type="button" class="secondary-button" data-camera-sync-stop="${escapeHtml(printerId)}">Stop sync</button>
        </div>
      </div>

      ${
        status?.lastCaptureAt
          ? `
            <div class="camera-snapshot-frame">
              <img
                src="${cameraSnapshotUrl(printerId)}"
                alt="Latest camera snapshot for ${escapeHtml(printerId)}"
                loading="lazy">
            </div>
          `
          : `
            <div class="empty-state">
              <h4>No snapshot yet</h4>
              <p class="muted">Capture a frame to create the first camera snapshot for this printer.</p>
            </div>
          `
      }
    </article>
  `;
}

export function renderCameraEventsCard(events) {
  const safeEvents = Array.isArray(events) ? events : [];

  return `
    <article class="section-card camera-events-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Events</div>
          <h3>Recent camera activity</h3>
        </div>
      </div>

      ${
        safeEvents.length === 0
          ? `
            <div class="empty-state">
              <h4>No camera events</h4>
              <p class="muted">Camera activity appears here after status checks or captures.</p>
            </div>
          `
          : `
            <div class="event-list">
              ${safeEvents.map(renderCameraEvent).join("")}
            </div>
          `
      }
    </article>
  `;
}

export function renderCameraAnalysisCard(printerId, sessions, samples, captureIntervalSeconds = 10) {
  const safeSessions = Array.isArray(sessions) ? sessions : [];
  const activeSession = safeSessions.find((session) => session.state === "RUNNING");
  const selectedSession = activeSession || safeSessions[0];
  const safeSamples = Array.isArray(samples) ? samples : [];
  const selectedSample = safeSamples[0];
  const safeCaptureIntervalSeconds = positiveInteger(captureIntervalSeconds, 10);

  return `
    <article
      class="section-card camera-analysis-card"
      data-camera-analysis-printer-id="${escapeHtml(printerId)}"
      data-camera-analysis-active-session="${escapeHtml(activeSession?.id || "")}"
      data-camera-capture-interval="${safeCaptureIntervalSeconds}">
      <div class="section-header compact">
        <div>
          <div class="kicker">Analysis session</div>
          <h3>Spaghetti trace review</h3>
        </div>
        <div class="action-row">
          <button type="button" data-camera-analysis-start="${escapeHtml(printerId)}" ${activeSession ? "disabled" : ""}>Start</button>
          <button type="button" class="secondary-button" data-camera-analysis-sample="${escapeHtml(printerId)}" data-session-id="${escapeHtml(activeSession?.id || "")}" ${activeSession ? "" : "disabled"}>Sample now</button>
          <button type="button" class="secondary-button" data-camera-analysis-stop="${escapeHtml(printerId)}" data-session-id="${escapeHtml(activeSession?.id || "")}" ${activeSession ? "" : "disabled"}>Stop</button>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">Active state</span>
          <strong>${formatNullable(activeSession?.state || "idle")}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Recent samples</span>
          <strong>${safeSamples.length}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Confidence</span>
          <strong>${formatRatio(selectedSample?.confidence)}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Delta score</span>
          <strong>${formatRatio(selectedSample?.deltaScore)}</strong>
        </div>
      </div>

      ${renderAnalysisSamplesTable(safeSamples)}

      <dl class="detail-list">
        <div>
          <dt>Selected sample</dt>
          <dd>${selectedSample?.capturedAt ? escapeHtml(formatDateTime(selectedSample.capturedAt)) : "—"}</dd>
        </div>
        <div>
          <dt>State</dt>
          <dd>${selectedSample ? (selectedSample.suspected ? "Suspicious" : "Good") : "—"}</dd>
        </div>
        <div>
          <dt>Reason codes</dt>
          <dd id="cameraAnalysisReasonCodes">${formatNullable(selectedSample?.reasonCodes)}</dd>
        </div>
        <div>
          <dt>Message</dt>
          <dd id="cameraAnalysisMessage">${formatNullable(selectedSample?.message)}</dd>
        </div>
      </dl>

      <div class="camera-analysis-snapshots">
        <div>
          <span class="metric-label">Latest snapshot path</span>
          <code id="cameraAnalysisLatestPath">${formatNullable(selectedSample?.latestSnapshotPath)}</code>
        </div>
        <div>
          <span class="metric-label">Delta snapshot path</span>
          <code id="cameraAnalysisDeltaPath">${formatNullable(selectedSample?.deltaSnapshotPath)}</code>
        </div>
      </div>

      ${renderSessionsList(safeSessions, selectedSession)}
    </article>
  `;
}

function renderAnalysisSamplesTable(samples) {
  if (samples.length === 0) {
    return `
      <div class="empty-state">
        <h4>No analysis samples</h4>
        <p class="muted">Start a session to capture reviewable spaghetti detector values.</p>
      </div>
    `;
  }

  return `
    <div class="table-wrap">
      <p class="muted table-note">Newest samples are shown first. The dashboard loads a recent window only, so long print jobs stay responsive.</p>
      <table class="data-table camera-analysis-table">
        <thead>
          <tr>
            <th>Captured at</th>
            <th>Analyzed at</th>
            <th>State</th>
            <th>Confidence</th>
            <th>Delta score</th>
            <th>Changed pixels</th>
            <th>Average delta</th>
            <th>Reason codes</th>
            <th>Message</th>
            <th>Latest snapshot</th>
            <th>Previous snapshot</th>
            <th>Delta snapshot</th>
          </tr>
        </thead>
        <tbody>
          ${samples.map(renderAnalysisSampleRow).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return parsed;
}

function renderAnalysisSampleRow(sample) {
  return `
    <tr class="${sample.suspected ? "analysis-suspected" : "analysis-good"}">
      <td>${sample.capturedAt ? escapeHtml(formatDateTime(sample.capturedAt)) : "—"}</td>
      <td>${sample.analyzedAt ? escapeHtml(formatDateTime(sample.analyzedAt)) : "—"}</td>
      <td>${sample.suspected ? '<span class="badge status-error">Suspicious</span>' : '<span class="badge badge-enabled">Good</span>'}</td>
      <td>${formatRatio(sample.confidence)}</td>
      <td>${formatRatio(sample.deltaScore)}</td>
      <td>${formatRatio(sample.changedPixelRatio)}</td>
      <td>${formatRatio(sample.averagePixelDelta)}</td>
      <td>${formatNullable(sample.reasonCodes)}</td>
      <td>${formatNullable(sample.message)}</td>
      <td><code>${formatNullable(sample.latestSnapshotPath)}</code></td>
      <td><code>${formatNullable(sample.previousSnapshotPath)}</code></td>
      <td><code>${formatNullable(sample.deltaSnapshotPath)}</code></td>
    </tr>
  `;
}

function renderSessionsList(sessions, selectedSession) {
  if (sessions.length === 0) {
    return "";
  }

  return `
    <div class="event-list compact-list">
      ${sessions.slice(0, 6).map((session) => `
        <article class="event-item ${session.id === selectedSession?.id ? "selected" : ""}">
          <div>
            <strong>${formatNullable(session.state)}</strong>
            <p>${escapeHtml(session.startedAt ? formatDateTime(session.startedAt) : formatDateTime(session.createdAt))}</p>
          </div>
          <time>${session.stoppedAt ? escapeHtml(formatDateTime(session.stoppedAt)) : "active"}</time>
        </article>
      `).join("")}
    </div>
  `;
}

export function renderCameraSnapshotCard(printerId, files, snapshotRange = {}) {
  const safeFiles = Array.isArray(files) ? files : [];

  return `
    <article class="section-card camera-snapshot-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Camera files</div>
          <h3>Snapshot snapshot</h3>
          <p class="muted">Review files written by the camera backend for the selected time window.</p>
        </div>
      </div>

      <form id="cameraSnapshotForm" class="inline-form">
        <label>
          Start
          <input id="cameraSnapshotFromInput" name="from" type="datetime-local" value="${escapeHtml(datetimeLocalValue(snapshotRange.from))}">
        </label>
        <label>
          Stop
          <input id="cameraSnapshotToInput" name="to" type="datetime-local" value="${escapeHtml(datetimeLocalValue(snapshotRange.to))}">
        </label>
        <button type="submit">List files</button>
      </form>

      ${renderSnapshotGallery(printerId, safeFiles)}
    </article>
  `;
}

function renderSnapshotGallery(printerId, files) {
  if (files.length === 0) {
    return `
      <div class="empty-state">
        <h4>No camera files</h4>
        <p class="muted">Capture a snapshot or widen the time window.</p>
      </div>
    `;
  }

  const previewFiles = files.slice(0, 3);

  return `
    <div class="camera-snapshot-gallery">
      <div id="cameraSnapshotFileList" class="camera-snapshot-list" tabindex="0">
        ${files.map((file, index) => renderSnapshotFileListItem(printerId, file, index)).join("")}
      </div>
      <div class="camera-snapshot-preview-stack">
        ${[0, 1, 2].map((slot) => renderSnapshotPreviewSlot(previewFiles[slot], printerId, slot)).join("")}
      </div>
    </div>
  `;
}

function renderSnapshotFileListItem(printerId, file, index) {
  const fileUrl = cameraSnapshotFileUrl(printerId, file.id);

  return `
    <button
      type="button"
      class="camera-snapshot-list-item ${index === 0 ? "selected" : ""}"
      data-camera-snapshot-select
      data-camera-snapshot-index="${index}"
      data-camera-snapshot-url="${escapeHtml(fileUrl)}"
      data-camera-snapshot-path="${escapeHtml(file.relativePath || file.fileName || "")}"
      data-camera-snapshot-type="${escapeHtml(file.type || "")}"
      data-camera-snapshot-time="${escapeHtml(file.modifiedAt || "")}"
      data-camera-snapshot-size="${escapeHtml(String(file.sizeBytes ?? ""))}">
      <span>
        <strong>${formatNullable(file.fileName)}</strong>
        <code>${formatNullable(file.relativePath)}</code>
      </span>
      <span class="camera-snapshot-list-meta">
        ${formatNullable(file.type)} · ${file.modifiedAt ? escapeHtml(formatDateTime(file.modifiedAt)) : "—"} · ${formatBytes(file.sizeBytes)}
      </span>
    </button>
  `;
}

function renderSnapshotPreviewSlot(file, printerId, slot) {
  const label = slot === 0 ? "Selected picture" : `Previous picture ${slot}`;
  const fileUrl = file ? cameraSnapshotFileUrl(printerId, file.id) : "";

  return `
    <figure class="camera-snapshot-preview" data-camera-snapshot-preview-slot="${slot}" ${file ? "" : "hidden"}>
      <figcaption>
        <span>${label}</span>
        <code data-camera-snapshot-preview-title="${slot}">${file ? formatNullable(file.relativePath) : "—"}</code>
      </figcaption>
      <img
        data-camera-snapshot-preview-image="${slot}"
        src="${escapeHtml(fileUrl)}"
        alt="${file ? `Camera snapshot file ${escapeHtml(file.relativePath || file.fileName || "")}` : ""}"
        loading="lazy">
    </figure>
  `;
}

function formatBytes(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes < 0) {
    return "—";
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function datetimeLocalValue(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toISOString().slice(0, 16);
}

function formatRatio(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "—";
  }
  return `${Math.round(numeric * 100)}%`;
}

function renderCameraEvent(event) {
  return `
    <article class="event-item">
      <div>
        <strong>${formatNullable(event.eventType)}</strong>
        <p>${formatNullable(event.message)}</p>
      </div>
      <time>${event.createdAt ? escapeHtml(formatDateTime(event.createdAt)) : "—"}</time>
    </article>
  `;
}

export function renderCameraPage(printerId, status, settings, events, sessions, samples, snapshotFiles, snapshotRange) {
  return `
    <div class="view printer-camera-view">
      <div class="section-header">
        <div>
          <div class="kicker">Selected printer</div>
          <h2>Camera monitoring</h2>
          <p class="lead">
            Capture and inspect printer-side snapshots without coupling camera logic to serial communication or job execution.
          </p>
        </div>
      </div>

      <section class="two-column-grid">
        ${renderCameraStatusCard(status)}
        ${renderCameraSnapshotCard(printerId, status, settings)}
      </section>

      <section class="two-column-grid">
        ${renderCameraSettingsCard(settings)}
        ${renderCameraEventsCard(events)}
      </section>

      <section>
        ${renderCameraAnalysisCard(printerId, sessions, samples, settings?.captureIntervalSeconds)}
      </section>

      <section>
        ${renderCameraSnapshotCard(printerId, snapshotFiles, snapshotRange)}
      </section>
    </div>
  `;
}
