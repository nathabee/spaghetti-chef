import { renderJobCard } from "../components/job-card.js";
import { renderExecutionStepList } from "../components/event-list.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { escapeHtml, formatDateTime } from "../utils/format.js";
import { disabledUnlessPermission, getPrinterSdUploadStatus, hasPermission, isJobCardSectionOpen, isJobSynchronized, state } from "../state.js";

export function renderPrinterPrint(printer, jobsForPrinter) {
  const synchronizedJobs = jobsForPrinter.filter((job) => isJobSynchronized(job.id));
  const jobsHtml = jobsForPrinter.length === 0
    ? `<div class="empty-state"><h3>No jobs assigned</h3><p class="muted">Create a job below or leave placeholders visible for the later production workflow.</p></div>`
    : jobsForPrinter.map((job) => renderJobCard(job, {
        eventsHtml: renderJobEvents(job.id),
        executionStepsHtml: renderJobExecutionSteps(job.id),
        historyOpen: isJobCardSectionOpen(job.id, "history"),
        diagnosticsOpen: isJobCardSectionOpen(job.id, "diagnostics"),
        printerUploadActive: getPrinterSdUploadStatus(job.printerId)?.active === true
      })).join("");
  const jobCreateNotice = hasPermission("JOB_CREATE")
    ? ""
    : `<p class="muted">Job creation is disabled for the current role.</p>`;

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">Print</div>
          <h2>Jobs for ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Current backend jobs stay visible here and this page is prepared to evolve toward piece-oriented production jobs later.</p>
        </div>
      </div>

      ${renderJobSynchronizationControls(synchronizedJobs)}

      ${jobCreateNotice}

      <form id="jobForm" class="form-grid">
        <label>
          Job name
          <input id="jobNameInput" name="jobName" type="text" placeholder="Optional for PRINT_FILE jobs">
        </label>

        <label>
          Job type
          <select id="jobTypeInput" name="type" required>
            <option value="READ_FIRMWARE_INFO">READ_FIRMWARE_INFO</option>
            <option value="READ_TEMPERATURE">READ_TEMPERATURE</option>
            <option value="READ_POSITION">READ_POSITION</option>
            <option value="HOME_AXES">HOME_AXES</option>
            <option value="SET_NOZZLE_TEMPERATURE">SET_NOZZLE_TEMPERATURE</option>
            <option value="SET_BED_TEMPERATURE">SET_BED_TEMPERATURE</option>
            <option value="SET_FAN_SPEED">SET_FAN_SPEED</option>
            <option value="TURN_FAN_OFF">TURN_FAN_OFF</option>
            <option value="PRINT_FILE">PRINT_FILE</option>
          </select>
        </label>

        <label>
          Printer
          <select id="jobPrinterIdInput" name="printerId">
            ${buildPrinterOptions(printer.id)}
          </select>
        </label>

        <label>
          Printer-side print file
          <select id="jobPrinterSdFileIdInput" name="printerSdFileId">
            ${buildPrinterSdFileOptions(printer.id)}
          </select>
        </label>

        <label>
          Target temperature
          <input id="jobTargetTemperatureInput" name="targetTemperature" type="number" step="1" min="0" placeholder="200">
        </label>

        <label>
          Fan speed
          <input id="jobFanSpeedInput" name="fanSpeed" type="number" step="1" min="0" max="255" placeholder="255">
        </label>

        <div class="form-actions">
          <button type="submit" ${disabledUnlessPermission("JOB_CREATE")}>Create job</button>
          <button id="clearJobFormButton" type="button" class="secondary-button" ${disabledUnlessPermission("JOB_CREATE")}>Clear form</button>
        </div>
      </form>
    </section>

    <section class="job-layout">${jobsHtml}</section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
        "Future production workflow",
        "Reserved for the later job model where one job produces a piece through several internal steps.",
        [
          "Model or plan file reference",
          "Preparation phase",
          "Execution phase",
          "Completion and piece traceability"
        ]
      )}
      ${renderPlaceholderCard(
        "Future print preview",
        "Reserved for later print-specific data that does not exist yet in the current backend.",
        [
          "Model preview",
          "Estimated duration",
          "Piece metadata",
          "Operator approval state"
        ]
      )}
    </section>
  `;
}

function renderJobSynchronizationControls(jobs) {
  if (jobs.length === 0) {
    return `
      <div class="sync-status-row">
        <span class="sync-active-indicator sync-idle"></span>
        <span class="muted">Manual job refresh only.</span>
      </div>
    `;
  }

  return `
    <div class="sync-status-row">
      <span class="sync-active-indicator sync-active"></span>
      <strong>Live job sync active</strong>
      ${jobs.map((job) => `
        <button type="button" class="secondary-button small-button" data-stop-sync-job="${escapeHtml(job.id)}">
          Stop sync ${escapeHtml(job.name || job.id)}
        </button>
      `).join("")}
    </div>
  `;
}

function renderJobEvents(jobId) {
  const events = state.jobEvents.get(jobId) ?? [];

  if (events.length === 0) {
    return `<p class="muted">Job history not loaded yet.</p>`;
  }

  return events.slice(0, 8).map((event) => `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(event.eventType || "UNKNOWN")}</strong>
        <span class="event-time">${escapeHtml(formatDateTime(event.createdAt))}</span>
      </div>
      <div class="event-message">${escapeHtml(event.message || "none")}</div>
    </div>
  `).join("");
}

function renderJobExecutionSteps(jobId) {
  const steps = state.jobExecutionSteps.get(jobId) ?? [];
  return renderExecutionStepList(steps, "Execution diagnostics not loaded yet.");
}

function buildPrinterOptions(selectedPrinterId) {
  const printers = state.printers;

  return [
    `<option value="">Select printer</option>`,
    ...printers.map((printer) => `
      <option value="${escapeHtml(printer.id)}" ${printer.id === selectedPrinterId ? "selected" : ""}>
        ${escapeHtml(printer.displayName || printer.id)}
      </option>
    `)
  ].join("");
}

function buildPrinterSdFileOptions(printerId) {
  const files = state.printerSdFiles.filter((file) =>
    file.printerId === printerId
    && file.enabled === true
    && file.deleted !== true
  );

  return [
    `<option value="">Select enabled SD file for PRINT_FILE jobs</option>`,
    ...files.map((file) => `
      <option value="${escapeHtml(file.id)}">
        ${escapeHtml(file.displayName || file.firmwarePath || file.id)}
      </option>
    `)
  ].join("");
}
