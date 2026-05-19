import {   isSimulatedMode, renderStatusLabel } from "../dashboard.js";
import { escapeHtml, formatTemperature } from "../utils/format.js";



export function renderPrinterStatusPanel(printer) {
  return `
    <section class="detail-card" data-printer-status-panel-id="${escapeHtml(printer.id)}">
      <div class="detail-header">
        <div>
          <div class="kicker">Home</div>
          <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
          <p class="lead">Live machine status, temperatures, response state, and quick context for the selected printer.</p>
        </div>
        <div class="badge-row">
          <span class="badge ${printer.enabled ? "badge-enabled" : "badge-disabled"}">${printer.enabled ? "enabled" : "disabled"}</span>
          <span class="badge ${isSimulatedMode(printer.mode) ? "badge-simulated" : "badge-real"}">${isSimulatedMode(printer.mode) ? "simulated" : "real"}</span>
        </div>
      </div>

      <div class="metric-grid">
        <div class="summary-card">
          <span class="summary-label">State</span>
          <strong data-live-printer-field="status">${escapeHtml(renderStatusLabel(printer, printer.state || "UNKNOWN"))}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">Hotend</span>
          <strong data-live-printer-field="hotendTemperature">${formatTemperature(printer.hotendTemperature)}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">Bed</span>
          <strong data-live-printer-field="bedTemperature">${formatTemperature(printer.bedTemperature)}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">Updated</span>
          <strong data-live-printer-field="updatedAt">${escapeHtml(printer.updatedAt || "n/a")}</strong>
        </div>
      </div>

      <div class="two-column-grid">
        <div class="panel-card">
          <h3>Operational summary</h3>
          <div class="info-row"><span>Printer ID</span><strong>${escapeHtml(printer.id)}</strong></div>
          <div class="info-row"><span>Port</span><strong>${escapeHtml(printer.portName || "n/a")}</strong></div>
          <div class="info-row"><span>Mode</span><strong>${escapeHtml(printer.mode || "n/a")}</strong></div>
          <div class="info-row"><span>Serial failure</span><strong data-live-printer-field="serialFailureType">${escapeHtml(printer.serialFailureType || "none")}</strong></div>
          <div class="info-row"><span>Last response</span><strong data-live-printer-field="lastResponse">${escapeHtml(printer.lastResponse || "n/a")}</strong></div>
        </div>

        <div class="panel-card">
          <h3>Current issue</h3>
          <div class="message-value" data-live-printer-field="errorMessage">${escapeHtml(printer.errorMessage || "No active error recorded.")}</div>
        </div>
      </div>
    </section>
  `;
}
