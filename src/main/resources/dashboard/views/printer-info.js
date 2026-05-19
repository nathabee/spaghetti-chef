import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { renderSerialPathNotice, serialPortKind, stableSerialPath } from "../components/serial-port-guidance.js";
import { escapeHtml } from "../utils/format.js";


export function renderPrinterInfo(printer) {
  return `
    <section class="two-column-grid">
      <article class="panel-card">
        <div class="section-header compact">
          <div>
            <div class="kicker">Info</div>
            <h3>Technical profile</h3>
            <p class="muted">Read-only technical information for the selected printer.</p>
          </div>
        </div>

        <div class="info-row"><span>Display name</span><strong>${escapeHtml(printer.displayName || printer.name || printer.id)}</strong></div>
        <div class="info-row"><span>Printer ID</span><strong>${escapeHtml(printer.id)}</strong></div>
        <div class="info-row"><span>Port</span><strong>${escapeHtml(printer.portName || "n/a")}</strong></div>
        <div class="info-row"><span>Serial path type</span><strong>${escapeHtml(printer.serialPortKind || serialPortKind(printer.mode, printer.portName))}</strong></div>
        <div class="info-row"><span>Stable serial path</span><strong>${escapeHtml(stableSerialPath(printer.mode, printer.portName, printer.stableSerialPath) ? "yes" : "no")}</strong></div>
        <div class="info-row"><span>Mode</span><strong>${escapeHtml(printer.mode || "n/a")}</strong></div>
        <div class="info-row"><span>Last response</span><strong>${escapeHtml(printer.lastResponse || "n/a")}</strong></div>
        <div class="info-row"><span>Serial failure type</span><strong>${escapeHtml(printer.serialFailureType || "none")}</strong></div>
        ${renderSerialPathNotice(printer)}
        <div class="message-block">
          <span class="message-label">Current error</span>
          <div class="message-value">${escapeHtml(printer.errorMessage || "none")}</div>
        </div>
      </article>

      ${renderPlaceholderCard(
        "Future printer info",
        "Reserved for firmware, capability, and hardware-profile details that will later be surfaced more explicitly.",
        [
          "Firmware profile",
          "Printer capabilities",
          "Hardware profile",
          "Maintenance metadata"
        ]
      )}
    </section>
  `;
}
