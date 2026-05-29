import {  isSimulatedMode } from "../dashboard.js";

import { escapeHtml } from "../utils/format.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { renderSerialPathNotice, serialPortKind, stableSerialPath } from "../components/serial-port-guidance.js";
import { currentLocalRole, disabledUnlessPermission, securityModeLabel, state } from "../state.js";

 export function renderSettingsPage() {
  const monitoringRules = state.monitoringRules || {};
  const printFileSettings = state.printFileSettings || {};
  const serialTransferSettings = state.serialTransferSettings || {};
  const engineSettings = state.cameraCalculationEngineSettings || [];
  const securitySettings = state.securitySettings || {};
  const appVersion = state.appVersion || {};
  const securityRoles = state.securityRoles || [];
  const printers = state.printers;
  const settingsDisabled = disabledUnlessPermission("SETTINGS_UPDATE");
  const monitoringDisabled = disabledUnlessPermission("MONITORING_CONFIGURE");
  const securityDisabled = disabledUnlessPermission("SECURITY_MANAGE");
  const printerConfigDisabled = disabledUnlessPermission("PRINTER_CONFIGURE");

  return `
    <section class="section-card security-context-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Local access</div>
          <h2>${escapeHtml(securityModeLabel())}</h2>
          <p class="lead">Current dashboard role: ${escapeHtml(currentLocalRole())}. Running SpaghettiChef ${escapeHtml(appVersion.version || "unknown")}.</p>
        </div>
        <span class="badge ${securitySettings.securityEnabled === true ? "badge-enabled" : "badge-disabled"}">${escapeHtml(currentLocalRole())}</span>
      </div>
    </section>

    <section class="two-column-grid">
      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Monitoring rules</h2>
            <p class="lead">Runtime polling and persistence settings already available in the backend.</p>
          </div>
        </div>

        <form id="monitoringRulesForm" class="form-grid">
          <label>
            Poll interval seconds
            <input id="pollIntervalSecondsInput" name="pollIntervalSeconds" type="number" step="1" min="1" value="${escapeHtml(monitoringRules.pollIntervalSeconds ?? 5)}" required>
          </label>

          <label>
            Snapshot minimum interval seconds
            <input id="snapshotMinimumIntervalSecondsInput" name="snapshotMinimumIntervalSeconds" type="number" step="1" min="0" value="${escapeHtml(monitoringRules.snapshotMinimumIntervalSeconds ?? 30)}" required>
          </label>

          <label>
            Temperature delta threshold
            <input id="temperatureDeltaThresholdInput" name="temperatureDeltaThreshold" type="number" step="0.1" min="0" value="${escapeHtml(monitoringRules.temperatureDeltaThreshold ?? 1.0)}" required>
          </label>

          <label>
            Event deduplication window seconds
            <input id="eventDeduplicationWindowSecondsInput" name="eventDeduplicationWindowSeconds" type="number" step="1" min="0" value="${escapeHtml(monitoringRules.eventDeduplicationWindowSeconds ?? 60)}" required>
          </label>

          <label>
            Error persistence behavior
            <select id="errorPersistenceBehaviorInput" name="errorPersistenceBehavior" required>
              <option value="DEDUPLICATED" ${(monitoringRules.errorPersistenceBehavior ?? "DEDUPLICATED") === "DEDUPLICATED" ? "selected" : ""}>DEDUPLICATED</option>
              <option value="ALWAYS" ${(monitoringRules.errorPersistenceBehavior ?? "DEDUPLICATED") === "ALWAYS" ? "selected" : ""}>ALWAYS</option>
            </select>
          </label>

          <label class="checkbox-label">
            <input id="debugWireTracingEnabledInput" name="debugWireTracingEnabled" type="checkbox" ${(monitoringRules.debugWireTracingEnabled ?? false) ? "checked" : ""}>
            Enable printer wire trace logging
          </label>

          <div class="form-actions">
            <button type="submit" ${monitoringDisabled}>Save monitoring rules</button>
          </div>
        </form>
      </article>

      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Serial transfer</h2>
            <p class="lead">Runtime SD upload and file-streaming limits used by printer transfer operations.</p>
          </div>
        </div>

        <form id="serialTransferSettingsForm" class="form-grid">
          <label>
            SD upload batch size
            <input id="transferSdUploadBatchSizeInput" name="sdUploadBatchSize" type="number" step="1" min="1" max="100" value="${escapeHtml(serialTransferSettings.sdUploadBatchSize ?? 5)}" required>
          </label>

          <label>
            SD upload recovery window multiplier
            <input id="transferSdUploadRecoveryWindowMultiplierInput" name="sdUploadRecoveryWindowMultiplier" type="number" step="1" min="1" max="100" value="${escapeHtml(serialTransferSettings.sdUploadRecoveryWindowMultiplier ?? 2)}" required>
          </label>

          <label>
            SD upload max errors
            <input id="transferSdUploadMaxErrorsInput" name="sdUploadMaxErrors" type="number" step="1" min="1" max="1000000" value="${escapeHtml(serialTransferSettings.sdUploadMaxErrors ?? 100)}" required>
          </label>

          <label>
            SD upload max consecutive identical resends
            <input id="transferSdUploadMaxConsecutiveIdenticalResendsInput" name="sdUploadMaxConsecutiveIdenticalResends" type="number" step="1" min="1" max="1000" value="${escapeHtml(serialTransferSettings.sdUploadMaxConsecutiveIdenticalResends ?? 10)}" required>
          </label>

          <label>
            SD upload min performance percent
            <input id="transferSdUploadMinPerformancePercentInput" name="sdUploadMinPerformancePercent" type="number" step="1" min="0" max="100" value="${escapeHtml(serialTransferSettings.sdUploadMinPerformancePercent ?? 5)}" required>
          </label>

          <label>
            SD upload max retries per line
            <input id="transferSdUploadMaxRetriesPerLineInput" name="sdUploadMaxRetriesPerLine" type="number" step="1" min="1" max="100" value="${escapeHtml(serialTransferSettings.sdUploadMaxRetriesPerLine ?? 3)}" required>
          </label>

          <label>
            File streaming read timeout ms
            <input id="transferFileStreamingReadTimeoutMsInput" name="fileStreamingReadTimeoutMs" type="number" step="1" min="1" max="600000" value="${escapeHtml(serialTransferSettings.fileStreamingReadTimeoutMs ?? 5000)}" required>
          </label>

          <label>
            File streaming quiet period ms
            <input id="transferFileStreamingQuietPeriodMsInput" name="fileStreamingQuietPeriodMs" type="number" step="1" min="0" max="60000" value="${escapeHtml(serialTransferSettings.fileStreamingQuietPeriodMs ?? 10)}" required>
          </label>

          <label>
            File streaming activity sleep ms
            <input id="transferFileStreamingReadActivitySleepMsInput" name="fileStreamingReadActivitySleepMs" type="number" step="1" min="0" max="60000" value="${escapeHtml(serialTransferSettings.fileStreamingReadActivitySleepMs ?? 1)}" required>
          </label>

          <label>
            File streaming idle sleep ms
            <input id="transferFileStreamingReadIdleSleepMsInput" name="fileStreamingReadIdleSleepMs" type="number" step="1" min="0" max="60000" value="${escapeHtml(serialTransferSettings.fileStreamingReadIdleSleepMs ?? 1)}" required>
          </label>

          <label>
            File streaming recovery replay delay ms
            <input id="transferFileStreamingRecoveryReplayDelayMsInput" name="fileStreamingRecoveryReplayDelayMs" type="number" step="1" min="0" max="60000" value="${escapeHtml(serialTransferSettings.fileStreamingRecoveryReplayDelayMs ?? 15)}" required>
          </label>

          <div class="form-actions">
            <button type="submit" ${settingsDisabled}>Save serial transfer settings</button>
          </div>
        </form>
      </article>

      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Print file storage</h2>
            <p class="lead">Directory where uploaded .gcode files are saved by SpaghettiChef.</p>
          </div>
        </div>

        <form id="printFileSettingsForm" class="form-grid">
          <label>
            Storage directory
            <input id="printFileStorageDirectoryInput" name="storageDirectory" type="text" value="${escapeHtml(printFileSettings.storageDirectory ?? "spaghettichef-print-files")}" required>
          </label>

          <div class="form-actions">
            <button type="submit" ${settingsDisabled}>Save print file settings</button>
          </div>
        </form>
      </article>

      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Calculation engines</h2>
            <p class="lead">Defaults used by live analysis and on-demand recalculation.</p>
          </div>
        </div>

        <div class="list-block">
          ${engineSettings.length === 0 ? `<p class="muted">No calculation engine settings loaded yet.</p>` : engineSettings.map((settings) => renderEngineSettingsForm(settings, settingsDisabled)).join("")}
        </div>
      </article>

      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Local security</h2>
            <p class="lead">Local role defaults used by the upcoming backend permission guards.</p>
          </div>
        </div>

        <form id="securitySettingsForm" class="form-grid">
          <label class="checkbox-label">
            <input id="securityEnabledInput" name="securityEnabled" type="checkbox" ${securitySettings.securityEnabled === true ? "checked" : ""}>
            Enable local security checks
          </label>

          <label>
            Default local role
            <select id="securityDefaultRoleInput" name="defaultRole" required>
              ${renderRoleOptions(securitySettings.defaultRole || "ADMIN")}
            </select>
          </label>

          <label class="checkbox-label">
            <input id="securityDangerousConfirmationInput" name="requireDangerousActionConfirmation" type="checkbox" ${securitySettings.requireDangerousActionConfirmation !== false ? "checked" : ""}>
            Require dangerous action confirmation
          </label>

          <div class="form-actions">
            <button type="submit" ${securityDisabled}>Save security settings</button>
          </div>
        </form>

        <div class="list-block">
          ${securityRoles.length === 0 ? `<p class="muted">Role profiles not loaded yet.</p>` : securityRoles.map(renderRoleProfile).join("")}
        </div>
      </article>

      <article class="section-card">
        <div class="section-header">
          <div>
            <h2>Printer administration</h2>
            <p class="lead">Create or update configured printer nodes.</p>
          </div>
        </div>

        <form id="printerConfigForm" class="form-grid">
          <label>
            Printer ID
            <input id="printerIdInput" name="id" type="text" placeholder="printer-1" required>
          </label>

          <label>
            Name
            <input id="printerNameInput" name="name" type="text" placeholder="Primary printer" required>
          </label>

          <label>
            Port
            <input id="printerPortInput" name="portName" type="text" placeholder="/dev/serial/by-id/... or COM3 or SIM_PORT" required>
            <span class="field-hint">For Linux real printers, prefer a stable /dev/serial/by-id/... path instead of /dev/ttyUSB0.</span>
          </label>

          <label>
            Mode
            <select id="printerModeInput" name="mode" required>
              <option value="real">real</option>
              <option value="sim">sim</option>
              <option value="simulated">simulated</option>
              <option value="sim-disconnected">sim-disconnected</option>
              <option value="sim-timeout">sim-timeout</option>
              <option value="sim-error">sim-error</option>
            </select>
          </label>

          <div class="form-actions">
            <button type="submit" ${printerConfigDisabled}>Save printer</button>
            <button id="clearPrinterFormButton" type="button" class="secondary-button" ${printerConfigDisabled}>Clear form</button>
          </div>
        </form>

        <div class="list-block">
          ${printers.length === 0 ? `<p class="muted">No configured printers found.</p>` : printers.map(renderConfiguredPrinter).join("")}
        </div>
      </article>
    </section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
        "General runtime settings",
        "Reserved for later settings beyond monitoring rules and printer administration.",
        [
          "Retention policy",
          "Runtime defaults",
          "Notification settings"
        ]
      )}
      ${renderPlaceholderCard(
        "Capability and profile settings",
        "Reserved for richer printer profiles and capability metadata.",
        [
          "Printer capability profile",
          "Maintenance profile",
          "Production job defaults"
        ]
      )}
    </section>
  `;
}

function renderRoleOptions(selectedRole) {
  return ["VIEWER", "OPERATOR", "ADMIN"].map((role) => `
    <option value="${role}" ${selectedRole === role ? "selected" : ""}>${role}</option>
  `).join("");
}

function renderRoleProfile(profile) {
  const permissions = Array.isArray(profile.permissions) ? profile.permissions : [];
  return `
    <article class="config-card compact-config-card">
      <div class="section-header compact">
        <div>
          <h3>${escapeHtml(profile.displayName || profile.role)}</h3>
          <p class="meta">${escapeHtml(profile.role || "n/a")} · ${permissions.length} permissions</p>
        </div>
        <span class="badge ${profile.builtIn ? "badge-enabled" : "badge-disabled"}">${profile.builtIn ? "built-in" : "custom"}</span>
      </div>
      <p class="muted">${permissions.map(escapeHtml).join(", ")}</p>
    </article>
  `;
}

function renderEngineSettingsForm(settings, disabled) {
  const engineName = settings.engineName || "";
  const isRust = engineName.includes("RUST");
  return `
    <form class="config-card form-grid" data-engine-settings-form="${escapeHtml(engineName)}">
      <div class="section-header compact">
        <div>
          <h3>${escapeHtml(settings.engineLabel || engineName)}</h3>
          <p class="meta">${escapeHtml(engineName)}</p>
        </div>
        <span class="badge ${settings.enabled ? "badge-enabled" : "badge-disabled"}">${settings.enabled ? "enabled" : "disabled"}</span>
      </div>

      <label class="checkbox-label">
        <input name="enabled" type="checkbox" ${settings.enabled ? "checked" : ""}>
        Enabled
      </label>

      <label>
        Engine label
        <input name="engineLabel" type="text" value="${escapeHtml(settings.engineLabel || engineName)}" required>
      </label>

      <label>
        Default method
        <input name="defaultMethodName" type="text" value="${escapeHtml(settings.defaultMethodName || "spaghetti-heuristic")}" required>
      </label>

      <label>
        Default confidence threshold
        <input name="defaultConfidenceThreshold" type="number" min="0" max="1" step="0.01" value="${escapeHtml(settings.defaultConfidenceThreshold ?? 0.85)}" required>
      </label>

      <label>
        Default parameters JSON
        <input name="defaultParameterJson" type="text" value="${escapeHtml(settings.defaultParameterJson || "{}")}" required>
      </label>

      ${isRust ? `
        <label>
          Rust executable path
          <input name="executablePath" type="text" value="${escapeHtml(settings.executablePath || "")}">
        </label>

        <label>
          Rust CLI method
          <input name="defaultCliMethod" type="text" value="${escapeHtml(settings.defaultCliMethod || "delta-basic")}">
        </label>
      ` : `
        <input name="executablePath" type="hidden" value="${escapeHtml(settings.executablePath || "")}">
        <input name="defaultCliMethod" type="hidden" value="${escapeHtml(settings.defaultCliMethod || "")}">
      `}

      <label>
        Timeout ms
        <input name="timeoutMs" type="number" min="1" step="1" value="${escapeHtml(settings.timeoutMs ?? 10000)}" required>
      </label>

      <label>
        Sort order
        <input name="sortOrder" type="number" min="0" step="1" value="${escapeHtml(settings.sortOrder ?? 10)}" required>
      </label>

      <div class="form-actions">
        <button type="submit" ${disabled}>Save ${escapeHtml(settings.engineLabel || engineName)}</button>
      </div>
    </form>
  `;
}

function renderConfiguredPrinter(printer) {
  const portKind = printer.serialPortKind || serialPortKind(printer.mode, printer.portName);
  const stablePath = stableSerialPath(printer.mode, printer.portName, printer.stableSerialPath);

  return `
    <article class="config-card">
      <div class="section-header compact">
        <div>
          <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
          <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")} · ${escapeHtml(printer.mode || "n/a")}</p>
        </div>
        <div class="badge-row">
          <span class="badge ${printer.enabled ? "badge-enabled" : "badge-disabled"}">${printer.enabled ? "enabled" : "disabled"}</span>
          <span class="badge ${isSimulatedMode(printer.mode) ? "badge-simulated" : "badge-real"}">${isSimulatedMode(printer.mode) ? "simulated" : "real"}</span>
          <span class="badge ${stablePath ? "badge-enabled" : "badge-simulated"}">${escapeHtml(portKind)}</span>
        </div>
      </div>
      ${renderSerialPathNotice(printer)}
      <div class="action-row">
        <button type="button" class="secondary-button" data-config-action="edit" data-printer-id="${escapeHtml(printer.id)}" ${disabledUnlessPermission("PRINTER_CONFIGURE")}>Edit</button>
        <button type="button" class="secondary-button" data-config-action="enable" data-printer-id="${escapeHtml(printer.id)}" ${disabledUnlessPermission("PRINTER_CONFIGURE")}>Enable</button>
        <button type="button" class="secondary-button" data-config-action="disable" data-printer-id="${escapeHtml(printer.id)}" ${disabledUnlessPermission("PRINTER_CONFIGURE")}>Disable</button>
        <button type="button" class="danger-button" data-config-action="delete" data-printer-id="${escapeHtml(printer.id)}" ${disabledUnlessPermission("PRINTER_CONFIGURE")}>Delete</button>
      </div>
    </article>
  `;
}
