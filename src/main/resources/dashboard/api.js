// BEGIN ano #54
export class ApiNetworkError extends Error {
  constructor(path, cause) {
    super(`Network error while requesting ${path}: ${cause?.message || "request failed"}`);
    this.name = "ApiNetworkError";
    this.path = path;
    this.cause = cause;
    this.networkError = true;
  }
}

export function isApiNetworkError(error) {
  return error instanceof ApiNetworkError || error?.networkError === true;
}

async function requestJson(path, options = {}) {
  let response;

  try {
    response = await fetch(path, options);
  } catch (error) {
    throw new ApiNetworkError(path, error);
  }

  const body = await safeJson(response);

  if (!response.ok) {
    const detail = body.message || body.error || `HTTP ${response.status}`;
    throw new Error(body.error && body.message ? `${body.error}: ${body.message}` : detail);
  }

  return body;
}
// END ano #54

export async function getActiveCameraJob(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/jobs/active`);
}

export async function startCameraJob(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/jobs/start`, {
    method: "POST"
  });
}

export async function stopCameraJob(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/jobs/stop`, {
    method: "POST"
  });
}



async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function getPrinters() {
  const data = await requestJson("/printers");
  return Array.isArray(data.printers) ? data.printers : [];
}

export async function getMonitoringOverview() {
  return requestJson("/monitoring");
}

export async function getAppVersion() {
  return requestJson("/version");
}

export async function getOperatorAuditEvents() {
  const data = await requestJson("/operator-audit");
  return Array.isArray(data.auditEvents) ? data.auditEvents : [];
}

export async function createPrinter(printer) {
  return requestJson("/printers", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(printer)
  });
}

export async function updatePrinter(printerId, printer) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(printer)
  });
}

export async function deletePrinter(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}`, {
    method: "DELETE"
  });
}

export async function setPrinterEnabled(printerId, enabled) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/${enabled ? "enable" : "disable"}`, {
    method: "POST"
  });
}

export async function executePrinterCommand(printerId, command, confirmation = {}) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/commands`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ command, ...confirmation })
  });
}

export async function getPrinterEvents(printerId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/events`);
  return Array.isArray(data.events) ? data.events : [];
}

export async function getPrinterSdCardFiles(printerId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/files`);
  return {
    files: Array.isArray(data.files) ? data.files : [],
    rawResponse: data.rawResponse || ""
  };
}

export async function getMonitoringRules() {
  return requestJson("/settings/monitoring");
}

export async function getPrintFileSettings() {
  return requestJson("/settings/print-files");
}

export async function getSerialTransferSettings() {
  return requestJson("/settings/serial-transfer");
}

export async function getSecuritySettings() {
  return requestJson("/settings/security");
}

export async function getSecurityRoles() {
  const data = await requestJson("/security/roles");
  return Array.isArray(data.roleProfiles) ? data.roleProfiles : [];
}

export async function saveMonitoringRules(rules) {
  return requestJson("/settings/monitoring", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(rules)
  });
}

export async function savePrintFileSettings(settings) {
  return requestJson("/settings/print-files", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(settings)
  });
}

export async function saveSerialTransferSettings(settings) {
  return requestJson("/settings/serial-transfer", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(settings)
  });
}

export async function saveSecuritySettings(settings) {
  return requestJson("/settings/security", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(settings)
  });
}

export async function getJobs() {
  const data = await requestJson("/jobs");
  return Array.isArray(data.jobs) ? data.jobs : [];
}

export async function getJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}`);
}

export async function getPrintFiles() {
  const data = await requestJson("/print-files");
  return Array.isArray(data.printFiles) ? data.printFiles : [];
}

export async function getPrinterSdFiles(printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/printer-sd-files${query}`);
  return Array.isArray(data.printerSdFiles) ? data.printerSdFiles : [];
}

export async function registerPrinterSdFile(file) {
  return requestJson("/printer-sd-files", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(file)
  });
}

export async function setPrinterSdFileEnabled(printerSdFileId, enabled) {
  return requestJson(`/printer-sd-files/${encodeURIComponent(printerSdFileId)}/${enabled ? "enable" : "disable"}`, {
    method: "POST"
  });
}

export async function deletePrinterSdFile(printerSdFileId, confirmation = {}) {
  return requestJson(`/printer-sd-files/${encodeURIComponent(printerSdFileId)}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(confirmation)
  });
}

export async function uploadPrinterSdFile(printerId, printFileId, targetFilename = "", confirmation = {}) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/uploads`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      printFileId,
      targetFilename,
      ...confirmation
    })
  });
}

export async function getPrinterSdUploadStatus(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/uploads/status`);
}

export async function registerPrintFile(path) {
  return requestJson("/print-files", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ path })
  });
}

export async function uploadPrintFile(file) {
  return requestJson(`/print-files/uploads?filename=${encodeURIComponent(file.name)}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/octet-stream"
    },
    body: file
  });
}




export async function getPrintFileContent(printFileId) {
  return requestJson(`/print-files/${encodeURIComponent(printFileId)}/content`);
}

export async function createJob(job) {
  return requestJson("/jobs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(job)
  });
}

export async function startJob(jobId, confirmation = {}) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/start`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(confirmation)
  });
}

export async function pauseJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/pause`, {
    method: "POST"
  });
}

export async function resumeJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/resume`, {
    method: "POST"
  });
}

export async function cancelJob(jobId, confirmation = {}) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(confirmation)
  });
}

export async function restartJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/restart`, {
    method: "POST"
  });
}

export async function deleteJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}`, {
    method: "DELETE"
  });
}

export async function closePrinterSdUploadSession(printerId, confirmation = {}) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/sd-card/recovery/close-upload`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(confirmation)
  });
}

export async function getJobEvents(jobId) {
  const data = await requestJson(`/jobs/${encodeURIComponent(jobId)}/events`);
  return Array.isArray(data.events) ? data.events : [];
}

export async function getJobExecutionSteps(jobId) {
  const data = await requestJson(`/jobs/${encodeURIComponent(jobId)}/execution-steps`);
  return Array.isArray(data.executionSteps) ? data.executionSteps : [];
}


export async function getCameraStatus(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/status`);
}

export async function getCameraSettings(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/settings`);
}

export async function saveCameraSettings(printerId, settings) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/settings`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(settings)
  });
}

export async function captureCameraSnapshot(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/snapshot`, {
    method: "POST"
  });
}

export async function getCameraEvents(printerId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/camera/events`);
  return Array.isArray(data) ? data : [];
}

export async function getCameraAnalysisSessions(printerId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions`);
  return Array.isArray(data.sessions) ? data.sessions : [];
}

export async function startCameraAnalysisSession(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions`, {
    method: "POST"
  });
}

export async function stopCameraAnalysisSession(printerId, sessionId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions/${encodeURIComponent(sessionId)}/stop`, {
    method: "POST"
  });
}

export async function getCameraAnalysisSamples(printerId, sessionId, limit = 200) {
  const params = new URLSearchParams();
  if (limit) {
    params.set("limit", String(limit));
  }
  const query = params.toString();
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions/${encodeURIComponent(sessionId)}/samples${query ? `?${query}` : ""}`);
  return Array.isArray(data.samples) ? data.samples : [];
}

export async function captureCameraAnalysisSample(printerId, sessionId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions/${encodeURIComponent(sessionId)}/samples`, {
    method: "POST"
  });
}

export async function getCameraSnapshotFiles(printerId, from, to) {
  const params = new URLSearchParams();
  if (from) {
    params.set("from", from);
  }
  if (to) {
    params.set("to", to);
  }

  const query = params.toString();
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/camera/snapshots${query ? `?${query}` : ""}`);
  return Array.isArray(data.files) ? data.files : [];
}

export function cameraSnapshotFileUrl(printerId, fileId) {
  return `/printers/${encodeURIComponent(printerId)}/camera/snapshots/${encodeURIComponent(fileId)}?t=${Date.now()}`;
}

export async function getCameraSnapshotJobs(printerId) {
  const params = new URLSearchParams();
  if (printerId) {
    params.set("printerId", printerId);
  }

  const query = params.toString();
  const data = await requestJson(`/admin/camera/snapshot/jobs${query ? `?${query}` : ""}`);
  return Array.isArray(data.jobs) ? data.jobs : [];
}

export async function getCameraSnapshotJobEntries(jobId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}${query}`);
  return Array.isArray(data.entries) ? data.entries : [];
}

export async function deleteCameraSnapshotJob(jobId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  return requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}${query}`, {
    method: "DELETE"
  });
}

export async function deleteCameraJobData(jobId, printerId, options = {}) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  return requestJson(`/admin/camera/jobs/${encodeURIComponent(jobId)}${query}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      deleteSnapshotFiles: options.deleteSnapshotFiles !== false,
      deleteSnapshotRows: options.deleteSnapshotRows !== false,
      deleteDeltaFiles: options.deleteDeltaFiles !== false,
      deleteDeltaRows: options.deleteDeltaRows !== false,
      deleteCalculationRuns: options.deleteCalculationRuns !== false,
      deleteCameraEvents: options.deleteCameraEvents !== false,
      deleteCameraJob: options.deleteCameraJob !== false,
      requiredConfirmation: options.requiredConfirmation || "DELETE_CAMERA_JOB"
    })
  });
}

export async function purgeCameraSnapshotJob(jobId, parameters = {}) {
  const query = parameters.printerId ? `?printerId=${encodeURIComponent(parameters.printerId)}` : "";
  return requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}/purge${query}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(parameters)
  });
}

export async function getCameraSnapshotJobTimeline(jobId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}/timeline${query}`);
  return Array.isArray(data.timeline) ? data.timeline : [];
}

export async function previewCameraSnapshotRecalculation(jobId, parameters = {}) {
  return requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}/recalculate-preview`, {
    method: "POST",
    body: JSON.stringify(parameters)
  });
}

export async function generateCameraDeltaSet(jobId, parameters = {}) {
  const query = parameters.printerId ? `?printerId=${encodeURIComponent(parameters.printerId)}` : "";
  return requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}/delta-sets${query}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(parameters)
  });
}

export async function getCameraDeltaSets(jobId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/snapshot/jobs/${encodeURIComponent(jobId)}/delta-sets${query}`);
  return Array.isArray(data.deltaSets) ? data.deltaSets : [];
}

export async function getCameraDeltaFrames(deltaSetId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/delta-sets/${encodeURIComponent(deltaSetId)}/frames${query}`);
  return Array.isArray(data.frames) ? data.frames : [];
}

export async function deleteCameraDeltaSet(deltaSetId, printerId, options = {}) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  return requestJson(`/admin/camera/delta-sets/${encodeURIComponent(deltaSetId)}${query}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      deleteDeltaFiles: options.deleteDeltaFiles !== false,
      deleteDeltaRows: options.deleteDeltaRows !== false,
      deleteCalculationRuns: options.deleteCalculationRuns !== false,
      requiredConfirmation: options.requiredConfirmation || "DELETE_DELTA_SET"
    })
  });
}

export async function getCameraCalculationRuns(deltaSetId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/delta-sets/${encodeURIComponent(deltaSetId)}/calculation-runs${query}`);
  return Array.isArray(data.calculationRuns) ? data.calculationRuns : [];
}

export async function runCameraCalculation(deltaSetId, parameters = {}) {
  return requestJson(`/admin/camera/delta-sets/${encodeURIComponent(deltaSetId)}/calculation-runs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(parameters)
  });
}

export async function getCameraCalculationTrace(calculationRunId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  const data = await requestJson(`/admin/camera/calculation-runs/${encodeURIComponent(calculationRunId)}/trace${query}`);
  return Array.isArray(data.trace) ? data.trace : [];
}

export async function getCameraCalculationVisual(calculationResultId, printerId) {
  const query = printerId ? `?printerId=${encodeURIComponent(printerId)}` : "";
  return requestJson(`/admin/camera/calculation-results/${encodeURIComponent(calculationResultId)}/visual${query}`);
}

export async function getCameraCalculationComparison(leftRunId, rightRunId, printerId) {
  const params = new URLSearchParams();
  params.set("rightRunId", rightRunId);
  if (printerId) {
    params.set("printerId", printerId);
  }
  return requestJson(`/admin/camera/calculation-runs/${encodeURIComponent(leftRunId)}/compare?${params.toString()}`);
}

export function adminCameraSnapshotEntryUrl(entryId) {
  return `/admin/camera/snapshot/files/${encodeURIComponent(entryId)}?t=${Date.now()}`;
}

export function cameraSnapshotUrl(printerId, version = "") {
  const cacheKey = version || Date.now();
  return `/printers/${encodeURIComponent(printerId)}/camera/snapshot?v=${encodeURIComponent(cacheKey)}`;
}
