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
  // LogicalNodes this element (a FunctionalNode) allocates to — Rhapsody: an "Allocate"-
  // stereotyped Dependency (see ModelStore#getAllocatedLogicalNodesOf/linkLogicalNode). A
  // reference, not ownership — same shape/reasoning as `capabilities`, just Functional→Logical
  // instead of element→Capability. Only meaningful for a FunctionalNode, same as `functions`
  // existing generically on ArchNode without every kind using it.
  allocatedLogicalNodes?: ElementRef[];
  // PhysicalNodes this element (a LogicalNode) allocates to — same mechanism/reasoning as
  // allocatedLogicalNodes, just Logical→Physical instead of Functional→Logical. Only meaningful
  // for a LogicalNode.
  allocatedPhysicalNodes?: ElementRef[];
  // Canvas position, keyed by Architecture-tab view — an architecture element needs one per view
  // (not a single x/y) because "Structure" and "Operational" both render the exact same tree/
  // guids, so a manual drag in one view must not move the node in the other (see
  // ModelStore#setPosition). A view missing from this map means "not yet positioned in that
  // view", so the frontend falls back to its own tidy-tree auto-layout for it. PLUS a dynamic
  // "Context:<contextViewGuid>" (or "Context:All") key for the system-of-interest's own box in the
  // Context tab — see the matching comment on `sizes` below (position needed the exact same
  // treatment, requested live right after: "die Größe geht jetzt, aber die Position noch nicht").
  // Kept as a plain string-indexed map for the same open-ended-Context-View reason as `sizes`.
  positions?: Record<string, { x: number; y: number }>;
  // Manually-resized box size (see ModelStore#setSize), keyed the same way as positions above for
  // the 5 Architecture-tab views — PLUS a dynamic "Context:<contextViewGuid>" (or "Context:All" for
  // the built-in unfiltered tab) key for the system-of-interest's own box in the Context tab (see
  // SystemOfInterestNode/App.tsx's contextViewKey) — one slot per Context View, not a single shared
  // "Context" slot, since Context Views are user-created and open-ended rather than a fixed set like
  // the 5 Architecture views. Originally a single flat width/height shared across literally
  // everything, then a single shared "Context" slot across every Context View — both meant resizing
  // in one place silently overwrote the size shown everywhere else (reported live, twice: "wenn ich
  // flexis in einer der Context Views verändere springt die Größe immer wieder zurück", then "egal
  // in welcher view ich die größe von flexis ändere werden dia anderen views mit geändert!"). A view
  // missing from this map means "never resized in that view, use the default". Kept as a plain
  // string-indexed map (not a closed union) specifically because of the open-ended Context View
  // case — see utils/contextViewKey.ts.
  sizes?: Record<string, { width: number; height: number }>;
}

export interface ElementRef {
  guid: string;
  name: string;
  kind: "Actor" | "UseCase" | "Function" | "Capability" | "ContextView" | "LogicalNode" | "PhysicalNode";
  x?: number | null;
  y?: number | null;
  width?: number | null;
  height?: number | null;
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

/** One row of the Connectors tab's table — see ModelStore#getConnectorTable's own javadoc.
 * fromOwner/toOwner/fromName/toName are the raw, unresolved End1Path/End2Path segments (or the
 * pending-connector equivalent) — not prettified back to a DisplayName. */
export interface ConnectorRow {
  view: PortView | null;
  fromOwner: string;
  fromName: string;
  toOwner: string;
  toName: string;
}

/** One alternative path (A1, A1.1, ...) in a UseCase — a branch off a specific step (or set of
 * steps) in the Basic Path. stepRefs identifies which step(s) it branches from ("always" = any
 * step); whatHappens is the human description; subSteps are the A1.1, A1.2... detail steps; and
 * postCondition is the state after the alternative completes. */
export interface AlternativePathGroup {
  title: string;
  stepRefs: string[];
  whatHappens: string;
  subSteps: string[];
  postCondition: string;
}

/** One extension path (E1, E1.1, ...) in a UseCase — a trigger that can happen at any point;
 * triggerText is the condition that fires it; subSteps are the E1.1, E1.2... detail steps. */
export interface ExtensionGroup {
  title: string;
  triggerText: string;
  subSteps: string[];
}

/** Full detail of a UseCase — the rich narrative beyond just its name (Goal, Actors, Preconditions,
 * Basic Path with B1 trigger, Alternative Paths, Extension Paths, Post Condition). Persisted to XML
 * and editable via the UseCase editor modal. actors are actor GUIDs (resolved to names for display).
 */
export interface UseCaseDetail {
  guid: string;
  name: string;
  capabilityGuid: string;
  goal: string;
  actors: string[];
  preconditions: string[];
  basicPath: string[];
  alternatives: AlternativePathGroup[];
  extensions: ExtensionGroup[];
  postCondition: string;
}
