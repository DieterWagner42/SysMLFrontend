import { useState } from "react";
import type { ElementRef } from "../../types";

interface UseCasesSectionProps {
  capabilityGuid: string;
  useCases: ElementRef[];
  onAdd: (capabilityGuid: string, name: string) => void;
  onDelete: (guid: string) => void;
}

/** UseCase list for a Capability's own node — mirrors FunctionsSection (just a name, no
 * direction/type/view/nesting): the UseCases a Capability groups together. */
export function UseCasesSection({ capabilityGuid, useCases, onAdd, onDelete }: UseCasesSectionProps) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");

  function submit() {
    if (!name.trim()) return;
    onAdd(capabilityGuid, name.trim());
    setName("");
    setAdding(false);
  }

  return (
    <div className="capabilities-section nodrag" onClick={(e) => e.stopPropagation()}>
      {useCases.map((u) => (
        <div key={u.guid} className="capability-row">
          <span className="capability-name">{u.name}</span>
          <button className="capability-delete" title="Delete use case" onClick={() => onDelete(u.guid)}>
            ×
          </button>
        </div>
      ))}
      {adding ? (
        <div className="port-add-form">
          <input
            autoFocus
            placeholder="Use case name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
          />
          <button onClick={submit}>Add</button>
          <button onClick={() => setAdding(false)}>Cancel</button>
        </div>
      ) : (
        <button className="add-port-btn" onClick={() => setAdding(true)}>
          + Use Case
        </button>
      )}
    </div>
  );
}
