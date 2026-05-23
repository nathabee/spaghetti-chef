export const PRIMARY_VIEW_IDS = Object.freeze({
  FARM_HOME: "farm-home",
  PRINTERS: "printers",
  JOBS: "jobs",
  MONITORING: "monitoring",
  HISTORY: "history",
  ADMIN_CAMERA: "admin-camera",
  SETTINGS: "settings"
});

export const PRINTER_VIEW_IDS = Object.freeze({
  HOME: "printer-home",
  PRINT: "printer-print",
  SD_CARD: "printer-sd-card",
  CAMERA: "printer-camera",
  PREPARE: "printer-prepare",
  CONTROL: "printer-control",
  INFO: "printer-info",
  HISTORY: "printer-history"
});

export const state = {
  activePrimaryView: PRIMARY_VIEW_IDS.FARM_HOME,
  activePrinterView: PRINTER_VIEW_IDS.HOME,
  selectedPrinterId: null,
  adminCameraPrinterId: null,
  printers: [],
  jobs: [],
  printFiles: [],
  printerSdFiles: [],
  monitoringRules: null,
  printFileSettings: null,
  serialTransferSettings: null,
  appVersion: null,
  securitySettings: null,
  securityRoles: [],
  monitoringOverview: null,
  operatorAuditEvents: [],
  cameraSnapshotJobs: [],
  adminCameraTimeline: [],
  adminCameraDeltaSets: [],
  adminCameraDeltaFrames: [],
  adminCameraCalculationRuns: [],
  adminCameraTraceRows: [],
  adminCameraSelectedJobId: null,
  adminCameraSelectedDeltaSetId: null,
  adminCameraSelectedCalculationRunId: null,
  adminCameraSelectedEntryId: null,
  adminCameraActionResult: null,
  printerEvents: new Map(),
  jobEvents: new Map(),
  jobExecutionSteps: new Map(),
  jobCardSections: new Map(),
  jobSynchronization: new Set(),
  printerSdCardFiles: new Map(),
  printerSdUploadStatus: new Map(),
  uploadStatusSynchronization: new Set(),
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

  if (!state.adminCameraPrinterId && state.printers.length > 0) {
    state.adminCameraPrinterId = state.selectedPrinterId || state.printers[0].id;
  }

  if (state.selectedPrinterId && !state.printers.some((printer) => printer.id === state.selectedPrinterId)) {
    state.selectedPrinterId = state.printers[0]?.id ?? null;
  }

  if (state.adminCameraPrinterId && !state.printers.some((printer) => printer.id === state.adminCameraPrinterId)) {
    state.adminCameraPrinterId = state.selectedPrinterId || state.printers[0]?.id || null;
  }
}

export function setJobs(jobs) {
  state.jobs = Array.isArray(jobs) ? jobs : [];
}

export function setJob(job) {
  if (!job || !job.id) {
    return;
  }

  const index = state.jobs.findIndex((candidate) => candidate.id === job.id);
  if (index === -1) {
    state.jobs = [job, ...state.jobs];
    return;
  }

  state.jobs = state.jobs.map((candidate) => candidate.id === job.id ? job : candidate);
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

export function setSerialTransferSettings(settings) {
  state.serialTransferSettings = settings;
}

export function setAppVersion(versionInfo) {
  state.appVersion = versionInfo || null;
}

export function setSecuritySettings(settings) {
  state.securitySettings = settings;
}

export function setSecurityRoles(roles) {
  state.securityRoles = Array.isArray(roles) ? roles : [];
}

export function setMonitoringOverview(overview) {
  state.monitoringOverview = overview || null;
}

export function setOperatorAuditEvents(events) {
  state.operatorAuditEvents = Array.isArray(events) ? events : [];
}

export function setCameraSnapshotJobs(jobs) {
  state.cameraSnapshotJobs = Array.isArray(jobs) ? jobs : [];
}

export function setAdminCameraPrinter(printerId) {
  if (!printerId) {
    state.adminCameraPrinterId = null;
    return;
  }

  const exists = state.printers.some((printer) => printer.id === printerId);
  state.adminCameraPrinterId = exists ? printerId : state.adminCameraPrinterId;
  state.adminCameraTimeline = [];
  state.adminCameraDeltaSets = [];
  state.adminCameraDeltaFrames = [];
  state.adminCameraCalculationRuns = [];
  state.adminCameraTraceRows = [];
  state.adminCameraSelectedJobId = null;
  state.adminCameraSelectedDeltaSetId = null;
  state.adminCameraSelectedCalculationRunId = null;
  state.adminCameraSelectedEntryId = null;
  state.adminCameraActionResult = null;
}

export function setAdminCameraTimeline(jobId, timeline) {
  state.adminCameraSelectedJobId = jobId || null;
  state.adminCameraTimeline = Array.isArray(timeline) ? timeline : [];
  state.adminCameraSelectedEntryId = state.adminCameraTimeline[0]?.id ?? null;
}

export function setAdminCameraAnalysisData(deltaSets, deltaFrames, calculationRuns, traceRows, selectedDeltaSetId, selectedCalculationRunId) {
  state.adminCameraDeltaSets = Array.isArray(deltaSets) ? deltaSets : [];
  state.adminCameraDeltaFrames = Array.isArray(deltaFrames) ? deltaFrames : [];
  state.adminCameraCalculationRuns = Array.isArray(calculationRuns) ? calculationRuns : [];
  state.adminCameraTraceRows = Array.isArray(traceRows) ? traceRows : [];
  state.adminCameraSelectedDeltaSetId = selectedDeltaSetId == null ? null : Number(selectedDeltaSetId);
  state.adminCameraSelectedCalculationRunId = selectedCalculationRunId == null ? null : Number(selectedCalculationRunId);
}

export function setAdminCameraSelectedDeltaSet(deltaSetId) {
  state.adminCameraSelectedDeltaSetId = deltaSetId == null || deltaSetId === "" ? null : Number(deltaSetId);
  state.adminCameraDeltaFrames = [];
  state.adminCameraCalculationRuns = [];
  state.adminCameraTraceRows = [];
  state.adminCameraSelectedCalculationRunId = null;
}

export function setAdminCameraSelectedCalculationRun(calculationRunId) {
  state.adminCameraSelectedCalculationRunId = calculationRunId == null || calculationRunId === ""
    ? null
    : Number(calculationRunId);
  state.adminCameraTraceRows = [];
}

export function setAdminCameraSelectedEntry(entryId) {
  state.adminCameraSelectedEntryId = entryId == null ? null : Number(entryId);
}

export function setAdminCameraActionResult(result) {
  state.adminCameraActionResult = result || null;
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

export function setJobSynchronization(jobId, active) {
  if (!jobId) {
    return;
  }

  if (active) {
    state.jobSynchronization.add(jobId);
    return;
  }

  state.jobSynchronization.delete(jobId);
}

export function isJobSynchronized(jobId) {
  if (!jobId) {
    return false;
  }

  return state.jobSynchronization.has(jobId);
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

export function setUploadStatusSynchronization(printerId, active) {
  if (!printerId) {
    return;
  }

  if (active) {
    state.uploadStatusSynchronization.add(printerId);
    return;
  }

  state.uploadStatusSynchronization.delete(printerId);
}

export function isUploadStatusSynchronized(printerId) {
  if (!printerId) {
    return false;
  }

  return state.uploadStatusSynchronization.has(printerId);
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

export function currentLocalRole() {
  return state.securitySettings?.defaultRole || "ADMIN";
}

export function currentRoleProfile() {
  const role = currentLocalRole();
  return state.securityRoles.find((profile) => profile.role === role) || null;
}

export function securityModeLabel() {
  return state.securitySettings?.securityEnabled === true ? "Security enabled" : "Security disabled";
}

export function hasPermission(permission) {
  if (!permission) {
    return true;
  }
  if (state.securitySettings?.securityEnabled !== true) {
    return true;
  }

  const profile = currentRoleProfile();
  const permissions = Array.isArray(profile?.permissions) ? profile.permissions : [];
  return permissions.includes(permission) || legacyPermissionAllows(permissions, permission);
}

export function disabledUnlessPermission(permission) {
  return hasPermission(permission) ? "" : `disabled title="${permissionDeniedLabel(permission)}"`;
}

export function permissionDeniedLabel(permission) {
  return `Current role ${currentLocalRole()} cannot execute ${permission}.`;
}

function legacyPermissionAllows(permissions, permission) {
  const legacyMap = {
    PRINTER_VIEW: ["VIEW_PRINTERS"],
    PRINTER_CONFIGURE: ["CONFIGURE_PRINTERS"],
    MONITORING_VIEW: ["VIEW_MONITORING"],
    MONITORING_CONFIGURE: ["CONFIGURE_MONITORING"],
    JOB_VIEW: ["VIEW_JOBS"],
    JOB_CREATE: ["CONTROL_JOBS"],
    JOB_START: ["CONTROL_JOBS"],
    JOB_PAUSE: ["CONTROL_JOBS"],
    JOB_RESUME: ["CONTROL_JOBS"],
    JOB_CANCEL: ["CONTROL_JOBS"],
    JOB_RESTART: ["CONTROL_JOBS"],
    JOB_DELETE: ["CONTROL_JOBS"],
    SD_VIEW: ["VIEW_PRINTERS"],
    SD_REFRESH: ["VIEW_PRINTERS"],
    SD_UPLOAD: ["UPLOAD_TO_SD_CARD"],
    SD_DELETE: ["MANAGE_SD_CARD_FILES"],
    SD_RECOVERY_CLOSE_UPLOAD: ["UPLOAD_TO_SD_CARD"],
    COMMAND_READ: ["EXECUTE_SAFE_COMMANDS"],
    COMMAND_SAFE_CONTROL: ["EXECUTE_SAFE_COMMANDS"],
    COMMAND_DANGEROUS_CONTROL: ["EXECUTE_DANGEROUS_COMMANDS"],
    COMMAND_RAW: ["EXECUTE_DANGEROUS_COMMANDS"],
    SETTINGS_VIEW: ["VIEW_SETTINGS"],
    SETTINGS_UPDATE: ["CONFIGURE_MONITORING", "CONFIGURE_TRANSFER_SETTINGS"],
    SECURITY_VIEW: ["MANAGE_SECURITY"],
    SECURITY_MANAGE: ["MANAGE_SECURITY"],
    CAMERA_DATA_MANAGE: ["MANAGE_SECURITY"]
  };

  return (legacyMap[permission] || []).some((legacyPermission) => permissions.includes(legacyPermission));
}
