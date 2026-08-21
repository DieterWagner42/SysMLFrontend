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
import { AddActorPicker } from "./components/AddActorPicker";
import { UseCaseEditorModal } from "./components/UseCaseEditorModal";
import { DocumentationModal } from "./components/DocumentationModal";
import { ConnectorsTable } from "./components/ConnectorsTable";
import { ArchitectureNode, type ArchitectureNodeData } from "./components/nodes/ArchitectureNode";
import { ActorNode, type ActorNodeData } from "./components/nodes/ActorNode";
import { CapabilityNode, type CapabilityNodeData } from "./components/nodes/CapabilityNode";
import { SystemOfInterestNode, type SystemOfInterestNodeData } from "./components/nodes/SystemOfInterestNode";
import type { ArchKind, ArchNode, ConnectorRow, ElementRef, KnownInterface, PortDirection, PortSpec, PortView, UseCaseDetail } from "./types";
import { contextViewKey } from "./utils/contextViewKey";

type Tab = "architecture" | "context" | "capabilities" | "connectors";

const COL_WIDTH = 260;
const ROW_HEIGHT = 170; // Context/Capabilities tabs' simple grid layout only
const ROW_GAP = 60; // vertical gap between architecture depth rows, on top of estimateNodeHeight

const nodeTypes: NodeTypes = {
  architecture: ArchitectureNode,
  actor: ActorNode,
  capability: CapabilityNode,
  systemOfInterest: SystemOfInterestNode,
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
  return filterPortsByViews(ports, new Set([view]));
}

/** Same walk-and-splice-up shape as filterPortsByView, generalized to a SET of allowed views
 * instead of a single exact one — needed for aspect nodes (see ASPECT_PORT_VIEWS below), where
 * "belongs to this aspect" isn't a single exact view match. */
function filterPortsByViews(ports: PortSpec[], allowed: ReadonlySet<PortView>): PortSpec[] {
  function walk(list: PortSpec[]): PortSpec[] {
    const out: PortSpec[] = [];
    for (const p of list) {
      const filteredChildren = walk(p.children);
      if (p.view != null && allowed.has(p.view)) {
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

/** Which port views an aspect node's own tab shows NESTED decomposition for — requested live,
 * correcting the earlier "aspect ports aren't filtered at all" assumption below: "warum werden in
 * system_f ... die physicalische nested ports übernommen? ... System_F nested funcional und
 * operational interface. System_L nested logical interface. System_P nested physical interface."
 * An external interface's top-level occurrence (e.g. "Truck") is shared via one interfaceBlock
 * contract across every root that reuses it (see backend/CLAUDE.md's "Interfaces are kept in sync
 * as a single Unikat" section), so its OWN nested children (Mechanical/Power, Physical; mechanic/
 * power, Operational) are the exact same objects everywhere Truck appears — including under
 * System_F, even though System_F is a Functional-only context. Functional gets BOTH Operational
 * and Functional (not just Functional): Operational has no aaspect tree of its own to be shown
 * under instead (unlike Logical/Physical, which each own a dedicated System_L/System_P tree), so
 * its nested content is folded into the next step of the decomposition chain rather than having
 * nowhere to appear at all. Logical/Physical each stay scoped to their own single exact view —
 * they DO have their own dedicated aspect tree, so nothing needs folding in for them. */
const ASPECT_PORT_VIEWS: Partial<Record<ArchView, PortView[]>> = {
  Functional: ["Operational", "Functional"],
  Logical: ["Logical"],
  Physical: ["Physical"],
};

function filterAspectPorts(ports: PortSpec[], archView: ArchView): PortSpec[] {
  const allowed = ASPECT_PORT_VIEWS[archView];
  return allowed ? filterPortsByViews(ports, new Set(allowed)) : ports;
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
function updateArchNodePosition(root: ArchNode, guid: string, view: string, pos: { x: number; y: number }): ArchNode {
  if (root.guid === guid) {
    return { ...root, positions: { ...root.positions, [view]: pos } };
  }
  if (root.children.length === 0) return root;
  return { ...root, children: root.children.map((c) => updateArchNodePosition(c, guid, view, pos)) };
}

// Mirrors updateArchNodePosition exactly — see onNodesChange's own "reflect in SOURCE state"
// comment for why this is needed (otherwise a later rebuild triggered by an unrelated state change
// reverts to the last-FETCHED size). Size is keyed per view the same way position is (see
// ArchNode.sizes in types.ts) — was flat until that let a resize in one view silently overwrite the
// size shown in every other one.
function updateArchNodeSize(root: ArchNode, guid: string, view: string, size: { width: number; height: number }): ArchNode {
  if (root.guid === guid) {
    return { ...root, sizes: { ...root.sizes, [view]: size } };
  }
  if (root.children.length === 0) return root;
  return { ...root, children: root.children.map((c) => updateArchNodeSize(c, guid, view, size)) };
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
    onLinkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) => void;
    onUnlinkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) => void;
    allLogicalNodes: ElementRef[];
    onLinkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) => void;
    onUnlinkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) => void;
    allPhysicalNodes: ElementRef[];
    onAddFunction: (ownerGuid: string, name: string) => void;
    onFunctionDelete: (guid: string) => void;
    onEditDocumentation: (guid: string, name: string) => void;
    selectedGuid: string | null;
    archView: ArchView;
    knownInterfaces: KnownInterface[];
    physicalInterfaceTypes: string[];
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
        // A top-level aspect-node port itself is already scoped to its own aspect by construction
        // (created there, in that view) — but its NESTED decomposition can still diverge (a shared
        // external interface's children span multiple views — see ASPECT_PORT_VIEWS/
        // filterAspectPorts), so aspect nodes still need filtering, just a different rule than the
        // System-tree's single-exact-view one.
        ports: callbacks.archView === "Structure"
          ? (archNode.ports ?? [])
          : ASPECT_KINDS.has(archNode.kind as ArchKind)
            ? filterAspectPorts(archNode.ports ?? [], callbacks.archView)
            : filterPortsByView(archNode.ports ?? [], callbacks.archView),
        capabilities: archNode.capabilities ?? [],
        allCapabilities: callbacks.allCapabilities,
        allocatedLogicalNodes: archNode.allocatedLogicalNodes ?? [],
        allLogicalNodes: callbacks.allLogicalNodes,
        allocatedPhysicalNodes: archNode.allocatedPhysicalNodes ?? [],
        allPhysicalNodes: callbacks.allPhysicalNodes,
        functions: archNode.functions ?? [],
        // System Structure is a pure containment hierarchy — no interfaces/capabilities shown
        // there (see ArchitectureNodeData's doc comment); doesn't apply to FunctionalNodes.
        hideInterfacesAndCapabilities: callbacks.archView === "Structure",
        lockedView,
        isRootOwner: depth === 0,
        knownInterfaces: callbacks.knownInterfaces,
        physicalInterfaceTypes: callbacks.physicalInterfaceTypes,
        isDropTarget: archNode.guid === callbacks.selectedGuid,
        onContextMenu: callbacks.onContextMenu,
        onAddPort: callbacks.onAddPort,
        onPortChange: callbacks.onPortChange,
        onPortDelete: callbacks.onPortDelete,
        onLinkCapability: callbacks.onLinkCapability,
        onUnlinkCapability: callbacks.onUnlinkCapability,
        onLinkLogicalNode: callbacks.onLinkLogicalNode,
        onUnlinkLogicalNode: callbacks.onUnlinkLogicalNode,
        onLinkPhysicalNode: callbacks.onLinkPhysicalNode,
        onUnlinkPhysicalNode: callbacks.onUnlinkPhysicalNode,
        onAddFunction: callbacks.onAddFunction,
        onFunctionDelete: callbacks.onFunctionDelete,
        onEditDocumentation: callbacks.onEditDocumentation,
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
  const [actors, setActors] = useState<(ElementRef & { ports: PortSpec[]; contextViews: ElementRef[] })[]>([]);
  const [capabilities, setCapabilities] = useState<(ElementRef & { useCases: ElementRef[] })[]>([]);
  const [connectorRows, setConnectorRows] = useState<ConnectorRow[]>([]);
  // Every user-defined Context View (e.g. "Operational Context", "Maintenance Context") — each
  // becomes its own tab in the Context tab's own tab bar, alongside a built-in "All" tab (selected
  // via contextViewTab === null) — see the tab bar rendering below. Requested live: "im context
  // gibt es mehrere user defined Views... wir brauchen für jeden neuen kontext einen neuen tab".
  const [contextViews, setContextViews] = useState<ElementRef[]>([]);
  const [contextViewTab, setContextViewTab] = useState<string | null>(null);
  const [actorPickerOpen, setActorPickerOpen] = useState(false);
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
  const [useCaseEditorOpen, setUseCaseEditorOpen] = useState(false);
  const [useCaseEditorDetail, setUseCaseEditorDetail] = useState<UseCaseDetail | null>(null);
  // "Edit Documentation..." modal-in-modal — one generic instance shared by every element kind
  // (context menu entry for Architecture/Actor/Capability nodes, icon button for Function/UseCase
  // rows, entry in PortRow's own retype popover for Ports — see DocumentationModal's own javadoc).
  const [documentationTarget, setDocumentationTarget] = useState<{ guid: string; name: string } | null>(null);
  const onEditDocumentation = useCallback((guid: string, name: string) => {
    setDocumentationTarget({ guid, name });
  }, []);

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
        // physikalische!" An external suggestion is offered in every view once found — no
        // kind-group restriction (see utils/knownInterfaces.ts' forView) — Logical/Physical resolve
        // exactly like Functional/Operational here, mirroring the backend's own unified
        // findOrCreateInterfaceBlock/findExternalInterfaceBlockAcrossAllViews.
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

  // Every LogicalNode anywhere in the model (the System_L tree can nest to arbitrary depth — see
  // HierarchyLevels.java), for AllocationsSection's own picker (see App.tsx's `allLogicalNodes`
  // usage below) — mirrors `capabilities`, but LogicalNodes aren't their own top-level fetched
  // list, they're architecture elements, so this walks the already-fetched tree instead of a
  // separate endpoint.
  const allLogicalNodes = useMemo<ElementRef[]>(() => {
    const out: ElementRef[] = [];
    function walk(node: ArchNode | null | undefined) {
      if (!node) return;
      if (node.kind === "LogicalNode") out.push({ guid: node.guid, name: node.name, kind: "LogicalNode" });
      node.children.forEach(walk);
    }
    architecture?.children.forEach(walk);
    return out;
  }, [architecture]);

  // Every PhysicalNode anywhere in the model — mirrors allLogicalNodes exactly, one aspect tree
  // down (System_P instead of System_L), for AllocationsSection's own picker on a LogicalNode.
  const allPhysicalNodes = useMemo<ElementRef[]>(() => {
    const out: ElementRef[] = [];
    function walk(node: ArchNode | null | undefined) {
      if (!node) return;
      if (node.kind === "PhysicalNode") out.push({ guid: node.guid, name: node.name, kind: "PhysicalNode" });
      node.children.forEach(walk);
    }
    architecture?.children.forEach(walk);
    return out;
  }, [architecture]);

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

  // Same event-shape quirk as lastDragPositionRef above, for resize instead of drag: react-flow's
  // final resizing:false event carries no `dimensions` field either — only the preceding
  // resizing:true events do. Tracks the latest size seen per node id across the whole resize so the
  // final resizing:false event can fall back to it (see onNodesChange).
  const lastDragSizeRef = useRef<Record<string, { width: number; height: number }>>({});

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    for (const c of changes) {
      // React Flow's own auto-measurement (ResizeObserver tracking a node's natural content size)
      // also emits type:"dimensions" changes, but never sets `resizing` — only NodeResizer's own
      // drag handler does (true while dragging, false on release). Only the latter is a deliberate
      // user resize we want to remember; treating the former as one would "freeze" every node at
      // its first-ever measured size (see the has-custom-size CSS comment in App.css).
      if (c.type === "dimensions" && c.dimensions && typeof c.resizing === "boolean") {
        nodeSizesRef.current[c.id] = { width: c.dimensions.width, height: c.dimensions.height };
        lastDragSizeRef.current[c.id] = { width: c.dimensions.width, height: c.dimensions.height };
      }
      // resizing:false fires once at resize end (continuously with resizing:true while dragging the
      // handle) — only persist then, mirroring position's own dragging:false handling below. Found
      // live (via a console.log dump of the raw `changes` array — the resize never fired ANY
      // network request, not even for a plain ArchitectureNode, contradicting the "verified live"
      // claim from when this was first built): unlike every resizing:true event, react-flow's FINAL
      // resizing:false event does NOT carry a `dimensions` field at all — only `{type, id,
      // resizing:false}`. The original condition required `c.dimensions` on this SAME event, so
      // `c.resizing === false` was never even reached, and NOTHING was ever persisted, for ANY node,
      // not just the system-of-interest — the box visually resized (react-flow's own internal state)
      // but reverted the instant anything else triggered a rebuild (e.g. clicking another node),
      // reported live as "die Größe wird nach Klicke auf eine andere Box sofort wieder
      // zurückgesetzt." This is the exact same event-shape quirk already documented and worked
      // around for position's own dragging:false (see lastDragPositionRef below) — now mirrored here
      // via lastDragSizeRef, falling back to the last dimensions seen during the drag when this
      // specific event omits them.
      if (c.type === "dimensions" && c.resizing === false) {
        const finalSize = c.dimensions ?? lastDragSizeRef.current[c.id];
        if (finalSize) {
          // Requested live: "kann ich alle boxen auch in der breite/höhe ändern? wenn ja müssen wir
          // das auch in der xml datei speichern." The system-of-interest's own Context-tab node uses
          // a "system-"-prefixed id (see its own builder in the Context tab effect — kept distinct
          // from the real element guid so a plain drag there could never collide with the Architecture
          // tab's own position tracking); strip that prefix back off before persisting, since the
          // backend only knows the real guid.
          const isSystemNode = c.id.startsWith("system-");
          const guid = isSystemNode ? c.id.slice("system-".length) : c.id;
          const { width, height } = finalSize;
          if (isSystemNode || tab === "architecture") {
            // A dedicated size slot per Context View (contextViewKey), distinct from every
            // Architecture-tab view AND from every other Context View — the system-of-interest's
            // Context-tab node shares its guid with the Architecture tab's own root element but is
            // rendered very differently there (surrounded by a different set of Actors per Context
            // View tab), so it needs its own per-tab size instead of colliding with whichever
            // Architecture view — or, in an earlier round of this same bug, whichever OTHER Context
            // View — was resized last (see ArchNode.sizes in types.ts).
            const view = isSystemNode ? contextViewKey(contextViewTab) : archView;
            api.setSize(guid, width, height, view).catch((e) => setError(e instanceof Error ? e.message : String(e)));
            // The system-of-interest's Context-tab node is derived from `architecture` (see its own
            // useMemo), not `actors` — even though it's shown while tab === "context".
            setArchitecture((prev) => prev && updateArchNodeSize(prev, guid, view, { width, height }));
          } else if (tab === "context") {
            api.setSize(guid, width, height).catch((e) => setError(e instanceof Error ? e.message : String(e)));
            setActors((prev) => prev.map((a) => (a.guid === guid ? { ...a, width, height } : a)));
          } else if (tab === "capabilities") {
            api.setSize(guid, width, height).catch((e) => setError(e instanceof Error ? e.message : String(e)));
            setCapabilities((prev) => prev.map((cap) => (cap.guid === guid ? { ...cap, width, height } : cap)));
          }
        }
      }
      if (c.type === "position" && c.position) {
        lastDragPositionRef.current[c.id] = c.position;
      }
      // dragging:false fires once at drag end (continuously with dragging:true while moving) — only
      // persist then, not on every mousemove. Only the Architecture tab has a "view" to scope the
      // position to (see ArchNode.positions in types.ts) — plain Actors/Capabilities pass
      // view=undefined, which api.setPosition treats as "no view" server-side. The system-of-
      // interest's own Context-tab node is a THIRD case (mirrors its own size handling above) — was
      // fixed-position/non-draggable entirely until requested live right after size got its
      // per-Context-View fix: "die Größe geht jetzt, aber die Position noch nicht."
      if (c.type === "position" && c.dragging === false) {
        const finalPosition = c.position ?? lastDragPositionRef.current[c.id];
        if (finalPosition) {
          const isSystemNode = c.id.startsWith("system-");
          const guid = isSystemNode ? c.id.slice("system-".length) : c.id;
          const view = isSystemNode ? contextViewKey(contextViewTab) : tab === "architecture" ? archView : undefined;
          api.setPosition(guid, finalPosition.x, finalPosition.y, view).catch((e) => setError(e instanceof Error ? e.message : String(e)));
          // Also reflect the new position in the SOURCE state (architecture/actors/capabilities),
          // not just React Flow's own transient node array (updated below via onNodesChangeRaw) —
          // otherwise the next rebuild triggered by ANY other state change (e.g. selecting a
          // different node, which recomputes isDropTarget and is in this effect's own dependency
          // array) would revert to the last-FETCHED position, since layoutArchitectureTree/the
          // Context+Capabilities builders always derive a node's position fresh from this source
          // state — reported live as "I move Missile, Rhapsody keeps the new position, but the
          // frontend snaps it back when I select something else."
          if (isSystemNode || (tab === "architecture" && view)) {
            // The system-of-interest's Context-tab node is derived from `architecture` (see its own
            // useMemo), not `actors` — even though it's shown while tab === "context".
            setArchitecture((prev) => prev && updateArchNodePosition(prev, guid, view!, finalPosition));
          } else if (tab === "context") {
            setActors((prev) => prev.map((a) => (a.guid === guid ? { ...a, x: finalPosition.x, y: finalPosition.y } : a)));
          } else if (tab === "capabilities") {
            setCapabilities((prev) => prev.map((cap) => (cap.guid === guid ? { ...cap, x: finalPosition.x, y: finalPosition.y } : cap)));
          }
        }
      }
    }
    onNodesChangeRaw(changes);
  }, [onNodesChangeRaw, tab, archView, contextViewTab]);

  function applyStoredSize<T extends { hasCustomSize?: boolean }>(node: Node<T>): Node<T> {
    const size = nodeSizesRef.current[node.id];
    if (!size) return node;
    return {
      ...node,
      style: { ...node.style, width: size.width, height: size.height },
      data: { ...node.data, hasCustomSize: true },
    };
  }

  // Seeds nodeSizesRef from a persisted width/height BEFORE the node array is (re)built, so a
  // size saved in an earlier session (or by another client) shows up correctly on first render —
  // not just after this session's own resize. MUST clear (not skip) when width/height are null:
  // now that size is per-view (see ArchNode.sizes), the same guid is reseeded with a DIFFERENT
  // view's value on every rebuild (e.g. switching the Architecture tab from "Operational" — where
  // Flexis was resized — to "Functional" — where it never was). Leaving the old entry in place on a
  // null seed (the pre-per-view behavior, when a guid only ever had ONE flat size that couldn't
  // toggle between "set" and "unset" across rebuilds) made the previous view's size silently bleed
  // into every other view — reported live as "die Größenänderung bei Flexis ist immer noch nicht
  // per view", even after per-view storage landed on the backend.
  function seedNodeSize(guid: string, width: number | null | undefined, height: number | null | undefined) {
    if (width != null && height != null) {
      nodeSizesRef.current[guid] = { width, height };
    } else {
      delete nodeSizesRef.current[guid];
    }
  }

  // view is one of the 5 Architecture-tab views (mirrors archView) — a node's size is now keyed the
  // same way its position already is (see ArchNode.sizes in types.ts), so seeding has to pick the
  // one slot that matches whichever view is currently being rendered.
  function seedArchTreeSizes(node: ArchNode, view: string) {
    const size = node.sizes?.[view];
    seedNodeSize(node.guid, size?.width, size?.height);
    node.children.forEach((c) => seedArchTreeSizes(c, view));
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
      items.map(async (a) => ({
        ...a,
        ports: await api.getPorts(a.guid).catch(() => []),
        contextViews: await api.getContextViewsOf(a.guid).catch(() => []),
      })),
    );
    setActors(withPorts);
  }), [withErrorHandling]);

  // Fetched unconditionally, like capabilities: the Context tab's own tab bar needs the full list
  // even before an Actor's own ContextViewsSection picker has ever been opened.
  const refreshContextViews = useCallback(() => withErrorHandling(async () => {
    setContextViews(await api.getContextViews());
  }), [withErrorHandling]);

  const refreshCapabilities = useCallback(() => withErrorHandling(async () => {
    const items = await api.getCapabilities();
    const withUseCases = await Promise.all(
      items.map(async (c) => ({ ...c, useCases: await api.getUseCasesOf(c.guid).catch(() => []) })),
    );
    setCapabilities(withUseCases);
  }), [withErrorHandling]);

  // Fetched lazily (only once the Connectors tab is actually opened, see the useEffect keyed on
  // `tab` below) rather than unconditionally on mount like architecture/capabilities — a
  // project-wide connector scan is heavier than those, and this tab's data isn't needed by any
  // other tab the way architecture's own guid is.
  const refreshConnectorTable = useCallback(() => withErrorHandling(async () => {
    setConnectorRows(await api.getConnectorTable());
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

  // Also unconditional: the Context tab's own tab bar needs the full Context View list even
  // before the Context tab has ever been visited.
  useEffect(() => {
    refreshContextViews();
  }, [refreshContextViews]);

  useEffect(() => {
    if (tab === "context") refreshContext();
  }, [tab, refreshContext]);

  useEffect(() => {
    if (tab === "connectors") refreshConnectorTable();
  }, [tab, refreshConnectorTable]);

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
      {
        label: "Edit Documentation...",
        onClick: () => {
          const target = architecture ? findArchNodeByGuid(architecture, guid) : null;
          setDocumentationTarget({ guid, name: target?.name ?? "" });
        },
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
  // Context tab now shows ports for TWO different kinds of node — an Actor (its own `actors`
  // state) AND the system-of-interest (a real architecture element, its ports living in
  // `architecture` state instead — see SystemOfInterestNode's own javadoc: "flexis muss auch alle
  // interfaces haben!"). Refreshing only `refreshContext()` there left the System node's own
  // ports stale after an edit, since that node's data comes from `architecture`, not `actors`. So
  // the Context tab refreshes BOTH — cheap (two GETs), and correctness matters more here than
  // saving one request.
  const onAddPort = useCallback((ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => {
    withErrorHandling(async () => {
      await api.createPort(ownerGuid, name, direction, type, view);
      if (tab === "architecture") await refreshArchitecture();
      else await Promise.all([refreshArchitecture(), refreshContext()]);
    });
  }, [tab, refreshArchitecture, refreshContext, withErrorHandling]);

  const onPortChange = useCallback((portGuid: string, direction: PortDirection, type: string, view: PortView) => {
    withErrorHandling(async () => {
      await api.updatePort(portGuid, direction, type, view);
      if (tab === "architecture") await refreshArchitecture();
      else await Promise.all([refreshArchitecture(), refreshContext()]);
    });
  }, [tab, refreshArchitecture, refreshContext, withErrorHandling]);

  const onPortDelete = useCallback((portGuid: string) => {
    withErrorHandling(async () => {
      await api.deletePort(portGuid);
      if (tab === "architecture") await refreshArchitecture();
      else await Promise.all([refreshArchitecture(), refreshContext()]);
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

  // LogicalNode allocation links are embedded inline in the architecture tree (see
  // ModelStore#getAllocatedLogicalNodesOf), same as Capability links above.
  const onLinkLogicalNode = useCallback((functionalNodeGuid: string, logicalNodeGuid: string) => {
    withErrorHandling(async () => {
      await api.linkLogicalNode(functionalNodeGuid, logicalNodeGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  const onUnlinkLogicalNode = useCallback((functionalNodeGuid: string, logicalNodeGuid: string) => {
    withErrorHandling(async () => {
      await api.unlinkLogicalNode(functionalNodeGuid, logicalNodeGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  // Logical→Physical allocation links — same mechanism as Functional→Logical above, one level down.
  const onLinkPhysicalNode = useCallback((logicalNodeGuid: string, physicalNodeGuid: string) => {
    withErrorHandling(async () => {
      await api.linkPhysicalNode(logicalNodeGuid, physicalNodeGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  const onUnlinkPhysicalNode = useCallback((logicalNodeGuid: string, physicalNodeGuid: string) => {
    withErrorHandling(async () => {
      await api.unlinkPhysicalNode(logicalNodeGuid, physicalNodeGuid);
      await refreshArchitecture();
    });
  }, [refreshArchitecture, withErrorHandling]);

  const onUnlinkContextView = useCallback((actorGuid: string, contextViewGuid: string) => {
    withErrorHandling(async () => {
      await api.unlinkContextView(actorGuid, contextViewGuid);
      await refreshContext();
    });
  }, [refreshContext, withErrorHandling]);

  // AddActorPicker's two actions — see its own javadoc for why this replaced the old unconditional
  // window.prompt create-only flow on the Context tab. Both link the result to the CURRENTLY
  // SELECTED Context View tab (contextViewTab) when one is active; on the built-in "All" tab
  // (contextViewTab === null) a create just creates, with nothing to link to yet.
  const onCreateActorFromPicker = useCallback((name: string) => {
    withErrorHandling(async () => {
      if (!architecture) return;
      const created = await api.createActor(architecture.guid, name);
      if (contextViewTab) await api.linkContextView(created.guid, contextViewTab);
      setActorPickerOpen(false);
      await refreshContext();
    });
  }, [architecture, contextViewTab, refreshContext, withErrorHandling]);

  // UseCaseEditorModal's "+ New Actor" — same create-and-optionally-link-to-a-Context-View shape
  // as onCreateActorFromPicker above, but the Context View is explicitly picked in that dialog
  // (NewActorPicker) rather than implied by the currently active Context tab, and the created
  // actor is returned so the modal can select it into the UseCase's own actor list right away.
  // Can't route through withErrorHandling (it discards its callback's return value) since the
  // caller needs the created ElementRef back — surfaces errors the same way, then rethrows so
  // UseCaseEditorModal's own picker doesn't silently close on failure.
  const onCreateActorForUseCase = useCallback(async (name: string, contextViewGuid: string | null) => {
    setInfo(null);
    try {
      if (!architecture) throw new Error("No model loaded");
      const created = await api.createActor(architecture.guid, name);
      if (contextViewGuid) await api.linkContextView(created.guid, contextViewGuid);
      await refreshContext();
      setError(null);
      return created;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      throw e;
    }
  }, [architecture, refreshContext]);

  const onSelectExistingActorFromPicker = useCallback((actorGuid: string) => {
    withErrorHandling(async () => {
      if (contextViewTab) await api.linkContextView(actorGuid, contextViewTab);
      setActorPickerOpen(false);
      await refreshContext();
    });
  }, [contextViewTab, refreshContext, withErrorHandling]);

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

  // Open the full UseCase editor modal — fetches the complete detail (goal/actors/paths/...)
  // for the clicked UseCase and shows the editor over the Capabilities tab canvas.
  const onOpenUseCase = useCallback((guid: string) => {
    withErrorHandling(async () => {
      const detail = await api.getUseCaseDetail(guid);
      // The backend's getUseCaseDetail JSON resolves actors to {guid,name} refs (for display
      // convenience — see ModelStore#getUseCaseDetail's own javadoc), but UseCaseDetail.actors
      // (and the editor's own <select value={actorGuid}> binding) is a plain guid string[] —
      // without normalizing here, each dropdown's value was an object matching no <option>, so a
      // saved UseCase's actors silently reset to "— Select Actor —" on every re-edit even though
      // the rows themselves round-tripped correctly.
      const actors = (detail.actors as unknown as Array<{ guid: string } | string>).map((a) =>
        typeof a === "string" ? a : a.guid
      );
      setUseCaseEditorDetail({ ...detail, actors });
      setUseCaseEditorOpen(true);
    });
  }, [withErrorHandling]);

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
    seedArchTreeSizes(architecture, archView);
    const { nodes: n, edges: e } = layoutArchitectureTree(architecture, {
      onContextMenu: onArchContextMenu,
      onAddPort,
      onPortChange,
      onPortDelete,
      onLinkCapability,
      onUnlinkCapability,
      allCapabilities: capabilities,
      onLinkLogicalNode,
      onUnlinkLogicalNode,
      allLogicalNodes,
      onLinkPhysicalNode,
      onUnlinkPhysicalNode,
      allPhysicalNodes,
      onAddFunction,
      onFunctionDelete,
      onEditDocumentation,
      selectedGuid,
      archView,
      knownInterfaces,
      physicalInterfaceTypes,
    });
    setNodes(n.map(applyStoredSize));
    setEdges(e);
  }, [tab, architecture, selectedGuid, archView, knownInterfaces, physicalInterfaceTypes, capabilities, allLogicalNodes, allPhysicalNodes, onArchContextMenu, onAddPort, onPortChange, onPortDelete, onLinkCapability, onUnlinkCapability, onLinkLogicalNode, onUnlinkLogicalNode, onLinkPhysicalNode, onUnlinkPhysicalNode, onAddFunction, onFunctionDelete, onEditDocumentation, setNodes, setEdges]);

  // ── Context tab graph ───────────────────────────────────────────────

  // The system-of-interest — the topmost non-aspect root of the Architecture tab's own tree
  // (SystemOfSystem/System, never a FunctionalNode/LogicalNode/PhysicalNode aspect root) — shown
  // automatically in every Context tab view as a fixed, read-only anchor. Requested live: "der
  // Systemblock muss immer in jeder view automatisch eingefügt werden." Only the first one is used
  // — a model normally has exactly one system-of-interest; if several exist (unusual), the rest
  // are simply not shown here (they still exist fine in the Architecture tab).
  const systemOfInterest = useMemo(
    () => architecture?.children.find((c) => !ASPECT_KINDS.has(c.kind as ArchKind)) ?? null,
    [architecture],
  );

  useEffect(() => {
    if (tab !== "context") return;
    actors.forEach((a) => seedNodeSize(a.guid, a.width, a.height));
    // The system-of-interest's own Context-tab node id is "system-"+guid (see below), not the
    // bare guid seedArchTreeSizes already covers via the Architecture tab effect — seed that
    // specific key too so its size shows up correctly here without ever having visited Architecture
    // first.
    if (systemOfInterest) {
      const key = contextViewKey(contextViewTab);
      const size = systemOfInterest.sizes?.[key];
      seedNodeSize(`system-${systemOfInterest.guid}`, size?.width, size?.height);
    }
    // contextViewTab === null is the built-in "All" tab (unfiltered) — a user-defined Context
    // View tab only shows Actors linked to it. An Actor may be linked to several Context Views
    // at once, so this is a simple membership filter, not a partition.
    const visibleActors = contextViewTab == null
      ? actors
      : actors.filter((a) => a.contextViews.some((cv) => cv.guid === contextViewTab));
    // A distinct node id ("system-"+guid, never the bare architecture guid) — this reuses the SAME
    // element as the Architecture tab's own root node, and dragging it here calls api.setPosition
    // with a dedicated "Context:<contextViewGuid>" view (contextViewKey), never the bare guid/no
    // view, so it can never collide with the Architecture tab's own per-view positions. Position was
    // originally fixed/non-draggable entirely (falling back to the same hardcoded offset now used
    // only when nothing's been dragged yet in this Context View) — requested live right after size
    // got the same treatment: "die Größe geht jetzt, aber die Position noch nicht." Shown regardless
    // of which Context View tab is selected, including "All", each with its own saved position.
    const systemNode: Node<SystemOfInterestNodeData>[] = systemOfInterest
      ? [{
          id: `system-${systemOfInterest.guid}`,
          type: "systemOfInterest",
          position: systemOfInterest.positions?.[contextViewKey(contextViewTab)] ?? { x: -COL_WIDTH - 40, y: 0 },
          draggable: true,
          selectable: false,
          data: {
            label: systemOfInterest.name,
            guid: systemOfInterest.guid,
            ports: systemOfInterest.ports ?? [],
            onAddPort,
            onPortChange,
            onPortDelete,
            onEditDocumentation,
            knownInterfaces,
            physicalInterfaceTypes,
            capabilities: systemOfInterest.capabilities ?? [],
            allCapabilities: capabilities,
            onLinkCapability,
            onUnlinkCapability,
          },
        }]
      : [];
    const n: Node<ActorNodeData>[] = visibleActors.map((a, i) => ({
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
          const removeFromContext = contextViewTab
            ? [{
                label: "Remove from this Context",
                onClick: () => onUnlinkContextView(guid, contextViewTab),
              }]
            : [];
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
                label: "Edit Documentation...",
                onClick: () => setDocumentationTarget({ guid, name: a.name }),
              },
              ...removeFromContext,
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
        onEditDocumentation,
        knownInterfaces,
        physicalInterfaceTypes,
      },
    }));
    setNodes([...systemNode.map(applyStoredSize), ...n.map(applyStoredSize)]);
    setEdges([]);
  }, [tab, actors, contextViewTab, systemOfInterest, selectedGuid, knownInterfaces, physicalInterfaceTypes, capabilities, onAddPort, onPortChange, onPortDelete, onEditDocumentation, onLinkCapability, onUnlinkCapability, onUnlinkContextView, refreshContext, setNodes, setEdges, withErrorHandling]);

  // ── Capabilities tab graph ──────────────────────────────────────────

  useEffect(() => {
    if (tab !== "capabilities") return;
    capabilities.forEach((c) => seedNodeSize(c.guid, c.width, c.height));
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
                label: "Edit Documentation...",
                onClick: () => setDocumentationTarget({ guid, name: c.name }),
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
        onOpenUseCase,
      },
    }));
    setNodes(n.map(applyStoredSize));
    setEdges([]);
  }, [tab, capabilities, selectedGuid, onAddUseCase, onUseCaseDelete, onOpenUseCase, refreshCapabilities, refreshArchitecture, setNodes, setEdges, withErrorHandling]);

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
      } else if (tab === "context" && itemType === "Actor") {
        // Opens AddActorPicker instead of creating unconditionally — requested live: "beim
        // drag&drop sollen auch existierende externe Systeme auswählbar sein" (see the picker's
        // own "onCreateNew"/"onSelectExisting" handlers below for what happens next).
        setActorPickerOpen(true);
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
          <button className={tab === "connectors" ? "active" : ""} onClick={() => setTab("connectors")}>
            Connectors
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

      {tab === "context" && (
        <div className="arch-view-tabs">
          <button className={contextViewTab == null ? "active" : ""} onClick={() => setContextViewTab(null)}>
            All
          </button>
          {contextViews.map((cv) => (
            <button
              key={cv.guid}
              className={contextViewTab === cv.guid ? "active" : ""}
              onClick={() => setContextViewTab(cv.guid)}
              onContextMenu={(e) => {
                e.preventDefault();
                setMenu({
                  x: e.clientX,
                  y: e.clientY,
                  items: [
                    {
                      label: "Rename",
                      onClick: () => withErrorHandling(async () => {
                        const name = window.prompt("New name:", cv.name);
                        if (!name) return;
                        await api.renameElement(cv.guid, name);
                        await refreshContextViews();
                      }),
                    },
                    {
                      label: "Delete",
                      danger: true,
                      onClick: () => withErrorHandling(async () => {
                        if (!window.confirm(`Delete context "${cv.name}"? Actors linked to it are unaffected.`)) return;
                        await api.deleteContextView(cv.guid);
                        if (contextViewTab === cv.guid) setContextViewTab(null);
                        await refreshContextViews();
                        await refreshContext();
                      }),
                    },
                  ],
                });
              }}
            >
              {cv.name}
            </button>
          ))}
          <button
            title="Create a new user-defined context (e.g. Operational Context, Maintenance Context)"
            onClick={() => withErrorHandling(async () => {
              const name = window.prompt("New context name:");
              if (!name) return;
              const created = await api.createContextView(name);
              await refreshContextViews();
              setContextViewTab(created.guid);
            })}
          >
            + New Context
          </button>
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

      {tab === "connectors" ? (
        <div className="app-body">
          <ConnectorsTable rows={connectorRows} />
        </div>
      ) : (
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
      )}

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
      {actorPickerOpen && (
        <AddActorPicker
          existingActors={contextViewTab ? actors.filter((a) => !a.contextViews.some((cv) => cv.guid === contextViewTab)) : []}
          contextName={contextViewTab ? contextViews.find((cv) => cv.guid === contextViewTab)?.name ?? null : null}
          onCreateNew={onCreateActorFromPicker}
          onSelectExisting={onSelectExistingActorFromPicker}
          onClose={() => setActorPickerOpen(false)}
        />
      )}
      {useCaseEditorOpen && useCaseEditorDetail && (
        <UseCaseEditorModal
          useCase={useCaseEditorDetail}
          actors={actors}
          contextViews={contextViews}
          onCreateActor={onCreateActorForUseCase}
          onSave={async (detail) => {
            await api.updateUseCase(useCaseEditorDetail.guid, detail);
            setUseCaseEditorOpen(false);
            setUseCaseEditorDetail(null);
          }}
          onClose={() => {
            setUseCaseEditorOpen(false);
            setUseCaseEditorDetail(null);
          }}
        />
      )}
      {documentationTarget && (
        <DocumentationModal
          guid={documentationTarget.guid}
          name={documentationTarget.name}
          onClose={() => setDocumentationTarget(null)}
        />
      )}
    </div>
  );
}

export default App;
