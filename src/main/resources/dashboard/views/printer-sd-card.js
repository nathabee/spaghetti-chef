import { escapeHtml } from "../dashboard.js";
import {
  disabledUnlessPermission,
  getPrinterSdTargetFilter,
  getPrinterSdUploadStatus,
  hasPermission,
  isUploadStatusSynchronized,
  state
} from "../state.js";

export function renderPrinterSdCard(printer) {
  const data = state.printerSdCardFiles.get(printer.id);
  const files = data?.files ?? [];
  const registeredFiles = state.printerSdFiles.filter((file) => file.printerId === printer.id);
  const registeredFilter = getPrinterSdTargetFilter(printer.id);
  const filteredRegisteredFiles = filterRegisteredFiles(registeredFiles, registeredFilter);
  const uploadStatus = getPrinterSdUploadStatus(printer.id);
  const uploadActive = uploadStatus?.active === true;
  const uploadSyncActive = isUploadStatusSynchronized(printer.id);
  const sdNotice = !hasPermission("SD_UPLOAD") || !hasPermission("SD_DELETE")
    ? `<p class="muted">Some SD-card actions are disabled for the current role.</p>`
    : "";

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">SD Card</div>
          <h2>Files on ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Printer-side files reported by the firmware, plus the registered printable targets used by print jobs.</p>
        </div>
        <div class="action-row">
          <button type="button" data-load-sd-card-files="${escapeHtml(printer.id)}" ${uploadActive ? "disabled" : disabledUnlessPermission("SD_REFRESH")}>Refresh files</button>
          <button
            type="button"
            data-sync-sd-upload-status="${escapeHtml(printer.id)}"
            ${uploadSyncActive ? "disabled" : ""}
          >${uploadSyncActive ? "Synchronizing..." : "Synchronize"}</button>
          <button
            type="button"
            class="secondary-button"
            data-stop-sync-sd-upload-status="${escapeHtml(printer.id)}"
            ${uploadSyncActive ? "" : "disabled"}
          >Stop sync</button>
        </div>
      </div>
      <div class="sync-status-row">
        <span class="sync-active-indicator ${uploadSyncActive ? "sync-active" : "sync-idle"}"></span>
        <span>${uploadSyncActive ? "Live upload sync active" : "Manual refresh only"}</span>
      </div>
            ${renderSdUploadStatus(uploadStatus)}

      
      ${files.length === 0
      ? ` 
      ${renderEmptyState(data)} 
  `
      : `
    <details class="events-section">
      <summary class="events-header">SD card files (${files.length})</summary>
      ${renderFirmwareFileTable(printer.id, files, registeredFiles)}
    </details>
  `
    }



      <details class="events-section">
        <summary class="events-header">
          <h4>Raw firmware response</h4>
        </summary>
        <pre class="command-result">${escapeHtml(data?.rawResponse || "Not loaded yet.")}</pre>
      </details>
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Registered targets</div>
          <h3>Printable files known for this printer</h3>
          <p class="lead">Print jobs select one of these registered printer-side paths.</p>
        </div>
        <button
          type="button"
          class="secondary-button"
          data-close-sd-upload-session
          data-printer-id="${escapeHtml(printer.id)}"
          ${uploadActive ? "disabled" : disabledUnlessPermission("SD_RECOVERY_CLOSE_UPLOAD")}
        >Close upload session</button>
      </div>
      ${sdNotice}

      ${renderRegisteredTargetFilters(printer.id, registeredFilter)}

      ${registeredFiles.length === 0 ? `
        <div class="empty-state">
          <h3>No registered SD target</h3>
          <p class="muted">Refresh the SD card and register an existing firmware file, or register a known printer-side path below.</p>
        </div>
      ` : filteredRegisteredFiles.length === 0 ? `
        <div class="empty-state">
          <h3>No matching SD target</h3>
          <p class="muted">Adjust the filters to show more registered printer-side files.</p>
        </div>
      ` : renderRegisteredFileTable(filteredRegisteredFiles, uploadActive)}

      <form id="printerSdFileForm" class="form-grid">
        <input id="printerSdFilePrinterIdInput" name="printerId" type="hidden" value="${escapeHtml(printer.id)}">

        <label>
          Printer-side path
          <input id="printerSdFilePathInput" name="firmwarePath" type="text" placeholder="CE3E3V~1.GCO" required>
        </label>

        <label>
          Display name
          <input id="printerSdFileDisplayNameInput" name="displayName" type="text" placeholder="Optional friendly name">
        </label>

        <label>
          Linked host file
          <select id="printerSdFilePrintFileIdInput" name="printFileId">
            ${buildHostPrintFileOptions()}
          </select>
        </label>

        <div class="form-actions">
          <button type="submit" ${disabledUnlessPermission("SD_UPLOAD")}>Register SD target</button>
        </div>
      </form>
    </section>

    <section class="section-card">
      <div class="section-header compact">
        <div>
          <div class="kicker">Host files</div>
          <h3>PrinterHub .gcode library</h3>
          <p class="lead">Upload or register prepared .gcode files on the PrinterHub host before mapping them to a printer-side path.</p>
        </div>
      </div>

      <form id="printFileForm" class="form-grid">
        <label>
          Pick .gcode file
          <input id="printFileUploadInput" name="printFileUpload" type="file" accept=".gcode,.gco">
        </label>

        <label>
          Or host path
          <input id="printFilePathInput" name="printFilePath" type="text" placeholder="/home/user/prints/benchy.gcode">
        </label>

        <div class="form-actions">
          <button type="submit" ${disabledUnlessPermission("MANAGE_PRINT_FILES")}>Register host file</button>
        </div>
      </form>

      ${state.printFiles.length === 0 ? `
        <div class="empty-state">
          <h3>No host print files registered</h3>
          <p class="muted">Use the form above to add prepared .gcode files to PrinterHub.</p>
        </div>
        ` : renderHostFileTable(printer.id, uploadActive)}
    </section>
  `;
}

function renderRegisteredTargetFilters(printerId, filter) {
  return `
    <div class="form-grid compact-form">
      <label>
        Availability
        <select data-sd-target-filter="availability" data-printer-id="${escapeHtml(printerId)}">
          ${renderFilterOption("all", "All", filter.availability)}
          ${renderFilterOption("available", "Available", filter.availability)}
          ${renderFilterOption("deleted", "Deleted", filter.availability)}
        </select>
      </label>
      <label>
        Enabled
        <select data-sd-target-filter="enabled" data-printer-id="${escapeHtml(printerId)}">
          ${renderFilterOption("all", "All", filter.enabled)}
          ${renderFilterOption("enabled", "Enabled", filter.enabled)}
          ${renderFilterOption("disabled", "Disabled", filter.enabled)}
        </select>
      </label>
      <label>
        Host link
        <select data-sd-target-filter="link" data-printer-id="${escapeHtml(printerId)}">
          ${renderFilterOption("all", "All", filter.link)}
          ${renderFilterOption("linked", "Linked", filter.link)}
          ${renderFilterOption("unlinked", "Unlinked", filter.link)}
        </select>
      </label>
    </div>
  `;
}

function renderFilterOption(value, label, selectedValue) {
  return `<option value="${escapeHtml(value)}" ${selectedValue === value ? "selected" : ""}>${escapeHtml(label)}</option>`;
}

function filterRegisteredFiles(files, filter) {
  return files.filter((file) => {
    if (filter.availability === "available" && file.deleted === true) {
      return false;
    }
    if (filter.availability === "deleted" && file.deleted !== true) {
      return false;
    }
    if (filter.enabled === "enabled" && file.enabled !== true) {
      return false;
    }
    if (filter.enabled === "disabled" && file.enabled === true) {
      return false;
    }
    if (filter.link === "linked" && !file.printFileId) {
      return false;
    }
    if (filter.link === "unlinked" && file.printFileId) {
      return false;
    }
    return true;
  });
}

function renderEmptyState(data) {
  return `
    <div class="empty-state">
      <h3>${data ? "No SD-card files reported" : "SD-card files not loaded"}</h3>
      <p class="muted">${data ? "The printer did not return printable files for this request." : "Use Refresh files to ask the selected printer for its SD-card file list."}</p>
    </div>
  `;
}

function renderSdUploadStatus(uploadStatus) {
  if (!uploadStatus) {
    return "";
  }

  const stateLabel = String(uploadStatus.state || "info");
  const badgeClass = stateLabel === "success"
    ? "badge-real"
    : stateLabel === "error"
      ? "badge-sim"
      : "badge-real";

  const title = stateLabel === "running"
    ? "Upload in progress"
    : stateLabel === "success"
      ? "Last upload"
      : "Upload status";

  const totalLineCount = toNumber(uploadStatus.totalLineCount, 0);
  const uploadedLineCount = toNumber(uploadStatus.uploadedLineCount, 0);
  const rejectedLineCount = toNumber(uploadStatus.rejectedLineCount, 0);
  const totalByteCount = toNumber(uploadStatus.totalByteCount, 0);
  const uploadedByteCount = totalByteCount > 0 && totalLineCount > 0
    ? Math.min(totalByteCount, Math.floor((totalByteCount * uploadedLineCount) / totalLineCount))
    : null;

  const percent = totalLineCount > 0
    ? Math.min(
      100,
      Math.max(
        0,
        toNumber(
          uploadStatus.percent,
          Math.floor((uploadedLineCount * 100) / totalLineCount)
        )
      )
    )
    : toNumber(uploadStatus.percent, 0);

  const qualityPercent = toNumber(
    uploadStatus.qualityPercent,
    calculateUploadQualityPercent(uploadedLineCount, rejectedLineCount)
  );

  const bytesPerSecond = toNullableNumber(uploadStatus.bytesPerSecond);
  const linesPerSecond = toNullableNumber(uploadStatus.linesPerSecond);
  const elapsedSeconds = toNullableNumber(uploadStatus.elapsedSeconds);
  const estimatedSecondsRemaining = toNullableNumber(uploadStatus.estimatedSecondsRemaining);
  const activeBatchSize = toNullableNumber(uploadStatus.activeBatchSize);
  const configuredMinBatchSize = toNullableNumber(uploadStatus.configuredMinBatchSize);
  const configuredMaxBatchSize = toNullableNumber(uploadStatus.configuredMaxBatchSize);
  const acceptedLinesSinceLastResend = toNullableNumber(uploadStatus.acceptedLinesSinceLastResend);
  const stableLinesForUpgrade = toNullableNumber(uploadStatus.stableLinesForUpgrade);
  const recentResendCount = toNullableNumber(uploadStatus.recentResendCount);
  const resendThresholdForDowngrade = toNullableNumber(uploadStatus.resendThresholdForDowngrade);
  const recoveryCount = toNullableNumber(uploadStatus.recoveryCount);
  const recoveryThresholdForMinBatch = toNullableNumber(uploadStatus.recoveryThresholdForMinBatch);

  const qualityClass = resolveUploadQualityClass(qualityPercent, rejectedLineCount);
  const health = resolveUploadHealth(uploadStatus, rejectedLineCount, qualityPercent, recentResendCount);

  const progressHtml = totalLineCount > 0
    ? `
      <div class="sd-upload-progress-block">
        <div class="sd-upload-progress-header">
          <span>Progress</span>
          <strong>${escapeHtml(String(percent))}%</strong>
        </div>
        ${renderMeter("Upload progress", percent, "normal")}
      </div>
    `
    : `
      <div class="sd-upload-progress-header">
        <span>Progress</span>
        <strong>${escapeHtml(String(uploadedLineCount))} confirmed lines</strong>
      </div>
    `;

  const lineValue = `${uploadedLineCount}/${totalLineCount || "n/a"}`;
  const byteValue = uploadedByteCount === null
    ? formatSize(totalByteCount)
    : `${formatSize(uploadedByteCount)} / ${formatSize(totalByteCount)}`;

  const operatorStats = [
    renderStatTile("Lines", lineValue, "Confirmed"),
    renderStatTile("Bytes", byteValue, "Uploaded"),
    renderStatTile("ETA", estimatedSecondsRemaining === null ? "n/a" : formatTimeRemaining(estimatedSecondsRemaining), "Remaining"),
    renderStatTile("Quality", `${qualityPercent}%`, rejectedLineCount > 0 ? `${rejectedLineCount} resend${rejectedLineCount === 1 ? "" : "s"}` : "No resends")
  ].join("");

  const throughputStats = [
    renderStatTile("Bytes/sec", bytesPerSecond === null ? "n/a" : formatDecimal(bytesPerSecond, 1), "Throughput"),
    renderStatTile("Lines/sec", linesPerSecond === null ? "n/a" : formatDecimal(linesPerSecond, 2), "Line rate"),
    renderStatTile("Elapsed", elapsedSeconds === null ? "n/a" : formatTimeRemaining(elapsedSeconds), "Runtime")
  ].join("");

  const currentDecisionRows = [
    renderMetricRow("Mode", uploadStatus.transportMode),
    renderMetricRow("Configured range", configuredMinBatchSize === null || configuredMaxBatchSize === null ? null : `${configuredMinBatchSize}-${configuredMaxBatchSize}`),
    renderMetricRow("Active batch size", activeBatchSize),
    renderMetricRow("Single-send mode", uploadStatus.singleSendMode === undefined ? null : uploadStatus.singleSendMode ? "yes" : "no"),
    renderMetricRow("Last adaptation at", uploadStatus.lastAdaptationAt)
  ].join("");

  const configuredLimitRows = [
    renderMetricRow("Configured max batch size", uploadStatus.configuredMaxBatchSize),
    renderMetricRow("Configured min batch size", uploadStatus.configuredMinBatchSize),
    renderMetricRow("Batch upgrade step", uploadStatus.batchUpgradeStep),
    renderMetricRow("Batch downgrade step", uploadStatus.batchDowngradeStep)
  ].join("");

  const stabilityRows = [
    renderMetricRow("Stable lines toward upgrade", acceptedLinesSinceLastResend === null || stableLinesForUpgrade === null ? null : `${acceptedLinesSinceLastResend}/${stableLinesForUpgrade}`),
    renderMetricRow("Recent resend window lines", uploadStatus.recentResendWindowLines),
    renderMetricRow("Recent resend pressure", recentResendCount === null || resendThresholdForDowngrade === null ? null : `${recentResendCount}/${resendThresholdForDowngrade}`),
    renderMetricRow("Recovery pressure", recoveryCount === null || recoveryThresholdForMinBatch === null ? null : `${recoveryCount}/${recoveryThresholdForMinBatch}`)
  ].join("");

  const stabilityPercent = calculateRatioPercent(acceptedLinesSinceLastResend, stableLinesForUpgrade);
  const resendPercent = calculateRatioPercent(recentResendCount, resendThresholdForDowngrade);
  const recoveryPercent = calculateRatioPercent(recoveryCount, recoveryThresholdForMinBatch);

  const qualityHtml = `
    <div class="upload-quality ${qualityClass}">
      <div class="info-row">
        <span>Transfer quality</span>
        <strong>${escapeHtml(String(qualityPercent))}%</strong>
      </div>
      ${renderMeter("Transfer quality", qualityPercent, qualityPercent >= 99 ? "good" : qualityPercent >= 95 ? "warn" : "bad")}
      <p class="muted">${escapeHtml(String(rejectedLineCount))} rejected/resend request${rejectedLineCount === 1 ? "" : "s"} for ${escapeHtml(String(uploadedLineCount))} confirmed line${uploadedLineCount === 1 ? "" : "s"}.</p>
    </div>
  `;

  return `
    <div class="sd-upload-monitoring">
      <article class="sd-upload-card sd-upload-status-card sd-upload-health-${escapeHtml(health.key)}">
        <div class="sd-upload-hero">
          <div>
            <div class="kicker">Upload status</div>
            <h3>${escapeHtml(title)}</h3>
            <p class="muted">${escapeHtml(uploadStatus.originalFilename || uploadStatus.requestedTargetFilename || "No file name reported")}</p>
          </div>
          <div class="sd-upload-state-stack">
            <span class="sd-upload-health-badge">${escapeHtml(health.label)}</span>
            <span class="badge ${badgeClass}">${escapeHtml(stateLabel.toUpperCase())}</span>
          </div>
        </div>

        ${progressHtml}
        ${qualityHtml}
        <div class="sd-upload-stat-grid">
          ${operatorStats}
        </div>
        <div class="sd-upload-stat-grid compact">
          ${throughputStats}
        </div>
        <div class="sd-upload-detail-line">
          <span>Last detail</span>
          <strong>${escapeHtml(uploadStatus.message || uploadStatus.detail || "n/a")}</strong>
        </div>
      </article>

      <details class="sd-upload-card sd-upload-diagnostics-card" open>
        <summary class="events-header">
          <span>Adaptive tuning / transfer diagnostics</span>
          <span class="sd-upload-mode-badge">${escapeHtml(uploadStatus.transportMode || "n/a")}</span>
        </summary>
        <div class="sd-upload-controller-strip">
          <span class="sd-upload-batch-chip">Batch ${escapeHtml(String(activeBatchSize ?? "n/a"))}</span>
          <span>${escapeHtml(configuredMinBatchSize === null || configuredMaxBatchSize === null ? "Range n/a" : `Range ${configuredMinBatchSize}-${configuredMaxBatchSize}`)}</span>
          <strong>${escapeHtml(uploadStatus.lastAdaptationReason || "No adaptation recorded")}</strong>
        </div>
        <div class="sd-upload-pressure-grid">
          ${renderPressurePanel("Stability to upgrade", acceptedLinesSinceLastResend, stableLinesForUpgrade, stabilityPercent, "good", "stable lines")}
          ${renderPressurePanel("Resend pressure", recentResendCount, resendThresholdForDowngrade, resendPercent, resendPercent >= 100 ? "bad" : resendPercent > 0 ? "warn" : "good", "resends")}
          ${renderPressurePanel("Recovery pressure", recoveryCount, recoveryThresholdForMinBatch, recoveryPercent, recoveryPercent >= 100 ? "bad" : recoveryPercent > 0 ? "warn" : "good", "recoveries")}
        </div>
        ${renderMetricGroup("Current runtime decision", currentDecisionRows)}
        ${renderMetricGroup("Configured limits", configuredLimitRows)}
        ${renderMetricGroup("Stability and resend pressure", stabilityRows)}
      </details>
    </div>
  `;
}

function renderStatTile(label, value, hint) {
  return `
    <div class="sd-upload-stat-tile">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(String(value))}</strong>
      <small>${escapeHtml(hint)}</small>
    </div>
  `;
}

function renderPressurePanel(title, value, max, percent, tone, caption) {
  const displayValue = value === null || max === null ? "n/a" : `${value}/${max}`;
  return `
    <div class="sd-upload-pressure-panel tone-${escapeHtml(tone)}">
      <div class="sd-upload-pressure-header">
        <span>${escapeHtml(title)}</span>
        <strong>${escapeHtml(displayValue)}</strong>
      </div>
      ${renderMeter(title, percent, tone)}
      <small>${escapeHtml(caption)}</small>
    </div>
  `;
}

function renderMeter(label, percent, tone) {
  const normalizedPercent = Number.isFinite(Number(percent))
    ? Math.max(0, Math.min(100, Number(percent)))
    : 0;

  return `
    <div class="sd-upload-meter tone-${escapeHtml(tone)}" aria-label="${escapeHtml(label)}">
      <span style="width: ${escapeHtml(String(normalizedPercent))}%"></span>
    </div>
  `;
}

function renderMetricGroup(title, rowsHtml) {
  return `
    <section class="sd-upload-metric-group">
      <h4>${escapeHtml(title)}</h4>
      <div class="info-list">
        ${rowsHtml}
      </div>
    </section>
  `;
}

function renderMetricRow(label, value) {
  if (value === undefined || value === null || value === "") {
    return `
      <div class="info-row">
        <span>${escapeHtml(label)}</span>
        <strong>n/a</strong>
      </div>
    `;
  }

  return `
    <div class="info-row">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(String(value))}</strong>
    </div>
  `;
}

function calculateUploadQualityPercent(uploadedLineCount, rejectedLineCount) {
  const uploaded = toNumber(uploadedLineCount, 0);
  const rejected = toNumber(rejectedLineCount, 0);

  if (uploaded <= 0) {
    return 100;
  }

  return Math.max(0, Math.min(100, Math.floor((uploaded * 100) / (uploaded + rejected))));
}

function calculateRatioPercent(value, max) {
  const numericValue = Number(value);
  const numericMax = Number(max);

  if (!Number.isFinite(numericValue) || !Number.isFinite(numericMax) || numericMax <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(100, Math.floor((numericValue * 100) / numericMax)));
}

function toNumber(value, fallback = 0) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : fallback;
}

function toNullableNumber(value) {
  if (value === undefined || value === null || value === "") {
    return null;
  }

  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function formatDecimal(value, digits = 1) {
  const numericValue = Number(value);

  if (!Number.isFinite(numericValue)) {
    return "n/a";
  }

  return numericValue.toFixed(digits);
}

function formatTimeRemaining(seconds) {
  const numericSeconds = Number(seconds);

  if (!Number.isFinite(numericSeconds) || numericSeconds < 0) {
    return "n/a";
  }

  if (numericSeconds < 60) {
    return `${Math.round(numericSeconds)}s`;
  }

  const minutes = Math.floor(numericSeconds / 60);
  const remainingSeconds = Math.round(numericSeconds % 60);

  if (minutes < 60) {
    return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
  }

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
}

function resolveUploadQualityClass(qualityPercent, rejectedLineCount) {
  if (rejectedLineCount <= 0) {
    return "upload-quality-good";
  }
  if (qualityPercent >= 95) {
    return "upload-quality-warn";
  }
  return "upload-quality-bad";
}

function resolveUploadHealth(uploadStatus, rejectedLineCount, qualityPercent, recentResendCount) {
  const state = String(uploadStatus.state || "").toLowerCase();
  const transportMode = String(uploadStatus.transportMode || "").toUpperCase();

  if (state === "error") {
    return { key: "failed", label: "Failed" };
  }
  if (uploadStatus.singleSendMode === true || transportMode === "SINGLE_SEND") {
    return { key: "fallback", label: "Fallback" };
  }
  if (uploadStatus.active === true && qualityPercent < 95) {
    return { key: "degraded", label: "Degraded" };
  }
  if (uploadStatus.active === true && toNumber(recentResendCount, 0) > 0) {
    return { key: "recovering", label: "Recovering" };
  }
  if (uploadStatus.active === true && rejectedLineCount === 0 && qualityPercent >= 99) {
    return { key: "healthy", label: "Healthy" };
  }
  if (state === "success") {
    return { key: "complete", label: "Complete" };
  }

  return { key: "idle", label: "Idle" };
}
 

function renderFirmwareFileTable(printerId, files, registeredFiles) {
  const registeredPaths = new Set(registeredFiles.map((file) => file.firmwarePath));

  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Filename</th>
            <th>Size</th>
            <th>Raw detail</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${files.map((file) => renderFirmwareFileRow(printerId, file, registeredPaths.has(file.filename))).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderFirmwareFileRow(printerId, file, registered) {
  const filename = file.filename || "";
  const sizeBytes = file.sizeBytes ?? "";
  const rawLine = file.rawLine || filename;

  return `
    <tr>
      <td>${escapeHtml(filename || "n/a")}</td>
      <td>${escapeHtml(formatSize(file.sizeBytes))}</td>
      <td>${escapeHtml(rawLine)}</td>
      <td>
        <button
          type="button"
          class="secondary-button small-button"
          data-register-sd-card-file
          data-printer-id="${escapeHtml(printerId)}"
          data-firmware-path="${escapeHtml(filename)}"
          data-display-name="${escapeHtml(filename)}"
          data-raw-line="${escapeHtml(rawLine)}"
          data-size-bytes="${escapeHtml(sizeBytes)}"
          ${registered ? "disabled" : ""}
        >${registered ? "Registered" : "Register"}</button>
      </td>
    </tr>
  `;
}

function renderRegisteredFileTable(files, uploadActive) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Display name</th>
            <th>Printer-side path</th>
            <th>Size</th>
            <th>Host file</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${files.map((file) => `
            <tr>
              <td>${escapeHtml(file.displayName || file.firmwarePath || file.id)}</td>
              <td>${escapeHtml(file.firmwarePath || "n/a")}</td>
              <td>${escapeHtml(formatSize(file.sizeBytes))}</td>
              <td>${escapeHtml(resolveHostFileLabel(file.printFileId))}</td>
              <td>
                <span class="badge ${resolvePrinterSdStatusClass(file)}">
                  ${escapeHtml(resolvePrinterSdStatusLabel(file))}
                </span>
              </td>
              <td>
                ${file.deleted === true
      ? `<span class="muted">Deleted</span>`
      : `
                <button
                  type="button"
                  class="secondary-button small-button"
                  data-printer-sd-file-id="${escapeHtml(file.id)}"
                  data-printer-sd-file-enabled="${file.enabled === true ? "false" : "true"}"
                  ${uploadActive ? "disabled" : disabledUnlessPermission("SD_UPLOAD")}
                >${file.enabled === true ? "Disable" : "Enable"}</button>
                <button
                  type="button"
                  class="danger-button small-button"
                  data-delete-printer-sd-file-id="${escapeHtml(file.id)}"
                  ${uploadActive ? "disabled" : disabledUnlessPermission("SD_DELETE")}
                >Delete</button>
                `}
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderHostFileTable(printerId, uploadActive) {
  return `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Filename</th>
            <th>Path</th>
            <th>Size</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${state.printFiles.map((file) => `
            <tr>
              <td>${escapeHtml(file.originalFilename || file.id)}</td>
              <td>${escapeHtml(file.path || "n/a")}</td>
              <td>${escapeHtml(formatSize(file.sizeBytes))}</td>
              <td>
                <button
                  type="button"
                  class="secondary-button small-button"
                  data-upload-print-file-to-sd
                  data-printer-id="${escapeHtml(printerId)}"
                  data-print-file-id="${escapeHtml(file.id)}"
                  data-target-filename="${escapeHtml(defaultSdTargetFilename(file.originalFilename || file.id))}"
                  ${uploadActive ? "disabled" : disabledUnlessPermission("SD_UPLOAD")}
                >Upload to SD card</button>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function buildHostPrintFileOptions() {
  return [
    `<option value="">No host file link</option>`,
    ...state.printFiles.map((file) => `
      <option value="${escapeHtml(file.id)}">
        ${escapeHtml(file.originalFilename || file.path || file.id)}
      </option>
    `)
  ].join("");
}

function resolveHostFileLabel(printFileId) {
  if (!printFileId) {
    return "none";
  }

  const file = state.printFiles.find((candidate) => candidate.id === printFileId);
  return file?.originalFilename || file?.path || printFileId;
}

function formatSize(sizeBytes) {
  if (sizeBytes === null || sizeBytes === undefined) {
    return "n/a";
  }

  const numericSize = Number(sizeBytes);

  if (!Number.isFinite(numericSize)) {
    return "n/a";
  }

  return `${numericSize} bytes`;
}

function resolvePrinterSdStatusLabel(file) {
  if (file.deleted === true) {
    return "Deleted";
  }
  return file.enabled === true ? "Enabled" : "Disabled";
}

function resolvePrinterSdStatusClass(file) {
  if (file.deleted === true) {
    return "badge-disabled";
  }
  return file.enabled === true ? "badge-real" : "badge-sim";
}

function defaultSdTargetFilename(filename) {
  if (!filename) {
    return "UPLOAD.GCO";
  }

  const normalized = String(filename)
    .trim()
    .replace(/^.*[\\/]/, "")
    .replace(/[^A-Za-z0-9._-]/g, "_");

  if (!normalized) {
    return "UPLOAD.GCO";
  }

  if (/\.gcode$/i.test(normalized) || /\.gco$/i.test(normalized)) {
    return normalized;
  }

  return `${normalized}.gco`;
}
