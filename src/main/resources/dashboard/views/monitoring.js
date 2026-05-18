import { escapeHtml } from "../dashboard.js";
import { serialPathWarning, serialPortKind, stableSerialPath } from "../components/serial-port-guidance.js";
import { state } from "../state.js";

export function renderMonitoringPage() {
  const overview = state.monitoringOverview;

  if (!overview) {
    return `
      <section class="section-card">
        <div class="section-header compact">
          <div>
            <div class="kicker">Monitoring</div>
            <h2>Global runtime overview</h2>
            <p class="lead">Runtime aggregation across configured printers.</p>
          </div>
          <button type="button" data-refresh-monitoring-overview>Refresh monitoring</button>
        </div>
        <div class="empty-state">
          <h3>No monitoring snapshot loaded</h3>
          <p class="muted">Refresh monitoring to load the current farm runtime state.</p>
        </div>
      </section>
    `;
  }

  const summary = overview.summary || {};
  const printers = Array.isArray(overview.printers) ? overview.printers : [];
  const activeJobs = Array.isArray(overview.activeJobs) ? overview.activeJobs : [];
  const activeUploads = Array.isArray(overview.activeUploads) ? overview.activeUploads : [];

  return `
    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Monitoring</div>
          <h2>Global runtime overview</h2>
          <p class="lead">Runtime aggregation across configured printers. Upload workflow controls remain on each printer SD Card page.</p>
        </div>
        <button type="button" data-refresh-monitoring-overview>Refresh monitoring</button>
      </div>
      <div class="monitoring-refresh-line">
        Snapshot generated at ${escapeHtml(formatDateTime(overview.generatedAt))}
      </div>

      <div class="monitoring-summary-grid">
        ${renderSummaryTile("Total printers", summary.totalPrinters)}
        ${renderSummaryTile("Enabled", summary.enabledPrinters)}
        ${renderSummaryTile("Disabled", summary.disabledPrinters)}
        ${renderSummaryTile("Busy", summary.busyPrinters)}
        ${renderSummaryTile("Errors", summary.errorPrinters)}
        ${renderSummaryTile("Active jobs", summary.activeJobs)}
        ${renderSummaryTile("Active uploads", summary.activeUploads)}
      </div>
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Jobs</div>
          <h3>Active and recent jobs</h3>
        </div>
      </div>
      ${activeJobs.length === 0 ? renderMonitoringEmpty("No active or recent job in the monitoring snapshot.") : renderJobsTable(activeJobs)}
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">SD uploads</div>
          <h3>Active upload telemetry</h3>
        </div>
      </div>
      ${activeUploads.length === 0 ? renderMonitoringEmpty("No active or last-known upload telemetry in memory.") : renderUploadsTable(activeUploads)}
    </section>

    <details class="section-card" open>
      <summary class="events-header">
        <span>Adaptive transfer diagnostics</span>
      </summary>
      ${activeUploads.length === 0 ? renderMonitoringEmpty("Adaptive upload diagnostics will appear when upload telemetry exists.") : renderAdaptiveTable(activeUploads)}
    </details>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Printers</div>
          <h3>Runtime states</h3>
        </div>
      </div>
      ${printers.length === 0 ? renderMonitoringEmpty("No configured printers.") : renderPrintersTable(printers)}
    </section>
  `;
}

function renderSummaryTile(label, value) {
  return `
    <div class="monitoring-summary-tile">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(String(value ?? 0))}</strong>
    </div>
  `;
}

function renderJobsTable(jobs) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Job</th>
            <th>Type</th>
            <th>Printer</th>
            <th>State</th>
            <th>Updated</th>
            <th>Failure</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${jobs.map((job) => `
            <tr>
              <td>${escapeHtml(job.name || job.id)}</td>
              <td>${escapeHtml(job.type || "n/a")}</td>
              <td>${escapeHtml(resolvePrinterName(job.printerId))}</td>
              <td><span class="badge ${resolveStateBadgeClass(job.state)}">${escapeHtml(job.state || "n/a")}</span></td>
              <td>${escapeHtml(formatDateTime(job.updatedAt))}</td>
              <td>${escapeHtml(job.failureReason || job.failureDetail || "none")}</td>
              <td>
                <button
                  type="button"
                  class="secondary-button small-button"
                  data-monitoring-sync-job="${escapeHtml(job.id)}"
                  data-printer-id="${escapeHtml(job.printerId || "")}"
                >Synchronize</button>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderUploadsTable(uploads) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Printer</th>
            <th>Target</th>
            <th>State</th>
            <th>Progress</th>
            <th>Speed</th>
            <th>ETA</th>
            <th>Quality</th>
            <th>Batch</th>
            <th>Mode</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${uploads.map((upload) => `
            <tr>
              <td>${escapeHtml(resolvePrinterName(upload.printerId))}</td>
              <td>${escapeHtml(upload.requestedTargetFilename || upload.originalFilename || "n/a")}</td>
              <td><span class="badge ${resolveStateBadgeClass(upload.state)}">${escapeHtml(upload.state || "n/a")}</span></td>
              <td>${escapeHtml(`${upload.uploadedLineCount ?? 0}/${upload.totalLineCount ?? "n/a"} (${upload.percent ?? 0}%)`)}</td>
              <td>${escapeHtml(`${formatDecimal(upload.bytesPerSecond, 1)} B/s · ${formatDecimal(upload.linesPerSecond, 2)} lines/s`)}</td>
              <td>${escapeHtml(formatDuration(upload.estimatedSecondsRemaining))}</td>
              <td>${escapeHtml(`${upload.qualityPercent ?? "n/a"}% · ${upload.rejectedLineCount ?? 0} resends`)}</td>
              <td>${escapeHtml(`${upload.activeBatchSize ?? "n/a"}/${upload.configuredMaxBatchSize ?? "n/a"}`)}</td>
              <td>${escapeHtml(upload.transportMode || "n/a")}</td>
              <td>
                <button
                  type="button"
                  class="secondary-button small-button"
                  data-monitoring-sync-upload="${escapeHtml(upload.printerId || "")}"
                >Synchronize</button>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderAdaptiveTable(uploads) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Printer</th>
            <th>Active batch</th>
            <th>Range</th>
            <th>Stable lines</th>
            <th>Resend pressure</th>
            <th>Recovery threshold</th>
            <th>Last adaptation</th>
          </tr>
        </thead>
        <tbody>
          ${uploads.map((upload) => `
            <tr>
              <td>${escapeHtml(resolvePrinterName(upload.printerId))}</td>
              <td>${escapeHtml(upload.activeBatchSize ?? "n/a")}</td>
              <td>${escapeHtml(`${upload.configuredMinBatchSize ?? "n/a"}-${upload.configuredMaxBatchSize ?? "n/a"}`)}</td>
              <td>${escapeHtml(`${upload.acceptedLinesSinceLastResend ?? 0}/${upload.stableLinesForUpgrade ?? "n/a"}`)}</td>
              <td>${escapeHtml(`${upload.recentResendCount ?? 0}/${upload.resendThresholdForDowngrade ?? "n/a"}`)}</td>
              <td>${escapeHtml(upload.recoveryThresholdForMinBatch ?? "n/a")}</td>
              <td>${escapeHtml(upload.lastAdaptationReason || "n/a")}<br><span class="muted">${escapeHtml(formatDateTime(upload.lastAdaptationAt))}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderPrintersTable(printers) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Printer</th>
            <th>Port</th>
            <th>Enabled</th>
            <th>State</th>
            <th>Busy</th>
            <th>Active job</th>
            <th>Serial failure</th>
            <th>Updated</th>
            <th>Error</th>
          </tr>
        </thead>
        <tbody>
          ${printers.map((printer) => `
            <tr>
              <td>${escapeHtml(printer.displayName || printer.id)}</td>
              <td>
                ${escapeHtml(printer.portName || "n/a")}
                <br><span class="muted">${escapeHtml(printer.serialPortKind || serialPortKind(printer.mode, printer.portName))}</span>
                ${stableSerialPath(printer.mode, printer.portName, printer.stableSerialPath) ? "" : `<br><span class="status-warn">${escapeHtml(serialPathWarning(printer.mode, printer.portName, printer.serialPathWarning) || "Unstable serial path")}</span>`}
              </td>
              <td>${escapeHtml(printer.enabled === true ? "yes" : "no")}</td>
              <td><span class="badge ${resolveStateBadgeClass(printer.state)}">${escapeHtml(printer.state || "UNKNOWN")}</span></td>
              <td>${escapeHtml(printer.busy === true ? "yes" : "no")}</td>
              <td>${escapeHtml(printer.activeJobId || "none")}</td>
              <td>${escapeHtml(printer.serialFailureType || "none")}</td>
              <td>${escapeHtml(formatDateTime(printer.updatedAt))}</td>
              <td>${escapeHtml(printer.errorMessage || "none")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderMonitoringEmpty(message) {
  return `
    <div class="empty-state compact-empty">
      <h3>${escapeHtml(message)}</h3>
    </div>
  `;
}

function resolvePrinterName(printerId) {
  if (!printerId) {
    return "none";
  }

  const printer = state.printers.find((candidate) => candidate.id === printerId)
    || state.monitoringOverview?.printers?.find((candidate) => candidate.id === printerId);
  return printer?.displayName || printer?.name || printerId;
}

function resolveStateBadgeClass(stateValue) {
  const stateText = String(stateValue || "").toUpperCase();

  if (stateText.includes("ERROR") || stateText.includes("FAILED") || stateText.includes("DISCONNECTED")) {
    return "badge-sim";
  }
  if (stateText.includes("RUNNING") || stateText.includes("QUEUED") || stateText.includes("PAUSED")) {
    return "badge-real";
  }
  return "badge-disabled";
}

function formatDecimal(value, digits) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue.toFixed(digits) : "n/a";
}

function formatDuration(seconds) {
  const numericSeconds = Number(seconds);

  if (!Number.isFinite(numericSeconds) || numericSeconds <= 0) {
    return "n/a";
  }
  if (numericSeconds < 60) {
    return `${Math.round(numericSeconds)}s`;
  }

  const minutes = Math.floor(numericSeconds / 60);
  const remainingSeconds = Math.round(numericSeconds % 60);
  return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
}

function formatDateTime(value) {
  if (!value) {
    return "n/a";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}
