import type { ArchLevel, ArchNode, ConnectorRow, ElementRef, PendingConnector, PortSpec, PortView, UseCaseDetail } from "../types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:4567/api";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options?.headers ?? {}) },
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body?.message ?? `Request to ${path} failed with status ${res.status}`);
  }
  return body as T;
}

export const api = {
  status: () => request<{ status: string; url: string; mode: string; rhapsodyAvailable: boolean; saveHealthy: boolean }>("/status"),

  /** Resets to a fresh, empty local model with the given title and makes it the active store —
   * "New Model" (always local; see exportToRhapsody to promote it into a real Rhapsody project). */
  newModel: (name: string) => request<ArchNode>("/newModel", {
    method: "POST",
    body: JSON.stringify({ name }),
  }),

  /** Points the local model's auto-persist/auto-load XML file at "<folder>/local-model.xml"
   * instead of config.ini's fixed default — loads it if it already exists there. Called once on
   * startup (while still in local mode) right after the user picks a folder via pickFile("open",
   * "folder", ...). Returns whatever Rhapsody project that folder's model already remembers (or
   * null) — see App.tsx's own startup effect, which pre-fills "Load Model" with it. */
  setLocalStateFolder: (folder: string) => request<{ status: string; rhapsodyPath: string | null }>("/localStateFolder", {
    method: "POST",
    body: JSON.stringify({ folder }),
  }),

  /** Connects to Rhapsody and switches straight to editing the given (already-existing) project —
   * no data transfer, just "start online editing this Rhapsody project directly". Also remembers
   * the path on the local model (same as exportToRhapsody) — see ModelStore#linkedRhapsodyPath. */
  loadModel: (path: string) => request<{ status: string; project: string }>("/loadModel", {
    method: "POST",
    body: JSON.stringify({ path }),
  }),

  /** Connects to Rhapsody, opens the given (already-existing, empty-or-not) project, pushes the
   * current local model's content into it, and switches the active store to Rhapsody. */
  exportToRhapsody: (path: string) => request<{ elementsCreated: number; actorsCreated: number; useCasesCreated: number; capabilitiesCreated: number }>("/exportToRhapsody", {
    method: "POST",
    body: JSON.stringify({ path }),
  }),

  selectElement: (guid: string) => request<{ status: string; element: string }>("/selectElement", {
    method: "POST",
    body: JSON.stringify({ guid }),
  }),

  /** Pops a native OS file/folder picker (the backend runs locally, same machine as the browser) —
   * used when a Load/Save path field was left empty, and for the startup local-XML-folder prompt
   * (filter "folder" — directory selection only, no extension filter). Returns the chosen absolute
   * path, or null if the user cancelled. */
  pickFile: (mode: "open" | "save", filter: "xml" | "rpyx" | "folder", title?: string, suggestedName?: string) =>
    request<{ path: string | null }>("/dialog", {
      method: "POST",
      body: JSON.stringify({ mode, filter, title, suggestedName }),
    }).then((r) => r.path),

  // ── Config (web config page — gear icon in the header) ──────────────

  // Open, extensible list of physical interface types offered when adding a port while viewing
  // the Physical architecture view — persisted to config.ini on the backend.
  getPhysicalInterfaceTypes: () => request<{ items: string[] }>("/config/physicalInterfaceTypes").then((r) => r.items),

  setPhysicalInterfaceTypes: (items: string[]) =>
    request<{ items: string[] }>("/config/physicalInterfaceTypes", {
      method: "PUT",
      body: JSON.stringify({ items }),
    }).then((r) => r.items),

  // ── Architecture ──────────────────────────────────────────────────────

  getArchitecture: () => request<ArchNode>("/architecture"),

  // kind is optional and honored only when parentGuid is the model root, and only for
  // "SystemOfSystem" — everywhere else the backend computes the level automatically from nesting.
  createArchitectureElement: (parentGuid: string, name: string, kind?: ArchLevel) =>
    request<ArchNode>("/architecture/elements", {
      method: "POST",
      body: JSON.stringify({ parentGuid, name, kind }),
    }),

  renameElement: (guid: string, name: string) =>
    request<{ status: string }>(`/architecture/elements/${encodeURIComponent(guid)}`, {
      method: "PATCH",
      body: JSON.stringify({ name }),
    }),

  deleteElement: (guid: string) =>
    request<{ status: string }>(`/architecture/elements/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // Moves an existing architecture element under a new parent (or the model root) — a true move,
  // not a copy. newParentGuid must be compatible with guid's own kind (same tree/family — see
  // backend/CLAUDE.md's HierarchyLevels#requireCompatibleMove); the backend throws a clear error
  // otherwise.
  moveElement: (guid: string, newParentGuid: string) =>
    request<{ status: string }>(`/architecture/elements/${encodeURIComponent(guid)}/parent`, {
      method: "PATCH",
      body: JSON.stringify({ newParentGuid }),
    }),

  // guid may be an architecture element, an Actor, or a UseCase (never a port, which isn't
  // independently positioned) — called once a drag ends, so manual layout survives reloads.
  // view is required for an architecture element (Structure/Operational/Functional/Logical/
  // Physical — see ArchNode.positions in types.ts) since Structure and Operational render the
  // same tree/guids; omit it for an Actor/UseCase, which have no view concept.
  setPosition: (guid: string, x: number, y: number, view?: string) =>
    request<{ status: string }>(`/positions/${encodeURIComponent(guid)}`, {
      method: "PATCH",
      body: JSON.stringify(view ? { x, y, view } : { x, y }),
    }),

  // view mirrors setPosition's own — required for an architecture element (one of the 5
  // Architecture-tab views, or "Context" for the system-of-interest's own Context-tab box, see
  // ArchNode.sizes in types.ts); omit it for an Actor/Capability/Context View, which have no view
  // concept and keep a single flat size.
  setSize: (guid: string, width: number, height: number, view?: string) =>
    request<{ status: string }>(`/sizes/${encodeURIComponent(guid)}`, {
      method: "PATCH",
      body: JSON.stringify(view ? { width, height, view } : { width, height }),
    }),

  // ── Context ───────────────────────────────────────────────────────────

  getContext: () => request<{ items: ElementRef[] }>("/context").then((r) => r.items),

  createActor: (parentGuid: string, name: string) =>
    request<ElementRef>("/context/actors", {
      method: "POST",
      body: JSON.stringify({ parentGuid, name }),
    }),

  deleteActor: (guid: string) => request<{ status: string }>(`/context/actors/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // ── Context Views (user-defined, top-level groupings of Actors — one tab each) ─────

  getContextViews: () => request<{ items: ElementRef[] }>("/contextViews").then((r) => r.items),

  createContextView: (name: string) =>
    request<ElementRef>("/contextViews", {
      method: "POST",
      body: JSON.stringify({ name }),
    }),

  deleteContextView: (guid: string) =>
    request<{ status: string }>(`/contextViews/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  getContextViewsOf: (actorGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(actorGuid)}/contextViews`).then((r) => r.items),

  linkContextView: (actorGuid: string, contextViewGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(actorGuid)}/contextViews`, {
      method: "POST",
      body: JSON.stringify({ contextViewGuid }),
    }),

  // Removes the link — does not delete the Context View itself (use deleteContextView for that).
  unlinkContextView: (actorGuid: string, contextViewGuid: string) =>
    request<{ status: string }>(`/elements/${encodeURIComponent(actorGuid)}/contextViews/${encodeURIComponent(contextViewGuid)}`, { method: "DELETE" }),

  // ── Capabilities (top-level grouping; each Capability owns a list of UseCases) ─────

  getCapabilities: () => request<{ items: ElementRef[] }>("/capabilities").then((r) => r.items),

  createCapability: (name: string) =>
    request<ElementRef>("/capabilities", {
      method: "POST",
      body: JSON.stringify({ name }),
    }),

  deleteCapability: (guid: string) =>
    request<{ status: string }>(`/capabilities/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // UseCases owned by a Capability, shown inside that Capability's own node — mirrors
  // getFunctionsOf/createFunction.
  getUseCasesOf: (capabilityGuid: string) =>
    request<{ items: ElementRef[] }>(`/capabilities/${encodeURIComponent(capabilityGuid)}/useCases`).then((r) => r.items),

  createUseCase: (capabilityGuid: string, name: string) =>
    request<ElementRef>(`/capabilities/${encodeURIComponent(capabilityGuid)}/useCases`, {
      method: "POST",
      body: JSON.stringify({ name }),
    }),

  // ── Documentation — free-text notes for ANY element kind (architecture element, actor,
  // capability, useCase, port, function, contextView), addressed purely by guid. ──

  getDocumentation: (guid: string) =>
    request<{ documentation: string }>(`/elements/${encodeURIComponent(guid)}/documentation`).then((r) => r.documentation),

  setDocumentation: (guid: string, documentation: string) =>
    request<{ status: string }>(`/elements/${encodeURIComponent(guid)}/documentation`, {
      method: "PATCH",
      body: JSON.stringify({ documentation }),
    }),

  // ── Pending connectors (Rhapsody mode only — see ModelStore#getPendingConnectors) ──

  getPendingConnectors: () => request<{ items: PendingConnector[] }>("/connectors").then((r) => r.items),

  createPendingConnector: (c: PendingConnector) =>
    request<{ status: string }>("/connectors", {
      method: "POST",
      body: JSON.stringify(c),
    }),

  // ── Connectors tab table (Rhapsody mode only — empty list in local mode, see
  // ModelStore#getConnectorTable) — existing AND pending connectors both included. ──
  getConnectorTable: () => request<{ items: ConnectorRow[] }>("/connectors/table").then((r) => r.items),

  deleteUseCase: (guid: string) =>
    request<{ status: string }>(`/useCases/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // ── Capabilities linked to an architecture element (reference, not ownership) ──────

  // Capabilities linked to a single architecture element — mirrors getPorts. Not needed for the
  // Architecture tab (already embedded inline on each node from getArchitecture), but kept for
  // symmetry/direct use the same way GET .../ports is.
  getElementCapabilities: (elementGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(elementGuid)}/capabilities`).then((r) => r.items),

  // Links an already-existing top-level Capability (see createCapability) to ownerGuid — a
  // reference, not ownership; the same Capability can be linked from multiple elements.
  linkCapability: (ownerGuid: string, capabilityGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(ownerGuid)}/capabilities`, {
      method: "POST",
      body: JSON.stringify({ capabilityGuid }),
    }),

  // Removes the link — does not delete the Capability itself (use deleteCapability for that).
  unlinkCapability: (ownerGuid: string, capabilityGuid: string) =>
    request<{ status: string }>(`/elements/${encodeURIComponent(ownerGuid)}/capabilities/${encodeURIComponent(capabilityGuid)}`, { method: "DELETE" }),

  // ── Functions (attached to a FunctionalNode, Functional architecture view only) ─────

  createFunction: (ownerGuid: string, name: string) =>
    request<ElementRef>(`/elements/${encodeURIComponent(ownerGuid)}/functions`, {
      method: "POST",
      body: JSON.stringify({ name }),
    }),

  deleteFunction: (guid: string) =>
    request<{ status: string }>(`/functions/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // Not needed for the Architecture tab (already embedded inline on each node from
  // getArchitecture), but kept for symmetry/direct use, same as getElementCapabilities.
  getElementFunctions: (ownerGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(ownerGuid)}/functions`).then((r) => r.items),

  // ── Functional→Logical allocation (Rhapsody: an "Allocate" Dependency; reference, not
  // ownership — same shape as Capability links) ──────────────────────────

  // Not needed for the Architecture tab (already embedded inline on each node from
  // getArchitecture), but kept for symmetry/direct use, same as getElementCapabilities.
  getAllocatedLogicalNodes: (functionalNodeGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(functionalNodeGuid)}/logicalNodes`).then((r) => r.items),

  linkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(functionalNodeGuid)}/logicalNodes`, {
      method: "POST",
      body: JSON.stringify({ logicalNodeGuid }),
    }),

  // Removes the link — does not delete the LogicalNode itself.
  unlinkLogicalNode: (functionalNodeGuid: string, logicalNodeGuid: string) =>
    request<{ status: string }>(`/elements/${encodeURIComponent(functionalNodeGuid)}/logicalNodes/${encodeURIComponent(logicalNodeGuid)}`, { method: "DELETE" }),

  // ── Logical→Physical allocation (same mechanism as Functional→Logical above) ────────

  getAllocatedPhysicalNodes: (logicalNodeGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(logicalNodeGuid)}/physicalNodes`).then((r) => r.items),

  linkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) =>
    request<{ items: ElementRef[] }>(`/elements/${encodeURIComponent(logicalNodeGuid)}/physicalNodes`, {
      method: "POST",
      body: JSON.stringify({ physicalNodeGuid }),
    }),

  unlinkPhysicalNode: (logicalNodeGuid: string, physicalNodeGuid: string) =>
    request<{ status: string }>(`/elements/${encodeURIComponent(logicalNodeGuid)}/physicalNodes/${encodeURIComponent(physicalNodeGuid)}`, { method: "DELETE" }),

  // ── Ports / interfaces ───────────────────────────────────────────────
  // Rhapsody GUIDs can contain spaces (e.g. "GUID 4303db76-...") — always encodeURIComponent
  // any guid used as a URL path segment, or the request silently 404s / breaks mid-URL.

  getPorts: (elementGuid: string) =>
    request<{ items: PortSpec[] }>(`/elements/${encodeURIComponent(elementGuid)}/ports`).then((r) => r.items),

  // ownerGuid may be a Block/Actor guid (a top-level interface) or an existing port's guid (a
  // decomposition of that port) — same endpoint either way, see backend/CLAUDE.md.
  createPort: (ownerGuid: string, name: string, direction: string, type: string, view?: PortView) =>
    request<PortSpec>(`/elements/${encodeURIComponent(ownerGuid)}/ports`, {
      method: "POST",
      body: JSON.stringify({ name, direction, type, view }),
    }),

  updatePort: (portGuid: string, direction?: string, type?: string, view?: PortView) =>
    request<PortSpec>(`/ports/${encodeURIComponent(portGuid)}`, {
      method: "PATCH",
      body: JSON.stringify({ direction, type, view }),
    }),

  deletePort: (guid: string) => request<{ status: string }>(`/ports/${encodeURIComponent(guid)}`, { method: "DELETE" }),

  // ── Save / load snapshot ─────────────────────────────────────────────

  exportModel: (path: string) => request<{ status: string; path: string }>("/export", {
    method: "POST",
    body: JSON.stringify({ path }),
  }),

  importModel: (path: string) => request<{ elementsCreated: number; actorsCreated: number; useCasesCreated: number; capabilitiesCreated: number }>("/import", {
    method: "POST",
    body: JSON.stringify({ path }),
  }),

  // ── UseCase Detail ───────────────────────────────────────────────────

  getUseCaseDetail: (guid: string) =>
    request<UseCaseDetail>(`/useCases/${encodeURIComponent(guid)}/detail`),

  updateUseCase: (guid: string, detail: Partial<UseCaseDetail>) =>
    request<{ status: string }>(`/useCases/${encodeURIComponent(guid)}/detail`, {
      method: "PATCH",
      body: JSON.stringify(detail),
    }),
};
