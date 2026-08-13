import { useState } from "react";
import type { ElementRef } from "../types";

interface AddActorPickerProps {
  // Actors not yet linked to the current Context View (empty/omitted on the "All" tab, where
  // every actor is already visible and there's nothing meaningful to "add existing").
  existingActors: ElementRef[];
  contextName: string | null;
  onCreateNew: (name: string) => void;
  onSelectExisting: (actorGuid: string) => void;
  onClose: () => void;
}

/** Dropping "External System" onto the Context tab canvas used to always create a brand-new Actor
 * — requested live: "beim drag&drop sollen auch existierende externe System auswählbar sein."
 * Offers both: type a name to create a new one, or pick an already-existing Actor (from anywhere
 * in the model) to link into the current Context View instead of duplicating it. Listbox pattern
 * (not a native <select>) mirrors MoveElementPicker — a native select with exactly one option
 * doesn't reliably fire a change event on click, which silently broke an earlier picker built that
 * way. */
export function AddActorPicker({ existingActors, contextName, onCreateNew, onSelectExisting, onClose }: AddActorPickerProps) {
  const [name, setName] = useState("");
  const [selected, setSelected] = useState("");
  const [filter, setFilter] = useState("");

  const filtered = existingActors.filter((a) => a.name.toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>Add External System{contextName ? ` to "${contextName}"` : ""}</h3>

        <label className="modal-field-label">
          Create new
          <input
            placeholder="New external system name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
            onKeyDown={(e) => e.key === "Enter" && name.trim() && onCreateNew(name.trim())}
          />
        </label>
        <div className="modal-actions">
          <button disabled={!name.trim()} onClick={() => onCreateNew(name.trim())}>Create</button>
        </div>

        {contextName && (
          <>
            <label className="modal-field-label">Or select an existing one</label>
            <input
              placeholder="Search…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
            <div className="modal-list" role="listbox">
              {filtered.length === 0 && <div className="modal-list-empty">No other external systems available</div>}
              {filtered.map((a) => (
                <div
                  key={a.guid}
                  role="option"
                  aria-selected={selected === a.guid}
                  className={`modal-list-row ${selected === a.guid ? "selected" : ""}`}
                  onClick={() => setSelected(a.guid)}
                >
                  {a.name}
                </div>
              ))}
            </div>
          </>
        )}

        <div className="modal-actions">
          <button onClick={onClose}>Cancel</button>
          {contextName && (
            <button disabled={!selected} onClick={() => selected && onSelectExisting(selected)}>Add selected</button>
          )}
        </div>
      </div>
    </div>
  );
}
