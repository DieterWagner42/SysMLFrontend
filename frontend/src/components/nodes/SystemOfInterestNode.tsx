import { Handle, Position, type NodeProps } from "reactflow";
import { PortsSection } from "./PortsSection";
import { CapabilitiesSection } from "./CapabilitiesSection";
import type { ElementRef, KnownInterface, PortDirection, PortSpec, PortView } from "../../types";

export interface SystemOfInterestNodeData {
  label: string;
  // The real architecture element GUID (not this node's own id, "system-"+guid — see App.tsx's
  // own comment on why those are kept distinct) — used for port/capability mutations, same
  // element/same ports/same capability links the Architecture tab shows for this same node.
  guid: string;
  ports: PortSpec[];
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortDelete: (portGuid: string) => void;
  knownInterfaces: KnownInterface[];
  // Capabilities LINKED to the system-of-interest, and every top-level Capability in the model —
  // same shape as ArchitectureNode's own CapabilitiesSection. Requested live: "im context muss
  // auch neue interfaces und capabilities angelegt werden können. das ist auch der sinn des
  // contextes!" — defining what the system exposes to its external environment (both interfaces
  // AND capabilities) is exactly the Context tab's own purpose.
  capabilities: ElementRef[];
  allCapabilities: ElementRef[];
  onLinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onUnlinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  // Never actually set (this node is never resized — no NodeResizer here) — present purely so
  // applyStoredSize's generic constraint (`T extends { hasCustomSize?: boolean }`) is satisfied,
  // matching every other node data interface's own field of the same name.
  hasCustomSize?: boolean;
}

/** The system-of-interest itself (the topmost System/SystemOfSystem in the Architecture tab's own
 * tree), shown automatically in every Context tab view — the central block a Context diagram is
 * built around. Fixed position (no drag/resize — see App.tsx's own comment on why), but its
 * interfaces AND capability links ARE editable here, same as in the Architecture tab — same
 * underlying element, same data, just a second place to edit it from (the Context tab's own
 * purpose being exactly this: what the system exposes to its external environment). Requested
 * live: "der Systemblock muss immer in jeder view automatisch eingefügt werden" / "flexis muss
 * auch alle interfaces haben!" / "im context muss auch neue interfaces und capabilities angelegt
 * werden können." */
export function SystemOfInterestNode({ data }: NodeProps<SystemOfInterestNodeData>) {
  return (
    <div className="arch-node system-of-interest">
      <Handle type="target" position={Position.Top} style={{ visibility: "hidden" }} />
      <div className="node-header">
        <span className="node-kind-badge">System</span>
        <span className="node-label">{data.label}</span>
      </div>
      <PortsSection
        ownerGuid={data.guid}
        ports={data.ports}
        onAddPort={data.onAddPort}
        onPortChange={data.onPortChange}
        onPortDelete={data.onPortDelete}
        knownInterfaces={data.knownInterfaces}
      />
      <CapabilitiesSection
        ownerGuid={data.guid}
        capabilities={data.capabilities}
        allCapabilities={data.allCapabilities}
        onLink={data.onLinkCapability}
        onUnlink={data.onUnlinkCapability}
      />
      <Handle type="source" position={Position.Bottom} style={{ visibility: "hidden" }} />
    </div>
  );
}
