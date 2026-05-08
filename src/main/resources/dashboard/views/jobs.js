import { renderJobCard } from "../components/job-card.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { renderExecutionStepList } from "../components/event-list.js";
import { escapeHtml, formatDateTime } from "../dashboard.js";
import { isJobCardSectionOpen, state } from "../state.js";

export function renderJobsPage() {
  const jobs = state.jobs;

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <h2>All jobs</h2>
          <p class="lead">Global job view across all printers. Backend naming stays aligned with the current job domain model.</p>
        </div>
      </div>
      <div class="job-layout">
        ${jobs.length === 0
          ? `<div class="empty-state"><h3>No jobs created yet</h3><p class="muted">Create jobs from the printer Print page or wait for backend data.</p></div>`
          : jobs.map((job) => renderJobCard(job, {
              eventsHtml: renderJobEvents(job.id),
              executionStepsHtml: renderJobExecutionSteps(job.id),
              historyOpen: isJobCardSectionOpen(job.id, "history"),
              diagnosticsOpen: isJobCardSectionOpen(job.id, "diagnostics")
            })).join("")}
      </div>
    </section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
        "Future production job model",
        "Reserved for the richer job structure that will later represent piece production rather than single command-like execution.",
        [
          "Piece identity",
          "Job phases",
          "File and preparation references",
          "Completion traceability"
        ]
      )}
      ${renderPlaceholderCard(
        "Job search and reporting",
        "Reserved for later filtering and reporting once the backend job model becomes richer.",
        [
          "Search by printer",
          "Search by state",
          "Search by piece",
          "Completion reporting"
        ]
      )}
    </section>
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
