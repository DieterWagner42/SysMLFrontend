import { useState } from "react";
import type { ElementRef } from "../../types";

interface AllocationsSectionProps {
  ownerGuid: string;
  linked: ElementRef[];
  // Every element of the target kind in the model (e.g. App.tsx's `allLogicalNodes`/
  // `allPhysicalNodes` useMemo) — this section only picks among already-existing ones, it never
  // creates a new one itself. Created in the Architecture tab's own matching view, like any other
  // architecture element.
  allTargets: ElementRef[];
  onLink: (ownerGuid: string, targetGuid: string) => void;
  onUnlink: (ownerGuid: string, targetGuid: string) => void;
  // "LogicalNode" or "PhysicalNode" — used only for this section's own UI copy (button/placeholder
  // text), not for any logic.
  targetKindLabel: string;
}

/** Elements THIS node ALLOCATES to (Rhapsody: an "Allocate"-stereotyped Dependency — see
 * ModelStore#getAllocatedLogicalNodesOf/linkLogicalNode and the mirrored .../PhysicalNodesOf/
 * linkPhysicalNode) — a reference, not ownership, so this only offers a picker among elements that
 * already exist, mirroring CapabilitiesSection exactly (same UX, requested live: "im Frontend ein
 * Hyperlink wie es bei den Capabilities genutzt wird"). Generic over the target kind — used both
 * for FunctionalNode→LogicalNode and, requested live right after ("nun müssen noch die Logical
 * Nodes mit PhysicalNodes auf gleiche weise allokiert werden"), LogicalNode→PhysicalNode — since
 * both are structurally identical, just a different target kind, genericizing here avoided a
 * second copy-pasted component. The "×" here unlinks, it does not delete the target element. */
export function AllocationsSection({ ownerGuid, linked, allTargets, onLink, onUnlink, targetKindLabel }: AllocationsSectionProps) {
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState("");
  const linkedGuids = new Set(linked.map((n) => n.guid));
  const available = allTargets.filter((n) => !linkedGuids.has(n.guid));

  function submit() {
    const guid = selected || available[0]?.guid;
    if (!guid) return;
    onLink(ownerGuid, guid);
    setSelected("");
    setAdding(false);
  }

  return (
    <div className="capabilities-section nodrag" onClick={(e) => e.stopPropagation()}>
      {linked.map((n) => (
        <div key={n.guid} className="capability-row">
          <span className="capability-name">{n.name}</span>
          <button className="capability-delete" title={`Unlink ${targetKindLabel}`} onClick={() => onUnlink(ownerGuid, n.guid)}>
            ×
          </button>
        </div>
      ))}
      {adding ? (
        available.length > 0 ? (
          <div className="port-add-form">
            <select autoFocus value={selected} onChange={(e) => setSelected(e.target.value)}>
              <option value="" disabled>Allocate to {targetKindLabel}…</option>
              {available.map((n) => (
                <option key={n.guid} value={n.guid}>{n.name}</option>
              ))}
            </select>
            <button onClick={submit}>Link</button>
            <button onClick={() => setAdding(false)}>Cancel</button>
          </div>
        ) : (
          <div className="port-add-form">
            <span>No {targetKindLabel}s yet — create one in the matching view.</span>
            <button onClick={() => setAdding(false)}>Close</button>
          </div>
        )
      ) : (
        <button className="add-port-btn" onClick={() => setAdding(true)}>
          + Allocate {targetKindLabel}
        </button>
      )}
    </div>
  );
}
