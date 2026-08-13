import type { ArchLevel, ArchNode, ElementRef, PendingConnector, PortSpec, PortView } from "../types";

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

  /** Connects to Rhapsody and switches straight to editing the given (already-existing) project —
   * no data transfer, just "start online editing this Rhapsody project directly". */
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

  /** Pops a native OS file picker (the backend runs locally, same machine as the browser) — used
   * when a Load/Save path field was left empty. Returns the chosen absolute path, or null if the
   * user cancelled. */
  pickFile: (mode: "open" | "save", filter: "xml" | "rpyx", title?: string, suggestedName?: string) =>
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

  // ── Pending connectors (Rhapsody mode only — see ModelStore#getPendingConnectors) ──

  getPendingConnectors: () => request<{ items: PendingConnector[] }>("/connectors").then((r) => r.items),

  createPendingConnector: (c: PendingConnector) =>
    request<{ status: string }>("/connectors", {
      method: "POST",
      body: JSON.stringify(c),
    }),

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
};
