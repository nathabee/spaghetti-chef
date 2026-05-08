export const PRIMARY_VIEW_IDS = Object.freeze({
  FARM_HOME: "farm-home",
  PRINTERS: "printers",
  JOBS: "jobs",
  HISTORY: "history",
  SETTINGS: "settings"
});

export const PRINTER_VIEW_IDS = Object.freeze({
  HOME: "printer-home",
  PRINT: "printer-print",
  SD_CARD: "printer-sd-card",
  PREPARE: "printer-prepare",
  CONTROL: "printer-control",
  INFO: "printer-info",
  HISTORY: "printer-history"
});

export const state = {
  activePrimaryView: PRIMARY_VIEW_IDS.FARM_HOME,
  activePrinterView: PRINTER_VIEW_IDS.HOME,
  selectedPrinterId: null,
  printers: [],
  jobs: [],
  printFiles: [],
  printerSdFiles: [],
  monitoringRules: null,
  printFileSettings: null,
  printerEvents: new Map(),
  jobEvents: new Map(),
  jobExecutionSteps: new Map(),
  jobCardSections: new Map(),
  printerSdCardFiles: new Map(),
  printerSdUploadStatus: new Map(),
  printerSdTargetFilters: new Map(),
  printerCommandResults: new Map(),
  message: "",
  lastRefreshLabel: "never"
};

export function setPrinters(printers) {
  state.printers = Array.isArray(printers) ? printers : [];

  if (!state.selectedPrinterId && state.printers.length > 0) {
    state.selectedPrinterId = state.printers[0].id;
  }

  if (state.selectedPrinterId && !state.printers.some((printer) => printer.id === state.selectedPrinterId)) {
    state.selectedPrinterId = state.printers[0]?.id ?? null;
  }
}

export function setJobs(jobs) {
  state.jobs = Array.isArray(jobs) ? jobs : [];
}

export function setPrintFiles(printFiles) {
  state.printFiles = Array.isArray(printFiles) ? printFiles : [];
}

export function setPrinterSdFiles(files) {
  state.printerSdFiles = Array.isArray(files) ? files : [];
}

export function setMonitoringRules(rules) {
  state.monitoringRules = rules;
}

export function setPrintFileSettings(settings) {
  state.printFileSettings = settings;
}

export function setPrimaryView(viewId) {
  state.activePrimaryView = viewId;
}

export function setPrinterView(viewId) {
  state.activePrinterView = viewId;
}

export function setSelectedPrinter(printerId) {
  if (!printerId) {
    state.selectedPrinterId = null;
    return;
  }

  const exists = state.printers.some((printer) => printer.id === printerId);
  state.selectedPrinterId = exists ? printerId : state.selectedPrinterId;
}

export function setPrinterSdUploadStatus(printerId, status) {
  if (!printerId) {
    return;
  }

  if (!status) {
    state.printerSdUploadStatus.delete(printerId);
    return;
  }

  state.printerSdUploadStatus.set(printerId, status);
}

export function setMessage(message) {
  state.message = message || "";
}

export function setLastRefreshLabel(label) {
  state.lastRefreshLabel = label;
}

export function setJobExecutionSteps(jobId, steps) {
  state.jobExecutionSteps.set(jobId, Array.isArray(steps) ? steps : []);
}

export function setJobCardSectionOpen(jobId, sectionId, open) {
  if (!jobId || !sectionId) {
    return;
  }

  state.jobCardSections.set(`${jobId}:${sectionId}`, open === true);
}

export function isJobCardSectionOpen(jobId, sectionId) {
  if (!jobId || !sectionId) {
    return false;
  }

  return state.jobCardSections.get(`${jobId}:${sectionId}`) === true;
}

export function getSelectedPrinter() {
  return state.printers.find((printer) => printer.id === state.selectedPrinterId) ?? null;
}

export function getJobsForSelectedPrinter() {
  if (!state.selectedPrinterId) {
    return [];
  }

  return state.jobs.filter((job) => job.printerId === state.selectedPrinterId);
}

export function getPrinterSdUploadStatus(printerId) {
  if (!printerId) {
    return null;
  }

  return state.printerSdUploadStatus.get(printerId) ?? null;
}

export function setPrinterSdTargetFilter(printerId, fieldName, value) {
  if (!printerId || !fieldName) {
    return;
  }

  const current = state.printerSdTargetFilters.get(printerId) ?? {
    availability: "all",
    enabled: "all",
    link: "all"
  };

  state.printerSdTargetFilters.set(printerId, {
    ...current,
    [fieldName]: value || "all"
  });
}

export function getPrinterSdTargetFilter(printerId) {
  if (!printerId) {
    return {
      availability: "all",
      enabled: "all",
      link: "all"
    };
  }

  return state.printerSdTargetFilters.get(printerId) ?? {
    availability: "all",
    enabled: "all",
    link: "all"
  };
}

export function setPrinterEvents(printerId, events) {
  state.printerEvents.set(printerId, Array.isArray(events) ? events : []);
}

export function setPrinterSdCardFiles(printerId, files, rawResponse = "") {
  state.printerSdCardFiles.set(printerId, {
    files: Array.isArray(files) ? files : [],
    rawResponse: rawResponse || ""
  });
}

export function setJobEvents(jobId, events) {
  state.jobEvents.set(jobId, Array.isArray(events) ? events : []);
}

export function setPrinterCommandResult(printerId, message) {
  state.printerCommandResults.set(printerId, message);
}
