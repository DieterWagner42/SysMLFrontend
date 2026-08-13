import { useState } from "react";
import type { ElementRef } from "../../types";

interface CapabilitiesSectionProps {
  ownerGuid: string;
  capabilities: ElementRef[];
  // Every top-level Capability in the model (see App.tsx's `capabilities` state) — this section
  // only picks among already-existing ones, it never creates a new Capability itself. A Capability
  // is created/named in the Capabilities tab (see CapabilityNode).
  allCapabilities: ElementRef[];
  onLink: (ownerGuid: string, capabilityGuid: string) => void;
  onUnlink: (ownerGuid: string, capabilityGuid: string) => void;
}

/** Capabilities LINKED to an architecture element's node (see ModelStore#getCapabilitiesOf/
 * linkCapability) — a reference, not ownership, so this only offers a picker among Capabilities
 * that already exist, mirroring how PortsSection reuses knownInterfaces instead of retyping. The
 * "×" here unlinks, it does not delete the Capability (which may still be linked elsewhere, or
 * exist unlinked in the Capabilities tab). */
export function CapabilitiesSection({ ownerGuid, capabilities, allCapabilities, onLink, onUnlink }: CapabilitiesSectionProps) {
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState("");
  const linkedGuids = new Set(capabilities.map((c) => c.guid));
  const available = allCapabilities.filter((c) => !linkedGuids.has(c.guid));

  function submit() {
    const guid = selected || available[0]?.guid;
    if (!guid) return;
    onLink(ownerGuid, guid);
    setSelected("");
    setAdding(false);
  }

  return (
    <div className="capabilities-section nodrag" onClick={(e) => e.stopPropagation()}>
      {capabilities.map((c) => (
        <div key={c.guid} className="capability-row">
          <span className="capability-name">{c.name}</span>
          <button className="capability-delete" title="Unlink capability" onClick={() => onUnlink(ownerGuid, c.guid)}>
            ×
          </button>
        </div>
      ))}
      {adding ? (
        available.length > 0 ? (
          <div className="port-add-form">
            <select autoFocus value={selected} onChange={(e) => setSelected(e.target.value)}>
              <option value="" disabled>Select a Capability…</option>
              {available.map((c) => (
                <option key={c.guid} value={c.guid}>{c.name}</option>
              ))}
            </select>
            <button onClick={submit}>Link</button>
            <button onClick={() => setAdding(false)}>Cancel</button>
          </div>
        ) : (
          <div className="port-add-form">
            <span>No Capabilities yet — create one in the Capabilities tab.</span>
            <button onClick={() => setAdding(false)}>Close</button>
          </div>
        )
      ) : (
        <button className="add-port-btn" onClick={() => setAdding(true)}>
          + Capability
        </button>
      )}
    </div>
  );
}
