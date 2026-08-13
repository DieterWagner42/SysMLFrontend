export type PortDirection = "In" | "Out" | "InOut";

/** Every port is a ProxyPort classified into one of four architecture views. "children" is the
 * port's own decomposition — not sub-elements of the port itself, but ports owned by the
 * interfaceBlock that types it (see backend/CLAUDE.md's RhapsodyModelStore notes) — nesting one
 * of these under another typically follows Operational → Functional → Logical → Physical, though
 * that progression isn't enforced by the backend. */
export type PortView = "Operational" | "Functional" | "Logical" | "Physical";

export interface PortSpec {
  guid: string;
  name: string;
  direction: PortDirection | null;
  type: string | null;
  view: PortView | null;
  children: PortSpec[];
}

/** A distinct {name, direction, type, view} combination seen among every port anywhere in the
 * current model (any architecture element, any nesting depth, any Actor — see App.tsx's
 * `knownInterfaces` useMemo) — offered as reuse suggestions when adding a new interface, so e.g. an
 * Operational "HEU" port already used on the System can be picked again on a Subsystem instead of
 * retyping name/direction/type from scratch. Purely a frontend convenience/autocomplete concept —
 * selecting one still creates an independent new port (no shared backend identity), see
 * PortsSection/PortRow's use of it via a native <datalist>.
 *
 * Suggestions must stay scoped to the view actually being edited (see utils/knownInterfaces.ts'
 * forView) — mirrors the backend's own per-view interfaceBlock scoping (backend/CLAUDE.md's
 * "Interfaces are scoped per view" section) instead of offering every interface in the whole model
 * regardless of view, which is confusing once same-named interfaces legitimately exist in different
 * views with different settings. `external` is the one exception: true for a port owned directly by
 * a tree-root architecture element (Flexis/System_F/System_L/System_P themselves, never a nested
 * element or an Actor) — mirrors the backend's `isRootLevelClass`/`isExternalPort`, and such a port
 * stays suggested regardless of which view is currently being edited, matching the backend's own
 * cross-view reuse allowance for those.
 *
 * `parentName` is display-only disambiguation, NOT a separate identity — requested live: "in der
 * Auswahl muss HEU.Voice und HEU1.Voice unterschieden werden", then clarified "Voice ist Voice
 * also auch das selbe ibVoice" — i.e. two same-named nested interfaces (e.g. "Voice" nested under
 * both "HEU" and "HEU1") really ARE the same shared backend interface (same interfaceBlock,
 * confirmed intentional), so `name` stays the single reuse key exactly as before; `parentName` (the
 * immediate owning port's own name, or null for a top-level port) only feeds a qualified label in
 * the suggestion list — see utils/knownInterfaces.ts' `labelFor` — so a user can tell WHICH
 * occurrence a same-named suggestion came from before picking it, without that choice changing
 * what gets linked. */
export interface KnownInterface {
  name: string;
  direction: PortDirection | null;
  type: string | null;
  view: PortView | null;
  external: boolean;
  parentName: string | null;
}

/** The hierarchy is automatic, not freely chosen: SystemOfSystem (optional, level -1) → System (0)
 * → Subsystem (1) → Equipment (2, leaf). "Block" is a read-only legacy fallback for an untyped
 * element predating this rule (only possible in Rhapsody mode) — not creatable from the UI.
 * "FunctionalNode"/"LogicalNode"/"PhysicalNode" are three more separate root-level trees (see
 * App.tsx's layoutArchitectureTree) shown only in their matching architecture view (Functional/
 * Logical/Physical) — each decomposes into sub-nodes of its own kind at arbitrary depth, not
 * through the SoS/System/Subsystem/Equipment chain. Each owns ports (like a Block); FunctionalNode
 * additionally owns an attached list of Functions (see ArchNode.functions below) — Logical/
 * PhysicalNode currently own only ports, no analogous attached list. */
export type ArchLevel = "SystemOfSystem" | "System" | "Subsystem" | "Equipment" | "FunctionalNode" | "LogicalNode" | "PhysicalNode";
export type ArchKind = ArchLevel | "Block";

/** The model root has kind "Model" and is never rendered as a canvas node — see App.tsx's
 * layoutArchitectureTree, which starts laying out nodes from root.children. */
export interface ArchNode {
  guid: string;
  name: string;
  kind: ArchKind | "Model";
  children: ArchNode[];
  ports?: PortSpec[];
  // Capabilities LINKED to this element (a reference, not ownership — see
  // ModelStore#getCapabilitiesOf/linkCapability), rendered like ports. The Capability itself is
  // created/named/owns its UseCases in the Capabilities tab (see CapabilityNode); this is just
  // which of those top-level Capabilities this element points at.
  capabilities?: ElementRef[];
  // Functions attached to a FunctionalNode (see ModelStore#getFunctionsOf) — same shape as
  // capabilities (just a name), but a distinct concept only FunctionalNodes carry.
  functions?: ElementRef[];
  // Canvas position, keyed by Architecture-tab view — an architecture element needs one per view
  // (not a single x/y) because "Structure" and "Operational" both render the exact same tree/
  // guids, so a manual drag in one view must not move the node in the other (see
  // ModelStore#setPosition). A view missing from this map means "not yet positioned in that
  // view", so the frontend falls back to its own tidy-tree auto-layout for it.
  positions?: Partial<Record<"Structure" | PortView, { x: number; y: number }>>;
}

export interface ElementRef {
  guid: string;
  name: string;
  kind: "Actor" | "UseCase" | "Function" | "Capability" | "ContextView";
  x?: number | null;
  y?: number | null;
}

/** One connector (Rhapsody IRPLink) that should exist but doesn't yet — see the backend's
 * ModelStore#getPendingConnectors javadoc. Rhapsody mode only; always an empty list in local mode.
 * The 5 GUIDs are opaque to the frontend — passed straight back to createPendingConnector, which
 * re-resolves them fresh rather than trusting a cached reference.
 *
 * A WARNING entry (e.g. an "internal" broadcast interface missing a sender/receiver — see the
 * backend's collectInternalBroadcastPending) has only `warning` set and every other field absent —
 * it's informational only, never something createPendingConnector can act on. */
export interface PendingConnector {
  linkOwnerGuid?: string;
  fromPartGuid?: string;
  toPartGuid?: string;
  fromPortGuid?: string;
  toPortGuid?: string;
  fromOwnerGuid?: string | null;
  toOwnerGuid?: string | null;
  description?: string;
  warning?: string;
}
