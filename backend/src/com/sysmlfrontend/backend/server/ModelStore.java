package com.sysmlfrontend.backend.server;

import java.util.List;
import java.util.Map;

/**
 * The CRUD surface {@link WebServer} talks to, independent of where the model actually lives.
 * Two implementations: {@link RhapsodyModelStore} (live sync into a running Rhapsody instance —
 * only usable in a JVM that has rhapsody.jar on its classpath) and {@link LocalXmlModelStore}
 * (pure Java, in-memory, no Rhapsody dependency at all — used whenever Rhapsody isn't configured
 * or isn't reachable). This interface itself must stay free of any com.telelogic.rhapsody.core
 * import, since {@link BootstrapApp} constructs a LocalXmlModelStore directly in its own JVM,
 * which never loads rhapsody.jar.
 *
 * All Map/List return values use the same JSON-shaped structures WebServer serializes directly
 * (see RhapsodyModelStore's node-builder javadocs for the exact shape of each), so both
 * implementations must produce identical shapes for {@link ModelXml} to stay store-agnostic.
 */
public interface ModelStore {

    /** The Architecture tab's five views, in the fixed order used wherever a store needs to
     * enumerate all of them (e.g. RhapsodyModelStore reading back every per-view position tag).
     * "Structure" and "Operational" both display the exact same System-of-Systems tree — see
     * setPosition below — while "Functional"/"Logical"/"Physical" each show a disjoint tree of
     * their own aspect-node kind, so in practice only ever have one view's worth of position, but
     * are still keyed by view here for a single uniform format. Must match the frontend's ArchView
     * union (App.tsx) exactly. */
    String[] ARCHITECTURE_VIEWS = {"Structure", "Operational", "Functional", "Logical", "Physical"};

    /** A short label for /api/status — "rhapsody" or "local". */
    String mode();

    /** False means the most recent mutation applied live but failed to persist to disk — see
     * RhapsodyModelStore#save's javadoc for why this matters there specifically (Rhapsody
     * automation does not auto-save; a failed save silently loses the change on the next
     * close/reopen or crash). Always true for LocalXmlModelStore — an auto-persist failure there
     * already prints its own console warning the same way, but this exists uniformly so WebServer/
     * the frontend don't need a store-specific check to poll it via /api/status. */
    default boolean isSaveHealthy() { return true; }

    /** Every connector (Rhapsody IRPLink) that should exist but doesn't yet — see
     * RhapsodyModelStore#getPendingConnectors' own javadoc. Local mode has no connector/diagram
     * concept at all (see backend/CLAUDE.md's "Interfaces are kept in sync..." section: scoped to
     * RhapsodyModelStore only), so this default is simply always empty there. */
    default List<Object> getPendingConnectors() { return java.util.Collections.emptyList(); }

    /** Creates one connector previously reported by getPendingConnectors, re-resolving its GUIDs
     * fresh (see RhapsodyModelStore's own implementation). No-op in local mode. */
    default void createPendingConnector(String linkOwnerGuid, String fromPartGuid, String toPartGuid, String fromPortGuid, String toPortGuid) { }

    /** The GUID to use as the default parent for new elements (the project/model root). */
    String rootGuid();

    /** Opens/loads a model from the given path. Rhapsody: an .rpy project file. Local: not
     * applicable — implementations should throw a clear error pointing at Save/Load XML instead. */
    String loadModel(String path);

    /** Selects/highlights the element in whatever native UI the store has (Rhapsody only; local
     * implementations should throw or no-op with a clear message). */
    String selectElement(String guid);

    /** "New Model" — clears architecture/context/capabilities and (re)titles the root. Local-only;
     * RhapsodyModelStore throws (there's no "blank new Rhapsody project" operation — see
     * WebServer's /api/exportToRhapsody for how a local model gets promoted into Rhapsody). */
    void reset(String name);

    /** The .rpyx path this model was last exported to, if any — local stores track this so a
     * reload remembers where "Export to Rhapsody" should go by default. Null for RhapsodyModelStore
     * (meaningless there — it *is* the Rhapsody project) and for a local model never exported. */
    default String linkedRhapsodyPath() { return null; }

    // ── Architecture ─────────────────────────────────────────────────────

    Map<String, Object> getArchitecture();

    /** Normal interactive creation (drag&drop, context menu, ...) — always creates fresh. */
    default Map<String, Object> createArchitectureElement(String parentGuid, String name, String kind) {
        return createArchitectureElement(parentGuid, name, kind, null);
    }

    /** sourceGuid is non-null only when called from {@link ModelXml#importInto} — it's the
     * originating element's own identity from a previous export. If an element with that identity
     * already exists in this store, this UPDATES it in place (matching name) instead of creating a
     * duplicate; otherwise it creates fresh, adopting sourceGuid as the new element's identity so a
     * future re-import of the same XML can find it again. Without this, re-importing (or loading
     * the same XML twice) silently duplicated the whole tree every time. */
    Map<String, Object> createArchitectureElement(String parentGuid, String name, String kind, String sourceGuid);

    void renameElement(String guid, String name);

    void deleteElement(String guid);

    /** Moves an existing architecture element (guid) to become a child of newParentGuid (or the
     * model root's own guid) — a true move, keeping the element's own guid/kind/children/ports
     * intact, not a copy or a new element. newParentGuid must be compatible with guid's own kind
     * (see {@link HierarchyLevels#requireCompatibleMove}): the root only accepts root-level kinds,
     * and any other parent must belong to the same architecture tree/family as guid — e.g. a
     * FunctionalNode can only be moved under the root or another FunctionalNode. Throws if the move
     * would create a cycle (under itself or one of its own descendants) or violate either rule. */
    void moveElement(String guid, String newParentGuid);

    /** Persists a canvas position for guid — an architecture element, actor, or useCase (never a
     * port, which isn't independently positioned on the canvas, only shown inside its owner's
     * box). The frontend calls this once a drag ends, so manual layout survives reloads instead of
     * always being recomputed by the tidy-tree auto-layout.
     *
     * view is one of {@link #ARCHITECTURE_VIEWS} for an architecture element — required there,
     * since System Structure and Operational both render the exact same tree/guids, so a position
     * has to be scoped per view or dragging a node in one view would silently move it in the other
     * too. Pass null for an actor/useCase (the Context/Capabilities tabs have no view concept —
     * those keep a single, view-independent position). */
    void setPosition(String guid, String view, double x, double y);

    // ── Context (external systems) ──────────────────────────────────────

    List<Object> getContext();

    default Map<String, Object> createActor(String parentGuid, String name) {
        return createActor(parentGuid, name, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics. */
    Map<String, Object> createActor(String parentGuid, String name, String sourceGuid);

    // ── Capabilities (top-level grouping; each Capability owns a list of UseCases) ──────

    /** Every top-level Capability in the whole model — backs the Capabilities tab, each shown as
     * its own box (like a FunctionalNode) with its own attached UseCase list, see getUseCasesOf.
     * A Capability is a grouping concept, distinct from the UseCases it owns — mirrors how a
     * FunctionalNode owns a list of Functions. */
    List<Object> getCapabilities();

    default Map<String, Object> createCapability(String name) {
        return createCapability(name, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics. */
    Map<String, Object> createCapability(String name, String sourceGuid);

    /** UseCases owned by capabilityGuid — rendered inside that Capability's own node, mirroring
     * getFunctionsOf(functionalNodeGuid). */
    List<Object> getUseCasesOf(String capabilityGuid);

    default Map<String, Object> createUseCase(String capabilityGuid, String name) {
        return createUseCase(capabilityGuid, name, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics.
     * capabilityGuid is the owning Capability — see getUseCasesOf. */
    Map<String, Object> createUseCase(String capabilityGuid, String name, String sourceGuid);

    // ── Capabilities linked to an architecture element (reference, not ownership) ───────

    /** Capabilities linked to ownerGuid (an architecture element) — rendered inside that
     * element's node like PortsSection, mirroring getPorts(classifierGuid). A link *references*
     * an existing top-level Capability (see getCapabilities/createCapability); it does not create
     * or own one, and the same Capability can be linked from multiple elements. */
    List<Object> getCapabilitiesOf(String ownerGuid);

    /** Links an already-existing Capability (capabilityGuid) to ownerGuid (an architecture
     * element). No-op if already linked. */
    void linkCapability(String ownerGuid, String capabilityGuid);

    /** Removes the link between ownerGuid and capabilityGuid — does not delete the Capability
     * itself (it may still be linked elsewhere, or exist unlinked in the Capabilities tab; use
     * deleteElement(capabilityGuid) to actually delete one). */
    void unlinkCapability(String ownerGuid, String capabilityGuid);

    // ── Interfaces (ports — ProxyPorts, one of 4 views, nestable for decomposition) ──────

    /** Ports directly owned by classifierGuid (a Block/Actor). Does not include nested ports of
     * those ports — each returned port's own "children" list carries its decomposition. */
    List<Object> getPorts(String classifierGuid);

    /** Creates a ProxyPort under ownerGuid, which may be a Block/Actor GUID (a top-level
     * interface) OR an existing port's GUID (a decomposition of that port). view is one of
     * "Operational" | "Functional" | "Logical" | "Physical", or null. */
    default Map<String, Object> createPort(String ownerGuid, String name, String direction, String type, String view) {
        return createPort(ownerGuid, name, direction, type, view, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics. */
    Map<String, Object> createPort(String ownerGuid, String name, String direction, String type, String view, String sourceGuid);

    Map<String, Object> updatePort(String portGuid, String direction, String type, String view);

    // ── Functions (attached to a FunctionalNode, shown only in the Functional view) ─────

    /** Functions owned by ownerGuid (a FunctionalNode) — rendered inside that element's node
     * alongside its ports, mirroring getCapabilitiesOf. Unlike Capabilities (system-level use
     * cases), Functions are specific to the Functional architecture view's own element kind. */
    List<Object> getFunctionsOf(String ownerGuid);

    default Map<String, Object> createFunction(String parentGuid, String name) {
        return createFunction(parentGuid, name, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics.
     * parentGuid is the owning FunctionalNode — see getFunctionsOf. */
    Map<String, Object> createFunction(String parentGuid, String name, String sourceGuid);
}
