import {
  captureCameraSnapshot,
  getCameraEvents,
  getCameraSettings,
  getCameraStatus,
  saveCameraSettings
} from "../api.js";
import { escapeHtml } from "../dashboard.js";
import { renderCameraPage } from "../components/camera-card.js";

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

export async function renderPrinterCamera(printer) {
  if (!printer) {
    return `
      <div class="empty-state">
        <h3>No printer selected</h3>
        <p class="muted">Select a printer before opening the camera view.</p>
      </div>
    `;
  }

  try {
    const [status, settings, events] = await Promise.all([
      getCameraStatus(printer.id),
      getCameraSettings(printer.id),
      getCameraEvents(printer.id)
    ]);

    return renderCameraPage(printer.id, status, settings, events);
  } catch (error) {
    return `
      <div class="empty-state error-state">
        <h3>Camera view failed</h3>
        <p>${escapeHtml(error.message || "Unable to load camera monitoring data.")}</p>
      </div>
    `;
  }
}

export async function capturePrinterCameraSnapshot(printerId) {
  return captureCameraSnapshot(printerId);
}

export async function savePrinterCameraSettings(printerId, form) {
  const payload = cameraSettingsPayload(form);
  return saveCameraSettings(printerId, payload);
}

function cameraSettingsPayload(form) {
  const enabled = form.querySelector("#cameraEnabledInput")?.checked === true;
  const sourceTypeInput = form.querySelector("#cameraSourceTypeInput");
  const sourceValueInput = form.querySelector("#cameraSourceValueInput");
  const captureIntervalInput = form.querySelector("#cameraCaptureIntervalSecondsInput");
  const retentionInput = form.querySelector("#cameraRetentionSnapshotCountInput");

  const sourceType = sourceTypeInput?.value || "disabled";

  return {
    enabled,
    sourceType: enabled ? sourceType : "disabled",
    sourceValue: sourceValueInput?.value?.trim() || "",
    captureIntervalSeconds: positiveInteger(captureIntervalInput?.value, 10),
    retentionSnapshotCount: positiveInteger(retentionInput?.value, 20)
  };
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);

  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }

  return parsed;
}