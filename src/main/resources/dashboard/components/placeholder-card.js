
import { escapeHtml } from "../utils/format.js";


export function renderPlaceholderCard(title, caption, items = []) {
  return `
    <article class="placeholder-card">
      <div class="section-header compact">
        <div>
          <h3>${escapeHtml(title)}</h3>
          <p class="placeholder-caption">${escapeHtml(caption)}</p>
        </div>
        <span class="badge badge-real">placeholder</span>
      </div>
      ${items.length > 0 ? `
        <ul class="placeholder-list">
          ${items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
        </ul>
      ` : ""}
    </article>
  `;
}
