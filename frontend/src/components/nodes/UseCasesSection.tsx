import { useState } from "react";
import type { ElementRef } from "../../types";

interface UseCasesSectionProps {
  capabilityGuid: string;
  useCases: ElementRef[];
  onAdd: (capabilityGuid: string, name: string) => void;
  onDelete: (guid: string) => void;
  onOpen: (guid: string) => void;
}

/** UseCase list for a Capability's own node — mirrors FunctionsSection (just a name, no
 * direction/type/view/nesting): the UseCases a Capability groups together. No standalone
 * "Edit documentation" entry (unlike every other kind — see PortsSection/FunctionsSection/etc.'s
 * own onEditDocumentation button): a UseCase's Rhapsody Documentation/Description field is always
 * just the auto-formatted view of its own structured editor data (see UseCaseDocFormatter,
 * ModelStore#updateUseCase) — requested live: "lassen wir das die documentation bei UC weg und
 * füllen stattdessen das Rhapsody documentationfeld mit den UC daten" — a separate manual-edit path
 * was redundant with the structured editor and could silently diverge from it, which is exactly
 * what an earlier round of this feature had to guard against (see updateUseCase's own history);
 * removing the manual entry point entirely is simpler than detecting/protecting a manual edit. */
export function UseCasesSection({ capabilityGuid, useCases, onAdd, onDelete, onOpen }: UseCasesSectionProps) {
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
        <div
          key={u.guid}
          className="capability-row"
          onClick={() => onOpen(u.guid)}
          style={{ cursor: "pointer" }}
        >
          <span className="capability-name">{u.name}</span>
          <button
            className="capability-delete"
            title="Delete use case"
            onClick={(e) => {
              e.stopPropagation();
              onDelete(u.guid);
            }}
          >
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
