import { renderPrinterCard } from "../components/printer-card.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { countDisabledPrinters, countEnabledPrinters,  getMostRecentUpdatedAt } from "../dashboard.js";
import { state } from "../state.js";
import { escapeHtml } from "../utils/format.js";

export function renderFarmHome() {
  const printers = state.printers;
  const printerCards = printers.length === 0
    ? `<div class="empty-state"><h3>No printers configured</h3><p class="muted">Add a printer in Settings to start the farm dashboard.</p></div>`
    : printers.map((printer) => renderPrinterCard(printer, {
      isSelected: state.selectedPrinterId === printer.id,
      actions: [
        state.selectedPrinterId === printer.id
          ? `<button type="button" class="primary-button" data-select-printer="${escapeHtml(printer.id)}">Selected printer</button>`
          : `<button type="button" class="secondary-button" data-select-printer="${escapeHtml(printer.id)}">Select printer</button>`
      ]
    })).join("");

  return `
    <section class="summary-grid">
      <article class="summary-card">
        <span class="summary-label">Configured printers</span>
        <strong>${printers.length}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Enabled printers</span>
        <strong>${countEnabledPrinters()}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Disabled printers</span>
        <strong>${countDisabledPrinters()}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Most recent printer update</span>
        <strong>${escapeHtml(getMostRecentUpdatedAt())}</strong>
      </article>
    </section>

    <section class="section-card">
      <div class="section-header">
        <div>
          <h2>Farm overview</h2>
          <p class="lead">Global fleet status with one card per configured printer.</p>
        </div>
      </div>
      <div class="card-grid">${printerCards}</div>
    </section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
    "Recent alerts",
    "Reserved for a future aggregated alert feed across all printers.",
    [
      "Global error roll-up",
      "Alert severity grouping",
      "Unread operator alerts"
    ]
  )}
      ${renderPlaceholderCard(
    "Recent activity",
    "Reserved for a future cross-printer activity stream.",
    [
      "Recent job starts and completions",
      "Recent command executions",
      "Recent printer transitions"
    ]
  )}
    </section>
  `;
}
