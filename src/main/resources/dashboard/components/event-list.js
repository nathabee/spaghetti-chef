import { escapeHtml, formatDateTime } from "../utils/format.js";

export function renderEventList(events, emptyMessage) {
  if (!events || events.length === 0) {
    return `<p class="muted">${escapeHtml(emptyMessage)}</p>`;
  }

  return events.map((event) => renderEventItem(event)).join("");
}

export function renderEventItem(event) {
  return `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(event.eventType || "UNKNOWN")}</strong>
        <span class="event-time">${escapeHtml(formatDateTime(event.createdAt))}</span>
      </div>
      <div class="event-message">${escapeHtml(event.message || "none")}</div>
    </div>
  `;
}


export function renderExecutionStepList(steps, emptyMessage) {
  if (!steps || steps.length === 0) {
    return `<p class="muted">${escapeHtml(emptyMessage)}</p>`;
  }

  return steps.map((step) => renderExecutionStepItem(step)).join("");
}

export function renderExecutionStepItem(step) {
  const outcome = step.outcome || (step.success ? "SUCCESS" : "FAILED");
  const detail = step.failureDetail || step.failureReason || "none";

  return `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(step.stepName || "unknown-step")}</strong>
        <span class="event-time">${escapeHtml(formatDateTime(step.createdAt))}</span>
      </div>
      <div class="event-message"><strong>Command:</strong> ${escapeHtml(step.wireCommand || "n/a")}</div>
      <div class="event-message"><strong>Response:</strong> ${escapeHtml(step.response || "none")}</div>
      <div class="event-message"><strong>Outcome:</strong> ${escapeHtml(outcome)}</div>
      <div class="event-message"><strong>Failure detail:</strong> ${escapeHtml(detail)}</div>
    </div>
  `;
}
