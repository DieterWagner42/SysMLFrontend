import { useEffect, useState } from "react";
import { api } from "../api/client";

interface DocumentationModalProps {
  guid: string;
  name: string;
  onClose: () => void;
}

/** Generic "Edit Documentation..." modal — free-text notes for ANY element (architecture element,
 * actor, capability, useCase, port, function, contextView), addressed purely by guid. Requested
 * live: "alle elemente des frontends benötigen noch ein dokumentationsfeld" — one reusable modal
 * covering every kind, opened from wherever that kind's own UI naturally offers it (a context menu
 * entry for the big canvas nodes, a small icon button for nested list rows, an entry in the port
 * retype popover — see App.tsx's various onContextMenu/onEditDocumentation wiring). Fetches the
 * current text on open rather than requiring the caller to have it already, since documentation
 * isn't embedded in any of the tree/list endpoints (kept out to avoid bloating every one of them). */
export function DocumentationModal({ guid, name, onClose }: DocumentationModalProps) {
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.getDocumentation(guid).then((doc) => {
      if (!cancelled) {
        setText(doc);
        setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [guid]);

  function save() {
    setSaving(true);
    api.setDocumentation(guid, text).then(onClose);
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>Documentation{name ? `: ${name}` : ""}</h3>
        <textarea
          className="modal-textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={loading ? "Loading..." : "Notes for this element..."}
          disabled={loading}
          autoFocus
          rows={10}
        />
        <div className="modal-actions">
          <button onClick={onClose}>Cancel</button>
          <button disabled={loading || saving} onClick={save}>
            Save
          </button>
        </div>
      </div>
    </div>
  );
}
