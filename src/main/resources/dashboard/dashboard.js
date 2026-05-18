import {
  cancelJob,
  closePrinterSdUploadSession,
  createJob,
  deletePrinterSdFile,
  createPrinter,
  deleteJob,
  deletePrinter,
  executePrinterCommand,
  getJob,
  getJobEvents,
  getJobExecutionSteps,
  getJobs,
  getMonitoringOverview,
  getMonitoringRules,
  getPrintFileContent,
  getPrintFileSettings,
  getSerialTransferSettings,
  getSecurityRoles,
  getSecuritySettings,
  getPrintFiles,
  getPrinterEvents,
  getPrinterSdCardFiles,
  getPrinterSdFiles,
  getPrinterSdUploadStatus as fetchPrinterSdUploadStatus,
  uploadPrinterSdFile,
  getPrinters,
  registerPrinterSdFile,
  registerPrintFile,
  pauseJob,
  restartJob,
  resumeJob,
  saveMonitoringRules,
  savePrintFileSettings,
  saveSerialTransferSettings,
  saveSecuritySettings,
  setPrinterSdFileEnabled,
  setPrinterEnabled,
  startJob,
  updatePrinter,
  uploadPrintFile
} from "./api.js";
import { renderNav } from "./components/nav.js";
import { renderFarmHome } from "./views/farm-home.js";
import { renderJobsPage } from "./views/jobs.js";
import { renderMonitoringPage } from "./views/monitoring.js";
import { renderPrinterControl } from "./views/printer-control.js";
import { renderPrinterHistory } from "./views/printer-history.js";
import { renderPrinterHome } from "./views/printer-home.js";
import { renderPrinterInfo } from "./views/printer-info.js";
import { renderPrinterPrepare } from "./views/printer-prepare.js";
import { renderPrinterPrint } from "./views/printer-print.js";
import { renderPrinterSdCard } from "./views/printer-sd-card.js";
import { renderSettingsPage } from "./views/settings.js";
import {
  getJobsForSelectedPrinter,
  getPrinterSdTargetFilter,
  getSelectedPrinter,
  PRIMARY_VIEW_IDS,
  PRINTER_VIEW_IDS,
  setJobEvents,
  setJobCardSectionOpen,
  setJobExecutionSteps,
  setJob,
  setJobSynchronization,
  setJobs,
  setLastRefreshLabel,
  setMessage,
  setMonitoringOverview,
  setMonitoringRules,
  setPrintFileSettings,
  setPrintFiles,
  setPrinterSdFiles,
  setSerialTransferSettings,
  setSecurityRoles,
  setSecuritySettings,
  setPrinterCommandResult,
  setPrinterEvents,
  setPrinterSdCardFiles,
  setPrinterSdTargetFilter,
  setPrinters,
  setPrimaryView,
  setPrinterView,
  setSelectedPrinter,
  setPrinterSdUploadStatus,
  setUploadStatusSynchronization,
  state
} from "./state.js";

const pageTitleElement = document.getElementById("pageTitle");
const pageLeadElement = document.getElementById("pageLead");
const pageContentElement = document.getElementById("pageContent");
const globalMessageElement = document.getElementById("globalMessage");
const refreshButton = document.getElementById("refreshButton");
const lastRefreshElement = document.getElementById("lastRefresh");

let printerRefreshInterval = null;
let pendingPrinterFormFill = null;
const uploadStatusPollers = new Map();
const jobStatusPollers = new Map();
const JOB_STATUS_POLLING_INTERVAL_MS = 2500;

async function boot() {
  bindGlobalListeners();
  await refreshAllData({ silent: false });
  startAutoRefresh();
}

async function refreshAllData(options = {}) {
  const silent = options.silent === true;

  try {
    const [
      printers,
      jobs,
      printFiles,
      printerSdFiles,
      monitoringRules,
      printFileSettings,
      serialTransferSettings,
      securitySettings,
      securityRoles,
      monitoringOverview
    ] = await Promise.all([
      getPrinters(),
      getJobs(),
      getPrintFiles(),
      getPrinterSdFiles(),
      getMonitoringRules(),
      getPrintFileSettings(),
      getSerialTransferSettings(),
      getSecuritySettings(),
      getSecurityRoles(),
      getMonitoringOverview()
    ]);

    setPrinters(printers);
    setJobs(jobs);
    setPrintFiles(printFiles);
    setPrinterSdFiles(printerSdFiles);
    setMonitoringRules(monitoringRules);
    setPrintFileSettings(printFileSettings);
    setSerialTransferSettings(serialTransferSettings);
    setSecuritySettings(securitySettings);
    setSecurityRoles(securityRoles);
    setMonitoringOverview(monitoringOverview);
    setLastRefreshLabel(new Date().toLocaleTimeString());
    await refreshUploadStatuses(printers);

    if (!silent) {
      setMessage("Dashboard refreshed.");
    }

    renderApp();
  } catch (error) {
    setMessage(`Failed to refresh dashboard: ${error.message}`);
    renderApp();
  }
}

async function refreshUploadStatuses(printers) {
  if (!Array.isArray(printers) || printers.length === 0) {
    return;
  }

  await Promise.all(printers.map(async (printer) => {
    try {
      const status = await fetchPrinterSdUploadStatus(printer.id);

      if (!status || status.state === "idle") {
        return;
      }

      setPrinterSdUploadStatus(printer.id, {
        ...status,
        message: uploadStatusMessage(status)
      });
    } catch (error) {
      // Upload status must not block normal dashboard refresh.
    }
  }));
}

async function refreshMonitoringOverview(options = {}) {
  try {
    const overview = await getMonitoringOverview();
    setMonitoringOverview(overview);

    if (options.silent !== true) {
      setMessage("Monitoring overview refreshed.");
    }
  } catch (error) {
    setMessage(`Failed to refresh monitoring overview: ${error.message}`);
  }
}

function renderApp() {
  renderNav();
  renderHeader();
  renderPage();
  renderGlobalMessage();
  bindPageListeners();
  lastRefreshElement.textContent = state.lastRefreshLabel;
}

function renderHeader() {
  const selectedPrinter = getSelectedPrinter();

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.FARM_HOME) {
    pageTitleElement.textContent = "Farm Home";
    pageLeadElement.textContent = "Global fleet monitoring with quick navigation into printer work areas.";
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.JOBS) {
    pageTitleElement.textContent = "Jobs";
    pageLeadElement.textContent = "Global job view aligned with the backend job domain model.";
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.MONITORING) {
    pageTitleElement.textContent = "Monitoring";
    pageLeadElement.textContent = "Cross-printer runtime activity, active jobs, and SD upload telemetry.";
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.HISTORY) {
    pageTitleElement.textContent = "History";
    pageLeadElement.textContent = "Global history shell reserved for broader audit views as backend coverage grows.";
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.SETTINGS) {
    pageTitleElement.textContent = "Settings";
    pageLeadElement.textContent = "Monitoring rules, printer administration, and future runtime settings.";
    return;
  }

  const printerName = selectedPrinter ? getSelectedPrinterDisplayName() : "No printer selected";
  const printerTitles = {
    [PRINTER_VIEW_IDS.HOME]: ["Printer Home", `Live machine view for ${printerName}.`],
    [PRINTER_VIEW_IDS.PRINT]: ["Print", `Jobs and future print workflow for ${printerName}.`],
    [PRINTER_VIEW_IDS.SD_CARD]: ["SD Card", `Printer-side printable files for ${printerName}.`],
    [PRINTER_VIEW_IDS.PREPARE]: ["Prepare", `Preparation actions inspired by the printer display workflow for ${printerName}.`],
    [PRINTER_VIEW_IDS.CONTROL]: ["Control", `Manual machine control and later tuning for ${printerName}.`],
    [PRINTER_VIEW_IDS.INFO]: ["Info", `Technical read-only information for ${printerName}.`],
    [PRINTER_VIEW_IDS.HISTORY]: ["Printer History", `Printer and job history for ${printerName}.`]
  };

  const [title, lead] = printerTitles[state.activePrinterView] || ["Printers", "Selected printer workspace."];
  pageTitleElement.textContent = title;
  pageLeadElement.textContent = lead;
}

function renderPage() {
  if (state.activePrimaryView === PRIMARY_VIEW_IDS.FARM_HOME) {
    pageContentElement.innerHTML = renderFarmHome();
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.JOBS) {
    pageContentElement.innerHTML = renderJobsPage();
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.MONITORING) {
    pageContentElement.innerHTML = renderMonitoringPage();
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.HISTORY) {
    pageContentElement.innerHTML = `
      <section class="two-column-grid">
        <article class="placeholder-card">
          <div class="section-header compact">
            <div>
              <h3>Global history</h3>
              <p class="placeholder-caption">This page is reserved for aggregated history views across printers and jobs.</p>
            </div>
            <span class="badge badge-real">placeholder</span>
          </div>
          <ul class="placeholder-list">
            <li>Cross-printer event stream</li>
            <li>Cross-job event stream</li>
            <li>Error history and filtering</li>
            <li>Snapshot browsing</li>
          </ul>
        </article>
      </section>
    `;
    return;
  }

  if (state.activePrimaryView === PRIMARY_VIEW_IDS.SETTINGS) {
    pageContentElement.innerHTML = renderSettingsPage();
    return;
  }

  const selectedPrinter = getSelectedPrinter();

  if (!selectedPrinter) {
    pageContentElement.innerHTML = `
      <div class="empty-state">
        <h3>No printer selected</h3>
        <p class="muted">Open a printer from Farm Home or add a printer in Settings.</p>
      </div>
    `;
    return;
  }

  const jobsForPrinter = getJobsForSelectedPrinter();

  if (state.activePrinterView === PRINTER_VIEW_IDS.HOME) {
    pageContentElement.innerHTML = renderPrinterHome(selectedPrinter);
    return;
  }

  if (state.activePrinterView === PRINTER_VIEW_IDS.PRINT) {
    pageContentElement.innerHTML = renderPrinterPrint(selectedPrinter, jobsForPrinter);
    return;
  }

  if (state.activePrinterView === PRINTER_VIEW_IDS.SD_CARD) {
    pageContentElement.innerHTML = renderPrinterSdCard(selectedPrinter);
    return;
  }

  if (state.activePrinterView === PRINTER_VIEW_IDS.PREPARE) {
    pageContentElement.innerHTML = renderPrinterPrepare(selectedPrinter);
    return;
  }

  if (state.activePrinterView === PRINTER_VIEW_IDS.CONTROL) {
    pageContentElement.innerHTML = renderPrinterControl(selectedPrinter);
    return;
  }

  if (state.activePrinterView === PRINTER_VIEW_IDS.INFO) {
    pageContentElement.innerHTML = renderPrinterInfo(selectedPrinter);
    return;
  }

  pageContentElement.innerHTML = renderPrinterHistory(selectedPrinter, jobsForPrinter);
}

function renderGlobalMessage() {
  const message = state.message || "";
  globalMessageElement.textContent = message;

  globalMessageElement.classList.remove(
    "is-visible",
    "global-message-info",
    "global-message-success",
    "global-message-warning",
    "global-message-error",
    "muted"
  );

  if (!message) {
    globalMessageElement.classList.add("global-message-info");
    return;
  }

  globalMessageElement.classList.add("is-visible");

  const normalized = message.toLowerCase();

  if (normalized.includes("failed") || normalized.includes("error")) {
    globalMessageElement.classList.add("global-message-error");
    return;
  }

  if (normalized.includes("uploading") || normalized.includes("running")) {
    globalMessageElement.classList.add("global-message-warning");
    return;
  }

  if (
    normalized.includes("saved")
    || normalized.includes("created")
    || normalized.includes("enabled")
    || normalized.includes("disabled")
    || normalized.includes("loaded")
    || normalized.includes("uploaded")
    || normalized.includes("executed")
    || normalized.includes("cancelled")
    || normalized.includes("deleted")
    || normalized.includes("refreshed")
  ) {
    globalMessageElement.classList.add("global-message-success");
    return;
  }

  globalMessageElement.classList.add("global-message-info");
}

function bindGlobalListeners() {
  refreshButton.addEventListener("click", () => refreshAllData({ silent: false }));

  document.addEventListener("click", async (event) => {
    const navButton = event.target.closest("[data-nav-target]");
    if (navButton) {
      stopManualUploadStatusSynchronizations();
      setPrimaryView(navButton.dataset.navTarget);
      renderApp();
      return;
    }

    const printerNavButton = event.target.closest("[data-printer-nav-target]");
    if (printerNavButton) {
      stopManualUploadStatusSynchronizations();
      setPrimaryView(PRIMARY_VIEW_IDS.PRINTERS);
      setPrinterView(printerNavButton.dataset.printerNavTarget);
      renderApp();
      return;
    }

    const selectPrinterButton = event.target.closest("[data-select-printer]");
    if (selectPrinterButton) {
      stopManualUploadStatusSynchronizations();
      setSelectedPrinter(selectPrinterButton.dataset.selectPrinter);
      setPrimaryView(PRIMARY_VIEW_IDS.PRINTERS);
      setPrinterView(PRINTER_VIEW_IDS.HOME);
      renderApp();
      return;
    }

    const loadPrinterEventsButton = event.target.closest("[data-load-printer-events]");
    if (loadPrinterEventsButton) {
      await loadPrinterEventsIntoState(loadPrinterEventsButton.dataset.loadPrinterEvents);
      renderApp();
      return;
    }

    const printerCommandButton = event.target.closest("[data-printer-command]");
    if (printerCommandButton) {
      await runPrinterCommand(printerCommandButton.dataset.printerId, printerCommandButton.dataset.printerCommand);
      renderApp();
      return;
    }

    const loadSdCardFilesButton = event.target.closest("[data-load-sd-card-files]");
    if (loadSdCardFilesButton) {
      await loadPrinterSdCardFilesIntoState(loadSdCardFilesButton.dataset.loadSdCardFiles);
      renderApp();
      return;
    }

    const syncUploadStatusButton = event.target.closest("[data-sync-sd-upload-status]");
    if (syncUploadStatusButton) {
      handleStartUploadStatusSynchronization(syncUploadStatusButton.dataset.syncSdUploadStatus);
      renderApp();
      return;
    }

    const stopSyncUploadStatusButton = event.target.closest("[data-stop-sync-sd-upload-status]");
    if (stopSyncUploadStatusButton) {
      handleStopUploadStatusSynchronization(stopSyncUploadStatusButton.dataset.stopSyncSdUploadStatus);
      renderApp();
      return;
    }

    const stopSyncJobButton = event.target.closest("[data-stop-sync-job]");
    if (stopSyncJobButton) {
      handleStopJobSynchronization(stopSyncJobButton.dataset.stopSyncJob);
      renderApp();
      return;
    }

    const refreshMonitoringButton = event.target.closest("[data-refresh-monitoring-overview]");
    if (refreshMonitoringButton) {
      await refreshMonitoringOverview({ silent: false });
      renderApp();
      return;
    }

    const monitoringSyncUploadButton = event.target.closest("[data-monitoring-sync-upload]");
    if (monitoringSyncUploadButton) {
      await handleMonitoringSyncUpload(monitoringSyncUploadButton.dataset.monitoringSyncUpload);
      renderApp();
      return;
    }

    const monitoringSyncJobButton = event.target.closest("[data-monitoring-sync-job]");
    if (monitoringSyncJobButton) {
      await handleMonitoringSyncJob(
        monitoringSyncJobButton.dataset.printerId,
        monitoringSyncJobButton.dataset.monitoringSyncJob
      );
      renderApp();
      return;
    }

    const registerSdCardFileButton = event.target.closest("[data-register-sd-card-file]");
    if (registerSdCardFileButton) {
      await handleRegisterSdCardFile(registerSdCardFileButton);
      renderApp();
      return;
    }

    const uploadPrintFileToSdButton = event.target.closest("[data-upload-print-file-to-sd]");
    if (uploadPrintFileToSdButton) {
      await handleUploadPrintFileToSd(
        uploadPrintFileToSdButton.dataset.printerId,
        uploadPrintFileToSdButton.dataset.printFileId,
        uploadPrintFileToSdButton.dataset.targetFilename || ""
      );
      renderApp();
      return;
    }

    const closeUploadSessionButton = event.target.closest("[data-close-sd-upload-session]");
    if (closeUploadSessionButton) {
      await handleCloseSdUploadSession(
        closeUploadSessionButton.dataset.printerId
      );
      renderApp();
      return;
    }

    const printerSdFileEnabledButton = event.target.closest("[data-printer-sd-file-enabled]");
    if (printerSdFileEnabledButton) {
      await handleSetPrinterSdFileEnabled(
        printerSdFileEnabledButton.dataset.printerSdFileId,
        printerSdFileEnabledButton.dataset.printerSdFileEnabled === "true"
      );
      renderApp();
      return;
    }

    const deletePrinterSdFileButton = event.target.closest("[data-delete-printer-sd-file-id]");
    if (deletePrinterSdFileButton) {
      await handleDeletePrinterSdFile(deletePrinterSdFileButton.dataset.deletePrinterSdFileId);
      renderApp();
      return;
    }

    const jobActionButton = event.target.closest("[data-job-action]");
    if (jobActionButton) {
      await handleJobAction(jobActionButton.dataset.jobAction, jobActionButton.dataset.jobId);
      renderApp();
      return;
    }

    const configActionButton = event.target.closest("[data-config-action]");
    if (configActionButton) {
      await handleConfigAction(configActionButton.dataset.configAction, configActionButton.dataset.printerId);
      renderApp();
    }
  });

  document.addEventListener("submit", (event) => {
    event.preventDefault();
  });

  document.addEventListener("change", (event) => {
    const filterInput = event.target.closest("[data-sd-target-filter]");
    if (!filterInput) {
      return;
    }

    setPrinterSdTargetFilter(
      filterInput.dataset.printerId,
      filterInput.dataset.sdTargetFilter,
      filterInput.value
    );
    renderApp();
  });

  document.addEventListener("toggle", (event) => {
    const details = event.target;

    if (!(details instanceof HTMLDetailsElement)) {
      return;
    }

    const jobId = details.dataset.jobId;
    const sectionId = details.dataset.jobDetailSection;

    if (!jobId || !sectionId) {
      return;
    }

    setJobCardSectionOpen(jobId, sectionId, details.open);
  }, true);
}

function bindPageListeners() {
  if (pendingPrinterFormFill) {
    fillPrinterForm(pendingPrinterFormFill);
    pendingPrinterFormFill = null;
  }

  const clearPrinterFormButton = document.getElementById("clearPrinterFormButton");
  if (clearPrinterFormButton) {
    clearPrinterFormButton.addEventListener("click", clearPrinterForm);
  }

  const printerConfigForm = document.getElementById("printerConfigForm");
  if (printerConfigForm) {
    printerConfigForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSavePrinter(printerConfigForm);
      renderApp();
    });
  }

  const monitoringRulesForm = document.getElementById("monitoringRulesForm");
  if (monitoringRulesForm) {
    monitoringRulesForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSaveMonitoringRules(monitoringRulesForm);
      renderApp();
    });
  }

  const printFileSettingsForm = document.getElementById("printFileSettingsForm");
  if (printFileSettingsForm) {
    printFileSettingsForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSavePrintFileSettings(printFileSettingsForm);
      renderApp();
    });
  }

  const serialTransferSettingsForm = document.getElementById("serialTransferSettingsForm");
  if (serialTransferSettingsForm) {
    serialTransferSettingsForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSaveSerialTransferSettings(serialTransferSettingsForm);
      renderApp();
    });
  }

  const securitySettingsForm = document.getElementById("securitySettingsForm");
  if (securitySettingsForm) {
    securitySettingsForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSaveSecuritySettings(securitySettingsForm);
      renderApp();
    });
  }

  const jobForm = document.getElementById("jobForm");
  if (jobForm) {
    jobForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSaveJob(jobForm);
      renderApp();
    });

    const jobPrinterIdInput = jobForm.querySelector("#jobPrinterIdInput");
    const jobPrinterSdFileIdInput = jobForm.querySelector("#jobPrinterSdFileIdInput");

    if (jobPrinterIdInput && jobPrinterSdFileIdInput) {
      jobPrinterIdInput.addEventListener("change", () => {
        jobPrinterSdFileIdInput.innerHTML = buildPrinterSdFileOptions(jobPrinterIdInput.value);
      });
    }
  }

  const printFileForm = document.getElementById("printFileForm");
  if (printFileForm) {
    printFileForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSavePrintFile(printFileForm);
      renderApp();
    });
  }

  const printerSdFileForm = document.getElementById("printerSdFileForm");
  if (printerSdFileForm) {
    printerSdFileForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleSavePrinterSdFile(printerSdFileForm);
      renderApp();
    });
  }

  const clearJobFormButton = document.getElementById("clearJobFormButton");
  if (clearJobFormButton) {
    clearJobFormButton.addEventListener("click", clearJobForm);
  }

  const sidebarPrinterSelect = document.getElementById("sidebarPrinterSelect");
  if (sidebarPrinterSelect) {
    sidebarPrinterSelect.addEventListener("change", (event) => {
      const printerId = event.target.value;

      if (!printerId) {
        return;
      }

      stopManualUploadStatusSynchronizations();
      setSelectedPrinter(printerId);
      setPrimaryView(PRIMARY_VIEW_IDS.PRINTERS);
      setPrinterView(PRINTER_VIEW_IDS.HOME);
      renderApp();
    });
  }
}

async function handleSavePrinter(form) {
  const printerIdInput = form.querySelector("#printerIdInput");
  const printerNameInput = form.querySelector("#printerNameInput");
  const printerPortInput = form.querySelector("#printerPortInput");
  const printerModeInput = form.querySelector("#printerModeInput");

  const printerId = printerIdInput.value.trim();
  const editingPrinterId = form.dataset.editingPrinterId || "";
  const targetPrinterId = editingPrinterId || printerId;
  const existingPrinter = state.printers.find((printer) => printer.id === targetPrinterId);
  const payload = {
    id: printerId,
    displayName: printerNameInput.value.trim(),
    portName: printerPortInput.value.trim(),
    mode: printerModeInput.value.trim(),
    enabled: existingPrinter?.enabled ?? true
  };

  try {
    if (existingPrinter) {
      await updatePrinter(targetPrinterId, payload);
      setMessage(`Saved printer ${targetPrinterId}.`);
    } else {
      await createPrinter(payload);
      setMessage(`Created printer ${printerId}.`);
    }

    clearPrinterForm();
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to save printer: ${error.message}`);
  }
}

async function handleSaveMonitoringRules(form) {
  const payload = {
    pollIntervalSeconds: Number.parseInt(form.querySelector("#pollIntervalSecondsInput").value, 10),
    snapshotMinimumIntervalSeconds: Number.parseInt(form.querySelector("#snapshotMinimumIntervalSecondsInput").value, 10),
    temperatureDeltaThreshold: Number.parseFloat(form.querySelector("#temperatureDeltaThresholdInput").value),
    eventDeduplicationWindowSeconds: Number.parseInt(form.querySelector("#eventDeduplicationWindowSecondsInput").value, 10),
    errorPersistenceBehavior: form.querySelector("#errorPersistenceBehaviorInput").value,
    debugWireTracingEnabled: form.querySelector("#debugWireTracingEnabledInput").checked
  };

  try {
    await saveMonitoringRules(payload);
    setMessage("Saved monitoring rules.");
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to save monitoring rules: ${error.message}`);
  }
}

async function handleSaveSerialTransferSettings(form) {
  const payload = {
    sdUploadBatchSize: Number.parseInt(form.querySelector("#transferSdUploadBatchSizeInput").value, 10),
    sdUploadRecoveryWindowMultiplier: Number.parseInt(
      form.querySelector("#transferSdUploadRecoveryWindowMultiplierInput").value,
      10
    ),
    sdUploadMaxErrors: Number.parseInt(form.querySelector("#transferSdUploadMaxErrorsInput").value, 10),
    sdUploadMaxConsecutiveIdenticalResends: Number.parseInt(
      form.querySelector("#transferSdUploadMaxConsecutiveIdenticalResendsInput").value,
      10
    ),
    sdUploadMinPerformancePercent: Number.parseInt(
      form.querySelector("#transferSdUploadMinPerformancePercentInput").value,
      10
    ),
    sdUploadMaxRetriesPerLine: Number.parseInt(
      form.querySelector("#transferSdUploadMaxRetriesPerLineInput").value,
      10
    ),
    fileStreamingReadTimeoutMs: Number.parseInt(
      form.querySelector("#transferFileStreamingReadTimeoutMsInput").value,
      10
    ),
    fileStreamingQuietPeriodMs: Number.parseInt(
      form.querySelector("#transferFileStreamingQuietPeriodMsInput").value,
      10
    ),
    fileStreamingReadActivitySleepMs: Number.parseInt(
      form.querySelector("#transferFileStreamingReadActivitySleepMsInput").value,
      10
    ),
    fileStreamingReadIdleSleepMs: Number.parseInt(
      form.querySelector("#transferFileStreamingReadIdleSleepMsInput").value,
      10
    ),
    fileStreamingRecoveryReplayDelayMs: Number.parseInt(
      form.querySelector("#transferFileStreamingRecoveryReplayDelayMsInput").value,
      10
    )
  };

  try {
    await saveSerialTransferSettings(payload);
    setMessage("Saved serial transfer settings.");
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to save serial transfer settings: ${error.message}`);
  }
}

async function handleSavePrintFileSettings(form) {
  const payload = {
    storageDirectory: form.querySelector("#printFileStorageDirectoryInput").value.trim()
  };

  try {
    await savePrintFileSettings(payload);
    setMessage("Saved print file settings.");
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to save print file settings: ${error.message}`);
  }
}

async function handleSaveSecuritySettings(form) {
  const payload = {
    securityEnabled: form.querySelector("#securityEnabledInput").checked,
    defaultRole: form.querySelector("#securityDefaultRoleInput").value,
    requireDangerousActionConfirmation: form.querySelector("#securityDangerousConfirmationInput").checked
  };

  try {
    await saveSecuritySettings(payload);
    setMessage("Saved local security settings.");
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to save local security settings: ${error.message}`);
  }
}

async function handleSaveJob(form) {
  const payload = {
    name: form.querySelector("#jobNameInput").value.trim(),
    type: form.querySelector("#jobTypeInput").value.trim(),
    printerId: emptyToNull(form.querySelector("#jobPrinterIdInput").value),
    printerSdFileId: emptyToNull(form.querySelector("#jobPrinterSdFileIdInput")?.value),
    targetTemperature: readOptionalNumber(form.querySelector("#jobTargetTemperatureInput").value),
    fanSpeed: readOptionalInteger(form.querySelector("#jobFanSpeedInput").value)
  };

  removeNullFields(payload);

  try {
    const response = await createJob(payload);
    setMessage(`Created job ${response.id}.`);
    clearJobForm();
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to create job: ${error.message}`);
  }
}

async function handleSavePrintFile(form) {
  const fileInput = form.querySelector("#printFileUploadInput");
  const pathInput = form.querySelector("#printFilePathInput");
  const selectedFile = fileInput?.files?.[0] ?? null;

  try {
    const response = selectedFile
      ? await uploadPrintFile(selectedFile)
      : await registerPrintFile(pathInput.value.trim());

    setMessage(`Registered print file ${response.originalFilename}.`);
    form.reset();
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to register print file: ${error.message}`);
  }
}

async function handleRegisterSdCardFile(button) {
  const payload = {
    printerId: button.dataset.printerId,
    firmwarePath: button.dataset.firmwarePath,
    displayName: button.dataset.displayName || button.dataset.firmwarePath,
    rawLine: button.dataset.rawLine || button.dataset.firmwarePath
  };

  if (button.dataset.sizeBytes) {
    payload.sizeBytes = Number.parseInt(button.dataset.sizeBytes, 10);
  }

  try {
    const response = await registerPrinterSdFile(payload);
    setMessage(`Registered SD file ${response.displayName}.`);
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to register SD file: ${error.message}`);
  }
}

async function handleUploadPrintFileToSd(printerId, printFileId, targetFilename) {
  if (!printerId || !printFileId) {
    return;
  }

  const hostFile = state.printFiles.find((file) => file.id === printFileId);
  const hostLabel = hostFile?.originalFilename || hostFile?.path || printFileId;

  const confirmation = requireDangerousActionConfirmation(
    "FILE_UPLOAD_OVERWRITE",
    `Upload ${hostLabel} to ${printerId}. This writes to the printer SD card and may overwrite an existing target file.`
  );
  if (!confirmation) {
    setMessage(`Cancelled SD upload for ${hostLabel}.`);
    return;
  }

  setPrinterSdUploadStatus(printerId, {
    state: "running",
    active: true,
    uploadedLineCount: 0,
    totalLineCount: 0,
    percent: 0,
    message: `Uploading ${hostLabel} to SD card...`
  });
  setMessage(`Uploading ${hostLabel} to ${printerId}...`);
  renderApp();
  startUploadStatusPolling(printerId);

  try {
    const response = await uploadPrinterSdFile(printerId, printFileId, targetFilename, confirmation);

    setPrinterSdUploadStatus(printerId, {
      state: "success",
      active: false,
      uploadedLineCount: response.uploadedLineCount,
      totalLineCount: response.totalLineCount,
      totalByteCount: response.totalByteCount,
      rejectedLineCount: response.rejectedLineCount,
      qualityPercent: calculateUploadQualityPercent(response.uploadedLineCount, response.rejectedLineCount),
      percent: 100,
      message: `Uploaded ${response.originalFilename} as ${response.linkedFirmwarePath || response.requestedTargetFilename}.`
    });

    setMessage(
      `Uploaded ${response.originalFilename} to ${printerId} as ${response.linkedFirmwarePath || response.requestedTargetFilename}.`
    );

    await refreshAllData({ silent: true });
    await loadPrinterSdCardFilesIntoState(printerId);
  } catch (error) {
    setPrinterSdUploadStatus(printerId, {
      state: "error",
      active: false,
      message: `Upload failed: ${error.message}`
    });
    setMessage(`Failed to upload print file to SD card: ${error.message}`);
  } finally {
    stopUploadStatusPolling(printerId);
    await refreshUploadStatus(printerId);
    renderApp();
  }
}

function startUploadStatusPolling(printerId) {
  stopUploadStatusPolling(printerId);

  const intervalId = window.setInterval(() => {
    refreshUploadStatus(printerId).catch(() => {
      // Keep the upload request itself as the source of user-visible failure.
    });
  }, 1000);

  uploadStatusPollers.set(printerId, intervalId);
}

function stopUploadStatusPolling(printerId) {
  const intervalId = uploadStatusPollers.get(printerId);

  if (!intervalId) {
    return;
  }

  window.clearInterval(intervalId);
  uploadStatusPollers.delete(printerId);
}

function handleStartUploadStatusSynchronization(printerId) {
  if (!printerId) {
    return;
  }

  setUploadStatusSynchronization(printerId, true);
  startUploadStatusPolling(printerId);
  refreshUploadStatus(printerId).catch(() => {
    setMessage(`Failed to synchronize upload status for ${printerId}.`);
    setUploadStatusSynchronization(printerId, false);
    stopUploadStatusPolling(printerId);
    renderApp();
  });
  setMessage(`Synchronizing upload status for ${printerId}.`);
}

function handleStopUploadStatusSynchronization(printerId) {
  if (!printerId) {
    return;
  }

  setUploadStatusSynchronization(printerId, false);
  stopUploadStatusPolling(printerId);
  setMessage(`Stopped upload status synchronization for ${printerId}.`);
}

async function handleMonitoringSyncUpload(printerId) {
  if (!printerId) {
    return;
  }

  setSelectedPrinter(printerId);
  setPrimaryView(PRIMARY_VIEW_IDS.PRINTERS);
  setPrinterView(PRINTER_VIEW_IDS.SD_CARD);
  handleStartUploadStatusSynchronization(printerId);
  await refreshUploadStatus(printerId);
  setMessage(`Following SD upload for ${printerId}.`);
}

async function handleMonitoringSyncJob(printerId, jobId) {
  if (!printerId) {
    setPrimaryView(PRIMARY_VIEW_IDS.JOBS);
    setMessage(`Open job ${jobId || ""} from the global Jobs page.`);
    return;
  }

  setSelectedPrinter(printerId);
  setPrimaryView(PRIMARY_VIEW_IDS.PRINTERS);
  setPrinterView(PRINTER_VIEW_IDS.PRINT);
  handleStartJobSynchronization(jobId);
  setMessage(`Following job ${jobId || ""} on ${printerId}.`);
}

function handleStartJobSynchronization(jobId) {
  if (!jobId) {
    return;
  }

  setJobSynchronization(jobId, true);
  setJobCardSectionOpen(jobId, "history", true);
  setJobCardSectionOpen(jobId, "diagnostics", true);
  startJobStatusPolling(jobId);
  refreshJobSynchronization(jobId).catch(() => {
    setMessage(`Failed to synchronize job ${jobId}.`);
    handleStopJobSynchronization(jobId);
    renderApp();
  });
}

function handleStopJobSynchronization(jobId) {
  if (!jobId) {
    return;
  }

  setJobSynchronization(jobId, false);
  stopJobStatusPolling(jobId);
  setMessage(`Stopped job synchronization for ${jobId}.`);
}

function startJobStatusPolling(jobId) {
  stopJobStatusPolling(jobId);

  const intervalId = window.setInterval(() => {
    refreshJobSynchronization(jobId).catch(() => {
      // Keep the last visible job state if one poll fails.
    });
  }, JOB_STATUS_POLLING_INTERVAL_MS);

  jobStatusPollers.set(jobId, intervalId);
}

function stopJobStatusPolling(jobId) {
  const intervalId = jobStatusPollers.get(jobId);

  if (!intervalId) {
    return;
  }

  window.clearInterval(intervalId);
  jobStatusPollers.delete(jobId);
}

function stopManualUploadStatusSynchronizations() {
  for (const printerId of state.uploadStatusSynchronization) {
    stopUploadStatusPolling(printerId);
  }

  state.uploadStatusSynchronization.clear();
}

function stopJobSynchronizations() {
  for (const jobId of state.jobSynchronization) {
    stopJobStatusPolling(jobId);
  }

  state.jobSynchronization.clear();
}

async function refreshJobSynchronization(jobId) {
  if (!jobId) {
    return;
  }

  const [job, events, executionSteps] = await Promise.all([
    getJob(jobId),
    getJobEvents(jobId),
    getJobExecutionSteps(jobId)
  ]);

  setJob(job);
  setJobEvents(jobId, events);
  setJobExecutionSteps(jobId, executionSteps);
  setLastRefreshLabel(new Date().toLocaleTimeString());

  if (isTerminalJobState(job.state)) {
    setJobSynchronization(jobId, false);
    stopJobStatusPolling(jobId);
    setMessage(`Job ${jobId} reached ${job.state}; synchronization stopped.`);
  }

  renderApp();
}

function isTerminalJobState(stateValue) {
  return ["COMPLETED", "FAILED", "CANCELLED"].includes(String(stateValue || "").toUpperCase());
}

async function refreshUploadStatus(printerId) {
  if (!printerId) {
    return;
  }

  const status = await fetchPrinterSdUploadStatus(printerId);

  if (!status || status.state === "idle") {
    return;
  }

  setPrinterSdUploadStatus(printerId, {
    ...status,
    message: uploadStatusMessage(status)
  });
  renderApp();
}

function uploadStatusMessage(status) {
  const filename = status.originalFilename || status.requestedTargetFilename || "file";
  const uploaded = Number(status.uploadedLineCount || 0);
  const total = Number(status.totalLineCount || 0);

  if (status.active) {
    if (total > 0) {
      return `Uploading ${filename}: ${uploaded}/${total} confirmed lines (${Number(status.percent || 0)}%).`;
    }
    return `Uploading ${filename}...`;
  }

  if (status.state === "success") {
    return status.detail || `Uploaded ${filename}.`;
  }

  if (status.state === "error") {
    return status.detail || `Upload failed for ${filename}.`;
  }

  return status.detail || "";
}

function calculateUploadQualityPercent(uploadedLineCount, rejectedLineCount) {
  const uploaded = Number(uploadedLineCount || 0);
  const rejected = Number(rejectedLineCount || 0);

  if (uploaded <= 0) {
    return 100;
  }

  return Math.max(0, Math.min(100, Math.floor((uploaded * 100) / (uploaded + rejected))));
}

 async function handleCloseSdUploadSession(printerId) {
  if (!printerId) {
    return;
  }

  try {
    const confirmation = requireDangerousActionConfirmation(
      "RECOVERY_CLOSE_UPLOAD",
      `Close the active SD upload session for ${printerId}. This sends recovery G-code to the printer.`
    );
    if (!confirmation) {
      setMessage(`Cancelled SD upload recovery close for ${printerId}.`);
      return;
    }

    const response = await closePrinterSdUploadSession(printerId, confirmation);

    setPrinterSdUploadStatus(printerId, {
      state: "success",
      active: false,
      message: `Closed upload session using line ${response.lineNumber} after ${response.attempts} attempt(s). Response: ${response.response || "n/a"}`
    });

    setMessage(`Closed SD upload session for ${printerId}.`);
    await refreshAllData({ silent: true });
  } catch (error) {
    setPrinterSdUploadStatus(printerId, {
      state: "error",
      active: false,
      message: `Recovery close failed: ${error.message}`
    });
    setMessage(`Failed to close SD upload session: ${error.message}`);
  }
}


async function handleSavePrinterSdFile(form) {
  const payload = {
    printerId: form.querySelector("#printerSdFilePrinterIdInput").value.trim(),
    firmwarePath: form.querySelector("#printerSdFilePathInput").value.trim(),
    displayName: emptyToNull(form.querySelector("#printerSdFileDisplayNameInput").value),
    printFileId: emptyToNull(form.querySelector("#printerSdFilePrintFileIdInput").value)
  };

  removeNullFields(payload);

  try {
    const response = await registerPrinterSdFile(payload);
    setMessage(`Registered SD target ${response.displayName}.`);
    form.reset();
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to register SD target: ${error.message}`);
  }
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

async function handleSetPrinterSdFileEnabled(printerSdFileId, enabled) {
  if (!printerSdFileId) {
    return;
  }

  try {
    const response = await setPrinterSdFileEnabled(printerSdFileId, enabled);
    setMessage(`${enabled ? "Enabled" : "Disabled"} SD target ${response.displayName}.`);
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to ${enabled ? "enable" : "disable"} SD target: ${error.message}`);
  }
}

async function handleDeletePrinterSdFile(printerSdFileId) {
  if (!printerSdFileId) {
    return;
  }

  try {
    const confirmation = requireDangerousActionConfirmation(
      "SD_DELETE",
      `Delete SD target ${printerSdFileId}. This removes PrinterHub's registered printer-side file record.`
    );
    if (!confirmation) {
      setMessage(`Cancelled SD target deletion for ${printerSdFileId}.`);
      return;
    }

    const response = await deletePrinterSdFile(printerSdFileId, confirmation);
    setMessage(`Deleted SD target ${response.displayName}.`);
    await refreshAllData({ silent: true });
  } catch (error) {
    setMessage(`Failed to delete SD target: ${error.message}`);
  }
}

async function handleJobAction(action, jobId) {
  if (!jobId) {
    return;
  }

  try {
    if (action === "start") {
      const confirmation = requireDangerousActionConfirmation(
        "PRINT_START",
        `Start job ${jobId}. This can heat, move, and begin printer-side execution.`
      );
      if (!confirmation) {
        setMessage(`Cancelled start for job ${jobId}.`);
        return;
      }

      const response = await startJob(jobId, confirmation);
      const stateLabel = response.job?.state || "UNKNOWN";
      const wireCommand = response.execution?.wireCommand || "n/a";
      const outcome = response.execution?.outcome || (response.execution?.success ? "SUCCESS" : "UNKNOWN");
      const executionResponse = response.execution?.response || response.execution?.failureDetail || "n/a";

      setMessage(
        `Started job ${jobId}. Result state: ${stateLabel}. Outcome: ${outcome}. Command: ${wireCommand}. Result: ${executionResponse}`
      );

      await refreshAllData({ silent: true });
      await loadJobEventsIntoState(jobId);
      await loadJobExecutionStepsIntoState(jobId);
      return;
    }

    if (action === "pause") {
      const response = await pauseJob(jobId);
      setMessage(`Paused job ${jobId}. Command: ${response.execution?.wireCommand || "n/a"}.`);
      await refreshAllData({ silent: true });
      await loadJobEventsIntoState(jobId);
      await loadJobExecutionStepsIntoState(jobId);
      return;
    }

    if (action === "resume") {
      const response = await resumeJob(jobId);
      setMessage(`Resumed job ${jobId}. Command: ${response.execution?.wireCommand || "n/a"}.`);
      await refreshAllData({ silent: true });
      await loadJobEventsIntoState(jobId);
      await loadJobExecutionStepsIntoState(jobId);
      return;
    }

    if (action === "cancel") {
      const confirmation = requireDangerousActionConfirmation(
        "PRINT_CANCEL",
        `Cancel job ${jobId}. This sends an abort/control request to the printer.`
      );
      if (!confirmation) {
        setMessage(`Cancelled cancel request for job ${jobId}.`);
        return;
      }

      await cancelJob(jobId, confirmation);
      setMessage(`Cancelled job ${jobId}.`);
      await refreshAllData({ silent: true });
      await loadJobEventsIntoState(jobId);
      await loadJobExecutionStepsIntoState(jobId);
      return;
    }

    if (action === "restart") {
      const response = await restartJob(jobId);
      const restartedJobId = response.job?.id || "new job";
      setMessage(`Created restart job ${restartedJobId} from ${jobId}.`);
      await refreshAllData({ silent: true });
      if (response.job?.id) {
        await loadJobEventsIntoState(response.job.id);
      }
      await loadJobEventsIntoState(jobId);
      return;
    }

    if (action === "delete") {
      await deleteJob(jobId);
      setMessage(`Deleted job ${jobId}.`);
      await refreshAllData({ silent: true });
      return;
    }

    if (action === "load-events") {
      await loadJobEventsIntoState(jobId);
      setJobCardSectionOpen(jobId, "history", true);
      setMessage(`Loaded history for job ${jobId}.`);
      return;
    }

    if (action === "load-execution-steps") {
      await loadJobExecutionStepsIntoState(jobId);
      setJobCardSectionOpen(jobId, "diagnostics", true);
      setMessage(`Loaded execution diagnostics for job ${jobId}.`);
      return;
    }

    if (action === "show-print-file") {
      await showPrintFileContent(jobId);
      return;
    }
  } catch (error) {
    setMessage(`Failed to ${action} job ${jobId}: ${error.message}`);
  }
}

async function showPrintFileContent(jobId) {
  const job = state.jobs.find((candidate) => candidate.id === jobId);

  if (!job?.printFileId) {
    setMessage(`Job ${jobId} has no print file.`);
    return;
  }

  const response = await getPrintFileContent(job.printFileId);
  const dialog = document.createElement("dialog");
  dialog.className = "print-file-dialog";
  dialog.innerHTML = `
    <div class="section-header compact">
      <div>
        <h3>${escapeHtml(response.printFile?.originalFilename || job.printFileId)}</h3>
        <p class="meta">${escapeHtml(response.printFile?.path || job.printFileId)}</p>
      </div>
      <button type="button" class="secondary-button" data-close-dialog>Close</button>
    </div>
    <pre class="print-file-content">${escapeHtml(response.content || "")}</pre>
  `;
  document.body.appendChild(dialog);
  dialog.querySelector("[data-close-dialog]").addEventListener("click", () => dialog.close());
  dialog.addEventListener("close", () => dialog.remove());
  dialog.showModal();
}

async function loadExecutionStepsForSelectedPrinterJobs() {
  const jobsForPrinter = getJobsForSelectedPrinter();

  for (const job of jobsForPrinter) {
    if (!state.jobExecutionSteps.has(job.id)) {
      await loadJobExecutionStepsIntoState(job.id);
    }
  }
}

async function handleConfigAction(action, printerId) {
  const printer = state.printers.find((item) => item.id === printerId);
  if (!printer) {
    setMessage(`Printer not found: ${printerId}`);
    return;
  }

  try {
    if (action === "edit") {
      setPrimaryView(PRIMARY_VIEW_IDS.SETTINGS);
      pendingPrinterFormFill = printer;
      fillPrinterForm(printer);
      setMessage(`Loaded printer ${printerId} into the configuration form.`);
      return;
    }

    if (action === "enable") {
      await setPrinterEnabled(printerId, true);
      setMessage(`Enabled printer ${printerId}.`);
      await refreshAllData({ silent: true });
      return;
    }

    if (action === "disable") {
      await setPrinterEnabled(printerId, false);
      setMessage(`Disabled printer ${printerId}.`);
      await refreshAllData({ silent: true });
      return;
    }

    if (action === "delete") {
      await deletePrinter(printerId);
      setMessage(`Deleted printer ${printerId}.`);
      await refreshAllData({ silent: true });
    }
  } catch (error) {
    setMessage(`Failed to ${action} printer ${printerId}: ${error.message}`);
  }
}

async function loadPrinterEventsIntoState(printerId) {
  try {
    const events = await getPrinterEvents(printerId);
    setPrinterEvents(printerId, events);
  } catch (error) {
    setMessage(`Failed to load printer events for ${printerId}: ${error.message}`);
  }
}

async function loadJobEventsIntoState(jobId) {
  try {
    const events = await getJobEvents(jobId);
    setJobEvents(jobId, events);
  } catch (error) {
    setMessage(`Failed to load job history for ${jobId}: ${error.message}`);
  }
}

async function loadJobExecutionStepsIntoState(jobId) {
  try {
    const steps = await getJobExecutionSteps(jobId);
    setJobExecutionSteps(jobId, steps);
  } catch (error) {
    setMessage(`Failed to load execution diagnostics for ${jobId}: ${error.message}`);
  }
}

async function loadPrinterSdCardFilesIntoState(printerId) {
  if (!printerId) {
    return;
  }

  try {
    const data = await getPrinterSdCardFiles(printerId);
    setPrinterSdCardFiles(printerId, data.files, data.rawResponse);
    setPrinterSdFiles(await getPrinterSdFiles());
    setMessage(`Loaded SD-card files for ${printerId}.`);
  } catch (error) {
    setMessage(`Failed to load SD-card files for ${printerId}: ${error.message}`);
  }
}

async function runPrinterCommand(printerId, command) {
  setPrinterCommandResult(printerId, `Running ${command}...`);
  renderApp();

  try {
    const dangerousAction = dangerousActionForCommand(command);
    const confirmation = dangerousAction
      ? requireDangerousActionConfirmation(
        dangerousAction,
        `Execute ${command} on ${printerId}. This command can affect physical printer state.`
      )
      : {};
    if (!confirmation) {
      setPrinterCommandResult(printerId, `Cancelled ${command}.`);
      setMessage(`Cancelled ${command} on ${printerId}.`);
      return;
    }

    const response = await executePrinterCommand(printerId, command, confirmation);
    const printerResponse = response.response ?? "no response";
    const successMessage = `${response.sentCommand}: ${printerResponse}`;

    setPrinterCommandResult(printerId, successMessage);
    setMessage(`Executed ${response.sentCommand} on ${printerId}.`);

    await refreshAllData({ silent: true });
    await loadPrinterEventsIntoState(printerId);
  } catch (error) {
    const failureMessage = `Command failed: ${error.message}`;
    setPrinterCommandResult(printerId, failureMessage);
    setMessage(`Failed to execute ${command} on ${printerId}: ${error.message}`);
    await refreshAllData({ silent: true });
  }
}

function fillPrinterForm(printer) {
  const form = document.getElementById("printerConfigForm");
  const printerIdInput = document.getElementById("printerIdInput");
  const printerNameInput = document.getElementById("printerNameInput");
  const printerPortInput = document.getElementById("printerPortInput");
  const printerModeInput = document.getElementById("printerModeInput");

  if (!form || !printerIdInput || !printerNameInput || !printerPortInput || !printerModeInput) {
    return;
  }

  printerIdInput.value = printer.id || "";
  printerNameInput.value = printer.displayName || printer.name || "";
  printerPortInput.value = printer.portName || "";
  printerModeInput.value = printer.mode || "real";
  form.dataset.editingPrinterId = printer.id || "";
}

function clearPrinterForm() {
  const form = document.getElementById("printerConfigForm");
  const printerModeInput = document.getElementById("printerModeInput");

  if (!form) {
    return;
  }

  form.reset();
  delete form.dataset.editingPrinterId;

  if (printerModeInput) {
    printerModeInput.value = "real";
  }
}

function clearJobForm() {
  const form = document.getElementById("jobForm");
  const jobTypeInput = document.getElementById("jobTypeInput");
  const jobPrinterIdInput = document.getElementById("jobPrinterIdInput");

  if (!form) {
    return;
  }

  form.reset();

  if (jobTypeInput) {
    jobTypeInput.value = "READ_FIRMWARE_INFO";
  }

  if (jobPrinterIdInput && state.selectedPrinterId) {
    jobPrinterIdInput.value = state.selectedPrinterId;
  }
}

function startAutoRefresh() {
  if (printerRefreshInterval) {
    clearInterval(printerRefreshInterval);
  }

  printerRefreshInterval = setInterval(async () => {
    try {
      const previousJobsSignature = jobsSignature(state.jobs);
      const [printers, jobs] = await Promise.all([
        getPrinters(),
        getJobs()
      ]);
      const nextJobsSignature = jobsSignature(jobs);

      setPrinters(printers);
      setJobs(jobs);
      setLastRefreshLabel(new Date().toLocaleTimeString());

      if (nextJobsSignature !== previousJobsSignature) {
        await refreshOpenJobDetails(jobs);
        renderApp();
        return;
      }

      renderLivePrinterRefresh(printers);
      lastRefreshElement.textContent = state.lastRefreshLabel;
    } catch {
      // keep current state visible
    }
  }, 3000);
}

async function refreshOpenJobDetails(jobs) {
  const openJobs = jobs.filter((job) =>
    isJobCardSectionOpen(job.id, "history") || isJobCardSectionOpen(job.id, "diagnostics")
  );

  await Promise.all(openJobs.map(async (job) => {
    if (isJobCardSectionOpen(job.id, "history")) {
      await loadJobEventsIntoState(job.id);
    }

    if (isJobCardSectionOpen(job.id, "diagnostics")) {
      await loadJobExecutionStepsIntoState(job.id);
    }
  }));
}

function jobsSignature(jobs) {
  return (Array.isArray(jobs) ? jobs : []).map((job) => [
    job.id,
    job.state,
    job.updatedAt,
    job.startedAt,
    job.finishedAt,
    job.failureReason,
    job.failureDetail
  ].join("|")).join(";");
}

function renderLivePrinterRefresh(printers) {
  for (const printer of printers) {
    updateLivePrinterFields(
      document.querySelector(`[data-printer-card-id="${cssEscape(printer.id)}"]`),
      printer);
    updateLivePrinterFields(
      document.querySelector(`[data-printer-status-panel-id="${cssEscape(printer.id)}"]`),
      printer);
  }
}

function cssEscape(value) {
  if (window.CSS && typeof window.CSS.escape === "function") {
    return window.CSS.escape(String(value));
  }

  return String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

function updateLivePrinterFields(container, printer) {
  if (!container) {
    return;
  }

  const fieldValues = {
    status: renderStatusLabel(printer, printer.state || "UNKNOWN"),
    hotendTemperature: formatTemperature(printer.hotendTemperature),
    bedTemperature: formatTemperature(printer.bedTemperature),
    updatedAt: formatDateTime(printer.updatedAt),
    lastResponse: printer.lastResponse || "n/a",
    serialFailureType: printer.serialFailureType || "none",
    errorMessage: printer.errorMessage || "none"
  };

  for (const [fieldName, value] of Object.entries(fieldValues)) {
    container
      .querySelectorAll(`[data-live-printer-field="${fieldName}"]`)
      .forEach((element) => {
        element.textContent = value;

        if (fieldName === "status" && element.classList.contains("badge")) {
          element.className = `badge ${resolveStateClass(printer)}`;
        }
      });
  }
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function formatTemperature(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "n/a";
  }

  return `${Number(value).toFixed(1)} °C`;
}

export function formatDateTime(value) {
  if (!value) {
    return "n/a";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(date);
}

export function resolveStateClass(printer) {
  if (!printer.enabled) {
    return "status-disabled";
  }

  const stateLabel = String(printer.state || "UNKNOWN").toLowerCase();

  if (stateLabel === "error" || stateLabel === "disconnected") {
    return "status-error";
  }

  if (["connecting", "heating", "printing"].includes(stateLabel)) {
    return "status-warn";
  }

  if (stateLabel === "idle") {
    return "status-ok";
  }

  return "status-unknown";
}

export function renderStatusLabel(printer, stateLabel) {
  if (!printer.enabled) {
    return "DISABLED";
  }

  return String(stateLabel || "UNKNOWN");
}

export function isSimulatedMode(mode) {
  const normalized = String(mode || "").toLowerCase();
  return ["sim", "simulated", "sim-error", "sim-timeout", "sim-disconnected"].includes(normalized);
}

export function countEnabledPrinters() {
  return state.printers.filter((printer) => printer.enabled).length;
}

export function countDisabledPrinters() {
  return state.printers.filter((printer) => !printer.enabled).length;
}

export function getSelectedPrinterDisplayName() {
  const printer = getSelectedPrinter();
  return printer ? (printer.displayName || printer.name || printer.id) : "No printer selected";
}

export function getMostRecentUpdatedAt() {
  const updatedValues = state.printers
    .map((printer) => printer.updatedAt)
    .filter((value) => typeof value === "string" && value.trim() !== "");

  return formatDateTime(updatedValues[0]);
}

function readOptionalNumber(value) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return null;
  }

  return Number.parseFloat(value);
}

function readOptionalInteger(value) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return null;
  }

  return Number.parseInt(value, 10);
}

function emptyToNull(value) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return null;
  }

  return String(value).trim();
}

function removeNullFields(object) {
  for (const key of Object.keys(object)) {
    if (object[key] === null || object[key] === undefined || object[key] === "") {
      delete object[key];
    }
  }
}

function requireDangerousActionConfirmation(requiredConfirmation, message) {
  const enabled = state.securitySettings?.requireDangerousActionConfirmation !== false;
  if (!enabled) {
    return {};
  }

  const confirmed = window.confirm(`${message}\n\nConfirm this dangerous action?`);
  if (!confirmed) {
    return null;
  }

  return {
    confirmed: true,
    confirmationReason: `Operator confirmed ${requiredConfirmation}`
  };
}

function dangerousActionForCommand(command) {
  const normalized = String(command || "").trim().toUpperCase();
  if (normalized.startsWith("M104") || normalized.startsWith("M109")
      || normalized.startsWith("M140") || normalized.startsWith("M190")) {
    return "HEATING";
  }
  if (normalized.startsWith("G28")) {
    return "HOMING";
  }
  if (normalized.startsWith("G0") || normalized.startsWith("G1")) {
    return "MOVEMENT";
  }
  if (normalized.startsWith("M105") || normalized.startsWith("M114") || normalized.startsWith("M115")) {
    return null;
  }

  return "RAW_COMMAND";
}

boot();
