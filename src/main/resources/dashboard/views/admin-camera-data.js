import { escapeHtml } from "../utils/format.js";
import { hasPermission } from "../state.js";

export function renderAdminCameraDataPage(
  printers = [],
  selectedPrinterId = null,
  jobs = [],
  selectedJobId = null,
  timeline = [],
  selectedEntryId = null,
  actionResult = null,
  snapshotEntryUrl = () => ""
) {
  if (!hasPermission("CAMERA_DATA_MANAGE")) {
    return `
      <section class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Camera picture management</h3>
            <p class="placeholder-caption">This area is reserved for administrators.</p>
          </div>
          <span class="badge badge-warning">restricted</span>
        </div>
      </section>
    `;
  }

  return `
    <section class="placeholder-card">
      <div class="section-header compact">
        <div>
          <h3>Picture/Data Management</h3>
          <p class="placeholder-caption">Choose one printer. Camera jobs, retained snapshots, cleanup, and future recalculation are scoped to that printer.</p>
        </div>
        <span class="badge badge-real">admin</span>
      </div>
      <label class="form-field">
        <span>Printer</span>
        <select data-admin-camera-printer>
          ${renderPrinterOptions(printers, selectedPrinterId)}
        </select>
      </label>
    </section>

    <section class="two-column-grid">
      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Camera snapshot jobs</h3>
            <p class="placeholder-caption">Retained source snapshots are grouped by camera job for the selected printer.</p>
          </div>
          <span class="badge badge-real">live</span>
        </div>
        ${selectedPrinterId ? renderJobTable(jobs) : `<p class="muted">Select a printer to load camera snapshot jobs.</p>`}
      </article>

      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Job actions</h3>
            <p class="placeholder-caption">Load a camera job timeline, select frames, preview recalculation state, or delete retained source snapshots after confirmation.</p>
          </div>
          <span class="badge badge-real">admin</span>
        </div>
        ${renderSelectedJobActions(selectedPrinterId, selectedJobId)}
        ${renderActionResult(actionResult)}
      </article>
    </section>

    <section class="two-column-grid">
      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Replay timeline</h3>
            <p class="placeholder-caption">Select a row to inspect its retained source snapshot and metadata.</p>
          </div>
          <span class="badge badge-real">${escapeHtml(selectedJobId || "no job")}</span>
        </div>
        ${renderTimelineTable(timeline, selectedEntryId)}
      </article>

      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Selected frame</h3>
            <p class="placeholder-caption">Snapshot preview and capture metadata.</p>
          </div>
          <span class="badge badge-real">preview</span>
        </div>
        ${renderSelectedEntry(timeline, selectedEntryId, snapshotEntryUrl)}
      </article>
    </section>
  `;
}

function renderPrinterOptions(printers, selectedPrinterId) {
  if (!Array.isArray(printers) || printers.length === 0) {
    return `<option value="">No printers configured</option>`;
  }

  return printers.map((printer) => `
    <option value="${escapeHtml(printer.id)}" ${printer.id === selectedPrinterId ? "selected" : ""}>
      ${escapeHtml(printer.displayName || printer.name || printer.id)}
    </option>
  `).join("");
}

function renderJobTable(jobs) {
  if (!Array.isArray(jobs) || jobs.length === 0) {
    return `<p class="muted">No camera snapshot jobs have been recorded yet.</p>`;
  }

  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Camera job</th>
            <th>State</th>
            <th>Linked print job</th>
            <th>Files</th>
            <th>Bytes</th>
            <th>First capture</th>
            <th>Last capture</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          ${jobs.map((job) => {
            const cameraJobId = cameraJobKey(job);
            return `
            <tr>
              <td>${escapeHtml(cameraJobId)}</td>
              <td>${escapeHtml(job.state ?? "-")}</td>
              <td>${escapeHtml(job.linkedPrintJobId ?? "-")}</td>
              <td>${escapeHtml(String(job.fileCount ?? 0))}</td>
              <td>${escapeHtml(String(job.totalBytes ?? 0))}</td>
              <td>${escapeHtml(job.firstCapturedAt ?? "-")}</td>
              <td>${escapeHtml(job.lastCapturedAt ?? "-")}</td>
              <td>
                <button type="button" class="button-secondary" data-admin-camera-load-job="${escapeHtml(cameraJobId)}">Load</button>
                <button type="button" class="button-secondary" data-admin-camera-recalculate="${escapeHtml(cameraJobId)}">Recalculate</button>
                <button type="button" class="button-danger" data-admin-camera-delete-job="${escapeHtml(cameraJobId)}">Delete</button>
              </td>
            </tr>
          `;
          }).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderSelectedJobActions(selectedPrinterId, selectedJobId) {
  if (!selectedPrinterId) {
    return `<p class="muted">Select a printer first.</p>`;
  }
  if (!selectedJobId) {
    return `<p class="muted">Load a job from the table to enable job actions.</p>`;
  }

  return `
    <div class="form-grid compact-form">
      <label>
        Delta snapshot step
        <input id="adminCameraDeltaSnapshotStepInput" type="number" min="1" step="1" value="1">
      </label>
      <label>
        Method
        <input id="adminCameraDeltaMethodInput" type="text" value="image-delta">
      </label>
    </div>
    <div class="inline-actions">
      <button type="button" class="button-secondary" data-admin-camera-load-job="${escapeHtml(selectedJobId)}">Reload timeline</button>
      <button type="button" class="button-secondary" data-admin-camera-generate-delta="${escapeHtml(selectedJobId)}">Generate delta set</button>
      <button type="button" class="button-secondary" data-admin-camera-recalculate="${escapeHtml(selectedJobId)}">Preview recalculation</button>
      <button type="button" class="button-danger" data-admin-camera-delete-job="${escapeHtml(selectedJobId)}">Delete retained snapshots</button>
    </div>
  `;
}

function renderTimelineTable(timeline, selectedEntryId) {
  if (!Array.isArray(timeline) || timeline.length === 0) {
    return `<p class="muted">No timeline loaded yet.</p>`;
  }

  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Captured at</th>
            <th>Retained at</th>
            <th>Bytes</th>
            <th>Source</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          ${timeline.map((entry) => `
            <tr class="${Number(entry.id) === Number(selectedEntryId) ? "selected-row" : ""}">
              <td>${escapeHtml(entry.capturedAt ?? "-")}</td>
              <td>${escapeHtml(entry.retainedAt ?? "-")}</td>
              <td>${escapeHtml(String(entry.sizeBytes ?? 0))}</td>
              <td>${escapeHtml(entry.sourceType ?? "-")}</td>
              <td><button type="button" class="button-secondary" data-admin-camera-select-entry="${escapeHtml(String(entry.id))}">View</button></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderSelectedEntry(timeline, selectedEntryId, snapshotEntryUrl) {
  const entry = Array.isArray(timeline)
    ? timeline.find((candidate) => Number(candidate.id) === Number(selectedEntryId))
    : null;

  if (!entry) {
    return `<p class="muted">Select a timeline row to preview the snapshot image.</p>`;
  }

  const imageUrl = snapshotEntryUrl(entry.id);

  return `
    <figure class="camera-snapshot-preview">
      <figcaption>${escapeHtml(entry.snapshotPath ?? "snapshot file")}</figcaption>
      <img src="${escapeHtml(imageUrl)}" alt="Camera snapshot entry ${escapeHtml(String(entry.id))}">
    </figure>
    <dl class="metric-list">
      <div><dt>Printer</dt><dd>${escapeHtml(entry.printerId ?? "-")}</dd></div>
      <div><dt>Camera job</dt><dd>${escapeHtml(entry.cameraJobKey ?? entry.cameraJobId ?? entry.jobKey ?? "-")}</dd></div>
      <div><dt>Linked print job</dt><dd>${escapeHtml(entry.linkedPrintJobId ?? "-")}</dd></div>
      <div><dt>Captured at</dt><dd>${escapeHtml(entry.capturedAt ?? "-")}</dd></div>
      <div><dt>Retained at</dt><dd>${escapeHtml(entry.retainedAt ?? "-")}</dd></div>
      <div><dt>Content type</dt><dd>${escapeHtml(entry.contentType ?? "-")}</dd></div>
      <div><dt>Message</dt><dd>${escapeHtml(entry.message ?? "-")}</dd></div>
    </dl>
  `;
}

function cameraJobKey(job) {
  if (job.cameraJobKey != null) {
    return String(job.cameraJobKey);
  }
  if (job.cameraJobId != null) {
    return String(job.cameraJobId);
  }
  if (job.jobId != null) {
    return String(job.jobId);
  }
  return "";
}

function renderActionResult(result) {
  if (!result) {
    return "";
  }

  if (result.error) {
    return `<p class="status-text status-error">${escapeHtml(result.error)}</p>`;
  }

  return `
    <pre class="diagnostic-output">${escapeHtml(JSON.stringify(result, null, 2))}</pre>
  `;
}
