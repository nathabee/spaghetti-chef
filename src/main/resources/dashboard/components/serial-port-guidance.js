import {  isSimulatedMode } from "../dashboard.js";
import { escapeHtml } from "../utils/format.js";


export function serialPortKind(mode, portName) {
  if (isSimulatedMode(mode)) {
    return "SIMULATED";
  }

  const normalizedPort = String(portName || "").trim();
  if (normalizedPort.startsWith("/dev/serial/by-id/")) {
    return "STABLE_LINUX_BY_ID";
  }
  if (normalizedPort.startsWith("/dev/ttyUSB") || normalizedPort.startsWith("/dev/ttyACM")) {
    return "UNSTABLE_LINUX_USB";
  }
  if (/^COM\d+$/i.test(normalizedPort)) {
    return "WINDOWS_COM";
  }
  if (normalizedPort.startsWith("/dev/")) {
    return "LINUX_DEVICE_PATH";
  }
  return "CUSTOM";
}

export function serialPathWarning(mode, portName, apiWarning = null) {
  if (apiWarning) {
    return apiWarning;
  }
  if (isSimulatedMode(mode)) {
    return null;
  }

  const normalizedPort = String(portName || "").trim();
  if (!normalizedPort) {
    return "Configured serial path is blank.";
  }
  if (normalizedPort.startsWith("/dev/ttyUSB") || normalizedPort.startsWith("/dev/ttyACM")) {
    return "This Linux USB serial path can change after reconnect or reboot. Prefer /dev/serial/by-id/... when available.";
  }
  if (normalizedPort.startsWith("/dev/") && !normalizedPort.startsWith("/dev/serial/by-id/")) {
    return "This device path is accepted, but /dev/serial/by-id/... is easier to identify and debug when available.";
  }
  return null;
}

export function stableSerialPath(mode, portName, apiStable = null) {
  if (apiStable !== null && apiStable !== undefined) {
    return apiStable === true;
  }
  const kind = serialPortKind(mode, portName);
  return kind === "SIMULATED" || kind === "STABLE_LINUX_BY_ID" || kind === "WINDOWS_COM";
}

export function renderSerialPathNotice(printer) {
  const warning = serialPathWarning(printer.mode, printer.portName, printer.serialPathWarning);
  if (!warning) {
    return "";
  }

  return `
    <div class="serial-path-notice">
      <strong>Serial path guidance</strong>
      <span>${escapeHtml(warning)}</span>
    </div>
  `;
}
