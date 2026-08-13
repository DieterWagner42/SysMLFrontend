import { useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { KnownInterface, PortDirection, PortSpec, PortView } from "../types";
import { allPortNames, excludeOwnNames, forView, knownTypes, matchKnownInterface, qualifiedValue, resolveQualifiedInput } from "../utils/knownInterfaces";

interface PortRowProps {
  port: PortSpec;
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onDelete: (portGuid: string) => void;
  depth?: number;
  // See PortsSectionProps for both — same context-aware "+ Nested Port" behavior as the top-level
  // "+ Interface" form, threaded down through recursive nesting. The retype popover (View/
  // Direction/Type buttons on an EXISTING port, below) is deliberately left fully manual regardless
  // of these — reclassifying a port you already have is a different action from creating a new one
  // while looking at a specific view.
  lockedView?: PortView;
  physicalTypes?: string[];
  // See PortsSectionProps#knownInterfaces — same reuse suggestions, offered on the nested-port form
  // below too.
  knownInterfaces: KnownInterface[];
}

const DIRECTIONS: PortDirection[] = ["In", "Out", "InOut"];
const VIEWS: PortView[] = ["Operational", "Functional", "Logical", "Physical"];

// The two auto-created "collector" ports every non-root architecture element's top-level ports get
// routed through (see backend's PORT_GROUP_EXTERNAL/_INTERNAL) — purely structural/backend-managed,
// never meant to be retyped, retargeted, or deleted by hand. Requested live: "ibexternal/ibinternal
// mussen unique pro functionalnode sein und dürfen vom nutzer nicht geändert werden." Only matches
// at the TOP level (depth 0) — these are only ever auto-created there; a real, user-named nested
// interface happening to be called "external"/"internal" several levels down stays fully editable.
const PROTECTED_PORT_NAMES = new Set(["external", "internal"]);

/** A single ProxyPort row with an inline popover for retyping (direction/type/view) — the
 * "interface typing" interaction — plus a "+ Nested Port" form and recursive rendering
 * of its own decomposition (port.children — see types.ts: these are ports owned by this port's
 * interfaceBlock, i.e. Operational → Functional → Logical → Physical refinement, not literal
 * sub-elements of the port). Reused recursively for arbitrary decomposition depth. */
export function PortRow({ port, onAddPort, onChange, onDelete, depth = 0, lockedView, physicalTypes, knownInterfaces }: PortRowProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; right: number } | null>(null);
  const [type, setType] = useState(port.type ?? "");
  const [addingNested, setAddingNested] = useState(false);
  const [nestedName, setNestedName] = useState("");
  const [nestedDirection, setNestedDirection] = useState<PortDirection>("InOut");
  const [nestedType, setNestedType] = useState("");
  const [nestedView, setNestedView] = useState<PortView>("Functional");
  const [expanded, setExpanded] = useState(true);
  const hasChildren = port.children.length > 0;
  const isProtected = depth === 0 && PROTECTED_PORT_NAMES.has(port.name);
  const rowRef = useRef<HTMLDivElement>(null);
  const usePhysicalTypeDropdown = lockedView === "Physical" && !!physicalTypes && physicalTypes.length > 0;
  const namesListId = `known-iface-names-${port.guid}`;
  const typesListId = `known-iface-types-${port.guid}`;
  // See PortsSection's scopedKnownInterfaces — same view-scoped + already-used-name-excluded
  // suggestion filtering, applied to this row's own nested-port form (scoped to lockedView, or
  // nestedView when there's no lock; "already used" here means this port's own existing
  // decomposition, at any depth — allPortNames — not the top-level element's own ports).
  const scopedKnownInterfaces = excludeOwnNames(
    forView(knownInterfaces, lockedView ?? nestedView),
    allPortNames(port.children),
  );

  // See PortsSection's handleNameChange — same "exact name match auto-fills direction/type" reuse
  // behavior, applied to the nested-port form: a qualified pick ALWAYS keeps its "Parent.Name" form
  // as the actual port name (requested live: "ich möchte dass immer der Prefix HEU oder HEU1 bei
  // den nested ports eingefügt wird"), not just on a name collision.
  function handleNestedNameChange(value: string) {
    const { name: resolved, matched } = resolveQualifiedInput(scopedKnownInterfaces, value);
    const finalName = matched?.parentName ? qualifiedValue(matched) : resolved;
    setNestedName(finalName);
    const match = matched ?? matchKnownInterface(scopedKnownInterfaces, resolved);
    if (match) {
      if (match.direction) setNestedDirection(match.direction);
      if (match.type && !usePhysicalTypeDropdown) setNestedType(match.type);
    }
  }

  // The popover is portaled to document.body (see below) instead of rendered inline, so it can
  // escape the node's own box — nodes need overflow:auto to support manual resizing (NodeResizer),
  // which would otherwise clip any absolutely-positioned popover to the node's bounds. Position is
  // computed from the port row's own on-screen rect each time the popover opens.
  function toggleMenu() {
    if (menuOpen) {
      setMenuOpen(false);
      return;
    }
    const rect = rowRef.current?.getBoundingClientRect();
    if (rect) setMenuPos({ top: rect.bottom, right: window.innerWidth - rect.right });
    setMenuOpen(true);
  }

  function submitNested() {
    if (!nestedName.trim()) return;
    onAddPort(port.guid, nestedName.trim(), nestedDirection, nestedType.trim(), lockedView ?? nestedView);
    setNestedName("");
    setNestedType("");
    setAddingNested(false);
  }

  return (
    <div className="port-row-wrapper" style={{ marginLeft: depth > 0 ? 12 : 0 }}>
      <div className="port-row" ref={rowRef} onClick={(e) => e.stopPropagation()}>
        {hasChildren && (
          <button
            className="port-expand-toggle"
            title={expanded ? "Collapse decomposition" : "Expand decomposition"}
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? "▾" : "▸"}
          </button>
        )}
        <span className="port-name">
          {port.name}
          {hasChildren && !expanded && <span className="port-child-count"> ({port.children.length})</span>}
        </span>
        {isProtected ? (
          <>
            <span className="port-protected-label" title="Auto-managed grouping port — not itself editable">
              (auto)
            </span>
            <button
              className="port-menu-trigger"
              title="Add an interface here"
              onClick={() => setAddingNested(true)}
            >
              + Interface
            </button>
          </>
        ) : (
          <button
            className="port-menu-trigger"
            title="Type this interface"
            onClick={toggleMenu}
          >
            {port.view ?? "?"} · {port.direction ?? "?"} / {port.type ?? "untyped"} ⋮
          </button>
        )}
        {!isProtected && menuOpen && menuPos && createPortal(
          <div
            className="context-menu port-context-menu"
            style={{ position: "fixed", top: menuPos.top, right: menuPos.right }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="port-context-row">
              <div className="port-context-column">
                <div className="context-menu-section">View</div>
                {VIEWS.map((v) => (
                  <button
                    key={v}
                    className={v === port.view ? "active" : ""}
                    onClick={() => onChange(port.guid, port.direction ?? "InOut", type, v)}
                  >
                    {v}
                  </button>
                ))}
              </div>
              <div className="port-context-column">
                <div className="context-menu-section">Direction</div>
                {DIRECTIONS.map((d) => (
                  <button
                    key={d}
                    className={d === port.direction ? "active" : ""}
                    onClick={() => onChange(port.guid, d, type, port.view ?? "Functional")}
                  >
                    {d}
                  </button>
                ))}
              </div>
            </div>
            <div className="context-menu-section">Type</div>
            <input
              value={type}
              placeholder="Interface block name"
              onChange={(e) => setType(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") onChange(port.guid, port.direction ?? "InOut", type, port.view ?? "Functional");
              }}
              onBlur={() => onChange(port.guid, port.direction ?? "InOut", type, port.view ?? "Functional")}
            />
            <button
              onClick={() => {
                setAddingNested(true);
                setMenuOpen(false);
              }}
            >
              + Nested Port (Decomposition)
            </button>
            <div className="port-context-actions">
              <button className="danger" onClick={() => onDelete(port.guid)}>
                Delete port
              </button>
              <button onClick={() => setMenuOpen(false)}>Close</button>
            </div>
          </div>,
          document.body,
        )}
      </div>

      {addingNested && (
        <div className="port-add-form nested" onClick={(e) => e.stopPropagation()}>
          <input
            autoFocus
            placeholder="Nested port name"
            value={nestedName}
            onChange={(e) => handleNestedNameChange(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submitNested()}
            list={namesListId}
          />
          <datalist id={namesListId}>
            {Array.from(new Set(scopedKnownInterfaces.map((k) => qualifiedValue(k)))).map((v) => (
              <option key={v} value={v} />
            ))}
          </datalist>
          {!lockedView && (
            <select value={nestedView} onChange={(e) => setNestedView(e.target.value as PortView)}>
              {VIEWS.map((v) => (
                <option key={v} value={v}>{v}</option>
              ))}
            </select>
          )}
          <select value={nestedDirection} onChange={(e) => setNestedDirection(e.target.value as PortDirection)}>
            {DIRECTIONS.map((d) => (
              <option key={d} value={d}>{d}</option>
            ))}
          </select>
          {usePhysicalTypeDropdown ? (
            <select value={nestedType} onChange={(e) => setNestedType(e.target.value)}>
              <option value="">Type (optional)</option>
              {physicalTypes!.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          ) : (
            <>
              <input
                placeholder="Type (optional)"
                value={nestedType}
                onChange={(e) => setNestedType(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && submitNested()}
                list={typesListId}
              />
              <datalist id={typesListId}>
                {knownTypes(scopedKnownInterfaces).map((t) => (
                  <option key={t} value={t} />
                ))}
              </datalist>
            </>
          )}
          <button onClick={submitNested}>Add</button>
          <button onClick={() => setAddingNested(false)}>Cancel</button>
        </div>
      )}

      {expanded && port.children.map((child) => (
        <PortRow
          key={child.guid}
          port={child}
          onAddPort={onAddPort}
          onChange={onChange}
          onDelete={onDelete}
          depth={depth + 1}
          lockedView={lockedView}
          physicalTypes={physicalTypes}
          knownInterfaces={knownInterfaces}
        />
      ))}
    </div>
  );
}
