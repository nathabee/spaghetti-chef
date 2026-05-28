import {
  captureCameraSnapshot,
  captureCameraAnalysisSample,
  getCameraAnalysisSamples,
  getCameraAnalysisSessions,
  getCameraCalculationRuns,
  getCameraCalculationTrace,
  getCameraDeltaFrames,
  getCameraDeltaSets,
  getCameraSnapshotFiles,
  getCameraSnapshotJobs,
  getCameraEvents,
  getCameraSettings,
  getCameraStatus,
  saveCameraSettings,
  startCameraAnalysisSession,
  stopCameraAnalysisSession,
  getActiveCameraJob,

} from "../api.js";

import { escapeHtml } from "../utils/format.js";
import { renderCameraAnalysisCard, renderCameraPage } from "../components/camera-card.js";

export function renderPrinterCameraLoading(printer) {
  if (!printer) {
    return `
      <div class="empty-state">
        <h3>No printer selected</h3>
        <p class="muted">Select a printer before opening the camera view.</p>
      </div>
    `;
  }

  return `
    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Camera</div>
          <h3>Loading camera monitoring</h3>
          <p class="muted">Reading camera status, settings, and recent activity for ${escapeHtml(printer.displayName || printer.name || printer.id)}.</p>
        </div>
      </div>
    </section>
  `;
}

export async function renderPrinterCamera(printer, snapshotRange = defaultSnapshotRange()) {
  if (!printer) {
    return `
      <div class="empty-state">
        <h3>No printer selected</h3>
        <p class="muted">Select a printer before opening the camera view.</p>
      </div>
    `;
  }

  try {
    const [status, settings, events, sessions, snapshotFiles, activeCameraJob] = await Promise.all([
      getCameraStatus(printer.id),
      getCameraSettings(printer.id),
      getCameraEvents(printer.id),
      getCameraAnalysisSessions(printer.id),
      getCameraSnapshotFiles(printer.id, snapshotRange.from, snapshotRange.to),
      getActiveCameraJob(printer.id)
    ]);
    const selectedSession = sessions.find((session) => session.state === "RUNNING") || sessions[0];
    const samples = selectedSession
      ? await getCameraAnalysisSamples(printer.id, selectedSession.id)
      : [];
    const analysisReview = await loadAnalysisReview(printer.id);

    return renderCameraPage(
      printer.id,
      status,
      settings,
      events,
      sessions,
      samples,
      snapshotFiles,
      snapshotRange,
      analysisReview,
      activeCameraJob
    );
  } catch (error) {
    return `
      <div class="empty-state error-state">
        <h3>Camera view failed</h3>
        <p>${escapeHtml(error.message || "Unable to load camera monitoring data.")}</p>
      </div>
    `;
  }
}

export async function renderPrinterCameraAnalysisPanel(printer) {
  if (!printer) {
    return "";
  }

  const [settings, sessions] = await Promise.all([
    getCameraSettings(printer.id),
    getCameraAnalysisSessions(printer.id)
  ]);
  const selectedSession = sessions.find((session) => session.state === "RUNNING") || sessions[0];
  const samples = selectedSession
    ? await getCameraAnalysisSamples(printer.id, selectedSession.id)
    : [];
  const analysisReview = await loadAnalysisReview(printer.id);

  return renderCameraAnalysisCard(printer.id, sessions, samples, settings.captureIntervalSeconds, analysisReview);
}

async function loadAnalysisReview(printerId) {
  try {
    const jobs = await getCameraSnapshotJobs(printerId);
    const cameraJob = jobs.find((job) => job.state === "ACTIVE" || job.state === "RUNNING") || jobs[0] || null;
    const cameraJobId = cameraJob?.cameraJobId ?? cameraJob?.cameraJobKey ?? cameraJob?.jobId ?? null;
    const deltaSets = cameraJobId ? await getCameraDeltaSets(cameraJobId, printerId) : [];
    const deltaSet = deltaSets[0] || null;
    const deltaFrames = deltaSet?.id ? await getCameraDeltaFrames(deltaSet.id, printerId) : [];
    const calculationRuns = deltaSet?.id ? await getCameraCalculationRuns(deltaSet.id, printerId) : [];
    const calculationRun = calculationRuns[0] || null;
    const traceRows = calculationRun?.id ? await getCameraCalculationTrace(calculationRun.id, printerId) : [];

    return {
      cameraJob,
      cameraJobId,
      sourceSnapshotCount: cameraJob?.fileCount ?? 0,
      deltaSet,
      deltaFrameCount: deltaFrames.length,
      calculationRun,
      traceRows
    };
  } catch (error) {
    return {
      error: error.message || "Unable to load analysis trace review.",
      traceRows: []
    };
  }
}

export async function capturePrinterCameraSnapshot(printerId) {
  return captureCameraSnapshot(printerId);
}

export async function savePrinterCameraSettings(printerId, form) {
  const payload = cameraSettingsPayload(form);
  return saveCameraSettings(printerId, payload);
}

export async function startPrinterCameraAnalysisSession(printerId) {
  return startCameraAnalysisSession(printerId);
}

export async function stopPrinterCameraAnalysisSession(printerId, sessionId) {
  return stopCameraAnalysisSession(printerId, sessionId);
}

export async function capturePrinterCameraAnalysisSample(printerId, sessionId) {
  return captureCameraAnalysisSample(printerId, sessionId);
}

export function cameraSnapshotRangeFromForm(form) {
  return {
    from: instantFromDateTimeLocal(form.querySelector("#cameraSnapshotFromInput")?.value),
    to: instantFromDateTimeLocal(form.querySelector("#cameraSnapshotToInput")?.value)
  };
}

function defaultSnapshotRange() {
  const to = new Date();
  const from = new Date(to.getTime() - 60 * 60 * 1000);

  return {
    from: from.toISOString(),
    to: to.toISOString()
  };
}

function instantFromDateTimeLocal(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toISOString();
}

function cameraSettingsPayload(form) {
  const enabled = form.querySelector("#cameraEnabledInput")?.checked === true;
  const analysisEnabled = form.querySelector("#cameraAnalysisEnabledInput")?.checked === true;
  const safetyEnabled = form.querySelector("#cameraSafetyEnabledInput")?.checked === true;
  const pauseOnConfirmedSpaghetti = form.querySelector("#cameraPauseOnConfirmedInput")?.checked === true;
  const diagnosticLoggingEnabled = form.querySelector("#cameraDiagnosticLoggingInput")?.checked === true;
  const purgeAutomatically = form.querySelector("#cameraPurgeAutomaticallyInput")?.checked === true;
  const sourceTypeInput = form.querySelector("#cameraSourceTypeInput");
  const sourceValueInput = form.querySelector("#cameraSourceValueInput");
  const storageDirectoryInput = form.querySelector("#cameraStorageDirectoryInput");
  const captureIntervalInput = form.querySelector("#cameraCaptureIntervalSecondsInput");
  const retentionInput = form.querySelector("#cameraRetentionSnapshotCountInput");
  const purgeRetentionFrequencyInput = form.querySelector("#cameraPurgeRetentionFrequencyInput");
  const confidenceThresholdInput = form.querySelector("#cameraConfidenceThresholdInput");
  const confirmationsRequiredInput = form.querySelector("#cameraConfirmationsRequiredInput");
  const ffmpegCommandInput = form.querySelector("#cameraFfmpegCommandInput");
  const ffmpegInputFormatInput = form.querySelector("#cameraFfmpegInputFormatInput");
  const ffmpegVideoSizeInput = form.querySelector("#cameraFfmpegVideoSizeInput");
  const ffmpegTimeoutMsInput = form.querySelector("#cameraFfmpegTimeoutMsInput");
  const ffmpegJpegQualityInput = form.querySelector("#cameraFfmpegJpegQualityInput");

  const sourceType = sourceTypeInput?.value || "disabled";

  return {
    enabled,
    sourceType: enabled ? sourceType : "disabled",
    sourceValue: sourceValueInput?.value?.trim() || "",
    storageDirectory: storageDirectoryInput?.value?.trim() || "camera",
    captureIntervalSeconds: positiveInteger(captureIntervalInput?.value, 10),
    retentionSnapshotCount: positiveInteger(retentionInput?.value, 20),
    purgeAutomatically,
    purgeRetentionFrequency: positiveInteger(purgeRetentionFrequencyInput?.value, 5),
    analysisEnabled,
    safetyEnabled,
    pauseOnConfirmedSpaghetti,
    diagnosticLoggingEnabled,
    confidenceThreshold: ratio(confidenceThresholdInput?.value, 0.85),
    confirmationsRequired: positiveInteger(confirmationsRequiredInput?.value, 3),
    ffmpegCommand: ffmpegCommandInput?.value?.trim() || "ffmpeg",
    ffmpegInputFormat: ffmpegInputFormatInput?.value?.trim() || "",
    ffmpegVideoSize: ffmpegVideoSizeInput?.value?.trim() || "640x480",
    ffmpegTimeoutMs: positiveInteger(ffmpegTimeoutMsInput?.value, 5000),
    ffmpegJpegQuality: positiveInteger(ffmpegJpegQualityInput?.value, 3)
  };
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);

  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }

  return parsed;
}

function ratio(value, fallback) {
  const parsed = Number.parseFloat(value);

  if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 1) {
    return fallback;
  }

  return parsed;
}
