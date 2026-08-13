import { Handle, NodeResizer, Position, type NodeProps } from "reactflow";
import { PortsSection } from "./PortsSection";
import { CapabilitiesSection } from "./CapabilitiesSection";
import { FunctionsSection } from "./FunctionsSection";
import type { ArchKind, ElementRef, KnownInterface, PortDirection, PortSpec, PortView } from "../../types";

export interface ArchitectureNodeData {
  label: string;
  kind: ArchKind;
  guid: string;
  ports: PortSpec[];
  // Capabilities LINKED to this element (a reference, not ownership) — see CapabilitiesSection.
  capabilities: ElementRef[];
  // Every top-level Capability in the model, passed through for CapabilitiesSection's picker.
  allCapabilities: ElementRef[];
  functions: ElementRef[];
  // The System Structure view is a pure containment hierarchy — no interfaces (ports) or
  // capabilities shown there, only the element tree itself. They only appear in the
  // Operational/Logical/Physical aspect views. Doesn't apply to FunctionalNodes (ports+functions
  // ARE the point of the Functional view, so they always show there).
  hideInterfacesAndCapabilities: boolean;
  // See PortsSectionProps — computed from the currently-selected architecture view (undefined for
  // System Structure, where interfaces aren't shown at all anyway).
  lockedView?: PortView;
  physicalTypes?: string[];
  // Reuse suggestions for the "+ Interface"/"+ Nested Port" forms — see App.tsx's knownInterfaces.
  knownInterfaces: KnownInterface[];
  isDropTarget: boolean;
  onContextMenu: (e: React.MouseEvent, guid: string, kind: ArchKind) => void;
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortDelete: (portGuid: string) => void;
  onLinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onUnlinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onAddFunction: (ownerGuid: string, name: string) => void;
  onFunctionDelete: (guid: string) => void;
  // Set once the user has manually dragged this node's NodeResizer handle — see applyStoredSize
  // in App.tsx for why the fill/scroll CSS only kicks in after that point.
  hasCustomSize?: boolean;
}

const ASPECT_KINDS = new Set(["FunctionalNode", "LogicalNode", "PhysicalNode"]);

/** Custom React Flow node for an architecture element — either a System-tree one (System of
 * Systems / System / Subsystem / Equipment, owning ports + Capabilities) or an aspect node
 * (FunctionalNode/LogicalNode/PhysicalNode, one of the Functional/Logical/Physical architecture
 * views — see HierarchyLevels.java for why these are separate trees from the System one).
 * FunctionalNode additionally owns an attached Functions list; LogicalNode/PhysicalNode currently
 * own only ports, no second section. The model root is never rendered as a node (see
 * layoutArchitectureTree in App.tsx). "Block" is a rare legacy fallback (an untyped element from
 * before this app's level tagging existed); it also owns ports + Capabilities like the System tree. */
export function ArchitectureNode({ data, selected }: NodeProps<ArchitectureNodeData>) {
  const isAspectNode = ASPECT_KINDS.has(data.kind);
  const isFunctionalNode = data.kind === "FunctionalNode";
  return (
    <div
      className={`arch-node level-${data.kind} ${selected ? "selected" : ""} ${data.isDropTarget ? "drop-target" : ""} ${data.hasCustomSize ? "has-custom-size" : ""}`}
      onContextMenu={(e) => {
        e.preventDefault();
        data.onContextMenu(e, data.guid, data.kind);
      }}
    >
      {/* isDropTarget (not React Flow's own `selected`) drives visibility: the architecture tree
       * rebuilds the whole node array on every click (to recompute isDropTarget for the new
       * selection), which wipes React Flow's internal `selected` flag before it ever reaches this
       * render — see App.tsx's onNodeClick/selectedGuid. */}
      <NodeResizer minWidth={200} minHeight={90} isVisible={data.isDropTarget} />
      <Handle type="target" position={Position.Top} />
      <div className="node-header">
        <span className="node-kind-badge">{data.kind}</span>
        <span className="node-label">{data.label}</span>
      </div>
      {(isAspectNode || !data.hideInterfacesAndCapabilities) && (
        <PortsSection
          ownerGuid={data.guid}
          ports={data.ports}
          onAddPort={data.onAddPort}
          onPortChange={data.onPortChange}
          onPortDelete={data.onPortDelete}
          lockedView={data.lockedView}
          physicalTypes={data.physicalTypes}
          knownInterfaces={data.knownInterfaces}
        />
      )}
      {isFunctionalNode ? (
        <FunctionsSection
          ownerGuid={data.guid}
          functions={data.functions}
          onAdd={data.onAddFunction}
          onDelete={data.onFunctionDelete}
        />
      ) : !isAspectNode && !data.hideInterfacesAndCapabilities ? (
        <CapabilitiesSection
          ownerGuid={data.guid}
          capabilities={data.capabilities}
          allCapabilities={data.allCapabilities}
          onLink={data.onLinkCapability}
          onUnlink={data.onUnlinkCapability}
        />
      ) : null}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
