import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ReactFlow, {
  Background,
  Controls,
  type Edge,
  type Node,
  type NodeChange,
  type NodeTypes,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
} from "reactflow";
import "reactflow/dist/style.css";
import "@reactflow/node-resizer/dist/style.css";
import "./App.css";
import { api } from "./api/client";
import { ContextMenu, type ContextMenuItem } from "./components/ContextMenu";
import { Palette, type PaletteItem } from "./components/Palette";
import { ConfigPanel } from "./components/ConfigPanel";
import { PendingConnectorsPanel } from "./components/PendingConnectorsPanel";
import { MoveElementPicker } from "./components/MoveElementPicker";
import { ArchitectureNode, type ArchitectureNodeData } from "./components/nodes/ArchitectureNode";
import { ActorNode, type ActorNodeData } from "./components/nodes/ActorNode";
import { CapabilityNode, type CapabilityNodeData } from "./components/nodes/CapabilityNode";
import type { ArchKind, ArchNode, ElementRef, KnownInterface, PortDirection, PortSpec, PortView } from "./types";

type Tab = "architecture" | "context" | "capabilities";

const COL_WIDTH = 260;
const ROW_HEIGHT = 170; // Context/Capabilities tabs' simple grid layout only
const ROW_GAP = 60; // vertical gap between architecture depth rows, on top of estimateNodeHeight

const nodeTypes: NodeTypes = {
  architecture: ArchitectureNode,
  actor: ActorNode,
  capability: CapabilityNode,
};

// The hierarchy is automatic (System of Systems → System → Subsystem → Equipment, by nesting
// depth), so the palette doesn't offer a level choice. "SystemOfSystem" is the one optional,
// root-only wrapper; "Element" creates whatever level the drop target implies (labeled "System"
// since that's what it becomes at the root, the most common case). Context (External Systems) is
// also offered here, not just on the Context tab, so switching tabs isn't required to add one.
// Only shown for the System Structure/Operational architecture views — Functional/Logical/
// Physical each show a completely different tree (see ASPECT_PALETTES) of their own aspect-node
// kind, not this System-of-Systems one.
const ARCH_PALETTE: PaletteItem[] = [
  { type: "SystemOfSystem", label: "System of Systems (optional)", hint: "Root level only" },
  { type: "Element", label: "System", hint: "Level automatic: System / Subsystem / Equipment" },
  { type: "Actor", label: "Context / External System", hint: "Actor outside the system-of-interest" },
];
// The Functional/Logical/Physical architecture views each show only their own aspect-node kind —
// a separate decomposition tree from the System-of-Systems structure, not the same elements with
// ports filtered (that's what Operational still does). Each aspect node owns ports (like a Block);
// FunctionalNode additionally owns an attached Functions list (see FunctionsSection) — Logical/
// PhysicalNode currently own only ports. Any of them can nest into sub-nodes of its own kind at
// arbitrary depth (see HierarchyLevels.java).
const ASPECT_PALETTES: Record<"Functional" | "Logical" | "Physical", PaletteItem[]> = {
  Functional: [{ type: "FunctionalNode", label: "Functional Node", hint: "Owns ports + functions; drop again on one to nest a sub-node" }],
  Logical: [{ type: "LogicalNode", label: "Logical Node", hint: "Owns ports; drop again on one to nest a sub-node" }],
  Physical: [{ type: "PhysicalNode", label: "Physical Node", hint: "Owns ports; drop again on one to nest a sub-node" }],
};
const CONTEXT_PALETTE: PaletteItem[] = [
  { type: "Actor", label: "External System", hint: "Actor outside the system-of-interest" },
];
const CAPABILITY_PALETTE: PaletteItem[] = [
  { type: "Capability", label: "Capability", hint: "Groups a set of related Use Cases" },
];

interface PendingMenu {
  x: number;
  y: number;
  items: ContextMenuItem[];
}

export type ArchView = "Structure" | PortView;
export const ARCH_VIEWS: ArchView[] = ["Structure", "Operational", "Functional", "Logical", "Physical"];

// Functional/Logical/Physical each show only their own aspect-node tree (see ASPECT_PALETTES);
// Structure/Operational show the System-of-Systems tree instead (everything NOT one of these
// three kinds) — see layoutArchitectureTree's topLevelChildren filter.
const ASPECT_KIND_BY_VIEW: Partial<Record<ArchView, ArchKind>> = {
  Functional: "FunctionalNode",
  Logical: "LogicalNode",
  Physical: "PhysicalNode",
};
const ASPECT_KINDS = new Set(Object.values(ASPECT_KIND_BY_VIEW));

/** Ports form a decomposition chain (Operational → Functional → Logical → Physical, one nested
 * under the previous — see backend/CLAUDE.md), though that progression isn't enforced, so two
 * ports of the SAME view can be directly nested too (e.g. an Operational port decomposed into two
 * more-detailed Operational sub-ports before further refining to Functional). A view other than
 * the full Structure one keeps only ports classified for that view, but must PRESERVE their
 * relative nesting where it exists — a matching port's parent, for display purposes, is its
 * nearest matching-view ancestor (skipping over any differently-classified ones in between, which
 * are themselves omitted); only a matching port with no matching ancestor at all becomes
 * top-level. Previously this fully flattened every match to a single top-level list regardless of
 * original nesting, losing same-view decomposition structure. */
function filterPortsByView(ports: PortSpec[], view: PortView): PortSpec[] {
  function walk(list: PortSpec[]): PortSpec[] {
    const out: PortSpec[] = [];
    for (const p of list) {
      const filteredChildren = walk(p.children);
      if (p.view === view) {
        out.push({ ...p, children: filteredChildren });
      } else {
        // p itself isn't in this view — splice its matching descendants up to this level instead
        // of dropping them, since p is just a transparent pass-through for display purposes here.
        out.push(...filteredChildren);
      }
    }
    return out;
  }
  return walk(ports);
}

function findArchNodeByGuid(node: ArchNode, guid: string): ArchNode | null {
  if (node.guid === guid) return node;
  for (const c of node.children) {
    const hit = findArchNodeByGuid(c, guid);
    if (hit) return hit;
  }
  return null;
}

/** Immutable update of guid's own positions[view] within the tree rooted at root — used to
 * optimistically reflect a just-completed drag in the SOURCE architecture state (see
 * onNodesChange), not just React Flow's own transient node array. */
function updateArchNodePosition(root: ArchNode, guid: string, view: ArchView, pos: { x: number; y: number }): ArchNode {
  if (root.guid === guid) {
    return { ...root, positions: { ...root.positions, [view]: pos } };
  }
  if (root.children.length === 0) return root;
  return { ...root, children: root.children.map((c) => updateArchNodePosition(c, guid, view, pos)) };
}

// Rough box-height estimate from a node's OWN (unfiltered) port/capability/function counts — used
// only to space auto-layout rows apart, not for pixel-perfect sizing. Deliberately ignores
// archView's filtering (see estimateRowHeights below for why). A node has either capabilities or
// functions (never both — see ArchitectureNode.tsx), so counting both here is harmless.
const NODE_HEADER_HEIGHT = 70;
const SECTION_ROW_HEIGHT = 32;
const SECTION_FOOTER_HEIGHT = 40; // the "+ Interface"/"+ Capability"/"+ Function" add-button row
function estimateNodeHeight(node: ArchNode): number {
  const portCount = node.ports?.length ?? 0;
  const capCount = node.capabilities?.length ?? 0;
  const fnCount = node.functions?.length ?? 0;
  return NODE_HEADER_HEIGHT
    + portCount * SECTION_ROW_HEIGHT + SECTION_FOOTER_HEIGHT
    + (capCount + fnCount) * SECTION_ROW_HEIGHT + SECTION_FOOTER_HEIGHT;
}

/** Lays out the architecture tree — the root itself is never rendered as a node (it's just the
 * model title, shown outside the canvas; see the header). Its children become the top-level
 * (depth 0) nodes, each its own independent tidy-tree laid out side by side. The element hierarchy
 * itself is the same across all 5 architecture views (see ArchView) — only which ports each node
 * shows changes; capabilities aren't view-classified, so they show in every view. */
function layoutArchitectureTree(
  root: ArchNode,
  callbacks: {
    onContextMenu: (e: React.MouseEvent, guid: string, kind: ArchKind) => void;
    onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
    onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
    onPortDelete: (portGuid: string) => void;
    onLinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
    onUnlinkCapability: (ownerGuid: string, capabilityGuid: string) => void;
    allCapabilities: ElementRef[];
    onAddFunction: (ownerGuid: string, name: string) => void;
    onFunctionDelete: (guid: string) => void;
    selectedGuid: string | null;
    archView: ArchView;
    physicalInterfaceTypes: string[];
    knownInterfaces: KnownInterface[];
  },
): { nodes: Node<ArchitectureNodeData>[]; edges: Edge[] } {
  const nodes: Node<ArchitectureNodeData>[] = [];
  const edges: Edge[] = [];
  let leafCounter = 0;

  // Per-depth Y spacing must come from each node's FULL (unfiltered) content — using archView's
  // filtered port count here would make row height (and therefore every not-yet-manually-moved
  // node's Y) depend on which view is selected, so switching views visibly rearranged nodes even
  // though their own x/y never changed. Using the worst case (all ports/capabilities) keeps rows
  // tall enough to never overlap in ANY view, and identical across all of them.
  // Functional/Logical/Physical each show a completely separate tree (their own aspect-node kind)
  // from the System-of-Systems one shown by Structure/Operational — see ASPECT_PALETTES in
  // App.tsx. All four trees live under the same model root, so filter which top-level subtree is
  // visible per the selected view; nested children never mix kinds within a subtree (an aspect
  // node's children are always the same aspect kind, see HierarchyLevels.java), so this one
  // top-level filter is enough.
  const aspectKind = ASPECT_KIND_BY_VIEW[callbacks.archView];
  const topLevelChildren = root.children.filter((c) => aspectKind ? c.kind === aspectKind : !ASPECT_KINDS.has(c.kind as ArchKind));

  // Adding an interface while looking at a specific view should just use that view directly
  // instead of asking the user to redundantly re-pick what's already implied — see
  // PortsSectionProps.lockedView. Undefined for Structure (interfaces aren't shown there at all).
  const lockedView: PortView | undefined = callbacks.archView === "Structure" ? undefined : callbacks.archView;

  const rowHeights: number[] = [];
  function measureRowHeights(node: ArchNode, depth: number) {
    rowHeights[depth] = Math.max(rowHeights[depth] ?? 0, estimateNodeHeight(node));
    node.children.forEach((c) => measureRowHeights(c, depth + 1));
  }
  topLevelChildren.forEach((c) => measureRowHeights(c, 0));

  function rowY(depth: number): number {
    let y = 0;
    for (let d = 0; d < depth; d++) y += rowHeights[d] + ROW_GAP;
    return y;
  }

  function visit(archNode: ArchNode, depth: number, parentId: string | null): number {
    let autoX: number;
    if (archNode.children.length === 0) {
      autoX = leafCounter * COL_WIDTH;
      leafCounter += 1;
    } else {
      const childXs = archNode.children.map((child) => visit(child, depth + 1, archNode.guid));
      autoX = childXs.reduce((a, b) => a + b, 0) / childXs.length;
    }
    // A manually-dragged position (see ModelStore#setPosition) always wins over the auto-layout —
    // but auto-layout siblings still average against autoX, not the saved position, so one dragged
    // node doesn't skew where its not-yet-positioned siblings land. Positions are keyed per
    // Architecture-tab view (see ArchNode.positions in types.ts) since Structure and Operational
    // both render the exact same tree/guids and must not share a single position.
    const saved = archNode.positions?.[callbacks.archView];
    const position = saved ?? { x: autoX, y: rowY(depth) };
    nodes.push({
      id: archNode.guid,
      type: "architecture",
      position,
      data: {
        label: archNode.name,
        // Safe: visit() is only ever called on root.children and below — "Model" (the root's own
        // kind) never reaches here, see the doc comment above.
        kind: archNode.kind as ArchKind,
        guid: archNode.guid,
        // Aspect-node (FunctionalNode/LogicalNode/PhysicalNode) ports aren't view-classified/
        // filtered — being inside one already scopes them to that perspective, unlike System-tree
        // ports which carry an explicit Operational/Functional/Logical/Physical view of their own.
        ports: ASPECT_KINDS.has(archNode.kind as ArchKind) || callbacks.archView === "Structure"
          ? (archNode.ports ?? [])
          : filterPortsByView(archNode.ports ?? [], callbacks.archView),
        capabilities: archNode.capabilities ?? [],
        allCapabilities: callbacks.allCapabilities,
        functions: archNode.functions ?? [],
        // System Structure is a pure containment hierarchy — no interfaces/capabilities shown
        // there (see ArchitectureNodeData's doc comment); doesn't apply to FunctionalNodes.
        hideInterfacesAndCapabilities: callbacks.archView === "Structure",
        lockedView,
        physicalTypes: callbacks.physicalInterfaceTypes,
        knownInterfaces: callbacks.knownInterfaces,
        isDropTarget: archNode.guid === callbacks.selectedGuid,
        onContextMenu: callbacks.onContextMenu,
        onAddPort: callbacks.onAddPort,
        onPortChange: callbacks.onPortChange,
        onPortDelete: callbacks.onPortDelete,
        onLinkCapability: callbacks.onLinkCapability,
        onUnlinkCapability: callbacks.onUnlinkCapability,
        onAddFunction: callbacks.onAddFunction,
        onFunctionDelete: callbacks.onFunctionDelete,
      },
    });
    if (parentId) {
      edges.push({ id: `${parentId}->${archNode.guid}`, source: parentId, target: archNode.guid });
    }
    return autoX;
  }

  topLevelChildren.forEach((child) => visit(child, 0, null));
  return { nodes, edges };
}

function App() {
  const [tab, setTab] = useState<Tab>("architecture");
  const [archView, setArchView] = useState<ArchView>("Structure");
  const [architecture, setArchitecture] = useState<ArchNode | null>(null);
  const [actors, setActors] = useState<(ElementRef & { ports: PortSpec[] })[]>([]);
  const [capabilities, setCapabilities] = useState<(ElementRef & { useCases: ElementRef[] })[]>([]);
  const [selectedGuid, setSelectedGuid] = useState<string | null>(null);
  const [menu, setMenu] = useState<PendingMenu | null>(null);
  const [movePickerTarget, setMovePickerTarget] = useState<{ guid: string; name: string } | null>(null);
  const [status, setStatus] = useState<"connecting" | "connected" | "error">("connecting");
  const [mode, setMode] = useState<"local" | "rhapsody" | null>(null);
  const [rhapsodyAvailable, setRhapsodyAvailable] = useState(false);
  // False means the backend's last mutation applied live in Rhapsody but failed to actually save
  // to disk (see ModelStore#isSaveHealthy) — surfaced as a persistent banner (not the transient
  // error banner) since this silently loses work on the next close/reopen/crash if unnoticed.
  const [saveHealthy, setSaveHealthy] = useState(true);
  const [modelPath, setModelPath] = useState("");
  const [xmlPath, setXmlPath] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [physicalInterfaceTypes, setPhysicalInterfaceTypes] = useState<string[]>([]);
  const [configOpen, setConfigOpen] = useState(false);
  const [connectorsOpen, setConnectorsOpen] = useState(false);

  // Every distinct {name, direction, type, view} combination seen among ALL ports anywhere in the
  // current model — any architecture element (any depth, any nesting/decomposition) plus every
  // Actor's own ports — offered as reuse suggestions (a <datalist>, see PortsSection/PortRow) when
  // adding a new interface elsewhere, e.g. Operational "HEU" already used on the System can be
  // picked again on a Subsystem (name-only match; direction/type only autofill on an exact,
  // unambiguous name match) instead of retyping it. Scoped by view when actually offered (see
  // utils/knownInterfaces.ts' forView) — mirroring the backend's own per-view interfaceBlock
  // scoping — except for `external` entries (a port owned directly by a root-level tree element:
  // Flexis/System_F/System_L/System_P themselves, never nested/decomposed and never an Actor), which
  // stay offered regardless of the view being edited, matching the backend's cross-view reuse
  // allowance for those. Derived purely from data already fetched for rendering, no separate
  // backend endpoint needed.
  const knownInterfaces = useMemo<KnownInterface[]>(() => {
    const seen = new Map<string, KnownInterface>();
    function walkPorts(ports: PortSpec[] | undefined, parentExternal: boolean, parentName: string | null) {
      for (const p of ports ?? []) {
        // A root element's own top-level port is external regardless of its view — INCLUDING
        // Physical (System_P's own ports): "System_P sind auch externe Schnittstellen, aber nur
        // physikalische!" What matters is that external suggestions stay confined to their own
        // KIND-GROUP when actually offered — see utils/knownInterfaces.ts' forView/sameKindGroup,
        // mirroring the backend's own findOrCreateInterfaceBlock/findInterfaceBlockAcrossAllViews
        // kind-group split (a physical connector is a fundamentally different kind of interface than
        // an Operational/Functional/Logical one, and the two must never merge).
        //
        // A port's own decomposition INHERITS externality from its parent — mirrors the backend's
        // isWithinExternalTree ("HEU ist der Container ... JMessages/Voice sind selbst externe
        // Schnittstellen"): a nested port under an external ancestor is ITSELF external too, not just
        // the top-level root port. Found live: this used to hardcode `false` for every nested child,
        // so "Voice" (nested under the external "HEU") silently dropped out of every suggestion list
        // entirely once its own view ("Operational") stopped matching the view being edited — the
        // backend's own EXTERNAL_INTERFACE_STEREOTYPE marking on Voice's contract was correct, but
        // the frontend never mirrored it.
        const external = parentExternal;
        // parentName is part of the key (not just direction/type/view) — requested live: "HEU.Voice
        // und HEU1.Voice" must both stay independently selectable/distinguishable in the suggestion
        // list even when they otherwise share the exact same direction/type/view (e.g. both nested
        // under an external ancestor, both resolving to the same shared contract) — collapsing them
        // into one entry loses which container each one came from, so labelFor could never show a
        // useful qualifier.
        // The auto-managed "external"/"internal" collector ports themselves (see backend's
        // PORT_GROUP_EXTERNAL/_INTERNAL) are never a real, reusable interface — only ever a
        // TOP-LEVEL container (parentName === null) named exactly this — so they must never appear
        // as a suggestion. Their own children (the actual interfaces nested inside) are unaffected
        // and still walked/offered below. Requested live: "in der auswahl von existierenden
        // schnittstellen darf internal und external nicht auftauchen!"
        const isProtectedContainer = parentName === null && (p.name === "internal" || p.name === "external");
        if (!isProtectedContainer) {
          const key = `${p.name} ${p.direction ?? ""} ${p.type ?? ""} ${p.view ?? ""} ${parentName ?? ""}`;
          const existing = seen.get(key);
          if (existing) {
            if (external) existing.external = true;
          } else {
            seen.set(key, { name: p.name, direction: p.direction, type: p.type, view: p.view, external, parentName });
          }
        }
        // The protected "external"/"internal" container is transparent for qualification purposes
        // — its own children are the TRUE top-level interfaces from the user's point of view (e.g.
        // "HEU_Voice" itself already carries a meaningful name), so they get parentName=null (no
        // prefix) rather than "external.HEU_Voice". A REAL named ancestor (e.g. root-level "HEU" on
        // System_F, still a plain port, not one of these containers) still qualifies its own
        // children normally. Requested live: "internal und external darf nicht als prefix verwendet
        // werden. nur wenn expizit ein anderer Top-level port existiert/angelegt wird. z.B. HEU oder
        // HEU1."
        walkPorts(p.children, external, isProtectedContainer ? null : p.name);
      }
    }
    function walkArch(node: ArchNode | null | undefined, external: boolean) {
      if (!node) return;
      walkPorts(node.ports, external, null);
      node.children.forEach((c) => walkArch(c, false));
    }
    // architecture.children are the depth-0 tree roots (Flexis/System_F/System_L/System_P) — the
    // model root itself (architecture) owns no ports and is never rendered as a node.
    architecture?.children.forEach((c) => walkArch(c, true));
    actors.forEach((a) => walkPorts(a.ports, false, null));
    return Array.from(seen.values());
  }, [architecture, actors]);

  const [nodes, setNodes, onNodesChangeRaw] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  // Node sizes set via NodeResizer aren't part of the domain model — they're a view preference,
  // not persisted to the backend. But layoutArchitectureTree (and the Context/Capabilities node
  // builders) rebuild the whole `nodes` array from scratch on every data refresh, which would wipe
  // any manual resize the moment the user e.g. adds a port. This ref survives those rebuilds so
  // the stored size can be re-applied each time nodes are (re)built — see applyStoredSize below.
  const nodeSizesRef = useRef<Record<string, { width: number; height: number }>>({});

  // React Flow's LAST "position" change of a drag (dragging:false, the actual drag-end signal) does
  // NOT carry a "position" field — only the preceding dragging:true events do (confirmed live: the
  // final change is `{type:"position", dragging:false}` with no position/positionAbsolute at all).
  // Requiring c.position on that final event (as an earlier version of this handler did) meant the
  // persist branch below never actually ran, for either a real mouse drag or automated testing — a
  // node visually moved and even survived within the same session (React Flow's own internal state
  // still had it), but nothing was ever sent to the backend, so the "position" was gone on the next
  // refresh/reload. This ref tracks the latest position seen per node id across the whole drag so
  // the final dragging:false event can fall back to it.
  const lastDragPositionRef = useRef<Record<string, { x: number; y: number }>>({});

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    for (const c of changes) {
      // React Flow's own auto-measurement (ResizeObserver tracking a node's natural content size)
      // also emits type:"dimensions" changes, but never sets `resizing` — only NodeResizer's own
      // drag handler does (true while dragging, false on release). Only the latter is a deliberate
      // user resize we want to remember; treating the former as one would "freeze" every node at
      // its first-ever measured size (see the has-custom-size CSS comment in App.css).
      if (c.type === "dimensions" && c.dimensions && typeof c.resizing === "boolean") {
        nodeSizesRef.current[c.id] = { width: c.dimensions.width, height: c.dimensions.height };
      }
      if (c.type === "position" && c.position) {
        lastDragPositionRef.current[c.id] = c.position;
      }
      // dragging:false fires once at drag end (continuously with dragging:true while moving) — only
      // persist then, not on every mousemove. Only the Architecture tab has a "view" to scope the
      // position to (see ArchNode.positions in types.ts) — Context/Capabilities nodes (Actors/
      // UseCases) pass view=undefined, which api.setPosition treats as "no view" server-side.
      if (c.type === "position" && c.dragging === false) {
        const finalPosition = c.position ?? lastDragPositionRef.current[c.id];
        if (finalPosition) {
          const view = tab === "architecture" ? archView : undefined;
          api.setPosition(c.id, finalPosition.x, finalPosition.y, view).catch((e) => setError(e instanceof Error ? e.message : String(e)));
          // Also reflect the new position in the SOURCE state (architecture/actors/capabilities),
          // not just React Flow's own transient node array (updated below via onNodesChangeRaw) —
          // otherwise the next rebuild triggered by ANY other state change (e.g. selecting a
          // different node, which recomputes isDropTarget and is in this effect's own dependency
          // array) would revert to the last-FETCHED position, since layoutArchitectureTree/the
          // Context+Capabilities builders always derive a node's position fresh from this source
          // state — reported live as "I move Missile, Rhapsody keeps the new position, but the
          // frontend snaps it back when I select something else."
          if (tab === "architecture" && view) {
            setArchitecture((prev) => prev && updateArchNodePosition(prev, c.id, view, finalPosition));
          } else if (tab === "context") {
            setActors((prev) => prev.map((a) => (a.guid === c.id ? { ...a, x: finalPosition.x, y: finalPosition.y } : a)));
          } else if (tab === "capabilities") {
            setCapabilities((prev) => prev.map((cap) => (cap.guid === c.id ? { ...cap, x: finalPosition.x, y: finalPosition.y } : cap)));
          }
        }
      }
    }
    onNodesChangeRaw(changes);
  }, [onNodesChangeRaw, tab, archView]);

  function applyStoredSize<T extends { hasCustomSize?: boolean }>(node: Node<T>): Node<T> {
    const size = nodeSizesRef.current[node.id];
    if (!size) return node;
    return {
      ...node,
      style: { ...node.style, width: size.width, height: size.height },
      data: { ...node.data, hasCustomSize: true },
    };
  }

  const withErrorHandling = useCallback(async (fn: () => Promise<void>) => {
    setInfo(null);
    try {
      await fn();
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  const refreshArchitecture = useCallback(() => withErrorHandling(async () => {
    setArchitecture(await api.getArchitecture());
  }), [withErrorHandling]);

  const refreshContext = useCallback(() => withErrorHandling(async () => {
    const items = await api.getContext();
    const withPorts = await Promise.all(
      items.map(async (a) => ({ ...a, ports: await api.getPorts(a.guid).catch(() => []) })),
    );
    setActors(withPorts);
  }), [withErrorHandling]);

  const refreshCapabilities = useCallback(() => withErrorHandling(async () => {
    const items = await api.getCapabilities();
    const withUseCases = await Promise.all(
      items.map(async (c) => ({ ...c, useCases: await api.getUseCasesOf(c.guid).catch(() => []) })),
    );
    setCapabilities(withUseCases);
  }), [withErrorHandling]);

  // Used by the Load Model / Save XML / Load XML buttons: if the corresponding path field is
  // empty, pop a native file dialog (the backend runs on the same machine as the browser) and
  // fill the field with what the user picked; returns null (caller should abort, no error shown)
  // if the field was empty and the dialog was cancelled.
  const resolvePath = useCallback(async (
    current: string,
    setter: (v: string) => void,
    mode: "open" | "save",
    filter: "xml" | "rpyx",
    title: string,
  ): Promise<string | null> => {
    if (current.trim()) return current;
    const picked = await api.pickFile(mode, filter, title);
    if (picked) setter(picked);
    return picked;
  }, []);

  const refreshStatus = useCallback(() => {
    api.status()
      .then((s) => {
        setStatus("connected");
        setMode(s.mode === "rhapsody" ? "rhapsody" : "local");
        setRhapsodyAvailable(s.rhapsodyAvailable);
        setSaveHealthy(s.saveHealthy);
      })
      .catch(() => setStatus("error"));
  }, []);

  // Polled periodically (not just once on mount / after specific actions like every other
  // refreshStatus call site) so a save starting to fail mid-session — the exact scenario that
  // silently lost a whole reparenting session's worth of work once — shows up within ~20s instead
  // of only the next time the user happens to trigger some other action that calls refreshStatus.
  useEffect(() => {
    const id = setInterval(refreshStatus, 20000);
    return () => clearInterval(id);
  }, [refreshStatus]);

  useEffect(() => {
    refreshStatus();
  }, [refreshStatus]);

  const refreshPhysicalInterfaceTypes = useCallback(() => withErrorHandling(async () => {
    setPhysicalInterfaceTypes(await api.getPhysicalInterfaceTypes());
  }), [withErrorHandling]);

  useEffect(() => {
    refreshPhysicalInterfaceTypes();
  }, [refreshPhysicalInterfaceTypes]);

  // Architecture is fetched unconditionally (not gated on the active tab): its root GUID is the
  // default parent for Actor/Package/Block creation from every tab, including Context and
  // Capabilities when opened first without ever visiting Architecture.
  useEffect(() => {
    refreshArchitecture();
  }, [refreshArchitecture]);

  // Also fetched unconditionally: the Architecture tab's own "+ Capability" picker (see
  // CapabilitiesSection) needs the full top-level Capability list even before the Capabilities tab
  // has ever been visited.
  useEffect(() => {
    refreshCapabilities();
  }, [refreshCapabilities]);

  useEffect(() => {
    if (tab === "context") refreshContext();
  }, [tab, refreshContext]);

  // ── Architecture tab: build React Flow graph from the fetched tree ────

  const onArchContextMenu = useCallback((e: React.MouseEvent, guid: string, kind: ArchKind) => {
    const items: ContextMenuItem[] = [
      {
        label: "Rename",
        onClick: () => withErrorHandling(async () => {
          const name = window.prompt("New name:");
          if (!name) return;
          await api.renameElement(guid, name);
          await refreshArchitecture();
        }),
      },
    ];
    // Equipment is the leaf level — no further nesting. Everywhere else the level of the new
    // child is automatic (System → Subsystem → Equipment), so there's only ever one quick-add.
    if (kind !== "Equipment") {
      items.push({
        label: "+ Child Element",
        onClick: () => withErrorHandling(async () => {
          const name = window.prompt("Element name:");
          if (!name) return;
          await api.createArchitectureElement(guid, name);
          await refreshArchitecture();
        }),
      });
      items.push({
        label: "+ Existing Element",
        onClick: () => {
          if (!architecture) return;
          const target = findArchNodeByGuid(architecture, guid);
          if (target) setMovePickerTarget({ guid: target.guid, name: target.name });
        },
      });
    }
    items.push({
      label: "Delete",
      danger: true,
      onClick: () => withErrorHandling(async () => {
        if (!window.confirm(`Delete "${kind}" element?`)) return;
        await api.deleteElement(guid);
        await refreshArchitecture();
      }),
    });
    setMenu({ x: e.clientX, y: e.clientY, items });
  }, [architecture, refreshArchitecture, withErrorHandling]);

  const onMoveElement = useCallback((guid: string, newParentGuid: string) => {
    withErrorHandling(async () => {
      await api.moveElement(guid, newParentGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  // ownerGuid may be a Block/Actor guid (top-level port) or an existing port's guid (a nested,
  // decomposed port) — same call either way, see PortRow's "+ Nested Port" action.
  const onAddPort = useCallback((ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => {
    withErrorHandling(async () => {
      await api.createPort(ownerGuid, name, direction, type, view);
      if (tab === "architecture") await refreshArchitecture();
      else await refreshContext();
    });
  }, [tab, refreshArchitecture, refreshContext, withErrorHandling]);

  const onPortChange = useCallback((portGuid: string, direction: PortDirection, type: string, view: PortView) => {
    withErrorHandling(async () => {
      await api.updatePort(portGuid, direction, type, view);
      if (tab === "architecture") await refreshArchitecture();
      else await refreshContext();
    });
  }, [tab, refreshArchitecture, refreshContext, withErrorHandling]);

  const onPortDelete = useCallback((portGuid: string) => {
    withErrorHandling(async () => {
      await api.deletePort(portGuid);
      if (tab === "architecture") await refreshArchitecture();
      else await refreshContext();
    });
  }, [tab, refreshArchitecture, refreshContext, withErrorHandling]);

  // Capability LINKS are embedded inline in the architecture tree (see
  // ModelStore#getCapabilitiesOf), so refresh that — the Capability itself isn't touched, only the
  // reference from this architecture element to it.
  const onLinkCapability = useCallback((ownerGuid: string, capabilityGuid: string) => {
    withErrorHandling(async () => {
      await api.linkCapability(ownerGuid, capabilityGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  const onUnlinkCapability = useCallback((ownerGuid: string, capabilityGuid: string) => {
    withErrorHandling(async () => {
      await api.unlinkCapability(ownerGuid, capabilityGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  // UseCases are owned by a Capability (Capabilities tab), not by an architecture element —
  // mirrors onAddFunction/onFunctionDelete, but refreshes the Capabilities tab's own data instead.
  const onAddUseCase = useCallback((capabilityGuid: string, name: string) => {
    withErrorHandling(async () => {
      await api.createUseCase(capabilityGuid, name);
      await refreshCapabilities();
    });
  }, [refreshCapabilities, withErrorHandling]);

  const onUseCaseDelete = useCallback((guid: string) => {
    withErrorHandling(async () => {
      await api.deleteUseCase(guid);
      await refreshCapabilities();
    });
  }, [refreshCapabilities, withErrorHandling]);

  // Functions are only attached to FunctionalNodes, embedded inline in the architecture tree —
  // mirrors onAddUseCase/onUseCaseDelete, but refreshes the architecture tree instead of
  // Capabilities (Functions are natively owned by their FunctionalNode, unlike UseCases).
  const onAddFunction = useCallback((ownerGuid: string, name: string) => {
    withErrorHandling(async () => {
      await api.createFunction(ownerGuid, name);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  const onFunctionDelete = useCallback((guid: string) => {
    withErrorHandling(async () => {
      await api.deleteFunction(guid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  useEffect(() => {
    if (tab !== "architecture" || !architecture) return;
    const { nodes: n, edges: e } = layoutArchitectureTree(architecture, {
      onContextMenu: onArchContextMenu,
      onAddPort,
      onPortChange,
      onPortDelete,
      onLinkCapability,
      onUnlinkCapability,
      allCapabilities: capabilities,
      onAddFunction,
      onFunctionDelete,
      selectedGuid,
      archView,
      physicalInterfaceTypes,
      knownInterfaces,
    });
    setNodes(n.map(applyStoredSize));
    setEdges(e);
  }, [tab, architecture, selectedGuid, archView, physicalInterfaceTypes, knownInterfaces, capabilities, onArchContextMenu, onAddPort, onPortChange, onPortDelete, onLinkCapability, onUnlinkCapability, onAddFunction, onFunctionDelete, setNodes, setEdges]);

  // ── Context tab graph ───────────────────────────────────────────────

  useEffect(() => {
    if (tab !== "context") return;
    const n: Node<ActorNodeData>[] = actors.map((a, i) => ({
      id: a.guid,
      type: "actor",
      position: a.x != null && a.y != null
        ? { x: a.x, y: a.y }
        : { x: (i % 4) * COL_WIDTH, y: Math.floor(i / 4) * ROW_HEIGHT },
      data: {
        label: a.name,
        guid: a.guid,
        ports: a.ports,
        isSelected: a.guid === selectedGuid,
        onContextMenu: (e, guid) => {
          e.preventDefault();
          setMenu({
            x: e.clientX,
            y: e.clientY,
            items: [
              {
                label: "Rename",
                onClick: () => withErrorHandling(async () => {
                  const name = window.prompt("New name:");
                  if (!name) return;
                  await api.renameElement(guid, name);
                  await refreshContext();
                }),
              },
              {
                label: "Delete",
                danger: true,
                onClick: () => withErrorHandling(async () => {
                  if (!window.confirm("Delete external system?")) return;
                  await api.deleteActor(guid);
                  await refreshContext();
                }),
              },
            ],
          });
        },
        onAddPort,
        onPortChange,
        onPortDelete,
        knownInterfaces,
      },
    }));
    setNodes(n.map(applyStoredSize));
    setEdges([]);
  }, [tab, actors, selectedGuid, knownInterfaces, onAddPort, onPortChange, onPortDelete, refreshContext, setNodes, setEdges, withErrorHandling]);

  // ── Capabilities tab graph ──────────────────────────────────────────

  useEffect(() => {
    if (tab !== "capabilities") return;
    const n: Node<CapabilityNodeData>[] = capabilities.map((c, i) => ({
      id: c.guid,
      type: "capability",
      position: c.x != null && c.y != null
        ? { x: c.x, y: c.y }
        : { x: (i % 4) * COL_WIDTH, y: Math.floor(i / 4) * ROW_HEIGHT },
      data: {
        label: c.name,
        guid: c.guid,
        useCases: c.useCases,
        isSelected: c.guid === selectedGuid,
        onContextMenu: (e, guid) => {
          e.preventDefault();
          setMenu({
            x: e.clientX,
            y: e.clientY,
            items: [
              {
                label: "Rename",
                onClick: () => withErrorHandling(async () => {
                  const name = window.prompt("New name:");
                  if (!name) return;
                  await api.renameElement(guid, name);
                  await refreshCapabilities();
                }),
              },
              {
                label: "Delete",
                danger: true,
                onClick: () => withErrorHandling(async () => {
                  if (!window.confirm("Delete this Capability and its Use Cases?")) return;
                  await api.deleteCapability(guid);
                  await refreshCapabilities();
                  await refreshArchitecture();
                }),
              },
            ],
          });
        },
        onAddUseCase,
        onUseCaseDelete,
      },
    }));
    setNodes(n.map(applyStoredSize));
    setEdges([]);
  }, [tab, capabilities, selectedGuid, onAddUseCase, onUseCaseDelete, refreshCapabilities, refreshArchitecture, setNodes, setEdges, withErrorHandling]);

  // ── Drag & drop from palette ─────────────────────────────────────────

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const itemType = e.dataTransfer.getData("application/sysmlfrontend-item");
    if (!itemType) return;

    withErrorHandling(async () => {
      // architecture.guid is the project root — required as a fallback parent for every kind of
      // element. If it's missing, no backend is connected / no model is loaded yet; surface that
      // explicitly instead of letting the drop silently do nothing.
      if (!architecture) {
        throw new Error("No model loaded — backend unreachable, or no Rhapsody model loaded yet (see 'Load Model' above).");
      }

      if (tab === "architecture" && itemType === "SystemOfSystem") {
        // SoS is the optional top-level wrapper — always targets the root, regardless of selection.
        const name = window.prompt("System of Systems name:");
        if (!name) return;
        await api.createArchitectureElement(architecture.guid, name, "SystemOfSystem");
        await refreshArchitecture();
      } else if (tab === "architecture" && itemType === "Element") {
        const parentGuid = selectedGuid ?? architecture.guid;
        const name = window.prompt("Element name:");
        if (!name) return;
        await api.createArchitectureElement(parentGuid, name); // level is automatic
        await refreshArchitecture();
      } else if (tab === "architecture" && (itemType === "FunctionalNode" || itemType === "LogicalNode" || itemType === "PhysicalNode")) {
        // A selected node of the same aspect kind nests as a sub-node; otherwise this starts a new
        // top-level one. Explicit kind is needed at the root (like SystemOfSystem) —
        // HierarchyLevels only auto-propagates aspect-node-under-same-aspect-node once the first
        // one exists.
        const parentGuid = selectedGuid ?? architecture.guid;
        const name = window.prompt(`${itemType.replace("Node", " node")} name:`);
        if (!name) return;
        await api.createArchitectureElement(parentGuid, name, itemType);
        await refreshArchitecture();
      } else if (itemType === "Actor") {
        const name = window.prompt("External system name:");
        if (!name) return;
        await api.createActor(architecture.guid, name);
        await refreshContext();
        if (tab === "architecture") setInfo(`External system "${name}" created — see the Context tab.`);
      } else if (tab === "capabilities" && itemType === "Capability") {
        const name = window.prompt("Capability name:");
        if (!name) return;
        await api.createCapability(name);
        await refreshCapabilities();
      }
    });
  }, [tab, selectedGuid, architecture, refreshArchitecture, refreshContext, refreshCapabilities, withErrorHandling]);

  const palette = useMemo<PaletteItem[]>(() => {
    if (tab === "architecture") {
      const aspectPalette = archView === "Functional" || archView === "Logical" || archView === "Physical"
        ? ASPECT_PALETTES[archView]
        : null;
      return aspectPalette ?? ARCH_PALETTE;
    }
    if (tab === "context") return CONTEXT_PALETTE;
    return CAPABILITY_PALETTE;
  }, [tab, archView]);

  return (
    <div className="app">
      <header className="app-header">
        <div className="tabs">
          <button className={tab === "architecture" ? "active" : ""} onClick={() => setTab("architecture")}>
            Architecture
          </button>
          <button className={tab === "context" ? "active" : ""} onClick={() => setTab("context")}>
            Context
          </button>
          <button className={tab === "capabilities" ? "active" : ""} onClick={() => setTab("capabilities")}>
            Capabilities
          </button>
        </div>
        <button
          className="model-title"
          disabled={!architecture}
          onClick={() => withErrorHandling(async () => {
            if (!architecture) return;
            const name = window.prompt("Model name:", architecture.name);
            if (!name) return;
            await api.renameElement(architecture.guid, name);
            await refreshArchitecture();
          })}
        >
          {architecture ? `Model: ${architecture.name}` : "No model loaded"}
        </button>
        <div className="model-controls">
          <span className={`status-dot ${status}`} title={status} />
          <span className="mode-indicator" title="Active store: local (XML) or live in Rhapsody">
            {mode === "rhapsody" ? "🔗 Rhapsody" : mode === "local" ? "💾 Local" : "…"}
          </span>
          <button
            onClick={() => withErrorHandling(async () => {
              const name = window.prompt("New model name:", "New Model");
              if (!name) return;
              await api.newModel(name);
              await refreshArchitecture();
              await refreshContext();
              await refreshCapabilities();
              refreshStatus();
              setInfo(`New local model "${name}" created.`);
            })}
          >
            New Model
          </button>
          <button
            disabled={!rhapsodyAvailable}
            title={rhapsodyAvailable ? "Export the current local model into an existing Rhapsody project" : "Rhapsody isn't configured (installDir in config.ini)"}
            onClick={() => withErrorHandling(async () => {
              window.alert(
                "New/Export Rhapsody Model:\n\n" +
                "1. First manually create a new, empty project in Rhapsody " +
                "(File > New Project) and note its file path (.rpyx).\n" +
                "2. Enter exactly that path in the next dialog.\n\n" +
                "The SysML profile is added to the project automatically, and the current " +
                "local model (Architecture, Context, Capabilities) is then transferred to Rhapsody."
              );
              const path = window.prompt("Path to the new .rpyx project file:", modelPath);
              if (!path) return;
              const summary = await api.exportToRhapsody(path);
              setModelPath(path);
              setInfo(`Exported to Rhapsody: ${summary.elementsCreated} elements, ${summary.actorsCreated} external systems, ${summary.capabilitiesCreated} capabilities, ${summary.useCasesCreated} use cases`);
              await refreshArchitecture();
              await refreshContext();
              await refreshCapabilities();
              refreshStatus();
            })}
          >
            New/Export Rhapsody Model
          </button>
          <input
            placeholder="Path to .rpyx project file"
            value={modelPath}
            onChange={(e) => setModelPath(e.target.value)}
          />
          <button
            disabled={!rhapsodyAvailable}
            title="Continue editing directly in an already-existing Rhapsody project (online), without transferring the local model"
            onClick={() => withErrorHandling(async () => {
              const path = await resolvePath(modelPath, setModelPath, "open", "rpyx", "Open Rhapsody project");
              if (!path) return;
              await api.loadModel(path);
              await refreshArchitecture();
              await refreshContext();
              await refreshCapabilities();
              refreshStatus();
            })}
          >
            Load Model
          </button>
          <input
            placeholder="Path to .xml backup file"
            value={xmlPath}
            onChange={(e) => setXmlPath(e.target.value)}
          />
          <button
            onClick={() => withErrorHandling(async () => {
              const path = await resolvePath(xmlPath, setXmlPath, "save", "xml", "Save XML as");
              if (!path) return;
              await api.exportModel(path);
              setInfo(`Saved: ${path}`);
            })}
          >
            Save XML
          </button>
          <button
            onClick={() => withErrorHandling(async () => {
              const path = await resolvePath(xmlPath, setXmlPath, "open", "xml", "Open XML file");
              if (!path) return;
              const summary = await api.importModel(path);
              setInfo(`Loaded: ${summary.elementsCreated} elements, ${summary.actorsCreated} external systems, ${summary.capabilitiesCreated} capabilities, ${summary.useCasesCreated} use cases`);
              await refreshArchitecture();
              await refreshContext();
              await refreshCapabilities();
            })}
          >
            Load XML
          </button>
          <button
            disabled={mode !== "rhapsody"}
            title={mode === "rhapsody" ? "Connectors that should exist in Rhapsody but haven't been created yet" : "Only available in Rhapsody mode"}
            onClick={() => setConnectorsOpen(true)}
          >
            🔗 Pending Connectors
          </button>
          <button title="Configuration" onClick={() => setConfigOpen(true)}>
            ⚙
          </button>
        </div>
      </header>

      {configOpen && (
        <ConfigPanel
          physicalInterfaceTypes={physicalInterfaceTypes}
          onClose={() => setConfigOpen(false)}
          onSave={(items) => withErrorHandling(async () => {
            setPhysicalInterfaceTypes(await api.setPhysicalInterfaceTypes(items));
          })}
        />
      )}

      {connectorsOpen && <PendingConnectorsPanel onClose={() => setConnectorsOpen(false)} />}

      {tab === "architecture" && (
        <div className="arch-view-tabs">
          {ARCH_VIEWS.map((v) => (
            <button key={v} className={archView === v ? "active" : ""} onClick={() => setArchView(v)}>
              {v === "Structure" ? "System Structure" : v}
            </button>
          ))}
        </div>
      )}

      {!saveHealthy && (
        <div className="error-banner">
          ⚠ Changes are applying live but NOT saving to disk in Rhapsody right now — check the
          Rhapsody window (a hidden dialog may be blocking it) before closing or reopening the
          project, or recent work will be lost.
        </div>
      )}
      {error && <div className="error-banner">{error}</div>}
      {info && !error && <div className="info-banner">{info}</div>}

      <div className="app-body">
        <Palette items={palette} />
        <div className="canvas" onDrop={onDrop} onDragOver={(e) => e.preventDefault()}>
          <ReactFlowProvider>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              nodeTypes={nodeTypes}
              onNodeClick={(_, node) => setSelectedGuid(node.id)}
              onPaneClick={() => setSelectedGuid(null)}
              fitView
            >
              <Background />
              <Controls />
            </ReactFlow>
          </ReactFlowProvider>
        </div>
      </div>

      {menu && <ContextMenu x={menu.x} y={menu.y} items={menu.items} onClose={() => setMenu(null)} />}
      {movePickerTarget && architecture && (
        <MoveElementPicker
          architecture={architecture}
          targetGuid={movePickerTarget.guid}
          targetName={movePickerTarget.name}
          onMove={(guid) => {
            const parentGuid = movePickerTarget.guid;
            setMovePickerTarget(null);
            onMoveElement(guid, parentGuid);
          }}
          onClose={() => setMovePickerTarget(null)}
        />
      )}
    </div>
  );
}

export default App;
