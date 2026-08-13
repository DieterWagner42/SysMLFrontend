import { useState } from "react";

interface ConfigPanelProps {
  physicalInterfaceTypes: string[];
  onSave: (items: string[]) => Promise<void> | void;
  onClose: () => void;
}

/** The app's web config page — currently just the open, extensible list of physical interface
 * types offered when adding a port in the Physical architecture view (see backend/config.ini's
 * [Physical] section). One item per line; blank lines are ignored. Persists to config.ini via
 * PUT /api/config/physicalInterfaceTypes. */
export function ConfigPanel({ physicalInterfaceTypes, onSave, onClose }: ConfigPanelProps) {
  const [text, setText] = useState(physicalInterfaceTypes.join("\n"));
  const [saving, setSaving] = useState(false);

  async function save() {
    const items = text.split("\n").map((s) => s.trim()).filter((s) => s.length > 0);
    setSaving(true);
    try {
      await onSave(items);
      onClose();
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>Configuration</h3>
        <label className="modal-field-label">
          Physical interface types
          <span className="modal-field-hint">
            Offered as a dropdown when adding a port while viewing the Physical architecture view. One per line.
          </span>
        </label>
        <textarea
          className="modal-textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={10}
          autoFocus
        />
        <div className="modal-actions">
          <button onClick={onClose} disabled={saving}>Cancel</button>
          <button onClick={save} disabled={saving}>{saving ? "Saving…" : "Save"}</button>
        </div>
      </div>
    </div>
  );
}
