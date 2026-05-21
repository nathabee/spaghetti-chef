import { escapeHtml } from "../utils/format.js";
import { hasPermission } from "../state.js";

export function renderAdminCameraDataPage(jobs = []) {
  if (!hasPermission("CAMERA_DATA_MANAGE")) {
    return `
      <section class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Camera picture management</h3>
            <p class="placeholder-caption">This area is reserved for administrators.</p>
          </div>
          <span class="badge badge-warning">restricted</span>
        </div>
      </section>
    `;
  }

  return `
    <section class="two-column-grid">
      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Camera archive jobs</h3>
            <p class="placeholder-caption">Backend archive rows are grouped by print job id or unassigned captures.</p>
          </div>
          <span class="badge badge-real">0.4.7</span>
        </div>
        ${renderJobTable(jobs)}
      </article>

      <article class="placeholder-card">
        <div class="section-header compact">
          <div>
            <h3>Replay and cleanup</h3>
            <p class="placeholder-caption">0.4.7 exposes admin endpoints for timeline replay, job archive deletion, and recalculation previews.</p>
          </div>
          <span class="badge badge-real">backend ready</span>
        </div>
        <ul class="placeholder-list">
          <li>Replay: selected archive frame, related delta, and detector values.</li>
          <li>Cleanup: delete archive files and archive metadata by job id.</li>
          <li>Recalculate: placeholder endpoint for testing detector parameter changes.</li>
          <li>Dashboard wiring and richer controls continue in 0.4.8.</li>
        </ul>
      </article>
    </section>
  `;
}

function renderJobTable(jobs) {
  if (!Array.isArray(jobs) || jobs.length === 0) {
    return `<p class="muted">No camera archive jobs have been recorded yet.</p>`;
  }

  return `
    <div class="table-scroll compact-table">
      <table>
        <thead>
          <tr>
            <th>Job id</th>
            <th>Files</th>
            <th>Bytes</th>
            <th>First capture</th>
            <th>Last capture</th>
          </tr>
        </thead>
        <tbody>
          ${jobs.map((job) => `
            <tr>
              <td>${escapeHtml(job.jobId ?? "unassigned")}</td>
              <td>${escapeHtml(String(job.fileCount ?? 0))}</td>
              <td>${escapeHtml(String(job.totalBytes ?? 0))}</td>
              <td>${escapeHtml(job.firstCapturedAt ?? "-")}</td>
              <td>${escapeHtml(job.lastCapturedAt ?? "-")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}
