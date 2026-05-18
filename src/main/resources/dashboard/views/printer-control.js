import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { escapeHtml } from "../dashboard.js";
import { disabledUnlessPermission, hasPermission, state } from "../state.js";

export function renderPrinterControl(printer) {
  const commandResult = state.printerCommandResults.get(printer.id) ?? "No manual command executed yet.";
  const commandNotice = hasPermission("COMMAND_READ")
    ? ""
    : `<p class="muted">Manual command buttons are disabled for the current role.</p>`;

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">Control</div>
          <h2>Manual control for ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Low-level printer control, manual commands, and future tuning/configuration functions.</p>
        </div>
      </div>

      <div class="two-column-grid">
        <article class="panel-card">
          <h3>Manual commands</h3>
          <div class="command-button-grid">
            <button type="button" data-printer-command="M105" data-printer-id="${escapeHtml(printer.id)}" ${printer.enabled ? disabledUnlessPermission("COMMAND_READ") : "disabled"}>Read temp</button>
            <button type="button" data-printer-command="M114" data-printer-id="${escapeHtml(printer.id)}" ${printer.enabled ? disabledUnlessPermission("COMMAND_READ") : "disabled"}>Read position</button>
            <button type="button" data-printer-command="M115" data-printer-id="${escapeHtml(printer.id)}" ${printer.enabled ? disabledUnlessPermission("COMMAND_READ") : "disabled"}>Read firmware</button>
          </div>
          ${commandNotice}
          <div class="message-block">
            <span class="message-label">Latest command result</span>
            <div class="message-value">${escapeHtml(commandResult)}</div>
          </div>
        </article>

        ${renderPlaceholderCard(
          "Future tuning and control",
          "Reserved for control features that will later deserve dedicated APIs and job-aware workflows.",
          [
            "Temperature setpoint forms",
            "Fan control panels",
            "Reset configuration",
            "Motion and print tuning"
          ]
        )}
      </div>
    </section>
  `;
}
