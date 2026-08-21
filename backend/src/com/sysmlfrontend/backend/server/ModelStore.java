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
     * fresh (see RhapsodyModelStore's own implementation). fromOwnerGuid/toOwnerGuid are the same
     * fields getPendingConnectors' own entries already carry (for their description text) — reused
     * here as the authoritative source for each end's owning classifier, rather than re-deriving it
     * from fromPart/toPart's own containment (which only works when those are Ports; a Context View
     * connector's fromPart/toPart are genuine Composition-part INSTANCES instead, which have no
     * equivalent "owner classifier" derivable from getOwner() alone). No-op in local mode. */
    default void createPendingConnector(String linkOwnerGuid, String fromPartGuid, String toPartGuid, String fromPortGuid, String toPortGuid,
            String fromOwnerGuid, String toOwnerGuid) { }

    /** One row per connector — EXISTING (a real IRPLink, project-wide) and PENDING (not yet
     * created, same candidates getPendingConnectors reports) both included, for the "Connectors"
     * tab's table. Each row: {"view","fromOwner","fromName","toOwner","toName"}. Local mode has no
     * connector concept at all (see getPendingConnectors' own javadoc), so this default is simply
     * always empty there. */
    default List<Map<String, Object>> getConnectorTable() { return java.util.Collections.emptyList(); }

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

    /** Free-text documentation/notes for ANY element with a guid — architecture element (any
     * kind, any nesting depth, including the model root), Actor, Capability, UseCase, Port (any
     * nesting depth), Function, or Context View. Returns "" if never set, never null. Requested
     * live: "alle elemente des frontends benötigen noch ein dokumentationsfeld" — a single
     * generic mechanism covering every element kind, mirroring renameElement's own
     * try-every-kind dispatch (each store's own javadoc on its implementation covers how it
     * finds the target). */
    String getDocumentation(String guid);

    /** Sets the free-text documentation/notes for ANY element (see {@link #getDocumentation}). */
    void setDocumentation(String guid, String documentation);

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
     * view is one of {@link #ARCHITECTURE_VIEWS} for an architecture element being dragged in the
     * Architecture tab — required there, since System Structure and Operational both render the
     * exact same tree/guids, so a position has to be scoped per view or dragging a node in one view
     * would silently move it in the other too. The system-of-interest's own box in the Context tab
     * is a THIRD case, exactly mirroring {@link #setSize}'s own javadoc: {@code view} there is
     * {@code "Context:" + contextViewGuid} (or {@code "Context:All"}), one slot per Context View —
     * originally fixed-position/non-draggable entirely, made draggable+per-view together with size,
     * requested live right after size shipped: "die Größe geht jetzt, aber die Position noch nicht."
     * Pass null for a plain actor/useCase (the Context/Capabilities tabs have no view concept for
     * those — a single, view-independent position). */
    void setPosition(String guid, String view, double x, double y);

    /** Persists a manually-resized box size (via the frontend's NodeResizer) for guid — any node
     * kind (architecture element, actor, capability, context view, or the system-of-interest).
     * Previously session-only (the frontend's own in-memory nodeSizesRef, wiped on reload) —
     * requested live: "kann ich alle boxen auch in der breite/höhe ändern? wenn ja müssen wir das
     * auch in der xml datei speichern." Originally FLAT (one size per element, no view), on the
     * assumption a chosen box size wouldn't need to differ per view the way position does — wrong in
     * practice: an architecture element's own content differs per Architecture-tab view (each view
     * filters which ports are shown) and, for the system-of-interest specifically, ALSO differs
     * again per Context View tab (a different set of surrounding Actors each time) — a single flat
     * size shared across all of those meant resizing in one view silently overwrote whichever size
     * the element had in every other view, reported live as "wenn ich flexis in einer der Context
     * Views verändere springt die Größe immer wieder zurück." Now mirrors setPosition: view is one of
     * {@link #ARCHITECTURE_VIEWS} for an architecture element being resized in the Architecture tab.
     * The system-of-interest's own box in the Context tab is a THIRD case, distinct from both of
     * those — {@code view} there is {@code "Context:" + contextViewGuid} (or {@code "Context:All"}
     * for the built-in unfiltered tab), one slot per Context View rather than a fixed enum, since
     * Context Views are user-created and open-ended, not a closed set like the 5 Architecture views.
     * Found live right after per-Architecture-view sizing shipped: "ich habe im context 3 view All
     * test und new! egal in welcher view ich die größe von flexis ändere werden dia anderen views mit
     * geändert!" — every Context View was still sharing one "Context" slot. Implementations don't
     * need to know this "Context:" convention is anything special — it's just an arbitrary view
     * string like any Architecture one, stored/read the same way (RhapsodyModelStore discovers
     * whatever view suffixes actually have tags rather than enumerating a fixed list, specifically so
     * this open-ended case works without the store needing to know how many Context Views exist).
     * Pass null for an actor/capability/context view itself (no view concept, single flat size). */
    void setSize(String guid, double width, double height, String view);

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

    /** Returns the full detail of a UseCase (goal, actors, preconditions, basicPath, alternatives, extensions, postCondition). */
    Map<String, Object> getUseCaseDetail(String guid);

    /** Updates the full detail of a UseCase. Keys not present in the map are left unchanged. */
    void updateUseCase(String guid, Map<String, Object> detail);

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

    // ── Context Views (user-defined, top-level groupings of Actors — e.g. "Operational
    // Context", "Maintenance Context") ──────────────────────────────────────────────────

    /** Every top-level Context View in the whole model — each becomes its own tab in the Context
     * tab's own tab bar (alongside a built-in "All" tab showing every Actor unfiltered), showing
     * only the Actors linked to it. Purely a user-defined filter/grouping — mirrors Capabilities'
     * shape (getCapabilities/linkCapability) exactly, minus the UseCase-owning layer: a Context
     * View owns nothing of its own, it only groups references to already-existing Actors.
     * Requested live: "im context gibt es mehrere user defined Views... wir brauchen für jeden
     * neuen kontext einen neuen tab". */
    List<Object> getContextViews();

    default Map<String, Object> createContextView(String name) {
        return createContextView(name, null);
    }

    /** See createArchitectureElement's sourceGuid javadoc — same upsert-by-identity semantics. */
    Map<String, Object> createContextView(String name, String sourceGuid);

    /** Context Views actorGuid is linked to — mirrors getCapabilitiesOf(ownerGuid). An Actor may
     * belong to any number of Context Views at once (confirmed live: "mehrere Kontexte möglich"),
     * unlike the Architecture tab's own views, which are mutually exclusive per element. */
    List<Object> getContextViewsOf(String actorGuid);

    /** Links an already-existing Context View (contextViewGuid) to actorGuid. No-op if already
     * linked. */
    void linkContextView(String actorGuid, String contextViewGuid);

    /** Removes the link between actorGuid and contextViewGuid — does not delete the Context View
     * itself (use deleteElement(contextViewGuid) for that). */
    void unlinkContextView(String actorGuid, String contextViewGuid);

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

    // ── Functional→Logical allocation (Rhapsody: an "Allocate"-stereotyped Dependency; frontend:
    // a hyperlink-style picker on the FunctionalNode's own node, same UX as a Capability link —
    // see getCapabilitiesOf/linkCapability) ─────────────────────────────────────────

    /** LogicalNodes allocated FROM functionalNodeGuid (a FunctionalNode) — rendered inside that
     * FunctionalNode's own node, mirroring getCapabilitiesOf(ownerGuid). A link is a reference
     * (Rhapsody: a Dependency owned by the FunctionalNode, stereotyped "Allocate"), not ownership —
     * the same LogicalNode may be allocated from more than one FunctionalNode. */
    List<Object> getAllocatedLogicalNodesOf(String functionalNodeGuid);

    /** Links logicalNodeGuid (an existing LogicalNode) to functionalNodeGuid. No-op if already
     * linked. */
    void linkLogicalNode(String functionalNodeGuid, String logicalNodeGuid);

    /** Removes the allocation link between functionalNodeGuid and logicalNodeGuid — does not
     * delete the LogicalNode itself. */
    void unlinkLogicalNode(String functionalNodeGuid, String logicalNodeGuid);

    // ── Logical→Physical allocation (same mechanism as Functional→Logical above — requested
    // live: "nun müssen noch die Logical Nodes mit PhysicalNodes auf gleiche weise allokiert
    // werden") ─────────────────────────────────────────────────────────

    /** PhysicalNodes allocated FROM logicalNodeGuid (a LogicalNode) — rendered inside that
     * LogicalNode's own node, mirroring getAllocatedLogicalNodesOf(functionalNodeGuid). */
    List<Object> getAllocatedPhysicalNodesOf(String logicalNodeGuid);

    /** Links physicalNodeGuid (an existing PhysicalNode) to logicalNodeGuid. No-op if already
     * linked. */
    void linkPhysicalNode(String logicalNodeGuid, String physicalNodeGuid);

    /** Removes the allocation link between logicalNodeGuid and physicalNodeGuid — does not delete
     * the PhysicalNode itself. */
    void unlinkPhysicalNode(String logicalNodeGuid, String physicalNodeGuid);
}
