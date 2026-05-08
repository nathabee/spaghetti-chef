import { escapeHtml } from "../dashboard.js";
import { getPrinterSdTargetFilter, getPrinterSdUploadStatus, state } from "../state.js";

export function renderPrinterSdCard(printer) {
  const data = state.printerSdCardFiles.get(printer.id);
  const files = data?.files ?? [];
  const registeredFiles = state.printerSdFiles.filter((file) => file.printerId === printer.id);
  const registeredFilter = getPrinterSdTargetFilter(printer.id);
  const filteredRegisteredFiles = filterRegisteredFiles(registeredFiles, registeredFilter);
  const uploadStatus = getPrinterSdUploadStatus(printer.id);

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">SD Card</div>
          <h2>Files on ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Printer-side files reported by the firmware, plus the registered printable targets used by print jobs.</p>
        </div>
        <div class="action-row">
          <button type="button" data-load-sd-card-files="${escapeHtml(printer.id)}">Refresh files</button>
        </div>
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
          data-line-number="2"
        >Close upload session</button>
      </div>

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
      ` : renderRegisteredFileTable(filteredRegisteredFiles)}

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
          <button type="submit">Register SD target</button>
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
          <button type="submit">Register host file</button>
        </div>
      </form>

      ${state.printFiles.length === 0 ? `
        <div class="empty-state">
          <h3>No host print files registered</h3>
          <p class="muted">Use the form above to add prepared .gcode files to PrinterHub.</p>
        </div>
        ` : renderHostFileTable(printer.id)}
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

  const stateLabel = uploadStatus.state || "info";
  const badgeClass = stateLabel === "success"
    ? "badge-real"
    : stateLabel === "error"
      ? "badge-sim"
      : "badge-real";

  const title = stateLabel === "running"
    ? "Upload in progress"
    : stateLabel === "success"
      ? "Last upload"
      : "Upload error";

  return `
    <div class="empty-state">
      <div class="section-header compact">
        <div>
          <h3>${escapeHtml(title)}</h3>
          <p class="muted">${escapeHtml(uploadStatus.message || "")}</p>
        </div>
        <span class="badge ${badgeClass}">${escapeHtml(stateLabel.toUpperCase())}</span>
      </div>
    </div>
  `;
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

function renderRegisteredFileTable(files) {
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
                >${file.enabled === true ? "Disable" : "Enable"}</button>
                <button
                  type="button"
                  class="danger-button small-button"
                  data-delete-printer-sd-file-id="${escapeHtml(file.id)}"
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

function renderHostFileTable(printerId) {
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
