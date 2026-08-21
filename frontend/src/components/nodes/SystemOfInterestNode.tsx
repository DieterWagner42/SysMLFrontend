import { Handle, NodeResizer, Position, type NodeProps } from "reactflow";
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
  onEditDocumentation: (guid: string, name: string) => void;
  knownInterfaces: KnownInterface[];
  // The configurable [Physical] interfaceTypes list — see PortsSectionProps#physicalInterfaceTypes.
  physicalInterfaceTypes: string[];
  // Capabilities LINKED to the system-of-interest, and every top-level Capability in the model —
  // same shape as ArchitectureNode's own CapabilitiesSection. Requested live: "im context muss
  // auch neue interfaces und capabilities angelegt werden können. das ist auch der sinn des
  // contextes!" — defining what the system exposes to its external environment (both interfaces
  // AND capabilities) is exactly the Context tab's own purpose.
  capabilities: ElementRef[];
  allCapabilities: ElementRef[];
  onLinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onUnlinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  // Drives NodeResizer visibility — see the comment on the same field in ArchitectureNode.tsx for
  // why hasCustomSize (not size itself) is what's tracked in data.
  hasCustomSize?: boolean;
}

/** The system-of-interest itself (the topmost System/SystemOfSystem in the Architecture tab's own
 * tree), shown automatically in every Context tab view — the central block a Context diagram is
 * built around. Both POSITION and SIZE are per-Context-View (App.tsx's contextViewKey) and fully
 * draggable/resizable, same as every other node — requested live: "kann ich alle boxen auch in der
 * breite/höhe ändern? ... derzeit schaut Flexis etwas komisch aus, weil es zu schmal dargestellt
 * wird" (size — this node previously had no NodeResizer at all), then "die Größe geht jetzt, aber
 * die Position noch nicht" (position — this node was fixed/non-draggable entirely until then).
 * isVisible is unconditionally true (not gated on selection like ArchitectureNode/ActorNode) since
 * this node is selectable={false} in App.tsx's own node-builder — it never participates in the
 * normal click-to-select flow those two rely on to show their own resize handles. Its interfaces
 * AND capability links ARE editable here too, same as in the Architecture tab — same underlying
 * element, same data, just a second place to edit it from (the Context tab's own purpose being
 * exactly this: what the system exposes to its external environment). Requested live: "der
 * Systemblock muss immer in jeder view automatisch eingefügt werden" / "flexis muss auch alle
 * interfaces haben!" / "im context muss auch neue interfaces und capabilities angelegt werden
 * können." */
export function SystemOfInterestNode({ data }: NodeProps<SystemOfInterestNodeData>) {
  return (
    <div className={`arch-node system-of-interest ${data.hasCustomSize ? "has-custom-size" : ""}`}>
      <NodeResizer minWidth={200} minHeight={90} isVisible={true} />
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
        onEditDocumentation={data.onEditDocumentation}
        knownInterfaces={data.knownInterfaces}
        physicalInterfaceTypes={data.physicalInterfaceTypes}
        defaultExpanded={false}
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
