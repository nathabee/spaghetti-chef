async function requestJson(path, options = {}) {
  const response = await fetch(path, options);
  const body = await safeJson(response);

  if (!response.ok) {
    throw new Error(body.error || `HTTP ${response.status}`);
  }

  return body;
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

export async function getCameraAnalysisSamples(printerId, sessionId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions/${encodeURIComponent(sessionId)}/samples`);
  return Array.isArray(data.samples) ? data.samples : [];
}

export async function captureCameraAnalysisSample(printerId, sessionId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/camera/analysis-sessions/${encodeURIComponent(sessionId)}/samples`, {
    method: "POST"
  });
}

export function cameraSnapshotUrl(printerId) {
  return `/printers/${encodeURIComponent(printerId)}/camera/snapshot?t=${Date.now()}`;
}
