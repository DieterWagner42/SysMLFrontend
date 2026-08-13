import { useState } from "react";
import type { ElementRef } from "../../types";

interface FunctionsSectionProps {
  ownerGuid: string;
  functions: ElementRef[];
  onAdd: (ownerGuid: string, name: string) => void;
  onDelete: (guid: string) => void;
}

/** Function list for a FunctionalNode's own node — mirrors CapabilitiesSection (just a name, no
 * direction/type/view/nesting), but a distinct domain concept: the functions a FunctionalNode
 * performs, shown only in the Functional architecture view, not capabilities of a System-tree
 * element. */
export function FunctionsSection({ ownerGuid, functions, onAdd, onDelete }: FunctionsSectionProps) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");

  function submit() {
    if (!name.trim()) return;
    onAdd(ownerGuid, name.trim());
    setName("");
    setAdding(false);
  }

  return (
    <div className="capabilities-section nodrag" onClick={(e) => e.stopPropagation()}>
      {functions.map((f) => (
        <div key={f.guid} className="capability-row">
          <span className="capability-name">{f.name}</span>
          <button className="capability-delete" title="Delete function" onClick={() => onDelete(f.guid)}>
            ×
          </button>
        </div>
      ))}
      {adding ? (
        <div className="port-add-form">
          <input
            autoFocus
            placeholder="Function name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
          />
          <button onClick={submit}>Add</button>
          <button onClick={() => setAdding(false)}>Cancel</button>
        </div>
      ) : (
        <button className="add-port-btn" onClick={() => setAdding(true)}>
          + Function
        </button>
      )}
    </div>
  );
}
