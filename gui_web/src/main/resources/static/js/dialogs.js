// Generic dialog/popup helpers (design §9).

function el(html) {
    const t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
}

function openOverlay(dialogHtml) {
    const root = document.getElementById('dialog-root');
    const overlay = el(`<div class="dialog-overlay"><div class="dialog">${dialogHtml}</div></div>`);
    root.appendChild(overlay);
    overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
    function close() { overlay.remove(); }
    return { overlay, close };
}

// Confirmation popup — exact format from flow §5:
// title, "The following effects will be applied:", then a list of
// "⚠ <path>" + indented reason, then [Confirm] [Cancel].
export function showConfirmDialog({ title = 'Confirm action', effects, onConfirm }) {
    const items = effects.map(e => `
        <div class="effect-item">
            <div class="effect-title">&#9888; ${escapeHtml(e.title)}</div>
            <div class="effect-detail">${escapeHtml(e.detail)}</div>
        </div>
    `).join('');

    const { overlay, close } = openOverlay(`
        <h2>${escapeHtml(title)}</h2>
        <p>The following effects will be applied:</p>
        <div class="effect-list">${items}</div>
        <div class="dialog-actions">
            <button class="btn btn-secondary" data-act="cancel">Cancel</button>
            <button class="btn btn-primary" data-act="confirm">Confirm</button>
        </div>
    `);
    overlay.querySelector('[data-act="cancel"]').addEventListener('click', close);
    overlay.querySelector('[data-act="confirm"]').addEventListener('click', () => { close(); onConfirm(); });
}

export function showTextInputDialog({ title, label, defaultValue = '', metaHtml = '', confirmLabel = 'Create', onConfirm, onCancel }) {
    const { overlay, close } = openOverlay(`
        <h2>${escapeHtml(title)}</h2>
        <label>${escapeHtml(label)}</label>
        <input type="text" id="dialog-text-input" value="${escapeHtml(defaultValue)}" autocomplete="off">
        ${metaHtml ? `<div class="meta">${metaHtml}</div>` : ''}
        <div class="dialog-actions">
            <button class="btn btn-secondary" data-act="cancel">Cancel</button>
            <button class="btn btn-primary" data-act="confirm">${escapeHtml(confirmLabel)}</button>
        </div>
    `);
    const input = overlay.querySelector('#dialog-text-input');
    input.focus();
    input.select();
    const submit = () => { const v = input.value.trim(); if (!v) return; close(); onConfirm(v); };
    const cancel = () => { close(); if (onCancel) onCancel(); };
    overlay.querySelector('[data-act="cancel"]').addEventListener('click', cancel);
    overlay.querySelector('[data-act="confirm"]').addEventListener('click', submit);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); if (e.key === 'Escape') cancel(); });
}

// Generic "unsaved draft" conflict dialog (flow §12b) — reused by both Amend
// (phase 6) and Recomposition (phase 7) whenever starting a session would
// require deciding what to do with the current NEW_PATCH draft.
export function showDraftConflictDialog({ onKeep, onOverwrite }) {
    const { overlay, close } = openOverlay(`
        <h2>Unsaved draft</h2>
        <p>You have unsaved staged changes for a new patch. What do you want to do with them?</p>
        <div class="dialog-actions">
            <button class="btn btn-secondary" data-act="cancel">Cancel</button>
            <button class="btn btn-secondary" data-act="keep">Keep draft</button>
            <button class="btn btn-primary" data-act="overwrite">Overwrite</button>
        </div>
    `);
    overlay.querySelector('[data-act="cancel"]').addEventListener('click', close);
    overlay.querySelector('[data-act="keep"]').addEventListener('click', () => { close(); onKeep(); });
    overlay.querySelector('[data-act="overwrite"]').addEventListener('click', () => { close(); onOverwrite(); });
}

export function showDangerConfirmDialog({ title, bodyHtml, confirmLabel = 'Delete permanently', onConfirm }) {
    const { overlay, close } = openOverlay(`
        <h2>&#9888; ${escapeHtml(title)}</h2>
        ${bodyHtml}
        <div class="dialog-actions">
            <button class="btn btn-secondary" data-act="cancel">Cancel</button>
            <button class="btn btn-danger" data-act="confirm">${escapeHtml(confirmLabel)}</button>
        </div>
    `);
    overlay.querySelector('[data-act="cancel"]').addEventListener('click', close);
    overlay.querySelector('[data-act="confirm"]').addEventListener('click', () => { close(); onConfirm(); });
}

export function escapeHtml(s) {
    return String(s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
