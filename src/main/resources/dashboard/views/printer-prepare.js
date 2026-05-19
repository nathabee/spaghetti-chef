import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { escapeHtml } from "../utils/format.js";



export function renderPrinterPrepare(printer) {
  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">Prepare</div>
          <h2>Preparation actions for ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Structured around the Creality printer logic: prepare the machine before and around print execution.</p>
        </div>
      </div>

      <div class="two-column-grid">
        <article class="panel-card">
          <h3>Immediate actions</h3>
          <div class="action-row">
            <button type="button" data-printer-command="G28" data-printer-id="${escapeHtml(printer.id)}">Auto home</button>
            <button type="button" data-printer-command="M84" data-printer-id="${escapeHtml(printer.id)}">Disable steppers</button>
            <button type="button" data-printer-command="M114" data-printer-id="${escapeHtml(printer.id)}">Read position</button>
          </div>
          <div class="message-block">
            <span class="message-label">Command result</span>
            <div class="message-value" id="commandResultPrepare">Use a preparation command to see the latest result here.</div>
          </div>
        </article>

        ${renderPlaceholderCard(
          "Preparation workflow placeholders",
          "Reserved for richer preparation flows that are not available yet as dedicated backend APIs.",
          [
            "Move axis helpers",
            "Z-offset workflow",
            "Preheat presets",
            "Filament load and unload"
          ]
        )}
      </div>
    </section>
  `;
}
