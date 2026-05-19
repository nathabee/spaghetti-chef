import { escapeHtml, formatDateTime } from "../utils/format.js";
import { cameraSnapshotUrl } from "../api.js";

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
          </select>
        </label>

        <label>
          Source value
          <input
            id="cameraSourceValueInput"
            name="sourceValue"
            type="text"
            value="${escapeHtml(settings?.sourceValue || "")}"
            placeholder="default or data/camera/p1">
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

export function renderCameraSnapshotCard(printerId, status) {
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

export function renderCameraAnalysisCard(printerId, sessions, samples) {
  const safeSessions = Array.isArray(sessions) ? sessions : [];
  const activeSession = safeSessions.find((session) => session.state === "RUNNING");
  const selectedSession = activeSession || safeSessions[0];
  const safeSamples = Array.isArray(samples) ? samples : [];
  const selectedSample = safeSamples[safeSamples.length - 1];

  return `
    <article class="section-card camera-analysis-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Analysis session</div>
          <h3>Spaghetti trace review</h3>
        </div>
        <div class="action-row">
          <button type="button" data-camera-analysis-start="${escapeHtml(printerId)}" ${activeSession ? "disabled" : ""}>Start</button>
          <button type="button" class="secondary-button" data-camera-analysis-sample="${escapeHtml(printerId)}" data-session-id="${escapeHtml(activeSession?.id || "")}" ${activeSession ? "" : "disabled"}>Sample</button>
          <button type="button" class="secondary-button" data-camera-analysis-stop="${escapeHtml(printerId)}" data-session-id="${escapeHtml(activeSession?.id || "")}" ${activeSession ? "" : "disabled"}>Stop</button>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">Active state</span>
          <strong>${formatNullable(activeSession?.state || "idle")}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">Samples</span>
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

      ${renderAnalysisGraph(safeSamples)}
      ${renderTimelineScrubber(safeSamples)}

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

function renderAnalysisGraph(samples) {
  if (samples.length === 0) {
    return `
      <div class="empty-state">
        <h4>No analysis samples</h4>
        <p class="muted">Start a session to capture reviewable spaghetti detector values.</p>
      </div>
    `;
  }

  const points = samples.map((sample, index) => {
    const x = samples.length === 1 ? 4 : 4 + (index / (samples.length - 1)) * 92;
    const confidenceY = 96 - Number(sample.confidence || 0) * 92;
    const deltaY = 96 - Number(sample.deltaScore || 0) * 92;
    return { x, confidenceY, deltaY, suspected: sample.suspected };
  });

  const confidenceLine = points.map((point) => `${point.x.toFixed(2)},${point.confidenceY.toFixed(2)}`).join(" ");
  const deltaLine = points.map((point) => `${point.x.toFixed(2)},${point.deltaY.toFixed(2)}`).join(" ");

  return `
    <svg class="camera-analysis-graph" viewBox="0 0 100 100" role="img" aria-label="Camera analysis confidence and delta score graph">
      <polyline points="${confidenceLine}" fill="none" stroke="currentColor" stroke-width="2"></polyline>
      <polyline points="${deltaLine}" fill="none" stroke="#b56d18" stroke-width="2"></polyline>
      ${points.map((point) => `<circle cx="${point.x.toFixed(2)}" cy="${point.confidenceY.toFixed(2)}" r="2.2" class="${point.suspected ? "suspected" : ""}"></circle>`).join("")}
    </svg>
  `;
}

function renderTimelineScrubber(samples) {
  if (samples.length === 0) {
    return "";
  }

  const encodedSamples = escapeHtml(JSON.stringify(samples));

  return `
    <label class="camera-analysis-slider">
      Timeline
      <input id="cameraAnalysisTimelineInput" type="range" min="0" max="${samples.length - 1}" value="${samples.length - 1}" step="1" data-analysis-samples="${encodedSamples}">
    </label>
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

export function renderCameraPage(printerId, status, settings, events, sessions, samples) {
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
        ${renderCameraSnapshotCard(printerId, status)}
      </section>

      <section class="two-column-grid">
        ${renderCameraSettingsCard(settings)}
        ${renderCameraEventsCard(events)}
      </section>

      <section>
        ${renderCameraAnalysisCard(printerId, sessions, samples)}
      </section>
    </div>
  `;
}
