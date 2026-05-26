import { escapeHtml } from "../utils/format.js";
import { hasPermission } from "../state.js";

export function renderAdminCameraDataPage(
  printers = [],
  selectedPrinterId = null,
  jobs = [],
  selectedJobId = null,
  timeline = [],
  selectedEntryId = null,
  deltaSets = [],
  deltaFrames = [],
  calculationRuns = [],
  traceRows = [],
  runComparison = null,
  selectedDeltaSetId = null,
  selectedCalculationRunId = null,
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

    <section class="two-column-grid">
      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Recalculation</h3>
            <p class="placeholder-caption">Create reusable delta sets and run persisted spaghetti calculations without replacing older runs.</p>
          </div>
          <span class="badge badge-real">0.4.12</span>
        </div>
        ${renderRecalculationPanel(selectedJobId, deltaSets, calculationRuns, runComparison, selectedDeltaSetId, selectedCalculationRunId)}
      </article>

      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Spaghetti trace review</h3>
            <p class="placeholder-caption">Rows are backed by persisted source snapshots, delta frames, and calculation results.</p>
          </div>
          <span class="badge badge-real">${escapeHtml(String(traceRows?.length ?? 0))} rows</span>
        </div>
        ${renderTraceReview(deltaFrames, traceRows)}
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

function renderRecalculationPanel(selectedJobId, deltaSets, calculationRuns, runComparison, selectedDeltaSetId, selectedCalculationRunId) {
  if (!selectedJobId) {
    return `<p class="muted">Load a camera job before generating deltas or running calculations.</p>`;
  }

  const safeDeltaSets = Array.isArray(deltaSets) ? deltaSets : [];
  const safeRuns = Array.isArray(calculationRuns) ? calculationRuns : [];

  return `
    <div class="form-grid compact-form">
      <label>
        Existing delta set
        <select data-admin-camera-delta-set>
          <option value="">No delta set selected</option>
          ${safeDeltaSets.map((deltaSet) => `
            <option value="${escapeHtml(String(deltaSet.id))}" ${Number(deltaSet.id) === Number(selectedDeltaSetId) ? "selected" : ""}>
              #${escapeHtml(String(deltaSet.id))} step ${escapeHtml(String(deltaSet.deltaSnapshotStep ?? "-"))} - ${escapeHtml(String(deltaSet.generatedDeltaCount ?? 0))} deltas
            </option>
          `).join("")}
        </select>
      </label>
      <label>
        Calculation run
        <select data-admin-camera-calculation-run ${selectedDeltaSetId ? "" : "disabled"}>
          <option value="">No calculation run selected</option>
          ${safeRuns.map((run) => `
            <option value="${escapeHtml(String(run.id))}" ${Number(run.id) === Number(selectedCalculationRunId) ? "selected" : ""}>
              #${escapeHtml(String(run.id))} - ${escapeHtml(run.engineName ?? run.methodName ?? "-")} - ${escapeHtml(run.engineStatus ?? "UNKNOWN")} - ${escapeHtml(String(run.resultCount ?? 0))} results
            </option>
          `).join("")}
        </select>
      </label>
      <label>
        Calculation method
        <input id="adminCameraCalculationMethodInput" type="text" value="spaghetti-heuristic">
      </label>
      <label>
        Calculation engine
        <select id="adminCameraCalculationEngineInput">
          <option value="JAVA_BASIC_DELTA">Java basic delta</option>
          <option value="RUST_CLI_DELTA">Rust CLI delta</option>
        </select>
      </label>
      <label>
        Confidence threshold
        <input id="adminCameraCalculationConfidenceInput" type="number" min="0" max="1" step="0.01" value="0.85">
      </label>
      <label>
        Rust executable
        <input id="adminCameraRustExecutableInput" type="text" placeholder="optional path for Rust CLI">
      </label>
      <label>
        Parameters JSON
        <input id="adminCameraCalculationParamsInput" type="text" value="{&quot;source&quot;:&quot;dashboard&quot;}">
      </label>
    </div>
    <div class="inline-actions">
      <button type="button" class="button-secondary" data-admin-camera-run-calculation="${escapeHtml(String(selectedDeltaSetId || ""))}" ${selectedDeltaSetId ? "" : "disabled"}>
        Run calculation
      </button>
    </div>
    ${renderRunComparison(safeRuns)}
    ${renderRunComparisonDetails(runComparison)}
  `;
}

function renderRunComparison(calculationRuns) {
  if (calculationRuns.length === 0) {
    return `<p class="muted">No calculation runs exist for the selected delta set yet.</p>`;
  }

  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Run</th>
            <th>Engine</th>
            <th>Status</th>
            <th>Version</th>
            <th>Method</th>
            <th>Results</th>
            <th>Duration</th>
            <th>ms/frame</th>
            <th>Created</th>
            <th>Parameters</th>
          </tr>
        </thead>
        <tbody>
          ${calculationRuns.map((run) => `
            <tr>
              <td>${escapeHtml(String(run.id ?? "-"))}</td>
              <td>${escapeHtml(run.engineName ?? "-")}</td>
              <td>${escapeHtml(run.engineStatus ?? "-")}</td>
              <td>${escapeHtml(run.engineVersion ?? "-")}</td>
              <td>${escapeHtml(run.methodName ?? "-")}</td>
              <td>${escapeHtml(String(run.resultCount ?? 0))}</td>
              <td>${formatDuration(run.executionDurationMs)}</td>
              <td>${formatMillisecondsPerFrame(run)}</td>
              <td>${escapeHtml(run.createdAt ?? "-")}</td>
              <td><code>${escapeHtml(run.parameterJson ?? "{}")}</code></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderRunComparisonDetails(comparison) {
  if (!comparison?.left?.run || !comparison?.right?.run) {
    return `<p class="muted">Select a calculation run when at least two runs exist to compare engine results for this delta set.</p>`;
  }

  const frames = Array.isArray(comparison.frames) ? comparison.frames : [];
  const rows = frames.slice(0, 50);
  return `
    <div class="section-header compact">
      <div>
        <h3>Engine comparison</h3>
        <p class="placeholder-caption">
          Run #${escapeHtml(String(comparison.left.run.id))} ${escapeHtml(comparison.left.run.engineName ?? "-")}
          vs run #${escapeHtml(String(comparison.right.run.id))} ${escapeHtml(comparison.right.run.engineName ?? "-")}.
        </p>
      </div>
      <span class="badge badge-real">${escapeHtml(String(comparison.suspectedMismatchCount ?? 0))} mismatches</span>
    </div>
    <dl class="metric-list">
      <div><dt>Compared frames</dt><dd>${escapeHtml(String(comparison.comparedFrameCount ?? 0))}</dd></div>
      <div><dt>Average confidence difference</dt><dd>${formatPercent(comparison.averageAbsoluteConfidenceDifference)}</dd></div>
      <div><dt>Left average confidence</dt><dd>${formatPercent(comparison.left.averageConfidence)}</dd></div>
      <div><dt>Right average confidence</dt><dd>${formatPercent(comparison.right.averageConfidence)}</dd></div>
      <div><dt>Left suspected</dt><dd>${escapeHtml(String(comparison.left.suspectedCount ?? 0))}</dd></div>
      <div><dt>Right suspected</dt><dd>${escapeHtml(String(comparison.right.suspectedCount ?? 0))}</dd></div>
    </dl>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Delta frame</th>
            <th>Left confidence</th>
            <th>Right confidence</th>
            <th>Difference</th>
            <th>Left state</th>
            <th>Right state</th>
            <th>Mismatch</th>
            <th>Left reasons</th>
            <th>Right reasons</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((frame) => `
            <tr class="${frame.suspectedMismatch ? "analysis-suspected" : ""}">
              <td>${escapeHtml(String(frame.deltaFrameId ?? "-"))}</td>
              <td>${formatPercent(frame.leftConfidence)}</td>
              <td>${formatPercent(frame.rightConfidence)}</td>
              <td>${formatPercent(frame.confidenceDifference)}</td>
              <td>${formatSuspected(frame.leftSuspected)}</td>
              <td>${formatSuspected(frame.rightSuspected)}</td>
              <td>${frame.suspectedMismatch ? '<span class="badge status-error">Mismatch</span>' : '<span class="badge badge-enabled">Match</span>'}</td>
              <td>${escapeHtml(frame.leftReasonCodes ?? "-")}</td>
              <td>${escapeHtml(frame.rightReasonCodes ?? "-")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
    ${frames.length > rows.length ? `<p class="muted">Showing first ${escapeHtml(String(rows.length))} of ${escapeHtml(String(frames.length))} compared frames.</p>` : ""}
  `;
}

function renderTraceReview(deltaFrames, traceRows) {
  const safeFrames = Array.isArray(deltaFrames) ? deltaFrames : [];
  const safeTraceRows = Array.isArray(traceRows) ? traceRows : [];
  if (safeTraceRows.length === 0) {
    return `
      <p class="muted">Select a calculation run to review persisted spaghetti trace rows.</p>
      ${safeFrames.length === 0 ? "" : `<p class="muted">${escapeHtml(String(safeFrames.length))} delta frames are available for the selected delta set.</p>`}
    `;
  }

  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Created</th>
            <th>Camera job</th>
            <th>Delta set</th>
            <th>Delta frame</th>
            <th>Run</th>
            <th>Result</th>
            <th>From snapshot</th>
            <th>To snapshot</th>
            <th>Delta file</th>
            <th>Confidence</th>
            <th>State</th>
            <th>Reason codes</th>
          </tr>
        </thead>
        <tbody>
          ${safeTraceRows.map((row) => `
            <tr class="${row.suspected ? "analysis-suspected" : "analysis-good"}">
              <td>${escapeHtml(row.createdAt ?? "-")}</td>
              <td>${escapeHtml(String(row.cameraJobId ?? "-"))}</td>
              <td>${escapeHtml(String(row.deltaSetId ?? "-"))}</td>
              <td>${escapeHtml(String(row.deltaFrameId ?? "-"))}</td>
              <td>${escapeHtml(String(row.calculationRunId ?? "-"))}</td>
              <td>${escapeHtml(String(row.calculationResultId ?? "-"))}</td>
              <td><code title="${escapeHtml(row.fromSnapshotPath ?? "")}">${escapeHtml(fileName(row.fromSnapshotPath))}</code></td>
              <td><code title="${escapeHtml(row.toSnapshotPath ?? "")}">${escapeHtml(fileName(row.toSnapshotPath))}</code></td>
              <td><code title="${escapeHtml(row.deltaPath ?? "")}">${escapeHtml(fileName(row.deltaPath))}</code></td>
              <td>${formatPercent(row.confidence)}</td>
              <td>${row.suspected ? '<span class="badge status-error">Suspicious</span>' : '<span class="badge badge-enabled">Good</span>'}</td>
              <td>${escapeHtml(row.reasonCodes ?? "-")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
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

function fileName(path) {
  if (!path) {
    return "-";
  }

  const normalized = String(path).replaceAll("\\", "/");
  return normalized.split("/").filter(Boolean).pop() || normalized;
}

function formatPercent(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return "-";
  }

  return `${Math.round(parsed * 100)}%`;
}

function formatDuration(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return "-";
  }

  return `${Math.round(parsed)} ms`;
}

function formatMillisecondsPerFrame(run) {
  const duration = Number(run?.executionDurationMs);
  const results = Number(run?.resultCount);
  if (!Number.isFinite(duration) || !Number.isFinite(results) || results <= 0) {
    return "-";
  }

  return `${Math.round(duration / results)} ms`;
}

function formatSuspected(value) {
  if (value == null) {
    return "-";
  }
  return value
    ? '<span class="badge status-error">Suspicious</span>'
    : '<span class="badge badge-enabled">Good</span>';
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
