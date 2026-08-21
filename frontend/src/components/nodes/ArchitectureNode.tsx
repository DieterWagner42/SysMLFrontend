import { Handle, NodeResizer, Position, type NodeProps } from "reactflow";
import { PortsSection } from "./PortsSection";
import { CapabilitiesSection } from "./CapabilitiesSection";
import { FunctionsSection } from "./FunctionsSection";
import { AllocationsSection } from "./AllocationsSection";
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
  // LogicalNodes THIS FunctionalNode allocates to (Rhapsody: an "Allocate" Dependency — see
  // AllocationsSection). Only meaningful when kind === "FunctionalNode".
  allocatedLogicalNodes: ElementRef[];
  // Every LogicalNode in the whole model, for AllocationsSection's own picker — see App.tsx's
  // `allLogicalNodes` useMemo, mirrors allCapabilities below.
  allLogicalNodes: ElementRef[];
  onLinkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) => void;
  onUnlinkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) => void;
  // PhysicalNodes THIS LogicalNode allocates to — same mechanism, one level down (Logical→
  // Physical instead of Functional→Logical). Only meaningful when kind === "LogicalNode".
  allocatedPhysicalNodes: ElementRef[];
  allPhysicalNodes: ElementRef[];
  onLinkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) => void;
  onUnlinkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) => void;
  // The System Structure view is a pure containment hierarchy — no interfaces (ports) or
  // capabilities shown there, only the element tree itself. They only appear in the
  // Operational/Logical/Physical aspect views. Doesn't apply to FunctionalNodes (ports+functions
  // ARE the point of the Functional view, so they always show there).
  hideInterfacesAndCapabilities: boolean;
  // See PortsSectionProps — computed from the currently-selected architecture view (undefined for
  // System Structure, where interfaces aren't shown at all anyway).
  lockedView?: PortView;
  // Whether this node is itself one of the four tree roots (Flexis/System_F/System_L/System_P) —
  // see PortsSectionProps#isRootOwner.
  isRootOwner: boolean;
  // Reuse suggestions for the "+ Interface"/"+ Nested Port" forms — see App.tsx's knownInterfaces.
  knownInterfaces: KnownInterface[];
  // The configurable [Physical] interfaceTypes list (mechanic/electric/radiofrequency/...) — see
  // PortsSectionProps#physicalInterfaceTypes.
  physicalInterfaceTypes: string[];
  isDropTarget: boolean;
  onContextMenu: (e: React.MouseEvent, guid: string, kind: ArchKind) => void;
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortDelete: (portGuid: string) => void;
  onLinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onUnlinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
  onAddFunction: (ownerGuid: string, name: string) => void;
  onFunctionDelete: (guid: string) => void;
  onEditDocumentation: (guid: string, name: string) => void;
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
  const isLogicalNode = data.kind === "LogicalNode";
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
          onEditDocumentation={data.onEditDocumentation}
          lockedView={data.lockedView}
          isRootOwner={data.isRootOwner}
          knownInterfaces={data.knownInterfaces}
          physicalInterfaceTypes={data.physicalInterfaceTypes}
        />
      )}
      {isFunctionalNode ? (
        <>
          <FunctionsSection
            ownerGuid={data.guid}
            functions={data.functions}
            onAdd={data.onAddFunction}
            onDelete={data.onFunctionDelete}
            onEditDocumentation={data.onEditDocumentation}
          />
          <AllocationsSection
            ownerGuid={data.guid}
            linked={data.allocatedLogicalNodes}
            allTargets={data.allLogicalNodes}
            onLink={data.onLinkLogicalNode}
            onUnlink={data.onUnlinkLogicalNode}
            targetKindLabel="LogicalNode"
          />
        </>
      ) : isLogicalNode ? (
        <AllocationsSection
          ownerGuid={data.guid}
          linked={data.allocatedPhysicalNodes}
          allTargets={data.allPhysicalNodes}
          onLink={data.onLinkPhysicalNode}
          onUnlink={data.onUnlinkPhysicalNode}
          targetKindLabel="PhysicalNode"
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
