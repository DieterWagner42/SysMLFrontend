import { useMemo, useState } from "react";
import type { ArchNode } from "../types";
import { kindFamily } from "../utils/hierarchy";

interface MoveElementPickerProps {
  architecture: ArchNode;
  targetGuid: string;
  targetName: string;
  onMove: (guid: string) => void;
  onClose: () => void;
}

interface Candidate {
  guid: string;
  name: string;
  kind: string;
  depth: number;
}

/** Lists every existing architecture element that could validly become a child of targetGuid —
 * same architecture tree/family as the target (see backend's HierarchyLevels#requireCompatibleMove
 * via utils/hierarchy's kindFamily mirror), excluding the target itself and any of its own
 * ancestors (moving an ancestor under its own descendant would be a cycle) — and lets the user pick
 * one to actually MOVE there (see ModelStore#moveElement), instead of the existing "+ Child
 * Element" flow which always creates a brand-new one. */
export function MoveElementPicker({ architecture, targetGuid, targetName, onMove, onClose }: MoveElementPickerProps) {
  const [selected, setSelected] = useState("");
  const [filter, setFilter] = useState("");

  const candidates = useMemo<Candidate[]>(() => {
    let targetKind: ArchNode["kind"] | null = null;
    const ancestorGuids = new Set<string>();
    function findTargetPath(node: ArchNode, path: string[]): boolean {
      if (node.guid === targetGuid) {
        targetKind = node.kind;
        path.forEach((g) => ancestorGuids.add(g));
        return true;
      }
      return node.children.some((c) => findTargetPath(c, [...path, node.guid]));
    }
    architecture.children.forEach((c) => findTargetPath(c, []));
    if (!targetKind || targetKind === "Model") return [];
    const family = kindFamily(targetKind);
    const out: Candidate[] = [];
    function walk(node: ArchNode, depth: number) {
      if (node.guid !== targetGuid && !ancestorGuids.has(node.guid) && node.kind !== "Model" && kindFamily(node.kind) === family) {
        out.push({ guid: node.guid, name: node.name, kind: node.kind, depth });
      }
      node.children.forEach((c) => walk(c, depth + 1));
    }
    architecture.children.forEach((c) => walk(c, 0));
    return out;
  }, [architecture, targetGuid]);

  const filtered = candidates.filter((c) => c.name.toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>Move existing element under "{targetName}"</h3>
        <input
          placeholder="Search…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          autoFocus
        />
        <div className="modal-list" role="listbox">
          {filtered.length === 0 && <div className="modal-list-empty">No compatible elements found</div>}
          {filtered.map((c) => (
            <div
              key={c.guid}
              role="option"
              aria-selected={selected === c.guid}
              className={`modal-list-row ${selected === c.guid ? "selected" : ""}`}
              onClick={() => setSelected(c.guid)}
            >
              {"— ".repeat(c.depth)}{c.name} ({c.kind})
            </div>
          ))}
        </div>
        <div className="modal-actions">
          <button onClick={onClose}>Cancel</button>
          <button disabled={!selected} onClick={() => selected && onMove(selected)}>Move here</button>
        </div>
      </div>
    </div>
  );
}
