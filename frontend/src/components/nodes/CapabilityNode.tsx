import { NodeResizer, type NodeProps } from "reactflow";
import { UseCasesSection } from "./UseCasesSection";
import type { ElementRef } from "../../types";

export interface CapabilityNodeData {
  label: string;
  guid: string;
  useCases: ElementRef[];
  isSelected?: boolean;
  onContextMenu: (e: React.MouseEvent, guid: string) => void;
  onAddUseCase: (capabilityGuid: string, name: string) => void;
  onUseCaseDelete: (guid: string) => void;
  onOpenUseCase: (guid: string) => void;
  hasCustomSize?: boolean;
}

/** Custom React Flow node for a Capability (Capabilities tab) — a top-level grouping that owns a
 * list of UseCases, mirroring how ArchitectureNode's FunctionalNode owns a list of Functions. */
export function CapabilityNode({ data, selected }: NodeProps<CapabilityNodeData>) {
  return (
    <div
      className={`arch-node capability ${selected ? "selected" : ""} ${data.hasCustomSize ? "has-custom-size" : ""}`}
      onContextMenu={(e) => {
        e.preventDefault();
        data.onContextMenu(e, data.guid);
      }}
    >
      <NodeResizer minWidth={200} minHeight={90} isVisible={data.isSelected} />
      <div className="node-header">
        <span className="node-kind-badge">Capability</span>
        <span className="node-label">{data.label}</span>
      </div>
      <UseCasesSection
        capabilityGuid={data.guid}
        useCases={data.useCases}
        onAdd={data.onAddUseCase}
        onDelete={data.onUseCaseDelete}
        onOpen={data.onOpenUseCase}
      />
    </div>
  );
}
