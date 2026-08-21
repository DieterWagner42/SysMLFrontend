import { useState } from "react";
import type { ElementRef } from "../types";

interface NewActorPickerProps {
  contextViews: ElementRef[];
  onCreate: (name: string, contextViewGuid: string | null) => void;
  onClose: () => void;
}

/** Small modal-in-modal for creating a brand-new Actor from inside the UseCase editor (Actors
 * section) instead of only being able to pick among already-existing ones. Requested live: "beim
 * anlegen eines neuen actors in der UC dlg context angeben (selection der existierenden Kontexte)
 * und dann actor im context anlegen" — a new actor should be placed into an existing Context View
 * right away, not created bare and left to be sorted into a context later. Listbox pattern (not a
 * native <select>) mirrors AddActorPicker/MoveElementPicker — picking the sole context view
 * wouldn't reliably fire a change event on a native <select>. Context selection is optional (no
 * row selected = "no context"), mirroring AddActorPicker's own on-the-"All"-tab behavior, so this
 * doesn't block actor creation before any Context View exists yet. */
export function NewActorPicker({ contextViews, onCreate, onClose }: NewActorPickerProps) {
  const [name, setName] = useState("");
  const [selectedContext, setSelectedContext] = useState<string | null>(null);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>New Actor</h3>

        <label className="modal-field-label">
          Name
          <input
            placeholder="Actor name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </label>

        <label className="modal-field-label">Context (optional)</label>
        <div className="modal-list" role="listbox">
          <div
            role="option"
            aria-selected={selectedContext === null}
            className={`modal-list-row ${selectedContext === null ? "selected" : ""}`}
            onClick={() => setSelectedContext(null)}
          >
            — No context —
          </div>
          {contextViews.length === 0 && (
            <div className="modal-list-empty">No Context Views exist yet</div>
          )}
          {contextViews.map((cv) => (
            <div
              key={cv.guid}
              role="option"
              aria-selected={selectedContext === cv.guid}
              className={`modal-list-row ${selectedContext === cv.guid ? "selected" : ""}`}
              onClick={() => setSelectedContext(cv.guid)}
            >
              {cv.name}
            </div>
          ))}
        </div>

        <div className="modal-actions">
          <button onClick={onClose}>Cancel</button>
          <button disabled={!name.trim()} onClick={() => onCreate(name.trim(), selectedContext)}>
            Create
          </button>
        </div>
      </div>
    </div>
  );
}
