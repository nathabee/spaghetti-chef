import { renderEventList, renderExecutionStepList } from "../components/event-list.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { escapeHtml, formatDateTime } from "../utils/format.js";
import { state } from "../state.js";

export function renderPrinterHistory(printer, jobsForPrinter) {
  const printerEvents = state.printerEvents.get(printer.id) ?? [];
  const auditEvents = state.operatorAuditEvents.filter((event) =>
    event.targetType === "printer" && event.targetId === printer.id
  );

  return `
    <section class="two-column-grid">
      <article class="panel-card">
        <div class="section-header compact">
          <div>
            <div class="kicker">History</div>
            <h3>Printer events</h3>
            <p class="muted">Operational events recorded for this printer.</p>
          </div>
          <button type="button" class="secondary-button small-button" data-load-printer-events="${printer.id}">Load events</button>
        </div>
        <div class="events-list">
          ${renderEventList(printerEvents, "Events not loaded yet.")}
        </div>
      </article>

      <article class="panel-card">
        <div class="section-header compact">
          <div>
            <h3>Job history for this printer</h3>
            <p class="muted">Job records currently assigned to the selected printer.</p>
          </div>
        </div>
        <div class="list-block">
          ${renderJobHistorySummary(jobsForPrinter)}
        </div>
      </article>
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <h3>Execution diagnostics for printer jobs</h3>
          <p class="muted">Structured workflow-step results for jobs assigned to this printer.</p>
        </div>
      </div>
      <div class="list-block">
        ${renderPrinterJobExecutionDiagnostics(jobsForPrinter)}
      </div>
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <h3>Operator audit</h3>
          <p class="muted">Accepted and rejected local dashboard actions targeting this printer.</p>
        </div>
      </div>
      <div class="events-list">
        ${renderAuditEvents(auditEvents)}
      </div>
    </section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
        "Snapshot history",
        "Reserved for future snapshot browsing when the dashboard exposes it directly.",
        [
          "Snapshot timestamps",
          "Temperature evolution",
          "Status transition timeline"
        ]
      )}
      ${renderPlaceholderCard(
        "Command result history",
        "Reserved for a later explicit command-result audit view.",
        [
          "Manual command log",
          "Command outcome status",
          "Structured execution response"
        ]
      )}
    </section>
  `;
}

function renderAuditEvents(events) {
  if (!events || events.length === 0) {
    return `<p class="muted">No operator audit entries for this printer yet.</p>`;
  }

  return events.slice(0, 12).map((event) => `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(event.result || "UNKNOWN")}</strong>
        <span class="event-time">${escapeHtml(formatDateTime(event.createdAt))}</span>
      </div>
      <div class="event-message">
        ${escapeHtml(event.actionType || "n/a")} · ${escapeHtml(event.role || "n/a")} · ${escapeHtml(event.permission || "no permission")}
      </div>
      ${event.failureReason ? `<div class="muted">${escapeHtml(event.failureReason)}</div>` : ""}
    </div>
  `).join("");
}

function renderJobHistorySummary(jobsForPrinter) {
  if (!jobsForPrinter || jobsForPrinter.length === 0) {
    return `<p class="muted">No jobs assigned to this printer yet.</p>`;
  }

  return jobsForPrinter.map((job) => `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(job.name || job.id)}</strong>
        <span class="event-time">${escapeHtml(job.state || "UNKNOWN")}</span>
      </div>
      <div class="event-message">${escapeHtml(job.type || "n/a")} · created ${escapeHtml(formatDateTime(job.createdAt))}</div>
      <div class="inline-actions">
        <button type="button" class="secondary-button small-button" data-job-action="load-events" data-job-id="${escapeHtml(job.id)}">Load job events</button>
        <button type="button" class="secondary-button small-button" data-job-action="load-execution-steps" data-job-id="${escapeHtml(job.id)}">Load diagnostics</button>
      </div>
    </div>
  `).join("");
}

function renderPrinterJobExecutionDiagnostics(jobsForPrinter) {
  if (!jobsForPrinter || jobsForPrinter.length === 0) {
    return `<p class="muted">No jobs assigned to this printer yet.</p>`;
  }

  return jobsForPrinter.map((job) => {
    const steps = state.jobExecutionSteps.get(job.id) ?? [];

    return `
      <div class="event-item">
        <div class="event-header">
          <strong>${escapeHtml(job.name || job.id)}</strong>
          <span class="event-time">${escapeHtml(job.state || "UNKNOWN")}</span>
        </div>
        <div class="event-message">${job.type || "n/a"}</div>
        <div class="events-list">
          ${renderExecutionStepList(steps, "Execution diagnostics not loaded yet.")}
        </div>
      </div>
    `;
  }).join("");
}
