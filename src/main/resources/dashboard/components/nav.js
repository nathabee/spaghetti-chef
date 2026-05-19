import { currentLocalRole, PRIMARY_VIEW_IDS, PRINTER_VIEW_IDS, securityModeLabel, state } from "../state.js";
import {  countEnabledPrinters, countDisabledPrinters, getSelectedPrinterDisplayName } from "../dashboard.js";
import { escapeHtml } from "../utils/format.js";


const PRIMARY_ITEMS = [
  { id: PRIMARY_VIEW_IDS.FARM_HOME, label: "Farm Home", meta: () => `${state.printers.length} printers` },
  { id: PRIMARY_VIEW_IDS.PRINTERS, label: "Printers", meta: () => state.selectedPrinterId ? "Printer work area" : "Select a printer" },
  { id: PRIMARY_VIEW_IDS.JOBS, label: "Jobs", meta: () => `${state.jobs.length} tracked` },
  { id: PRIMARY_VIEW_IDS.MONITORING, label: "Monitoring", meta: () => `${state.monitoringOverview?.summary?.activeJobs ?? 0} active jobs` },
  { id: PRIMARY_VIEW_IDS.HISTORY, label: "History", meta: () => "Events and audit" },
  { id: PRIMARY_VIEW_IDS.SETTINGS, label: "Settings", meta: () => "Monitoring and runtime" }
];

const PRINTER_ITEMS = [
  { id: PRINTER_VIEW_IDS.HOME, label: "Home" },
  { id: PRINTER_VIEW_IDS.PRINT, label: "Print" },
  { id: PRINTER_VIEW_IDS.SD_CARD, label: "SD Card" },
  { id: PRINTER_VIEW_IDS.CAMERA, label: "Camera" },
  { id: PRINTER_VIEW_IDS.PREPARE, label: "Prepare" },
  { id: PRINTER_VIEW_IDS.CONTROL, label: "Control" },
  { id: PRINTER_VIEW_IDS.INFO, label: "Info" },
  { id: PRINTER_VIEW_IDS.HISTORY, label: "History" }
];


export function renderNav() {
  const primaryNav = document.getElementById("primaryNav");
  const selectedPrinterNav = document.getElementById("selectedPrinterNav");
  const selectedPrinterNavSection = document.getElementById("selectedPrinterNavSection");
  const selectedPrinterLabel = document.getElementById("selectedPrinterLabel");

  primaryNav.innerHTML = PRIMARY_ITEMS.map((item) => `
    <button
      type="button"
      class="nav-button ${state.activePrimaryView === item.id ? "active" : ""}"
      data-nav-target="${escapeHtml(item.id)}">
      <span>${escapeHtml(item.label)}</span>
      <span class="nav-meta">${escapeHtml(item.meta())}</span>
    </button>
  `).join("");

  selectedPrinterLabel.textContent = state.selectedPrinterId
    ? `${getSelectedPrinterDisplayName()} · ${countEnabledPrinters()} enabled · ${countDisabledPrinters()} disabled · ${securityModeLabel()} · ${currentLocalRole()}`
    : `No printer selected. · ${securityModeLabel()} · ${currentLocalRole()}`;

  if (!state.selectedPrinterId) {
    selectedPrinterNavSection.classList.add("hidden");
    selectedPrinterNav.innerHTML = "";
    return;
  }

  selectedPrinterNavSection.classList.remove("hidden");
  selectedPrinterNav.innerHTML = `
    <label class="sidebar-select-label">
      <span class="nav-section-label">Select printer</span>
      <select id="sidebarPrinterSelect" class="sidebar-select">
        ${buildPrinterOptions()}
      </select>
    </label>

    <div class="sidebar-selected-printer-meta">
      ${escapeHtml(getSelectedPrinterDisplayName())}
    </div>

    <div class="secondary-nav">
      ${PRINTER_ITEMS.map((item) => `
        <button
          type="button"
          class="nav-button ${state.activePrinterView === item.id ? "active" : ""}"
          data-printer-nav-target="${escapeHtml(item.id)}">
          <span>${escapeHtml(item.label)}</span>
        </button>
      `).join("")}
    </div>
  `;
}

function buildPrinterOptions() {
  return state.printers.map((printer) => `
    <option value="${escapeHtml(printer.id)}" ${printer.id === state.selectedPrinterId ? "selected" : ""}>
      ${escapeHtml(printer.displayName || printer.name || printer.id)}
    </option>
  `).join("");
}
