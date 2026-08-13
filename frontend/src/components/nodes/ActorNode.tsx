import { Handle, NodeResizer, Position, type NodeProps } from "reactflow";
import { PortsSection } from "./PortsSection";
import type { KnownInterface, PortDirection, PortSpec, PortView } from "../../types";

export interface ActorNodeData {
  label: string;
  guid: string;
  ports: PortSpec[];
  onContextMenu: (e: React.MouseEvent, guid: string) => void;
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortDelete: (portGuid: string) => void;
  // Reuse suggestions for the "+ Interface"/"+ Nested Port" forms — see App.tsx's knownInterfaces.
  knownInterfaces: KnownInterface[];
  hasCustomSize?: boolean;
  // Drives NodeResizer visibility — see the comment on the same field in ArchitectureNode.tsx for
  // why this (App.tsx's own selectedGuid) is used instead of React Flow's native `selected`.
  isSelected?: boolean;
}

/** Custom node for an external system (SysML Actor) in the Context tab.
 * Actors can own ports too, representing the interfaces they expose to the system-of-interest. */
export function ActorNode({ data, selected }: NodeProps<ActorNodeData>) {
  return (
    <div
      className={`arch-node actor ${selected ? "selected" : ""} ${data.hasCustomSize ? "has-custom-size" : ""}`}
      onContextMenu={(e) => {
        e.preventDefault();
        data.onContextMenu(e, data.guid);
      }}
    >
      <NodeResizer minWidth={200} minHeight={90} isVisible={data.isSelected} />
      <Handle type="target" position={Position.Top} style={{ visibility: "hidden" }} />
      <div className="node-header">
        <span className="node-kind-badge">External System</span>
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
      <Handle type="source" position={Position.Bottom} style={{ visibility: "hidden" }} />
    </div>
  );
}
