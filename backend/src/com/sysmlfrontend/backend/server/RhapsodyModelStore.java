package com.sysmlfrontend.backend.server;

import com.sysmlfrontend.backend.AppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPInstance;
import com.telelogic.rhapsody.core.IRPLink;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPObjectModelDiagram;
import com.telelogic.rhapsody.core.IRPOperation;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPPort;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPRelation;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPStructureDiagram;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.IRPTag;
import com.telelogic.rhapsody.core.IRPUseCaseDiagram;

import com.ibm.rhapsody.samples.plugin.model.ECADContext;
import com.ibm.rhapsody.samples.plugin.services.DiagramService;
import com.ibm.rhapsody.samples.plugin.services.ModelElementService;
import com.ibm.rhapsody.samples.plugin.services.StereotypeService;

/**
 * {@link ModelStore} backed by a live Rhapsody instance via com.telelogic.rhapsody.core — the
 * "on the fly" sync mode, only used when a Rhapsody installation was actually configured and
 * reached (see BootstrapApp/ModelServer). Only usable in a JVM that has rhapsody.jar on its
 * classpath and the Rhapsody bin directory on java.library.path.
 *
 * Maps the frontend's three domain aspects (Architecture / Context / Capabilities) onto
 * Rhapsody's SysML metamodel:
 *
 *   Architecture (hierarchy) → IRPClass ("Block"), nested via addClass — no
 *     IRPPackage involved: the app doesn't author packages, only the four
 *     hierarchy levels (System of Systems / System / Subsystem / Equipment),
 *     each a Block tagged with a same-named Stereotype so the level survives
 *     a round trip through Rhapsody. levelMetaType (config.ini) is the
 *     metaclass the stereotype applies to (default "Class"). Legacy/foreign
 *     IRPPackages already present in a model are still read (kind "Package")
 *     but never created by this app.
 *   Context (external systems) → IRPActor, owned by a package.
 *   Capabilities (grouping)    → IRPPackage nested under Operational/Capabilities (see
 *     capabilitiesPackage) — each Capability owns a list of UseCases (IRPUseCase, native package
 *     containment) and can be linked (not owned) by architecture elements via a comma-separated
 *     GUID list Tag (LINKED_OWNERS_TAG) on the Capability package itself.
 *   Interfaces (ProxyPorts)    → see the "Interfaces" section below; this is the pattern verified
 *     against D:\KI\plugin\ECAD's IRPPort/proxyPort/interfaceBlock usage, not guessed.
 *
 * Rhapsody's IRPCollection is 1-based, not 0-based (same gotcha as SPREAD).
 *
 * ── Interfaces (ProxyPorts) ──────────────────────────────────────────────────────────────
 *
 * Verified against a live Rhapsody 10.0.3 instance and cross-checked against D:\KI\plugin\ECAD's
 * ICDExporter/ModelElementService/StereotypeService (a working, independently-developed SysML
 * reader/writer for the same kind of model). Earlier attempts used metaclass "FlowPort" (not a
 * real metaclass at all — see metaclasses.txt in the Doc directory) and IRPSysMLPort.setType();
 * both were wrong. The actual pattern:
 *
 *   - A port is created via classifier.addNewAggr(portMetaType, name) with portMetaType = "Port"
 *     (a real, listed metaclass), yielding a plain IRPPort — not IRPSysMLPort.
 *   - It's tagged as a ProxyPort via addStereotype("proxyPort", portMetaType) (lowercase — matches
 *     ECAD's convention; addStereotype creates the stereotype ad-hoc if a project doesn't already
 *     define it, so this doesn't require a specific profile to be loaded).
 *   - It's typed via IRPPort.setContract(interfaceBlock), where interfaceBlock is an IRPClass
 *     stereotyped "interfaceBlock" (find-or-created by name under the hidden default package —
 *     see containerFor()/findOrCreateInterfaceBlock()).
 *   - Direction (In/Out/InOut) has no native IRPPort property, so it's stored as a Tag named
 *     "Direction" (element.getTag/addNewAggr("Tag",...)/IRPTag.setValue — same Tag pattern ECAD
 *     uses for its own custom attributes).
 *   - View (Operational/Functional/Logical/Physical — this app's own classification, not part of
 *     the verified ECAD pattern) is a same-named Stereotype on the port, same mechanism as the
 *     architecture hierarchy levels.
 *   - Nested ports (interface decomposition) are NOT children of the port element itself — they
 *     are ports owned by the port's own interfaceBlock (contract.getPorts()), exactly mirroring
 *     ECAD's ICDExporter recursion (buildPortSection → contract → getProxyPorts(contract) → ...).
 *     Creating a "nested" port passes an existing port's GUID as ownerGuid; resolvePortContainer()
 *     redirects that to the port's contract, auto-creating one if the port doesn't have one yet.
 */
public class RhapsodyModelStore implements ModelStore {

    private static final String PROXY_PORT_STEREOTYPE = "proxyPort";
    private static final String INTERFACE_BLOCK_STEREOTYPE = "interfaceBlock";
    private static final String DIRECTION_TAG = "Direction";
    // A nested port (e.g. "Boardnet") that lives inside a TOP-LEVEL port's own shared interfaceBlock
    // contract (e.g. "ibPower") is, natively, ONE Rhapsody object — Rhapsody has no way to give
    // ComSuite/PowerUnit/CN/SN their own separate "Boardnet" Port objects, confirmed live: neither a
    // Port owning a nested Port directly ("Can't add aggregate of type Port... Cannot aggregate
    // object of type Port to object of type Proxy Port") nor an implicit per-instance realization
    // (IRPInstance#getAllNestedElements() on a live Power port returns only its own Direction Tag,
    // nothing else) exist as native mechanisms. Confirmed correct/expected as-is ("da der Boardnet im
    // contract liegt. da ist auch normal.") — Boardnet stays the one shared object, addressed via its
    // own contract, same as always.
    //
    // What CAN be independent per occurrence, since the TOP-LEVEL port ITSELF is already a genuine,
    // separate native object per Block: a direction OVERRIDE, stored as a Tag on the TOP-LEVEL port
    // (not on the shared nested child), named per nested child by its own name. Addressed from the
    // frontend via a COMPOSITE guid "<topLevelPortGuid>|<nativeChildGuid>" (see portNode/updatePort) —
    // "eine Kombination aus Power UUID und Boardnet UUID... dann ist es auf jedenfall eindeutig"
    // (requested live) — unique per occurrence even though the underlying child object is shared.
    // Falls back to the shared child's own Direction tag when no override has been set yet, so an
    // occurrence that never touches this nested port's direction stays in sync with whatever the
    // interfaceBlock's own canonical value is.
    private static final String NESTED_DIRECTION_OVERRIDE_TAG_PREFIX = "NestedDirection_";
    // The port's own resolved contract (interfaceBlock) is the canonical "Interface" identity for
    // reuse — see findOrCreateInterfaceBlock's own "shared reusable type" doc — so these two tags,
    // stamped on the INTERFACEBLOCK itself (not the port), are the single source of truth for an
    // interface's direction/view, kept in sync with every port that uses it (see
    // applyPortSpec/propagateToSiblingPorts). Requested live: "wenn ich ein interface nochmals
    // nutzen will[,] soll[en] alle einstellungen des interfaces übernommen werden... wir verlinken
    // ... die interfaces so dass wir immer nur ein unicat haben" — a Port can't literally be the
    // same Rhapsody object shared across two owners (unlike a Capability), so "Unikat" is
    // implemented as keep-in-sync-via-shared-contract instead of true object sharing.
    private static final String INTERFACE_DIRECTION_TAG = "SysMLFrontendInterfaceDirection";
    private static final String INTERFACE_VIEW_TAG = "SysMLFrontendInterfaceView";
    // Marks an interfaceBlock as reusable across ALL views (the "externe Interfaces von System_F"
    // exception — a port on a tree-root element itself, see isRootLevelClass/isExternalPort), as an
    // actual visible Rhapsody stereotype rather than a purely in-memory heuristic recomputed from
    // ownership every time — requested live: "am besten wir setzen für die externen Schnittstellen
    // einen Stereotyp in Rhapsody". Applied to the interfaceBlock (not the port) alongside
    // INTERFACE_BLOCK_STEREOTYPE whenever findOrCreateInterfaceBlock resolves one with external=true,
    // so it's visible directly in Rhapsody's Model Browser/stereotype label and also lets
    // findInterfaceBlockAcrossAllViews recognize an already-external interfaceBlock even if the
    // owning port's own topology were ever to change later.
    private static final String EXTERNAL_INTERFACE_STEREOTYPE = "externalInterface";
    // Names of the two auto-created "collector" ports every NON-root architecture element's own
    // top-level ports get routed through, once that port's interface identity is shared with
    // something outside this element's own class (see classifyDelegationGroup/portGroupContainer).
    // Pattern reverse-engineered from a live, hand-built reference the user constructed directly in
    // Rhapsody (System_F/PerformMission/Planning's ibdSystem_F — see CLAUDE.md's Connector section):
    // an interface delegating up to a boundary port on this element's own PARENT (e.g. Voice,
    // delegating to System_F's own HEU) is nested under "external"; an interface with no such
    // ancestor counterpart, shared only between SIBLING parts of the same immediate parent (e.g.
    // intern1, between PerformMission and Planning), is nested under "internal". A ROOT tree
    // element's own boundary ports (System_F.HEU/HEU1 themselves) are never wrapped this way — they
    // ARE the thing everything else delegates to.
    private static final String PORT_GROUP_EXTERNAL = "external";
    private static final String PORT_GROUP_INTERNAL = "internal";
    // Rhapsody always mints its own native GUID on creation — it can't adopt an externally-chosen
    // one the way LocalXmlModelStore can. So cross-import identity (see ModelStore's sourceGuid
    // javadoc) is tracked via this Tag instead: stamped on every element/actor/useCase/port created
    // via an XML import, and searched for before creating anything new, so re-importing the same
    // XML (or re-running "Export to Rhapsody") updates in place instead of duplicating.
    private static final String SOURCE_GUID_TAG = "SysMLFrontendSourceGuid";
    // Canvas position (frontend-only concept, no native Rhapsody equivalent used here) — stored the
    // same way as SOURCE_GUID_TAG, stamped after a drag and read back into blockNode/elementRef.
    // Flat POS_X_TAG/POS_Y_TAG is for actors/useCases (no view concept — see ModelStore#setPosition);
    // architecture elements instead get one X/Y tag pair PER view (POS_X_TAG_PREFIX/POS_Y_TAG_PREFIX
    // + the view name, e.g. "SysMLFrontendX_Operational"), since System Structure and Operational
    // both render the exact same tree/guids and must not share a single position.
    private static final String POS_X_TAG = "SysMLFrontendX";
    private static final String POS_Y_TAG = "SysMLFrontendY";
    private static final String POS_X_TAG_PREFIX = "SysMLFrontendX_";
    private static final String POS_Y_TAG_PREFIX = "SysMLFrontendY_";
    // Node width/height set via the frontend's NodeResizer — a view preference like position.
    // Requested live: "kann ich alle boxen auch in der breite/höhe ändern? wenn ja müssen wir das
    // auch in der xml datei speichern" — previously session-only (frontend's own nodeSizesRef,
    // never sent to the backend at all), which is also why a fresh SystemOfInterestNode always
    // rendered too narrow — no size was ever available to restore.
    // Flat WIDTH_TAG/HEIGHT_TAG is for actors/capabilities/context views (no view concept — see
    // ModelStore#setSize); architecture elements instead get one width/height tag pair PER VIEW
    // (WIDTH_TAG_PREFIX/HEIGHT_TAG_PREFIX + the view name, e.g. "SysMLFrontendWidth_Operational" or
    // "SysMLFrontendWidth_Context:<guid>") — originally flat like the tags below, but that let a
    // resize in one Architecture view silently overwrite the size used everywhere else, reported
    // live as "wenn ich flexis in einer der Context Views verändere springt die Größe immer wieder
    // zurück." "view" isn't a fixed enum here (see readSizes below, which discovers whatever view
    // suffixes actually have tags rather than enumerating a closed list) — the system-of-interest's
    // own Context-tab box in particular gets one slot per Context View, an open-ended, user-created
    // set, not a single shared "Context" slot (that in turn caused a SECOND round of this exact bug,
    // reported live as "egal in welcher view ich die größe von flexis ändere werden dia anderen
    // views mit geändert! sind den die einzelnen context views auch unabhängig views?").
    private static final String WIDTH_TAG = "SysMLFrontendWidth";
    private static final String HEIGHT_TAG = "SysMLFrontendHeight";
    private static final String WIDTH_TAG_PREFIX = "SysMLFrontendWidth_";
    private static final String HEIGHT_TAG_PREFIX = "SysMLFrontendHeight_";
    // Which architecture elements a Capability (an IRPPackage, see the "Capabilities" section) is
    // linked to — SysML gives no native relationship for this, so it's a comma-separated list of
    // owner GUIDs stamped as a single Tag on the Capability package itself, the same Tag-based
    // workaround as SOURCE_GUID_TAG/POS_*_TAG use for other frontend-only concepts.
    private static final String LINKED_OWNERS_TAG = "SysMLFrontendLinkedOwners";
    // UseCase detail fields — no native Rhapsody equivalent, stamped as Tags on the UseCase itself.
    private static final String UC_GOAL_TAG = "SysMLFrontendUCGoal";
    private static final String UC_ACTORS_TAG = "SysMLFrontendUCActors";
    private static final String UC_PRECONDITIONS_TAG = "SysMLFrontendUCPreconditions";
    private static final String UC_BASICPATH_TAG = "SysMLFrontendUCBasicPath";
    private static final String UC_ALTERNATIVES_TAG = "SysMLFrontendUCAlternatives";
    private static final String UC_EXTENSIONS_TAG = "SysMLFrontendUCExtensions";
    private static final String UC_POSTCONDITION_TAG = "SysMLFrontendUCPostCondition";
    // A Function (attached to a FunctionalNode, see getFunctionsOf) has no natural SysML element
    // either — modeled the same way interfaceBlocks are: a plain IRPClass under the hidden default
    // package, distinguished by this stereotype so collectArchitectureChildren can filter it out of
    // the visible tree (it would otherwise double up, same bug class as interfaceBlocks — see
    // backend/CLAUDE.md bug notes).
    private static final String FUNCTION_STEREOTYPE = "function";
    // A Context View is a real IRPClass ("Block") living directly under kontextPackage() — a FLAT
    // structure, no separate "ContextViews" sub-package (requested live: "das Package ContextViews
    // brauchen wir nicht eine flache Struktur ist ausreichend!") — so it can't be told apart from a
    // genuine architecture element by package location alone the way interfaceBlocks/Functions can.
    // This dedicated stereotype (applied alongside "Block" at creation — see createContextView) is
    // what lets collectArchitectureChildren filter it out of the visible Architecture tree, same
    // idiom as INTERFACE_BLOCK_STEREOTYPE/FUNCTION_STEREOTYPE above.
    private static final String CONTEXT_VIEW_STEREOTYPE = "contextView";
    // A LinkedHashSet, not Set.of(...) — found live: Set.of()'s iteration order is randomized PER
    // JVM RUN (a deliberate JDK behavior, specifically to catch code that accidentally depends on
    // it), which findInterfaceBlockAcrossAllViews iterates over to search every view's own package
    // for an existing external interfaceBlock. The search is still CORRECT regardless of order (it
    // just returns the first match, and there's normally only one legitimate match to find), but an
    // orphaned same-named interfaceBlock left over in the WRONG package (e.g. from a deleted port —
    // see createPort's own "delete doesn't clean up its interfaceBlock" note) could get found first
    // purely by the luck of that run's random order, intermittently returning the wrong contract.
    // Fixing the iteration order to something fixed and predictable (Operational first, matching
    // where a shared external interface like "Truck" is normally first established) doesn't prevent
    // an orphan from EXISTING, but at least makes which one gets found deterministic and debuggable
    // rather than varying run to run.
    private static final Set<String> PORT_VIEWS = new LinkedHashSet<>(List.of("Operational", "Functional", "Logical", "Physical"));

    private final IRPApplication application;
    private final String portMetaType;
    private final String levelMetaType;
    private final String viewMetaType;
    // Frontend canvas coordinates and Rhapsody's own diagram coordinate system don't necessarily
    // agree on scale — configurable via config.ini's [Rhapsody] diagramPositionScale (see
    // diagramPositionScale() below), read FRESH on every use rather than captured once at
    // construction: WebServer already runs a background file watcher that reloads config.ini into
    // this same AppConfig instance on any edit, so hand-editing this one value and saving takes
    // effect on the very next drag — no backend restart/reconnect needed to iterate on it. Found
    // live to matter in practice: the "right" factor can only really be judged by looking at the
    // resulting Rhapsody diagram, so the user needs to try several values in quick succession.
    private final AppConfig config;
    // Vendored from D:\KI\plugin\ECAD (backend/src/com/ibm/rhapsody/samples/plugin/...) — a
    // separate, independently-developed Rhapsody plugin that reads/writes the exact same ProxyPort/
    // interfaceBlock object model this app targets, proven to work against a project with the real
    // SysML profile loaded (which this app's own ad-hoc addStereotype(name, metaType) does not, see
    // CLAUDE.md bug notes). Used here instead of re-deriving the same logic ourselves, to avoid
    // subtly diverging from their verified working sequence.
    private final StereotypeService stereotypeService;
    private final ModelElementService modelElementService;
    private final DiagramService diagramService;

    public RhapsodyModelStore(IRPApplication application, String portMetaType, String levelMetaType, String viewMetaType, AppConfig config) {
        this.application = application;
        this.portMetaType = (portMetaType == null || portMetaType.isEmpty()) ? "Port" : portMetaType;
        this.levelMetaType = (levelMetaType == null || levelMetaType.isEmpty()) ? "Class" : levelMetaType;
        this.viewMetaType = (viewMetaType == null || viewMetaType.isEmpty()) ? "Port" : viewMetaType;
        this.config = config;
        this.stereotypeService = new StereotypeService(application);
        this.stereotypeService.loadStandardStereotypes();
        this.modelElementService = new ModelElementService(application);
        this.diagramService = new DiagramService(application, stereotypeService);
    }

    @Override
    public String mode() {
        return "rhapsody";
    }

    /** False means the most recent mutation applied to the live Rhapsody session but its save()
     * call failed — see save()'s own javadoc. Polled by WebServer's /api/status so the frontend can
     * show a persistent warning instead of this only ever showing up in a backend console log. */
    @Override
    public boolean isSaveHealthy() {
        return saveHealthy;
    }

    @Override
    public synchronized String rootGuid() {
        return activeProject().getGUID();
    }

    /** There's no "blank new Rhapsody project" operation — a local model gets promoted into
     * Rhapsody via WebServer's /api/exportToRhapsody instead (see ModelStore.reset's javadoc). */
    @Override
    public synchronized void reset(String name) {
        throw new UnsupportedOperationException(
                "Not available in Rhapsody mode — create a new model locally instead and use 'Export to Rhapsody'.");
    }

    // Tracks whether the LAST save() call actually reached disk — see save()'s own javadoc and
    // isSaveHealthy(). Found live: several mutations (a reparenting session) applied fine to the
    // live in-memory model but their save() calls were silently failing (only a console warning,
    // easy to miss) right before Rhapsody crashed on its own shortly after — reopening the project
    // afterward reverted to the last state that WAS actually written, silently losing everything
    // saved only in memory. This flag is what makes that visible in real time instead of only
    // after the fact in a backend console log nobody was watching live.
    private volatile boolean saveHealthy = true;

    /** Rhapsody automation does NOT auto-save changes to disk — found live: editing via this app,
     * then reloading the same project, silently discarded every unsaved change. Every mutation
     * below calls this so "on the fly" sync actually means durable, not just in-memory. Wrapped
     * defensively: a save hiccup shouldn't turn an otherwise-successful edit into an error
     * response, since the change did apply to the live model either way — but see saveHealthy/
     * isSaveHealthy for how this is still surfaced, just not as a failed API call. */
    private void save() {
        try {
            activeProject().save();
            if (!saveHealthy) {
                System.out.println("[backend] Rhapsody project save recovered.");
            }
            saveHealthy = true;
        } catch (Exception e) {
            saveHealthy = false;
            System.err.println("[backend] !!! SAVE FAILED — this change applied to the live Rhapsody "
                    + "session but was NOT written to disk; it will be LOST if the project is closed/"
                    + "reopened or Rhapsody restarts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Opens a Rhapsody project and returns its name. */
    @Override
    public synchronized String loadModel(String path) {
        requireNonEmpty(path, "path");
        IRPProject project = application.openProject(path);
        if (project == null) {
            throw new IllegalStateException("openProject returned null — model could not be loaded");
        }
        return project.getName();
    }

    /** Selects and highlights the element with the given GUID in the Rhapsody UI; returns its name. */
    @Override
    public synchronized String selectElement(String guid) {
        IRPModelElement element = findElement(guid);
        IRPCollection sel = application.createNewCollection();
        sel.addItem(element);
        application.selectModelElements(sel);
        application.highLightElement(element);
        return element.getName();
    }

    // ── Architecture ─────────────────────────────────────────────────────

    /** Returns the full architecture tree rooted at the active project. */
    @Override
    public synchronized Map<String, Object> getArchitecture() {
        return rootNode(activeProject());
    }

    /** Creates a new architecture element under the given parent (the model root, or a Block).
     * The level is automatic (see {@link HierarchyLevels}), not freely chosen — requestedKind is
     * only honored at the root, and only as an opt-in for "SystemOfSystem". Tagged as a
     * same-named Stereotype so the level survives a round trip through Rhapsody. sourceGuid (see
     * ModelStore's javadoc): if an element already carries a matching SOURCE_GUID_TAG, this updates
     * it in place instead of creating a duplicate. If sourceGuid doesn't match anything (including
     * the normal case of it being null), this ALSO checks for an existing Class of the same name
     * directly in the resolved container/parent before creating fresh — same reasoning as
     * findOrCreateCapabilityPackage: an interactively-created element is never Tag-stamped, so
     * without this, re-importing an XML previously exported from this same already-populated
     * project throws `"Can't add aggregate of type Class. Cannot add Class due to a clash with an
     * existing element."` (found live loading an export back into the project it came from). */
    @Override
    public synchronized Map<String, Object> createArchitectureElement(String parentGuid, String name, String requestedKind, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing instanceof IRPClass) {
                existing.setName(name);
                ensureBlockStereotype((IRPClass) existing);
                save();
                return blockNode((IRPClass) existing);
            }
        }
        IRPModelElement parent = findElement(parentGuid);

        String kind;
        IRPClass created;
        if (parent instanceof IRPPackage) {
            kind = HierarchyLevels.childLevel(true, null, requestedKind);
            IRPPackage container = containerForKind((IRPPackage) parent, kind);
            IRPClass existingByName = findClassByNameDirect(container, name);
            created = existingByName != null ? existingByName : container.addClass(name);
        } else if (parent instanceof IRPClass) {
            kind = HierarchyLevels.childLevel(false, levelOf(parent), requestedKind);
            IRPClass existingByName = findNestedClassByNameDirect((IRPClass) parent, name);
            if (existingByName != null) {
                created = existingByName;
            } else {
                created = ((IRPClass) parent).addClass(name);
                // Rhapsody's own nested-classifier containment (addClass above) is a namespace
                // concept, not a SysML "part"/aggregation relationship — this app's System Structure
                // hierarchy additionally needs the latter: a proper Composition association plus a
                // part in the parent's own Internal Block Diagram (see addAggregationPart). Only for
                // a genuinely new child — re-running this on an already-related existing pair risks
                // a duplicate Composition association, and it would already be in place from the
                // original creation anyway.
                addAggregationPart((IRPClass) parent, created);
            }
        } else {
            throw new IllegalArgumentException("An architecture element can only be created under the model root or a Block");
        }
        applyStereotypeSafely(created, kind, levelMetaType);
        ensureBlockStereotype(created);
        // Every element gets its own IBD and a spot on its tree's BDD immediately at creation, not
        // only once it happens to get its own first child — found live: System_F/System_L/
        // System_P (root elements created with no children yet) had neither, since
        // addAggregationPart (the only other thing that creates/populates these) only ever runs for
        // a NEW CHILD, touching the PARENT's IBD/the tree's BDD, never the newly-created element's
        // OWN (future) IBD. Idempotent — safe to call for a re-matched existing element too.
        ensureOwnDiagrams(created);
        // Every NON-root element gets its own "external"/"internal" collector ports up front, not
        // only lazily once a port happens to need one (classifyDelegationGroup/portGroupContainer,
        // used by createPort) — requested live: "wenn a new functional node will be created (drag
        // child) we can add both interfaces automatically!". Both are idempotent find-or-creates, so
        // this is safe for a re-matched existing element too. A root-level element (parent
        // instanceof IRPPackage) never gets these — see PORT_GROUP_EXTERNAL/_INTERNAL's own javadoc.
        if (parent instanceof IRPClass) {
            portGroupContainer(created, PORT_GROUP_EXTERNAL);
            portGroupContainer(created, PORT_GROUP_INTERNAL);
        }
        stampSourceGuid(created, sourceGuid);
        save();
        return blockNode(created);
    }

    /** Ensures cls has its own Internal Block Diagram (ready for its own future children — see
     * addAggregationPart, which places a NEW CHILD onto its PARENT's IBD but never creates the
     * child's own one) and appears as a node on its tree's Block Definition Diagram (owned by
     * topLevelAncestor — see createOrGetBDD's own javadoc for why the BDD is one-per-tree, not
     * one-per-element, so this deliberately does NOT call createOrGetBDD(cls) directly unless cls
     * already IS the tree root). Both DiagramService#createIBD and createOrGetBDD/addBlockToBDD are
     * themselves idempotent, so this is safe to call on every creation, including a re-matched
     * existing element (e.g. via sourceGuid) that already has both. */
    private void ensureOwnDiagrams(IRPClass cls) {
        IRPClass root = topLevelAncestor(cls);
        IRPObjectModelDiagram bdd = createOrGetBDD(root);
        addBlockToBDD(bdd, root);
        addBlockToBDD(bdd, cls);
        diagramService.createIBD(cls, new ECADContext());
    }

    /** Moves an existing architecture element to a new parent via IRPModelElement#setOwner — a
     * real, documented Rhapsody API for changing an element's containment, found by reading
     * IRPModelElement.java directly out of the jar (the earlier "no reparent/move operation" gap
     * noted elsewhere in this class's docs only considered the riskier clone()+delete route, never
     * checked for a dedicated API). setOwner only changes NAMESPACE containment — same distinction
     * addAggregationPart's own javadoc already draws for addClass — so moving under another
     * architecture element ALSO calls addAggregationPart for the new parent/child pair, same as a
     * freshly-created nested element gets, so the Composition association + IBD part + BDD nodes
     * actually reflect the new structure (found live: moving System-of-Systems-tree children under
     * a Functional/Logical/Physical aspect node left those without any Composition/IBD/BDD at all,
     * since they'd originally been created at the root, not nested — root-level creation never calls
     * addAggregationPart either). addAggregationPart's own idempotency guard (hasCompositionTo)
     * means this is safe even if the same element is moved to the same parent more than once. The
     * element's PREVIOUS parent's own Composition/IBD placement is deliberately left untouched (a
     * known, accepted gap — see backend/CLAUDE.md's "Known gaps" note) — cleaning up a stale
     * relation on the old side isn't attempted here. A move to the root has no such relationship to
     * establish (a root-level element has no parent classifier to compose under — it IS the top of
     * its own tree). Only architecture elements (IRPClass) are moveable here — Actors/Capabilities/
     * UseCases/Functions have no exposed move operation. */
    @Override
    public synchronized void moveElement(String guid, String newParentGuid) {
        IRPModelElement el = findElement(guid);
        if (!(el instanceof IRPClass)) {
            throw new IllegalArgumentException("Only architecture elements can be moved");
        }
        IRPClass cls = (IRPClass) el;
        IRPModelElement newParentEl = findElement(newParentGuid);
        if (isSameOrDescendant(cls, newParentGuid)) {
            throw new IllegalArgumentException("Cannot move an element under itself or one of its own descendants");
        }
        String kind = levelOf(cls);
        if (newParentEl instanceof IRPPackage) {
            HierarchyLevels.requireCompatibleMove(kind, true, null);
            cls.setOwner(containerForKind((IRPPackage) newParentEl, kind));
        } else if (newParentEl instanceof IRPClass) {
            HierarchyLevels.requireCompatibleMove(kind, false, levelOf((IRPClass) newParentEl));
            cls.setOwner(newParentEl);
            addAggregationPart((IRPClass) newParentEl, cls);
        } else {
            throw new IllegalArgumentException("Elements can only be moved under the model root or another architecture element");
        }
        save();
    }

    /** Whether candidateGuid is cls itself, or a nested classifier of cls at any depth — cycle
     * prevention for moveElement (moving an element under itself or one of its own descendants
     * would otherwise silently detach it from the tree entirely, or loop forever if ever walked). */
    private boolean isSameOrDescendant(IRPClass cls, String candidateGuid) {
        if (((IRPModelElement) cls).getGUID().equals(candidateGuid)) return true;
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass && isSameOrDescendant((IRPClass) item, candidateGuid)) return true;
        }
        return false;
    }

    @Override
    public synchronized void renameElement(String guid, String name) {
        requireNonEmpty(name, "name");
        IRPModelElement el = findElement(guid);
        // A Capability is the only user-creatable IRPPackage in this app (see capabilitiesPackage/
        // createCapability) — the model root (IRPProject, which itself extends IRPPackage) must NOT
        // go through the sanitize/DisplayName path, so it's explicitly excluded here.
        if (el instanceof IRPPackage && !(el instanceof IRPProject)) {
            el.setName(sanitizePackageName(name));
            setDisplayName(el, name);
        } else {
            el.setName(name);
        }
        save();
    }

    /** Backed directly by Rhapsody's own native Description property (IRPModelElement#getDescription/
     * setDescription) rather than a Tag like most of this file's other custom fields — every kind
     * this app creates (Class/Block, Actor, Package/Capability, UseCase, Operation/Function, Port)
     * inherits IRPModelElement, so this one native property genuinely covers "really all elements"
     * uniformly, and it's the actual field Rhapsody's own UI shows as an element's documentation. */
    @Override
    public synchronized String getDocumentation(String guid) {
        String d = findElement(guid).getDescription();
        return d == null ? "" : d;
    }

    @Override
    public synchronized void setDocumentation(String guid, String documentation) {
        findElement(guid).setDescription(documentation == null ? "" : documentation);
        save();
    }

    /** Dragging a node on the frontend canvas only ever stamped POS_X_TAG/POS_Y_TAG(_PREFIX) —
     * this app's own record of "where the frontend last put it", read back by readPositions/
     * scaledFrontendPosition. It never touched the ACTUAL BDD node / IBD part Rhapsody had already
     * drawn for that element (only addAggregationPart, at creation/move time, ever positioned
     * those) — so a drag done after that initial placement was invisible in Rhapsody's own diagrams
     * until the element was moved again, reported live as "moving an element in the frontend isn't
     * transferred to Rhapsody on the fly." Fixed: whenever the drag's own view matches the view
     * that element's kind mirrors onto diagrams (positionViewFor — a drag in some OTHER view, e.g.
     * "Structure" for a Structure-family element whose diagrams mirror "Operational", has nothing
     * to do with the diagram layout and is intentionally not propagated), also moves the existing
     * BDD node / IBD part in place via updateDiagramPositions. Both are genuinely optional finds —
     * an element that was never actually placed on either (e.g. a childless root-level element with
     * no parent to have an IBD, or one addAggregationPart was never called for) simply has nothing
     * to move; this never creates a BDD/IBD as a side effect of a drag. */
    @Override
    public synchronized void setPosition(String guid, String view, double x, double y) {
        IRPModelElement el = findElement(guid);
        if (view != null && !view.trim().isEmpty()) {
            String tagSuffix = sanitizeTagNameSuffix(view);
            stampTagValue(el, POS_X_TAG_PREFIX + tagSuffix, String.valueOf(x));
            stampTagValue(el, POS_Y_TAG_PREFIX + tagSuffix, String.valueOf(y));
            if (el instanceof IRPClass && isDiagramPositionView(levelOf((IRPClass) el), view)) {
                updateDiagramPositions((IRPClass) el, x, y);
            }
        } else {
            stampTagValue(el, POS_X_TAG, String.valueOf(x));
            stampTagValue(el, POS_Y_TAG, String.valueOf(y));
        }
        save();
    }

    @Override
    public synchronized void setSize(String guid, double width, double height, String view) {
        IRPModelElement el = findElement(guid);
        if (view != null && !view.trim().isEmpty()) {
            String tagSuffix = sanitizeTagNameSuffix(view);
            stampTagValue(el, WIDTH_TAG_PREFIX + tagSuffix, String.valueOf(width));
            stampTagValue(el, HEIGHT_TAG_PREFIX + tagSuffix, String.valueOf(height));
        } else {
            stampTagValue(el, WIDTH_TAG, String.valueOf(width));
            stampTagValue(el, HEIGHT_TAG, String.valueOf(height));
        }
        save();
    }

    /** Moves cls's own already-drawn BDD node (on the tree-wide BDD owned by topLevelAncestor) and
     * IBD part (an "its"+name instance on the PARENT's own IBD — see addAggregationPart) to (x, y),
     * scaled the same way scaledFrontendPosition scales a freshly-placed one. No-op for either half
     * that was never actually drawn (read-only finds — findExistingBDD/DiagramService#getIBD, never
     * create-if-missing, unlike createOrGetBDD/createIBD). */
    private void updateDiagramPositions(IRPClass cls, double xRaw, double yRaw) {
        double scale = diagramPositionScale();
        int x = (int) Math.round(xRaw * scale);
        int y = (int) Math.round(yRaw * scale);

        IRPObjectModelDiagram bdd = findExistingBDD(topLevelAncestor(cls));
        if (bdd != null) {
            IRPGraphNode node = findGraphNode(bdd, cls);
            if (node != null) moveGraphNode(node, x, y);
        }

        IRPModelElement owner = cls.getOwner();
        if (owner instanceof IRPClass) {
            IRPStructureDiagram ibd = diagramService.getIBD((IRPClass) owner);
            IRPInstance instance = ibd != null ? modelElementService.getInstance((IRPClass) owner, itsInstanceName(cls.getName())) : null;
            if (instance != null) {
                IRPGraphNode node = findGraphNode(ibd, (IRPModelElement) instance);
                if (node != null) moveGraphNode(node, x, y);
            }
        }
    }

    /** Read-only counterpart to createOrGetBDD — finds root's tree-wide BDD without creating one,
     * since setPosition (unlike addAggregationPart) should never spontaneously create a BDD just
     * because an element was dragged. */
    private IRPObjectModelDiagram findExistingBDD(IRPClass root) {
        String name = "bdd" + root.getName();
        IRPCollection refs = root.getReferences();
        for (int i = 1; i <= refs.getCount(); i++) {
            Object obj = refs.getItem(i);
            if (obj instanceof IRPObjectModelDiagram && name.equals(((IRPModelElement) obj).getName())) {
                return (IRPObjectModelDiagram) obj;
            }
        }
        return null;
    }

    /** The graphical node on diagram representing modelObject, by GUID — null if it isn't drawn
     * there at all. */
    private IRPGraphNode findGraphNode(IRPDiagram diagram, IRPModelElement modelObject) {
        String guid = modelObject.getGUID();
        IRPCollection elements = diagram.getGraphicalElements();
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphNode) {
                IRPModelElement mo = ((IRPGraphNode) obj).getModelObject();
                if (mo != null && guid.equals(mo.getGUID())) return (IRPGraphNode) obj;
            }
        }
        return null;
    }

    /** Repositions an already-drawn 100x100 node in place — same fixed size addNewNodeForElement
     * itself always uses (see addAggregationPart), so only the two corners actually move. Found via
     * IRPGraphNode#getAllGraphicalProperties() (neither getX()/getY() nor a dedicated move method
     * exist on IRPGraphNode): a node's position is the "Position" graphical property (top-left
     * corner, "x,y") plus a "Polygon" one (the four corners, "4,x,y,x+w,y,x+w,y+h,x,y+h") — both
     * set here to keep them consistent, since it was unclear from the property dump alone whether
     * Rhapsody derives one from the other or expects both kept in sync by the caller. */
    private void moveGraphNode(IRPGraphNode node, int x, int y) {
        int width = 100;
        int height = 100;
        node.setGraphicalProperty("Position", x + "," + y);
        node.setGraphicalProperty("Polygon", "4," + x + "," + y + "," + (x + width) + "," + y + ","
                + (x + width) + "," + (y + height) + "," + x + "," + (y + height));
    }

    @Override
    public synchronized void deleteElement(String guid) {
        // A composite guid ("<parent>|<child>", see portNode/updatePort's own javadoc) addresses one
        // specific OCCURRENCE of a shared nested port — but the underlying child object is genuinely
        // shared, so there is no "delete for just this occurrence" (matches the Rhapsody-native
        // reality confirmed live: a nested port always exists for every occurrence of its parent's
        // shared type, only its per-occurrence direction can differ). Deleting removes the shared
        // object itself, same as deleting a plain-addressed nested port always did — this only fixes
        // GUID resolution (findElement doesn't understand the composite form), not the semantics.
        int sep = guid.indexOf('|');
        String targetGuid = sep < 0 ? guid : guid.substring(sep + 1);
        IRPModelElement el = findElement(targetGuid);
        if (el instanceof IRPProject) {
            throw new IllegalArgumentException("The model root cannot be deleted.");
        }
        // Capture the port's own contract BEFORE deleting — if it's a "many side" endpoint of a
        // hub relationship (internal broadcast receiver, or external delegation child), the hub's
        // own MULTIPLICITY needs to shrink to match once this one is gone. Requested live: "wenn
        // ein port gelöscht wird muss die Multiplizität korrigiert werden."
        IRPClassifier contractToRecalc = el instanceof IRPPort || el instanceof IRPSysMLPort ? getContract(el) : null;
        el.deleteFromProject();
        if (contractToRecalc instanceof IRPClass) {
            try {
                recalculateHubMultiplicity((IRPClass) contractToRecalc);
            } catch (Exception ex) {
                System.err.println("[RhapsodyModelStore] multiplicity recalc after delete failed: " + ex.getMessage());
            }
        }
        save();
    }

    /** Recomputes and reapplies the "hub" port's own MULTIPLICITY for contract — the port other
     * ports connect to (internal broadcast sender, or external delegation's root boundary leaf) —
     * called after one of its connected ports is deleted, since the hub's count needs to shrink.
     * Deliberately LINK-truth-based rather than re-deriving "which port is the hub" from ownership
     * heuristics (fragile — see findConnectorCandidates' own external-case owner-chain walk, which
     * needs a specific root/tree context this method doesn't have): whichever port among those still
     * sharing this contract is referenced by 2+ SURVIVING links (fromPort or toPort, either side) IS
     * the hub, by definition, however it got that way. A port left with only 0-1 references gets its
     * multiplicity reset back to "1" (no longer a hub, or never was — safe no-op either way). */
    private void recalculateHubMultiplicity(IRPClass contract) {
        List<IRPModelElement> siblings = new ArrayList<>();
        collectPortsByContract(activeProject(), contract, siblings);
        if (siblings.isEmpty()) return;
        Set<String> siblingGuids = new HashSet<>();
        Map<String, IRPPort> byGuid = new HashMap<>();
        for (IRPModelElement p : siblings) {
            siblingGuids.add(p.getGUID());
            if (p instanceof IRPPort) byGuid.put(p.getGUID(), (IRPPort) p);
        }
        Map<String, Integer> counts = new HashMap<>();
        countLinkOccurrences(activeProject(), siblingGuids, counts);
        for (Map.Entry<String, IRPPort> entry : byGuid.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            entry.getValue().setMultiplicity(count > 1 ? String.valueOf(count) : "1");
        }
    }

    private void countLinkOccurrences(IRPPackage pkg, Set<String> portGuids, Map<String, Integer> counts) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            countLinkOccurrencesInClass((IRPClass) classes.getItem(i), portGuids, counts);
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            countLinkOccurrences((IRPPackage) nestedPkgs.getItem(i), portGuids, counts);
        }
    }

    private void countLinkOccurrencesInClass(IRPClass cls, Set<String> portGuids, Map<String, Integer> counts) {
        Iterator<Object> it = cls.getNestedElements().toList().iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof IRPLink) {
                IRPLink link = (IRPLink) o;
                IRPPort fp = link.getFromPort();
                IRPPort tp = link.getToPort();
                if (fp != null && portGuids.contains(((IRPModelElement) fp).getGUID())) {
                    counts.merge(((IRPModelElement) fp).getGUID(), 1, Integer::sum);
                }
                if (tp != null && portGuids.contains(((IRPModelElement) tp).getGUID())) {
                    counts.merge(((IRPModelElement) tp).getGUID(), 1, Integer::sum);
                }
            } else if (o instanceof IRPClass) {
                countLinkOccurrencesInClass((IRPClass) o, portGuids, counts);
            }
        }
    }

    // ── Context (external systems) ──────────────────────────────────────

    /** Lists all Actors anywhere in the active project, regardless of nesting. */
    @Override
    public synchronized List<Object> getContext() {
        List<Object> actors = new ArrayList<>();
        collectActors(activeProject(), actors);
        return actors;
    }

    @Override
    public synchronized Map<String, Object> createActor(String parentGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing != null) {
                existing.setName(name);
                save();
                return elementRef(existing, "Actor");
            }
        }
        IRPModelElement parent = findElement(parentGuid);
        if (!(parent instanceof IRPPackage)) {
            throw new IllegalArgumentException("An Actor can only be created under a Package");
        }
        // Actors (Context tab — external systems the system-of-interest interacts with) are always
        // created directly under the model root (the Context tab has no nesting/view picker of its
        // own), so unlike containerFor's generic root-vs-nested-package handling, this always goes
        // to "Context" — a package nested under "Operational" (see kontextPackage), not a
        // standalone top-level one (tried first, corrected).
        IRPPackage kontext = kontextPackage();
        // Find-or-create by name — same reasoning as createArchitectureElement/createPort/
        // createFunction's fallbacks: an interactively-created Actor is never Tag-stamped, so
        // re-importing an XML previously exported from this same already-populated project would
        // otherwise risk the same "clash with an existing element" Rhapsody threw for Package/
        // Class/Port/Operation before those were fixed the same way.
        IRPModelElement created = findActorByNameDirect(kontext, name);
        if (created == null) {
            created = (IRPModelElement) kontext.addActor(name);
        }
        stampSourceGuid(created, sourceGuid);
        save();
        return elementRef(created, "Actor");
    }

    /** DIRECT (non-recursive) Actor-by-name lookup, scoped to one package's own immediate Actors —
     * used by createActor's find-or-create-by-name fallback. */
    private IRPModelElement findActorByNameDirect(IRPPackage pkg, String name) {
        IRPCollection actors = pkg.getActors();
        for (int i = 1; i <= actors.getCount(); i++) {
            IRPModelElement a = (IRPModelElement) actors.getItem(i);
            if (name.equals(a.getName())) return a;
        }
        return null;
    }

    // ── Capabilities (top-level grouping; each Capability owns a list of UseCases) ──────

    /** A Capability is an IRPPackage nested under "Operational/Capabilities" (see
     * capabilitiesPackage) — a grouping concept mirroring how a FunctionalNode groups Functions,
     * except Rhapsody gives packages no native "Block"-like containment, so a package is the
     * closest native fit for "a named box that owns a list of UseCases". Only DIRECT children of
     * capabilitiesPackage() are listed — nesting a package inside a Capability isn't a thing this
     * app creates, but even if one existed it shouldn't surface as a second-level Capability. */
    @Override
    public synchronized List<Object> getCapabilities() {
        List<Object> out = new ArrayList<>();
        IRPCollection nested = capabilitiesPackage().getPackages();
        for (int i = 1; i <= nested.getCount(); i++) {
            out.add(elementRef((IRPModelElement) nested.getItem(i), "Capability"));
        }
        return out;
    }

    @Override
    public synchronized Map<String, Object> createCapability(String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing instanceof IRPPackage) {
                existing.setName(sanitizePackageName(name));
                setDisplayName(existing, name);
                refreshUseCaseDiagram(existing.getGUID());
                save();
                return elementRef(existing, "Capability");
            }
        }
        // Package names are far more restricted than Class/Actor/UseCase names in Rhapsody — see
        // sanitizePackageName's javadoc for the live error this fixes. Find-or-create BY NAME (not
        // a blind addNestedPackage) — see findOrCreateCapabilityPackage's javadoc for why this is
        // required, not just an optimization.
        IRPPackage created = findOrCreateCapabilityPackage(sanitizePackageName(name));
        setDisplayName((IRPModelElement) created, name);
        stampSourceGuid((IRPModelElement) created, sourceGuid);
        // Every Capability gets its own Use Case Diagram immediately, even with no UseCases/links
        // yet — mirrors ensureOwnDiagrams for architecture elements (see refreshUseCaseDiagram).
        refreshUseCaseDiagram(((IRPModelElement) created).getGUID());
        save();
        return elementRef((IRPModelElement) created, "Capability");
    }

    /** UseCases owned by capabilityGuid — native package containment (IRPPackage#getUseCases),
     * unlike the old flat model this replaced, so no owner-tag indirection is needed here. */
    @Override
    public synchronized List<Object> getUseCasesOf(String capabilityGuid) {
        List<Object> out = new ArrayList<>();
        IRPModelElement el = findElement(capabilityGuid);
        if (el instanceof IRPPackage) {
            IRPCollection useCases = ((IRPPackage) el).getUseCases();
            for (int i = 1; i <= useCases.getCount(); i++) {
                out.add(elementRef((IRPModelElement) useCases.getItem(i), "UseCase"));
            }
        }
        return out;
    }

    @Override
    public synchronized Map<String, Object> createUseCase(String capabilityGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing != null) {
                existing.setName(name);
                refreshUseCaseDiagram(capabilityGuid);
                save();
                return elementRef(existing, "UseCase");
            }
        }
        IRPModelElement parent = findElement(capabilityGuid);
        if (!(parent instanceof IRPPackage)) {
            throw new IllegalArgumentException("A UseCase can only be created under a Capability");
        }
        // Find-or-create by name within this Capability's own package, same reasoning as
        // findOrCreateCapabilityPackage — an interactively-created UseCase (never Tag-stamped) would
        // otherwise be invisible to the sourceGuid match above, and re-importing an XML that already
        // exists live would keep piling up same-named duplicates instead of updating in place.
        IRPModelElement created = findOrCreateUseCase((IRPPackage) parent, name);
        stampSourceGuid(created, sourceGuid);
        // New UseCase must appear on its Capability's own Use Case Diagram right away — see
        // refreshUseCaseDiagram's javadoc.
        refreshUseCaseDiagram(capabilityGuid);
        save();
        return elementRef(created, "UseCase");
    }

    private IRPModelElement findOrCreateUseCase(IRPPackage pkg, String name) {
        IRPCollection useCases = pkg.getUseCases();
        for (int i = 1; i <= useCases.getCount(); i++) {
            IRPModelElement el = (IRPModelElement) useCases.getItem(i);
            if (name.equals(el.getName())) return el;
        }
        return (IRPModelElement) pkg.addUseCase(name);
    }

    /** Full detail of a UseCase (goal, actors, preconditions, basicPath, alternatives, extensions,
     * postCondition) — the fields have no native Rhapsody equivalent, so like the other frontend-
     * only concepts (SOURCE_GUID_TAG/POS_*_TAG/LINKED_OWNERS_TAG) they're stamped as Tags on the
     * UseCase itself. Multi-line lists (preconditions/basicPath) use a newline separator; the
     * structured alternatives/extensions are a single JSON blob each (the only two fields whose own
     * shape is more than a flat string list). Actors are stored as comma-separated Actor GUIDs,
     * resolved to names on read so the frontend's autocomplete/highlight can show them the same as
     * local mode — see getActors. */
    @Override
    public synchronized Map<String, Object> getUseCaseDetail(String guid) {
        IRPModelElement el = findElement(guid);
        if (el == null || !isUseCase(el)) {
            throw new IllegalArgumentException("No UseCase found with GUID '" + guid + "'");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guid", el.getGUID());
        m.put("name", el.getName());
        String capGuid = capabilityGuidOf(el);
        m.put("capabilityGuid", capGuid);
        m.put("goal", tagValue(el, UC_GOAL_TAG));
        // actors: resolve guids -> names
        List<Object> actorRefs = new ArrayList<>();
        String actorGuids = tagValue(el, UC_ACTORS_TAG);
        if (actorGuids != null) {
            for (String g : actorGuids.split(",")) {
                if (g.isEmpty()) continue;
                IRPModelElement actor = findElement(g);
                if (actor != null) {
                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("guid", actor.getGUID());
                    ref.put("name", actor.getName());
                    actorRefs.add(ref);
                }
            }
        }
        m.put("actors", actorRefs);
        m.put("preconditions", splitLines(tagValue(el, UC_PRECONDITIONS_TAG)));
        m.put("basicPath", splitLines(tagValue(el, UC_BASICPATH_TAG)));
        m.put("alternatives", parseJsonList(tagValue(el, UC_ALTERNATIVES_TAG)));
        m.put("extensions", parseJsonList(tagValue(el, UC_EXTENSIONS_TAG)));
        m.put("postCondition", tagValue(el, UC_POSTCONDITION_TAG));
        return m;
    }

    @Override
    public synchronized void updateUseCase(String guid, Map<String, Object> detail) {
        IRPModelElement el = findElement(guid);
        if (el == null || !isUseCase(el)) {
            throw new IllegalArgumentException("No UseCase found with GUID '" + guid + "'");
        }
        if (detail.containsKey("goal")) stampTagValue(el, UC_GOAL_TAG, str(detail, "goal"));
        if (detail.containsKey("actors")) {
            // Two different shapes reach here depending on the caller — see LocalXmlModelStore's
            // own updateUseCase javadoc for the full explanation: the frontend's own PATCH
            // .../detail sends a plain list of guid strings (UseCaseDetail.actors: string[]), while
            // ModelXml's XML import path (importUseCaseDetail) sends {guid,name} ref maps
            // (mirroring capabilityLink/contextViewLink's own shape). Previously only the Map shape
            // was handled here, so a normal frontend save threw a ClassCastException against
            // Rhapsody mode specifically (never surfaced until the UseCase editor was actually
            // exercised against a live Rhapsody project).
            StringBuilder sb = new StringBuilder();
            for (Object a : list(detail, "actors")) {
                String actorGuid = a instanceof Map ? (String) ((Map<String, Object>) a).get("guid") : (String) a;
                if (sb.length() > 0) sb.append(",");
                sb.append(actorGuid);
            }
            stampTagValue(el, UC_ACTORS_TAG, sb.toString());
        }
        if (detail.containsKey("preconditions")) stampTagValue(el, UC_PRECONDITIONS_TAG, joinLines(list(detail, "preconditions")));
        if (detail.containsKey("basicPath")) stampTagValue(el, UC_BASICPATH_TAG, joinLines(list(detail, "basicPath")));
        if (detail.containsKey("alternatives")) stampTagValue(el, UC_ALTERNATIVES_TAG, toJson(list(detail, "alternatives")));
        if (detail.containsKey("extensions")) stampTagValue(el, UC_EXTENSIONS_TAG, toJson(list(detail, "extensions")));
        if (detail.containsKey("postCondition")) stampTagValue(el, UC_POSTCONDITION_TAG, str(detail, "postCondition"));
        // Write the full structured narrative into Rhapsody's own native Description property too
        // (see UseCaseDocFormatter), so the UseCase's documentation is readable directly in
        // Rhapsody (or anywhere else that only shows the native field, not this app's own editor) —
        // requested live: "wir müssen den UC als Text in die UC dokumentation eintragen". Rebuilds
        // the FULL detail (not just the fields this call changed) so a partial update still produces
        // a complete, correct narrative. Unconditional — a UseCase has no manual-edit path for
        // Documentation at all (the frontend's own generic "Edit documentation" button was removed
        // for UseCase rows specifically — see UseCasesSection.tsx's own javadoc), so there's nothing
        // else that could ever write here to protect against; an earlier version of this guarded
        // against manual edits via a text-diff heuristic, dropped as unnecessary once the manual
        // entry point itself was removed instead — requested live: "lassen wir das die documentation
        // bei UC weg und füllen stattdessen das Rhapsody documentationfeld mit den UC daten".
        el.setDescription(UseCaseDocFormatter.format(getUseCaseDetail(guid)));
        // Actors may have changed — the Capability's own Use Case Diagram needs its Actor nodes/
        // Associations refreshed too (see refreshUseCaseDiagram's javadoc).
        String capGuid = capabilityGuidOf(el);
        if (capGuid != null) refreshUseCaseDiagram(capGuid);
        save();
    }

    /** Whether el is a UseCase (an IRPUseCase lives natively inside a Capability package — see
     * getUseCasesOf). Used to validate getUseCaseDetail/updateUseCase's argument. */
    private boolean isUseCase(IRPModelElement el) {
        // IRPUseCase is a distinct metaclass with no convenient instanceof here; a UseCase is one
        // that is directly owned by a Capability package (which itself nests under capabilitiesPackage).
        return capabilityGuidOf(el) != null;
    }

    /** The owning Capability's GUID for a UseCase element, or null if el isn't a UseCase. Walks up
     * via getOwner() until either a Capability package (a direct child of capabilitiesPackage) or
     * the project root is reached — a UseCase's owner is always its Capability package directly. */
    private String capabilityGuidOf(IRPModelElement el) {
        IRPModelElement owner = el.getOwner();
        while (owner != null) {
            if (owner instanceof IRPPackage && isCapabilityPackage((IRPPackage) owner)) {
                return owner.getGUID();
            }
            owner = owner.getOwner();
        }
        return null;
    }

    /** Whether pkg is a direct child of capabilitiesPackage() (i.e. it IS a Capability, not the
     * capabilitiesPackage itself nor any deeper package). Compares owner identity by GUID, not
     * Java object identity — Rhapsody's COM bridge can hand back a different Java proxy instance
     * for the same underlying element across separate calls (pkg.getOwner() here vs. a fresh
     * getPackages() lookup), so an earlier version of this method that searched
     * owner.getPackages() for "nested.getItem(i) == pkg" intermittently returned false for a
     * genuine Capability — found live: opening the UseCase editor threw "No UseCase found with
     * GUID ..." because isUseCase (which delegates to this) spuriously failed. Every other identity
     * check in this file already compares by GUID or IRPModelElement#equals for exactly this
     * reason (see capabilityGuidOf, hasRelationTo, addBlockToBDD's own comment on this); this one
     * just needed the same treatment, and the extra getPackages() membership search wasn't even
     * necessary — pkg.getOwner() already answers "what package is pkg directly inside". */
    private boolean isCapabilityPackage(IRPPackage pkg) {
        IRPModelElement owner = pkg.getOwner();
        return owner instanceof IRPPackage && owner.getGUID().equals(capabilitiesPackage().getGUID());
    }

    // ── Capabilities linked to an architecture element (reference, not ownership) ───────

    /** Capabilities linked to ownerGuid (an architecture element) — rendered inside that
     * element's node like PortsSection, mirroring getPorts. A link is a reference to an existing
     * top-level Capability (see LINKED_OWNERS_TAG), not ownership — the same Capability can be
     * linked from multiple elements. */
    @Override
    public synchronized List<Object> getCapabilitiesOf(String ownerGuid) {
        List<Object> out = new ArrayList<>();
        IRPCollection nested = capabilitiesPackage().getPackages();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPModelElement cap = (IRPModelElement) nested.getItem(i);
            if (linkedOwners(cap).contains(ownerGuid)) out.add(elementRef(cap, "Capability"));
        }
        return out;
    }

    @Override
    public synchronized void linkCapability(String ownerGuid, String capabilityGuid) {
        IRPModelElement cap = findElement(capabilityGuid);
        if (!(cap instanceof IRPPackage)) {
            throw new IllegalArgumentException("No Capability found with GUID '" + capabilityGuid + "'");
        }
        Set<String> owners = linkedOwners(cap);
        if (owners.add(ownerGuid)) {
            stampTagValue(cap, LINKED_OWNERS_TAG, String.join(",", owners));
            save();
        }
    }

    @Override
    public synchronized void unlinkCapability(String ownerGuid, String capabilityGuid) {
        IRPModelElement cap = findElement(capabilityGuid);
        if (cap == null) return;
        Set<String> owners = linkedOwners(cap);
        if (owners.remove(ownerGuid)) {
            stampTagValue(cap, LINKED_OWNERS_TAG, String.join(",", owners));
            save();
        }
    }

    /** Parses LINKED_OWNERS_TAG's comma-separated value back into a set of element GUIDs — empty
     * (never null) when the Capability has no links yet. Reused as-is for Context Views too (see
     * that section below) — same Tag, same mechanism, just stamped on a different kind of
     * IRPPackage; the tag name itself is generic ("linked owners"), not Capability-specific. */
    private Set<String> linkedOwners(IRPModelElement cap) {
        Set<String> out = new LinkedHashSet<>();
        String v = tagValue(cap, LINKED_OWNERS_TAG);
        if (v != null) {
            for (String guid : v.split(",")) {
                if (!guid.isEmpty()) out.add(guid);
            }
        }
        return out;
    }

    /** Find-or-create, then fully repopulate, a Capability's own Use Case Diagram — Rhapsody
     * metaclass "UseCaseDiagram" (IRPUseCaseDiagram, a real listed metaclass — see metaclasses.txt
     * in the Rhapsody installation's Doc directory), named "ucd" + the Capability's own sanitized
     * package name, owned directly by the Capability package itself (mirrors how a Block's own
     * BDD/IBD are owned by the Block — createOrGetBDD/DiagramService#createIBD — searched the same
     * way, via the owner's own getReferences()).
     *
     * Populated with:
     *   - the system boundary: a purely GRAPHICAL "System Border" node (metaType "System Border" —
     *     found live via IRPGraphNode#getAllGraphicalProperties() on one created through
     *     addNewNodeByType; not a model element, addNewNodeForElement doesn't apply — see
     *     findOrCreateBoundaryBox), labeled with the PROJECT's own name (activeProject) via its
     *     "Text" graphical property. Superseded two earlier attempts: first the Capability's own
     *     linked elements, then a systemOfInterest() fallback, both as plain classifier nodes —
     *     corrected live ("im UC diagramm ist der Block flexis, das gehört aber nicht hierher.
     *     statt dessen soll die boundary box verwendet werden... die Boundary box bekommt den
     *     projectnamen") — there's no per-Capability "system" concept for this box at all, just
     *     one project-wide boundary, the same on every Capability's own diagram.
     *   - every UseCase owned by the Capability (IRPPackage#getUseCases, same collection
     *     getUseCasesOf reads), placed INSIDE the boundary box's own rectangle — requested live:
     *     "alle UC mussen in der boundery box liegen". Purely a coordinate-placement convention (no
     *     true graphical parent/child containment API was found for this — getGraphicalParent()
     *     returned null for a freshly-created boundary node), matching how a UML system-boundary
     *     box conventionally just visually encloses its use cases by position.
     *   - every Actor referenced by any of those UseCases' own UC_ACTORS_TAG, placed OUTSIDE the
     *     boundary box (to its left), each connected to the UseCase(s) it participates in via a
     *     plain (non-Composition) Association relation — idempotent via findRelationTo — with an
     *     EXPLICIT graphical edge via addNewEdgeForElement. Confirmed live this diagram kind needs
     *     that explicit call: unlike a BDD's Composition association (which Rhapsody draws
     *     automatically once both ends share a diagram — see addAggregationPart's own javadoc, and
     *     where addNewEdgeForElement itself reproducibly threw "Rhapsody operation failed"), a plain
     *     Association on a Use Case Diagram does NOT auto-render — the model-level relation existed
     *     correctly (getRelations()) but no edge ever appeared until drawn explicitly, which DOES
     *     work here (also confirmed live) — idempotent via hasEdgeFor, since addNewEdgeForElement
     *     itself happily creates a duplicate graphic for the same relation if called again.
     *
     * Laid out top-to-bottom by index. The boundary box and every UseCase node have their
     * position/size actively re-applied on EVERY refresh (setNodeGeometry) rather than only at
     * creation — requested live as explicit diagram postprocessing ("Boundary box vergrössern. UCs
     * Größenverhaltniss auf 2/1 setzen und Position in die Boundary Box verschieben"), so an
     * existing diagram gets corrected in place too, not just new ones going forward. Actor nodes are
     * still only positioned once (addElementToDiagramAt's own idempotency), left to manual
     * rearrangement afterward — not requested to be force-corrected the way UseCases were. Exact
     * layout numbers are a first pass, not pixel-tuned.
     *
     * Called after every capability mutation that can change this diagram's content:
     * createCapability (so even an empty Capability gets its own diagram immediately, mirroring
     * ensureOwnDiagrams for architecture elements), createUseCase, updateUseCase (actors may have
     * changed), linkCapability, unlinkCapability. A capabilityGuid that doesn't resolve to a
     * Capability package is a silent no-op — defensive, not expected to trigger in practice. */
    private void refreshUseCaseDiagram(String capabilityGuid) {
        IRPModelElement capEl = findElement(capabilityGuid);
        if (!(capEl instanceof IRPPackage)) return;
        IRPPackage capPkg = (IRPPackage) capEl;
        IRPUseCaseDiagram ucd = findOrCreateUseCaseDiagram(capPkg);

        String projectName = activeProject().getDisplayName();
        if (projectName == null || projectName.isEmpty()) projectName = activeProject().getName();
        int boundaryX = 250;
        int boundaryY = 60;

        List<IRPModelElement> ucList = new ArrayList<>();
        IRPCollection useCases = capPkg.getUseCases();
        for (int i = 1; i <= useCases.getCount(); i++) {
            ucList.add((IRPModelElement) useCases.getItem(i));
        }

        // UseCase size/position and the boundary box's own size are recomputed and re-applied on
        // EVERY refresh (unlike addElementToDiagramAt's own "never move an already-placed node"
        // idempotency) — requested live as explicit diagram postprocessing: "Boundary box
        // vergrössern. UCs Größenverhaltniss (Breite/Höhe) auf 2/1 setzen und Position in die
        // Boundary Box verschieben" — existing UseCase nodes (placed by an earlier version of this
        // method, before the 2:1 sizing/generous margins existed) needed correcting in place, not
        // just new ones going forward.
        int ucWidth = 200;
        int ucHeight = 100; // 2:1 width:height, as requested
        int ucGapY = 40;
        int marginX = 60;
        int marginTop = 70;
        int marginBottom = 30;
        int boundaryWidth = ucWidth + marginX * 2;
        int boundaryHeight = Math.max(220, marginTop + ucList.size() * (ucHeight + ucGapY) + marginBottom);
        IRPGraphNode boundary = findOrCreateBoundaryBox(ucd, projectName, boundaryX, boundaryY, boundaryWidth, boundaryHeight);
        setNodeGeometry(boundary, boundaryX, boundaryY, boundaryWidth, boundaryHeight);

        for (int i = 0; i < ucList.size(); i++) {
            IRPGraphNode ucNode = addElementToDiagramAt(ucd, ucList.get(i), boundaryX + marginX, boundaryY + marginTop + i * (ucHeight + ucGapY), ucWidth, ucHeight);
            setNodeGeometry(ucNode, boundaryX + marginX, boundaryY + marginTop + i * (ucHeight + ucGapY), ucWidth, ucHeight);
        }

        int actorIndex = 0;
        for (IRPModelElement uc : ucList) {
            String actorGuids = tagValue(uc, UC_ACTORS_TAG);
            if (actorGuids == null || actorGuids.isEmpty()) continue;
            for (String g : actorGuids.split(",")) {
                if (g.isEmpty()) continue;
                IRPModelElement actorEl = findElement(g);
                if (!(actorEl instanceof IRPClassifier) || !(uc instanceof IRPClassifier)) continue;
                boolean isNewActorNode = findGraphNode(ucd, actorEl) == null;
                addElementToDiagramAt(ucd, actorEl, boundaryX - 150, boundaryY + 30 + actorIndex * 110, 70, 100);
                if (isNewActorNode) actorIndex++;
                IRPClassifier actorClassifier = (IRPClassifier) actorEl;
                IRPClassifier ucClassifier = (IRPClassifier) uc;
                if (findRelationTo(actorClassifier, ucClassifier) == null) {
                    actorClassifier.addRelationTo(ucClassifier, "", "Association", "", "", "Association", "", "");
                }
                drawEdgeIfMissing(ucd, actorClassifier, ucClassifier);
            }
        }
    }

    /** Find-or-create the diagram's one purely-graphical "System Border" node — see
     * refreshUseCaseDiagram's own javadoc for how this was found (Type="System Border" via
     * addNewNodeByType, label via the "Text" graphical property, not a model element at all so
     * there's no GUID/getModelObject to search by — found instead via its own Type property).
     * Re-stamps the Text label on every call (cheap) so a project rename is picked up on the next
     * save; the caller (refreshUseCaseDiagram) separately re-applies size/position via
     * setNodeGeometry every time too, since the box needs to keep growing as UseCases are added. */
    private IRPGraphNode findOrCreateBoundaryBox(IRPDiagram diagram, String label, int x, int y, int width, int height) {
        IRPCollection elements = diagram.getGraphicalElements();
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphNode) {
                IRPGraphNode node = (IRPGraphNode) obj;
                IRPGraphicalProperty type = node.getGraphicalProperty("Type");
                if (type != null && "System Border".equals(type.getValue())) {
                    node.setGraphicalProperty("Text", label);
                    return node;
                }
            }
        }
        IRPGraphNode node = diagram.addNewNodeByType("System Border", x, y, width, height);
        node.setGraphicalProperty("Text", label);
        return node;
    }

    /** Find-or-add el as a graph node on diagram at the given position/size — idempotent via
     * findGraphNode, and (unlike addElementToBDD's own auto-incrementing grid fallback) never
     * repositions an already-placed node, so a manual rearrangement in Rhapsody survives later
     * refreshes. Used by refreshUseCaseDiagram for deliberate boundary-relative placement (UseCases
     * inside the boundary box, Actors outside it) that a generic grid can't express. UseCase nodes
     * specifically get their geometry forced back to the intended layout on every call regardless
     * (see refreshUseCaseDiagram's own setNodeGeometry call right after this) — this method's own
     * idempotency still matters for THEM too, since it's what avoids creating a duplicate node. */
    private IRPGraphNode addElementToDiagramAt(IRPDiagram diagram, IRPModelElement el, int x, int y, int width, int height) {
        IRPGraphNode existing = findGraphNode(diagram, el);
        if (existing != null) return existing;
        return diagram.addNewNodeForElement(el, x, y, width, height);
    }

    /** Sets both position AND size on an already-placed graph node, all four graphical properties
     * kept consistent (Position = top-left corner, Width/Height, Polygon = all four corners) —
     * extends moveGraphNode's own Position/Polygon pattern (found via
     * IRPGraphNode#getAllGraphicalProperties(), same technique) with the two extra properties
     * needed to actually resize a node, not just move it; moveGraphNode itself never needed to
     * resize (BDD/IBD nodes there stay a fixed 100x100). */
    private void setNodeGeometry(IRPGraphNode node, int x, int y, int width, int height) {
        node.setGraphicalProperty("Position", x + "," + y);
        node.setGraphicalProperty("Width", String.valueOf(width));
        node.setGraphicalProperty("Height", String.valueOf(height));
        node.setGraphicalProperty("Polygon", "4," + x + "," + y + "," + (x + width) + "," + y + ","
                + (x + width) + "," + (y + height) + "," + x + "," + (y + height));
    }

    /** Find-or-create capPkg's own Use Case Diagram — see refreshUseCaseDiagram's javadoc. */
    private IRPUseCaseDiagram findOrCreateUseCaseDiagram(IRPPackage capPkg) {
        String name = "ucd" + capPkg.getName();
        IRPCollection refs = capPkg.getReferences();
        for (int i = 1; i <= refs.getCount(); i++) {
            Object obj = refs.getItem(i);
            if (obj instanceof IRPUseCaseDiagram && name.equals(((IRPModelElement) obj).getName())) {
                return (IRPUseCaseDiagram) obj;
            }
        }
        return (IRPUseCaseDiagram) capPkg.addNewAggr("UseCaseDiagram", name);
    }

    /** a's own existing relation (any type) whose other end is b, by GUID — or null — generalizes
     * hasCompositionTo (IRPClass-scoped, boolean-only) to any IRPClassifier pair, returning the
     * relation itself (not just whether one exists) since refreshUseCaseDiagram's edge-drawing
     * needs the actual IRPRelation object to pass to addNewEdgeForElement. */
    private IRPRelation findRelationTo(IRPClassifier a, IRPClassifier b) {
        String bGuid = ((IRPModelElement) b).getGUID();
        IRPCollection relations = a.getRelations();
        for (int i = 1; i <= relations.getCount(); i++) {
            IRPRelation rel = (IRPRelation) relations.getItem(i);
            IRPClassifier other = rel.getOtherClass();
            if (other != null && bGuid.equals(((IRPModelElement) other).getGUID())) return rel;
        }
        return null;
    }

    /** diagram's own graph edge whose model object is relation, by GUID, or null — mirrors
     * addElementToBDD/addBlockToBDD's own idempotent-node pattern, but for edges (see
     * drawEdgeIfMissing's own comment on why an edge needs this explicit check: unlike a node,
     * addNewEdgeForElement happily creates a duplicate graphic for an already-drawn relation). */
    private IRPGraphEdge findEdgeFor(IRPDiagram diagram, IRPModelElement relation) {
        String relGuid = relation.getGUID();
        IRPCollection elements = diagram.getGraphicalElements();
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphEdge) {
                IRPModelElement mo = ((IRPGraphEdge) obj).getModelObject();
                if (mo != null && relGuid.equals(mo.getGUID())) return (IRPGraphEdge) obj;
            }
        }
        return null;
    }

    private boolean hasEdgeFor(IRPDiagram diagram, IRPModelElement relation) {
        return findEdgeFor(diagram, relation) != null;
    }

    /** Draws an explicit graphical edge on diagram for the relation between a and b, if one exists
     * (findRelationTo) and isn't already drawn (hasEdgeFor) and both ends are actually placed as
     * nodes on diagram (findGraphNode — a genuinely optional find, same as every other read-only
     * lookup here; never creates a node as a side effect of drawing an edge). Used for
     * refreshUseCaseDiagram's Actor-to-UseCase Association, where actor/usecase are placed
     * side-by-side (not vertically parent/child), so there's no fixed anchor convention to enforce
     * the way drawCompositionEdgeIfMissing does — the default corner-ish anchor is good enough and
     * Rhapsody's own routing handles the rest. addAggregationPart's BDD Composition and
     * addContextViewPart's Context View BDD Composition use drawCompositionEdgeIfMissing instead
     * (see its own javadoc). All three used to assume Rhapsody auto-renders an association once
     * both ends share a diagram — confirmed live, repeatedly, that it does NOT: the model-level
     * relation exists correctly but no edge appears until drawn explicitly via
     * addNewEdgeForElement. */
    private void drawEdgeIfMissing(IRPDiagram diagram, IRPClassifier a, IRPClassifier b) {
        IRPRelation relation = findRelationTo(a, b);
        if (relation == null) return;
        if (hasEdgeFor(diagram, (IRPModelElement) relation)) return;
        IRPGraphNode aNode = findGraphNode(diagram, (IRPModelElement) a);
        IRPGraphNode bNode = findGraphNode(diagram, (IRPModelElement) b);
        if (aNode != null && bNode != null) {
            diagram.addNewEdgeForElement((IRPModelElement) relation, aNode, 10, 10, bNode, 10, 10);
        }
    }

    /** Draws (or redraws, correcting its anchor points) the graphical edge for the Composition
     * relation from parent to child on diagram — anchored at parent's own bottom-center and
     * child's own top-center, matching the vertical parent-above/child-below convention this app's
     * own Architecture tab canvas already uses for the same tree. Requested live: "kannst Du den
     * Ankerpunkt der direkten Aggregationen beim Parent unten und beim Child oben jeweils mittig im
     * Postprocessing hinschieben." Used by addAggregationPart (the System-of-Systems/aspect tree's
     * own BDD) and addContextViewPart (a Context View's own BDD — same Composition relation shape,
     * contextView "owns" part the same way) — NOT by refreshUseCaseDiagram's Actor-to-UseCase
     * Association, which stays on drawEdgeIfMissing (actors sit beside their UseCases, not above/
     * below, so a vertical anchor wouldn't make visual sense there).
     *
     * ALWAYS deletes and recreates the edge (unlike drawEdgeIfMissing's skip-if-already-drawn) —
     * found live that setGraphicalProperty("SourcePosition"/"TargetPosition", ...) on an ALREADY
     * existing edge does NOT reliably take the literal point requested (Rhapsody's own rectilinear
     * auto-router silently re-snapped it elsewhere), while addNewEdgeForElement given the exact
     * same coordinates AT CREATION time honors them exactly — confirmed live on the real Flexis/
     * Planning composition: post-hoc property edits landed at (321,-21)/(240,144) instead of the
     * requested (371,-10)/(241,145), while a fresh addNewEdgeForElement call with those same
     * numbers landed exactly on them. Recreating is a cheap delete+add and stays idempotent in
     * effect: called again with unchanged node positions, it produces the identical edge every
     * time. */
    private void drawCompositionEdgeIfMissing(IRPDiagram diagram, IRPClassifier parent, IRPClassifier child) {
        IRPRelation relation = findRelationTo(parent, child);
        if (relation == null) return;
        IRPGraphNode parentNode = findGraphNode(diagram, (IRPModelElement) parent);
        IRPGraphNode childNode = findGraphNode(diagram, (IRPModelElement) child);
        if (parentNode == null || childNode == null) return;

        IRPGraphEdge existing = findEdgeFor(diagram, (IRPModelElement) relation);
        if (existing != null) {
            IRPCollection toRemove = application.createNewCollection();
            toRemove.addGraphicalItem(existing);
            diagram.removeGraphElements(toRemove);
        }

        int[] parentGeom = readNodeGeometry(parentNode);
        int[] childGeom = readNodeGeometry(childNode);
        int srcX = parentGeom[0] + parentGeom[2] / 2;
        int srcY = parentGeom[1] + parentGeom[3];
        int trgX = childGeom[0] + childGeom[2] / 2;
        int trgY = childGeom[1];
        diagram.addNewEdgeForElement((IRPModelElement) relation, parentNode, srcX, srcY, childNode, trgX, trgY);
    }

    /** node's own {x, y, width, height}, read from its Position/Width/Height graphical properties
     * (the same properties setNodeGeometry writes) — used by drawCompositionEdgeIfMissing to
     * compute an anchor point relative to the node's ACTUAL current geometry rather than assuming
     * any fixed size, so a manually-resized node still gets a correctly-centered anchor. */
    private int[] readNodeGeometry(IRPGraphNode node) {
        String[] pos = node.getGraphicalProperty("Position").getValue().split(",");
        int x = Integer.parseInt(pos[0].trim());
        int y = Integer.parseInt(pos[1].trim());
        int w = Integer.parseInt(node.getGraphicalProperty("Width").getValue().trim());
        int h = Integer.parseInt(node.getGraphicalProperty("Height").getValue().trim());
        return new int[]{x, y, w, h};
    }

    // ── Context Views (user-defined; each is a real Block whose own IBD/BDD show the
    // system-of-interest and every linked Actor as Composition PARTS — not a Package-based tag
    // reference the way Capabilities are) ────────────────────────────────────────────────

    /** A Context View is an IRPClass ("Block") living directly under "Operational/Context" (see
     * kontextPackage — a FLAT structure, no separate sub-package: "das Package ContextViews
     * brauchen wir nicht eine flache Struktur ist ausreichend!", told apart from a real Actor or
     * architecture element via CONTEXT_VIEW_STEREOTYPE, not by location) — corrected live from an
     * earlier Package-based design (mirroring Capabilities) after the user built a live reference
     * example (Context_Operaional) showing the REAL intended shape: "context ist ein block der
     * alle externen und SoI beinhaltet (parts)".
     * Both the system-of-interest AND every linked Actor become genuine Composition parts
     * (itsFlexis, itsHEU, ...) of this Block, exactly the same addRelationTo/"its"+Name mechanism
     * already used for the System-of-Systems architecture tree (see addAggregationPart) — just
     * generalized here (addContextViewPart) to accept an Actor (IRPClassifier, not IRPClass) as
     * well as the system-of-interest itself. Its own IBD is where the actual interface connectors
     * get drawn (see getPendingConnectors' Context View section) — "in Rhapsody sind context views
     * ibds in denen auch die schnittstellen verbunden werden. aber nur die high-level ohne nested
     * ports!" */
    @Override
    public synchronized List<Object> getContextViews() {
        List<Object> out = new ArrayList<>();
        IRPCollection nested = kontextPackage().getClasses();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPClass c = (IRPClass) nested.getItem(i);
            if (hasStereotype(c, CONTEXT_VIEW_STEREOTYPE)) out.add(elementRef((IRPModelElement) c, "ContextView"));
        }
        return out;
    }

    @Override
    public synchronized Map<String, Object> createContextView(String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing instanceof IRPClass) {
                existing.setName(sanitizePackageName(name));
                setDisplayName(existing, name);
                ensureStereotype(existing, CONTEXT_VIEW_STEREOTYPE, levelMetaType);
                save();
                return elementRef(existing, "ContextView");
            }
        }
        IRPClass created = findOrCreateContextViewClass(sanitizePackageName(name));
        setDisplayName((IRPModelElement) created, name);
        ensureBlockStereotype((IRPModelElement) created);
        ensureStereotype((IRPModelElement) created, CONTEXT_VIEW_STEREOTYPE, levelMetaType);
        ensureOwnDiagrams(created);
        // The system-of-interest is ALWAYS a part of every Context View, from the moment it's
        // created — "der Systemblock muss immer in jeder view automatisch eingefügt werden."
        IRPClass soi = systemOfInterest();
        if (soi != null) addContextViewPart(created, soi);
        stampSourceGuid((IRPModelElement) created, sourceGuid);
        save();
        return elementRef((IRPModelElement) created, "ContextView");
    }

    @Override
    public synchronized List<Object> getContextViewsOf(String actorGuid) {
        List<Object> out = new ArrayList<>();
        IRPModelElement actorEl = findElement(actorGuid);
        if (!(actorEl instanceof IRPClassifier)) return out;
        IRPCollection nested = kontextPackage().getClasses();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPClass cv = (IRPClass) nested.getItem(i);
            if (hasStereotype(cv, CONTEXT_VIEW_STEREOTYPE) && hasCompositionTo(cv, (IRPClassifier) actorEl)) out.add(elementRef(cv, "ContextView"));
        }
        return out;
    }

    @Override
    public synchronized void linkContextView(String actorGuid, String contextViewGuid) {
        IRPModelElement cv = findElement(contextViewGuid);
        if (!(cv instanceof IRPClass)) {
            throw new IllegalArgumentException("No Context View found with GUID '" + contextViewGuid + "'");
        }
        IRPModelElement actorEl = findElement(actorGuid);
        if (!(actorEl instanceof IRPClassifier)) {
            throw new IllegalArgumentException("No element found with GUID '" + actorGuid + "'");
        }
        addContextViewPart((IRPClass) cv, (IRPClassifier) actorEl);
        save();
    }

    @Override
    public synchronized void unlinkContextView(String actorGuid, String contextViewGuid) {
        IRPModelElement cv = findElement(contextViewGuid);
        IRPModelElement actorEl = findElement(actorGuid);
        if (!(cv instanceof IRPClass) || !(actorEl instanceof IRPClassifier)) return;
        removeContextViewPart((IRPClass) cv, (IRPClassifier) actorEl);
        save();
    }

    /** The system-of-interest — the sole root-level, non-aspect architecture element directly
     * under the "Operational" view package (see viewPackage) — mirrors the frontend's own
     * `systemOfInterest` useMemo (App.tsx) exactly: the first root-level Structure-family Class.
     * Every root-level Class under "Operational" already IS Structure-family by construction (a
     * FunctionalNode/LogicalNode/PhysicalNode lives in its OWN separate view package instead — see
     * containerForKind), so no extra kind filtering is needed here. Null if no architecture element
     * has been created yet. */
    private IRPClass systemOfInterest() {
        IRPCollection classes = viewPackage("Operational").getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            return (IRPClass) classes.getItem(i);
        }
        return null;
    }

    /** Adds part (the system-of-interest, or an Actor) as a Composition part of contextView — the
     * same underlying addRelationTo/"its"+Name mechanism as addAggregationPart (used for the
     * System-of-Systems tree), generalized to accept any IRPClassifier since a Context View
     * composes BOTH the system-of-interest (an IRPClass) AND external Actors (IRPClassifier, not
     * IRPClass) as peers. Deliberately NOT reusing addAggregationPart/addBlockToBDD/
     * scaledFrontendPosition directly — those assume architecture-tree-specific semantics (levelOf,
     * the System-of-Systems family walk via topLevelAncestor, frontend per-view canvas positions)
     * that don't apply to a Context View or an Actor. Idempotent (see hasCompositionTo). */
    private void addContextViewPart(IRPClass contextView, IRPClassifier part) {
        if (!hasCompositionTo(contextView, part)) {
            contextView.addRelationTo(part, "", "Composition", "", "", "Association", "", "");
        }
        ECADContext context = new ECADContext();
        IRPStructureDiagram ibd = diagramService.createIBD(contextView, context);
        IRPInstance instance = modelElementService.getInstance(contextView, itsInstanceName(((IRPModelElement) part).getName()));
        if (instance != null && !diagramService.isPartInIBD(ibd, instance)) {
            int offset = countImplementationObjects(ibd) * 150;
            diagramService.addPartToIBD(ibd, instance, 100 + offset, 100);
        }
        revealTopLevelPortsOnly(ibd);
        IRPObjectModelDiagram bdd = createOrGetBDD(contextView);
        addElementToBDD(bdd, (IRPModelElement) contextView);
        addElementToBDD(bdd, (IRPModelElement) part);
        // Explicit, anchor-corrected edge for the Composition association — see
        // drawCompositionEdgeIfMissing's own javadoc for why this is needed at all (a plain
        // association does NOT auto-render just because both ends share a diagram, confirmed live
        // — this Context View BDD had the same gap addAggregationPart's own BDD did) and for why
        // the anchor points need explicit correction too.
        drawCompositionEdgeIfMissing(bdd, contextView, part);
    }

    /** Removes part from contextView — the inverse of addContextViewPart. Deletes the Composition
     * relation itself (IRPRelation#deleteFromProject); Rhapsody's own auto-created "its"+Name part
     * instance and its IBD/BDD placement are expected to go with it (same lifecycle a Composition
     * association's end instance always has), not cleaned up separately here. */
    private void removeContextViewPart(IRPClass contextView, IRPClassifier part) {
        String partGuid = ((IRPModelElement) part).getGUID();
        IRPCollection relations = contextView.getRelations();
        for (int i = 1; i <= relations.getCount(); i++) {
            IRPRelation rel = (IRPRelation) relations.getItem(i);
            IRPClassifier other = rel.getOtherClass();
            if (other != null && partGuid.equals(((IRPModelElement) other).getGUID())) {
                ((IRPModelElement) rel).deleteFromProject();
                break;
            }
        }
    }

    /** Find-or-add el (the Context View itself, the system-of-interest, or an Actor — never
     * restricted to IRPClass the way addBlockToBDD is, since an Actor needs to appear here too) as
     * a graph node on bdd — idempotent. Grid-positioned by how many nodes are already on the
     * diagram; no frontend-position mirroring (unlike addBlockToBDD/scaledFrontendPosition) since
     * neither a Context View nor an Actor has a per-view canvas position concept to mirror.
     * Typed to the generic IRPDiagram base (both getGraphicalElements/addNewNodeForElement are
     * declared there) rather than IRPObjectModelDiagram specifically, so refreshUseCaseDiagram can
     * reuse this exact same idempotent-placement logic for a Capability's own IRPUseCaseDiagram. */
    private void addElementToBDD(IRPDiagram bdd, IRPModelElement el) {
        IRPCollection elements = bdd.getGraphicalElements();
        int nodeCount = 0;
        String elGuid = el.getGUID();
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphNode) {
                IRPModelElement mo = ((IRPGraphNode) obj).getModelObject();
                if (mo != null && elGuid.equals(mo.getGUID())) return;
                nodeCount++;
            }
        }
        int x = 100 + (nodeCount % 5) * 150;
        int y = 100 + (nodeCount / 5) * 150;
        bdd.addNewNodeForElement(el, x, y, 100, 100);
    }

    /** Counts existing "ImplementationObject"-typed (part) graph nodes already on ibd — used only
     * to space out a newly-added Context View part's own X position; purely cosmetic. */
    private int countImplementationObjects(IRPStructureDiagram ibd) {
        int count = 0;
        IRPCollection elements = ibd.getGraphicalElements();
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphNode && "ImplementationObject".equals(((IRPGraphNode) obj).getGraphicalProperty("Type").getValue())) {
                count++;
            }
        }
        return count;
    }

    // ── Functions (attached to a FunctionalNode, shown only in the Functional view) ─────

    /** A Function is a native Operation (IRPClassifier#addOperation) owned directly by the
     * FunctionalNode's own class — not a separate class of its own the way an earlier version of
     * this modeled it (a plain IRPClass stereotyped "function", living in the "Functional" package
     * with ownership tracked via an owner-GUID Tag, the same "Rhapsody has no native ownership for
     * this" workaround Capability-to-element links still use — see LINKED_OWNERS_TAG). An Operation
     * genuinely IS natively owned by its classifier, so none of that indirection is needed here:
     * parentGuid must be the FunctionalNode itself. */
    @Override
    public synchronized Map<String, Object> createFunction(String parentGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        IRPModelElement parent = findElement(parentGuid);
        if (!(parent instanceof IRPClass)) {
            throw new IllegalArgumentException("A Function can only be created under a FunctionalNode");
        }
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing instanceof IRPOperation) {
                existing.setName(name);
                save();
                return elementRef(existing, "Function");
            }
            if (existing != null) {
                // A stale Function-as-class from before Functions became native Operations (see
                // this method's own javadoc) — matched by the same sourceGuid a re-export/
                // re-import is now trying to update. Found live: a project that had already been
                // exported to before this change kept an orphaned "EnterVoiceData" class sitting
                // in SysMLFrontendData ("es gibt immer noch eine Klasse") — the sourceGuid match
                // above only handled the case where it was ALREADY an Operation, silently leaving
                // any older, still-class-shaped match untouched instead of migrating it. Deleting
                // it here and creating the real Operation below (stamped with the same sourceGuid)
                // fixes both this one and any future re-import of the same kind.
                existing.deleteFromProject();
            }
        }
        // Find-or-create by name among the FunctionalNode's own existing Operations — same
        // reasoning as createArchitectureElement/createPort's fallbacks: an interactively-created
        // Function is never Tag-stamped, so re-importing an XML previously exported from this same
        // already-populated project threw "Can't add aggregate of type Operation. Cannot add
        // Operation due to a clash with an existing element." otherwise (found live).
        IRPOperation created = findOperationByNameDirect((IRPClass) parent, name);
        if (created == null) {
            created = ((IRPClass) parent).addOperation(name);
        }
        stampSourceGuid((IRPModelElement) created, sourceGuid);
        save();
        return elementRef((IRPModelElement) created, "Function");
    }

    /** DIRECT (non-recursive) Operation-by-name lookup, scoped to one FunctionalNode's own
     * immediate Operations — used by createFunction's find-or-create-by-name fallback. */
    private IRPOperation findOperationByNameDirect(IRPClass cls, String name) {
        IRPCollection ops = cls.getOperations();
        for (int i = 1; i <= ops.getCount(); i++) {
            IRPOperation op = (IRPOperation) ops.getItem(i);
            if (name.equals(((IRPModelElement) op).getName())) return op;
        }
        return null;
    }

    /** ownerGuid's own Operations, directly — no owner-tag search needed, unlike Capabilities/
     * UseCases (see createFunction's javadoc for why Functions don't need that indirection). */
    @Override
    public synchronized List<Object> getFunctionsOf(String ownerGuid) {
        List<Object> out = new ArrayList<>();
        IRPModelElement el = findElement(ownerGuid);
        if (el instanceof IRPClass) {
            IRPCollection ops = ((IRPClass) el).getOperations();
            for (int i = 1; i <= ops.getCount(); i++) {
                out.add(elementRef((IRPModelElement) ops.getItem(i), "Function"));
            }
        }
        return out;
    }

    // ── Functional→Logical allocation (Rhapsody: an "Allocate"-stereotyped Dependency) ────

    /** LogicalNodes allocated FROM functionalNodeGuid — read from the FunctionalNode's own
     * getOwnedDependencies() (a Dependency created via addDependencyTo is owned by its own
     * DEPENDENT end, confirmed live — el.getOwnedDependencies() found it, but the DEPENDS-ON
     * end's own getDependencies() did NOT, so this only ever needs to look at the FunctionalNode's
     * own side, never the LogicalNode's), filtered to ones stereotyped "Allocate" so an unrelated
     * dependency some other feature might create isn't picked up here too. */
    @Override
    public synchronized List<Object> getAllocatedLogicalNodesOf(String functionalNodeGuid) {
        List<Object> out = new ArrayList<>();
        IRPModelElement el = findElement(functionalNodeGuid);
        IRPCollection deps = el.getOwnedDependencies();
        for (int i = 1; i <= deps.getCount(); i++) {
            IRPDependency dep = (IRPDependency) deps.getItem(i);
            if (!hasStereotype((IRPModelElement) dep, "Allocate")) continue;
            IRPModelElement target = dep.getDependsOn();
            if (target != null) out.add(elementRef(target, "LogicalNode"));
        }
        return out;
    }

    @Override
    public synchronized void linkLogicalNode(String functionalNodeGuid, String logicalNodeGuid) {
        IRPModelElement functionalEl = findElement(functionalNodeGuid);
        IRPModelElement logicalEl = findElement(logicalNodeGuid);
        if (findAllocateDependency(functionalEl, logicalNodeGuid) != null) return;
        IRPDependency dep = functionalEl.addDependencyTo(logicalEl);
        // No pre-existing "Allocate" stereotype was found live in this project (unlike Block/
        // proxyPort, which DID clash with a read-only profile-owned stereotype of the same name) —
        // applyStereotypeSafely still reuses one if a project happens to already define it, same
        // as everywhere else in this file.
        applyStereotypeSafely((IRPModelElement) dep, "Allocate", "Dependency");
        save();
    }

    @Override
    public synchronized void unlinkLogicalNode(String functionalNodeGuid, String logicalNodeGuid) {
        IRPModelElement functionalEl = findElement(functionalNodeGuid);
        IRPDependency dep = findAllocateDependency(functionalEl, logicalNodeGuid);
        if (dep != null) {
            functionalEl.deleteDependency(dep);
            save();
        }
    }

    /** The "Allocate" Dependency owned by functionalEl whose target is logicalNodeGuid, or null —
     * used to keep linkLogicalNode idempotent and to find what unlinkLogicalNode should delete. */
    private IRPDependency findAllocateDependency(IRPModelElement functionalEl, String logicalNodeGuid) {
        IRPCollection deps = functionalEl.getOwnedDependencies();
        for (int i = 1; i <= deps.getCount(); i++) {
            IRPDependency dep = (IRPDependency) deps.getItem(i);
            IRPModelElement target = dep.getDependsOn();
            if (target != null && logicalNodeGuid.equals(target.getGUID()) && hasStereotype((IRPModelElement) dep, "Allocate")) {
                return dep;
            }
        }
        return null;
    }

    // ── Logical→Physical allocation (same "Allocate" Dependency mechanism as Functional→Logical
    // above — requested live: "nun müssen noch die Logical Nodes mit PhysicalNodes auf gleiche
    // weise allokiert werden"; findAllocateDependency above is already generic enough to reuse
    // as-is here) ─────────────────────────────────────────────────────

    @Override
    public synchronized List<Object> getAllocatedPhysicalNodesOf(String logicalNodeGuid) {
        List<Object> out = new ArrayList<>();
        IRPModelElement el = findElement(logicalNodeGuid);
        IRPCollection deps = el.getOwnedDependencies();
        for (int i = 1; i <= deps.getCount(); i++) {
            IRPDependency dep = (IRPDependency) deps.getItem(i);
            if (!hasStereotype((IRPModelElement) dep, "Allocate")) continue;
            IRPModelElement target = dep.getDependsOn();
            if (target != null) out.add(elementRef(target, "PhysicalNode"));
        }
        return out;
    }

    @Override
    public synchronized void linkPhysicalNode(String logicalNodeGuid, String physicalNodeGuid) {
        IRPModelElement logicalEl = findElement(logicalNodeGuid);
        IRPModelElement physicalEl = findElement(physicalNodeGuid);
        if (findAllocateDependency(logicalEl, physicalNodeGuid) != null) return;
        IRPDependency dep = logicalEl.addDependencyTo(physicalEl);
        applyStereotypeSafely((IRPModelElement) dep, "Allocate", "Dependency");
        save();
    }

    @Override
    public synchronized void unlinkPhysicalNode(String logicalNodeGuid, String physicalNodeGuid) {
        IRPModelElement logicalEl = findElement(logicalNodeGuid);
        IRPDependency dep = findAllocateDependency(logicalEl, physicalNodeGuid);
        if (dep != null) {
            logicalEl.deleteDependency(dep);
            save();
        }
    }

    // ── Interfaces (ProxyPorts, one of 4 views, nestable for decomposition) ─────

    @Override
    public synchronized List<Object> getPorts(String classifierGuid) {
        IRPModelElement el = findElement(classifierGuid);
        if (!(el instanceof IRPClassifier)) {
            throw new IllegalArgumentException("Element is not a classifier (has no ports): " + classifierGuid);
        }
        return portsOf((IRPClassifier) el, new HashSet<>());
    }

    /** Creates a ProxyPort under ownerGuid — a Block/Actor (a top-level interface) or an existing
     * port (a decomposition of that port, redirected to the port's interfaceBlock contract — see
     * class javadoc). direction: "In" | "Out" | "InOut", stored as a Tag. type: name of an
     * interfaceBlock to type the port with (find-or-created), or null. view: one of PORT_VIEWS,
     * or null. sourceGuid (see ModelStore's javadoc): if a port already carries a matching
     * SOURCE_GUID_TAG, this updates it in place instead of creating a duplicate. */
    @Override
    public synchronized Map<String, Object> createPort(String ownerGuid, String name, String direction, String type, String view, String sourceGuid) {
        requireNonEmpty(name, "name");
        // A port's own Name (and its auto-derived "ib"+name default contract CLASS name) rejects the
        // same characters as a Package (see sanitizePackageName's own javadoc) — found live: picking
        // a disambiguated qualified suggestion like "HEU1.Voice" (see frontend's qualifiedValue)
        // threw "Name 'ibHEU1.Voice' is illegal for element of type Class" the very first time any
        // port name ever contained a dot. Same DisplayName pattern as Capability names: the actual
        // Name (and everything derived from it) uses the sanitized form; the user's original text is
        // kept as the native DisplayName, applied AFTER creation so it never has to flow through
        // ECAD's vendored addOrGetPort (which derives the PORT's own name FROM the contract's name,
        // stripping "ib" — see ModelElementService#addOrGetPort — so the sanitized form has to be
        // what's actually stored everywhere, with DisplayName layered on top as a separate step).
        String sanitizedName = sanitizePackageName(name);
        if (sourceGuid != null) {
            IRPModelElement existing = findBySourceGuid(sourceGuid);
            if (existing != null) {
                existing.setName(sanitizedName);
                if (!sanitizedName.equals(name)) setDisplayName(existing, name);
                applyPortSpec(existing, direction, type, view);
                save();
                return portNode(existing, new HashSet<>());
            }
        }
        // ownerGuid may itself be a NESTED port's own composite guid ("immediateParent|nativeGuid" —
        // see portNode's own construction, and updatePort's matching split) when nesting a port under
        // an already-nested one (depth 2+) — e.g. "internal|internalPower". Found live: without this,
        // IRPProject#findElementByGUID silently resolves a pipe-joined string to just its FIRST
        // segment (confirmed live: passing "internal|internalPower" resolved to "internal" itself,
        // no error) — so a "+ Nested Port" submitted against internalPower's own row was silently
        // nesting the new port under "internal" instead, one level too shallow. "ich habe in
        // powerUnit unter internalPower ein nested port V12 angelegt, aber es ist kein child von
        // internalPower." Only the part AFTER the first "|" is ever the actual port being nested
        // under — a plain (non-composite) ownerGuid is unaffected (indexOf returns -1, substring
        // from 0 is a no-op).
        int ownerSep = ownerGuid.indexOf('|');
        if (ownerSep >= 0) ownerGuid = ownerGuid.substring(ownerSep + 1);
        IRPModelElement owner = findElement(ownerGuid);
        IRPModelElement created;
        // A port is always a DIRECT, natively-owned member of its owner classifier (Block/Actor, or
        // — for a nested/decomposed port — the parent port's own interfaceBlock contract) — never
        // redirected through a separate owner-tagged container. An earlier attempt at exactly that
        // redirection (thinking ECAD's own ModelElementService#addOrGetPort put the port INSIDE the
        // interfaceBlock it's given) turned out to misread that method: it creates the port ON
        // parentClass and only uses the interfaceBlock as the port's contract — same shape as here.
        if (owner instanceof IRPPort || owner instanceof IRPSysMLPort) {
            // MUST run before container resolution/creation below, not after — Rhapsody only
            // supports ONE level of nested ProxyPorts, so owner itself can't stay nested inside
            // "internal"/"external" (already one level) while also gaining its OWN nested child
            // (a second level) without exceeding that limit. An earlier version ran the equivalent
            // of this call AFTER creating the new child, which deleted `owner` itself (mid-creation
            // of that very child) via the migration's own delete+recreate — confirmed live: "ich
            // habe in powerUnit unter internalPower ein nested port V12 angelegt, aber es ist kein
            // child von internalPower" (the fallout of this exact ordering bug once the composite-
            // guid resolution above was fixed and owner started correctly resolving to a genuinely
            // nested port like internalPower for the first time). See the helper's own javadoc.
            owner = migrateOutOfDelegationCollectorIfNeeded(owner);
            IRPClassifier container = resolvePortContainer(owner);
            // A nested port under an ORDINARY shared top-level port (e.g. "Boardnet" under "Power",
            // reused verbatim by ComSuite/PowerUnit/CN/SN) needs TWO genuinely separate native
            // objects, not one — one for the sender role, one shared by however many receivers — so
            // Direction/Multiplicity/addLink each become real, independent native properties instead
            // of one shared value visible everywhere. Requested live: "wir brauchen nur für den
            // Sender ein eigenes Port. das heisst im interfaceblock power gibt es 2 ports für
            // Boardnet. ein sender und ein receiver." Both are named distinctly in Rhapsody itself
            // ("Boardnet_Out"/"Boardnet_In" — Rhapsody can't have two same-named ports on one
            // contract) but DisplayName keeps both showing as "Boardnet" in the frontend, same
            // mechanism as the "HEU1.Voice" dot-sanitization elsewhere in this method — "in rhapsody
            // ja aber nicht im frontend!" Which variant a given creation call resolves to is decided
            // purely by the caller's own requested direction (Out = sender, anything else = the one
            // shared receiver variant) — never guessed from existing data.
            //
            // The already-established "internal"/"external" collector pattern (isWithinExternalTree,
            // or owner literally being one of those two collector ports) is explicitly excluded —
            // those already have their OWN, separate, working two-object mechanism (a private
            // per-owner collector for "internal"/"external"; two genuinely different leaf objects,
            // child-side vs root-side, for an external tree) and don't need this on top.
            boolean roleSplit = !isWithinExternalTree(owner)
                    && !PORT_GROUP_EXTERNAL.equals(owner.getName()) && !PORT_GROUP_INTERNAL.equals(owner.getName());
            String roleSuffix = SENDER_DIRECTION.equals(direction) ? "_Out" : "_In";
            String technicalName = roleSplit ? sanitizedName + roleSuffix : sanitizedName;
            // Find-or-create by name, same reasoning as createArchitectureElement's fallback — an
            // interactively-created nested port is never Tag-stamped, so re-importing an XML
            // previously exported from this same already-populated project throws "Cannot add Port
            // ... There is a (name) clash with an existing Proxy Port ..." otherwise (found live).
            IRPModelElement existingByName = findPortByNameDirect(container, technicalName);
            if (existingByName != null) {
                created = existingByName;
            } else {
                created = container.addNewAggr(portMetaType, technicalName);
                applyStereotypeSafely(created, PROXY_PORT_STEREOTYPE, portMetaType);
                // Every port needs an interfaceBlock contract immediately, not just lazily once its
                // own first nested child is added (resolvePortContainer's fallback) — a leaf nested
                // port (no children of its own) would otherwise never get one at all. "External"
                // exactly when the port we're nesting UNDER (owner) is itself within an established
                // external tree (isWithinExternalTree) — a nested/decomposed port is never a tree-
                // root's OWN direct interface, but IS itself external when its parent is (or is
                // itself part of one) — see isWithinExternalTree's own javadoc: "HEU ist der
                // Container", JMessages/Voice (nested under it) are themselves the external
                // interfaces.
                //
                // The contract NAME is qualified by owner's own name ("ib"+owner+"_"+name) exactly
                // when nested-external — found live: "HEU.Voice" and "HEU1.Voice" were colliding onto
                // the SAME flat "ibVoice" class (same name, same kind-group, both external), silently
                // merging two conceptually different interfaces. Requested live, after first
                // confirming (then reversing) that they should share one contract: "dann brauchen wir
                // 2 ibVoice! es können ja unterschiedliche Informationen kommen!" — different external
                // containers' same-named nested interfaces must now resolve to genuinely SEPARATE
                // contracts, never merged just because the leaf name matches. Non-external nested
                // ports are UNAFFECTED (keep the flat "ib"+name convention) — collision risk there is
                // lower stakes and out of scope for this fix.
                boolean nestedExternal = isWithinExternalTree(owner);
                String contractName = nestedExternal ? "ib" + owner.getName() + "_" + sanitizedName : "ib" + sanitizedName;
                setContract((IRPModelElement) created, findOrCreateInterfaceBlock(contractName, view, nestedExternal, false));
            }
            if (!technicalName.equals(name)) setDisplayName(created, name);
            // Only THIS occurrence's own resolved variant (Out/In) should ever be visible under
            // owner's (e.g. ComSuite's own "Power") graph node — see roleSplit's own javadoc above.
            if (roleSplit) {
                revealOnlyThisNestedPortVariant(owner, (IRPPort) created);
                // Conjugate the RECEIVER variant immediately at creation time, not later when a
                // connector happens to be made — "beim anlegen von in kann das port gleich
                // conjugiert werden!" Idempotent (setting the same value twice is harmless), so this
                // is safe to run every time a "Boardnet_In"-style port is created OR re-resolved by
                // name. The sender variant is never conjugated (matches createConnectorIfAbsent's own
                // existing "only internal RECEIVERS get conjugated" rule).
                if (!SENDER_DIRECTION.equals(direction)) {
                    ((IRPPort) created).setIsReversed(1);
                }
            }
        } else if (owner instanceof IRPClass) {
            // Delegates to ECAD's own ModelElementService#addOrGetPort (vendored — see this class's
            // field javadoc above) rather than re-deriving the same call sequence ourselves — proven
            // to work against a project with the real SysML profile loaded, PROVIDED the owner
            // itself already carries the SysML "Block" stereotype (see ensureBlockStereotype in
            // createArchitectureElement — a plain Class isn't enough to host a Port there, which was
            // the actual root cause behind "Can't add aggregate of type Port..." — see CLAUDE.md bug
            // notes for everything else that was ruled out first). addOrGetPort already does its own
            // find-or-get-by-name internally, so this branch needs no extra guard. "External" (see
            // findOrCreateInterfaceBlock's own javadoc) when owner ITSELF is a tree root
            // (Flexis/System_F/System_L/System_P — a direct child of a view package, not nested
            // under another Block) — requested live: "externe interfaces von System_F... dürfen
            // überall wiederverwendet werden." System_P's own root ports count as external too —
            // "System_P sind auch externe Schnittstellen, aber nur physikalische!" — the KIND-GROUP
            // separation (physical external interfaces never merge with logical ones) is enforced
            // inside findOrCreateInterfaceBlock/findInterfaceBlockAcrossAllViews instead of by
            // suppressing `external` for Physical altogether.
            IRPClass ownerClass = (IRPClass) owner;
            boolean rootOwner = isRootLevelClass(ownerClass);
            IRPClass ib = findOrCreateInterfaceBlock("ib" + sanitizedName, view, rootOwner, true);
            // A root element's own boundary ports stay flat (see PORT_GROUP_EXTERNAL's own javadoc)
            // — classification only applies to a NESTED element's top-level ports.
            //
            // A reused interface that ALREADY carries its own nested decomposition (ib already has
            // ports, e.g. picking "MissileLink" — which elsewhere already has Uplink/DownLink — or
            // "HMI" — which already has Display) must NEVER be routed through the internal/external
            // delegation collector either, regardless of rootOwner — Rhapsody only supports ONE level
            // of nested ProxyPorts, and portGroupContainer's own contract (ibinternal_<child>/
            // ibexternal_<child>) is ALREADY one level of nesting relative to the child itself;
            // nesting a port THERE that itself owns further nested ports would push those to a
            // second level, which Rhapsody doesn't support. Such an interface is instead created as
            // its own standalone, PARALLEL top-level ProxyPort directly on the child, same as a root
            // element's own boundary ports — confirmed live: "wenn in child ein Top level interface
            // angelegt wird, dann muss es aus ibinternal_childe entfernt werden und als eigenes
            // Proxport paralell dazu angelegt werden. Rhapsody unterstützt nur eine ebene nested
            // proxports." A brand-new/still-leaf interface (ib has zero ports yet) is unaffected —
            // it still safely lands one level deep in ibinternal_<child>, exactly as before.
            boolean hasOwnDecomposition = ib.getPorts().getCount() > 0;
            String group = (rootOwner || hasOwnDecomposition) ? null : classifyDelegationGroup(ownerClass, ib);
            if (group != null) {
                IRPPort containerPort = portGroupContainer(ownerClass, group);
                IRPClass containerContract = (IRPClass) containerPort.getContract();
                IRPModelElement existingByName = findPortByNameDirect(containerContract, sanitizedName);
                created = existingByName != null ? existingByName
                        : modelElementService.addOrGetPort(containerContract, ib, stereotypeService);
                if (!sanitizedName.equals(name)) setDisplayName(created, name);
                syncDelegationConnector(ownerClass, containerPort, (IRPPort) created, ib, PORT_GROUP_EXTERNAL.equals(group));
            } else {
                created = modelElementService.addOrGetPort(ownerClass, ib, stereotypeService);
                if (!sanitizedName.equals(name)) setDisplayName(created, name);
            }
        } else if (owner instanceof IRPClassifier) {
            // Actor — IRPActor doesn't extend IRPClass (only IRPClassifier), so it can't be passed
            // to ECAD's IRPClass-typed addOrGetPort directly (which does its own find-or-get). Same
            // sequence, generalized, with the same explicit find-or-create-by-name guard as the
            // nested-port branch above (unlike addOrGetPort, this hand-rolled version has none of
            // its own). Never "external" — the "root element" exception is specifically about
            // Flexis/System_F/System_L/System_P, not Actors (confirmed live when this was designed).
            IRPModelElement existingByName = findPortByNameDirect((IRPClassifier) owner, sanitizedName);
            if (existingByName != null) {
                created = existingByName;
            } else {
                IRPClass ib = findOrCreateInterfaceBlock("ib" + sanitizedName, view, false, true);
                IRPPort port = (IRPPort) ((IRPClassifier) owner).addNewAggr(portMetaType, sanitizedName);
                port.addSpecificStereotype(stereotypeService.getProxyPortStereotype());
                port.setContract(ib);
                created = port;
            }
            if (!sanitizedName.equals(name)) setDisplayName(created, name);
        } else {
            throw new IllegalArgumentException("Element cannot own ports: " + ownerGuid);
        }
        applyPortSpec(created, direction, type, view);
        stampSourceGuid(created, sourceGuid);
        // A newly (or re-)created port needs to actually be DRAWN, not just exist in the model —
        // see refreshPortVisibility's own javadoc. Only meaningful for a top-level port on a Block
        // that already has an IBD (from addAggregationPart) — a read-only find (getIBD), never
        // creates one as a side effect of adding a port. Actors and nested/decomposed ports (whose
        // "owner" is another port, redirected onto an ad-hoc interfaceBlock with no IBD of its own)
        // have no such diagram to refresh here.
        if (owner instanceof IRPClass) {
            refreshPortVisibility(diagramService.getIBD((IRPClass) owner));
        }
        save();
        // A nested port's own creation response uses the same composite-guid/override-aware shape as
        // every subsequent read (see portNode's 3-arg overload) — owner IS the immediate parent here,
        // since created was just added to owner's own contract. A top-level port (owner is a Block/
        // Actor, not a Port) has no such parent.
        boolean nested = owner instanceof IRPPort || owner instanceof IRPSysMLPort;
        return portNode(created, new HashSet<>(), nested ? owner : null);
    }

    /** Applies stereotypeName (metaType-scoped) to el. If a stereotype by that exact name already
     * exists ANYWHERE in the project — e.g. provided by a real profile like SysML, which commonly
     * defines "proxyPort" itself — that existing definition is reused via addSpecificStereotype
     * instead of ensuring/creating one via the string-based addStereotype(name, metaType). Matches
     * ECAD's own StereotypeService#applyStereotype pattern (find via findNestedElementRecursive,
     * apply via addSpecificStereotype). Found live: addStereotype(name, metaType) with a name that
     * collides with an existing, profile-owned (read-only) stereotype of the same name fails —
     * "Can't add aggregate of type Port. Cannot modify read only element ... SysMLProfile_rpy\
     * SysML.sbs." — apparently because it tries to ensure/redefine that stereotype's own
     * definition rather than just referencing it. Falls back to the ad-hoc addStereotype(name,
     * metaType) when no existing one is found (a profile-free project — see this class's own
     * "no profile required" note in backend/CLAUDE.md's Ports section). */
    private void applyStereotypeSafely(IRPModelElement el, String stereotypeName, String metaType) {
        IRPModelElement existing = activeProject().findNestedElementRecursive(stereotypeName, "Stereotype");
        if (existing instanceof IRPStereotype) {
            el.addSpecificStereotype((IRPStereotype) existing);
        } else {
            el.addStereotype(stereotypeName, metaType);
        }
    }

    /** Ensures el (an architecture element's IRPClass) carries the SysML "Block" stereotype, in
     * addition to this app's own level stereotype (System/Subsystem/...) — confirmed live: a plain
     * Class isn't enough to host a Port in a project where the real SysML profile is loaded, no
     * matter which container or stereotype-application method was tried (see CLAUDE.md bug notes
     * for everything already ruled out before this was found to be the actual missing piece).
     * Matches ECAD's own StereotypeService#getBlockStereotype() + addSpecificStereotype pattern —
     * a stereotype pre-loaded once via loadStandardStereotypes() in the constructor, applied
     * directly rather than looked up by name every time. Idempotent, so it's safe to call both on
     * fresh creation and when re-matching an existing element by sourceGuid (a re-export of a model
     * exported before this fix existed won't have it yet). */
    private void ensureBlockStereotype(IRPModelElement el) {
        if (hasStereotype(el, "Block")) return;
        IRPStereotype block = stereotypeService.getBlockStereotype();
        if (block != null) {
            el.addSpecificStereotype(block);
        } else {
            applyStereotypeSafely(el, "Block", levelMetaType);
        }
    }

    /** A composite guid ("<immediateParent>|<nativeChild>", see portNode's own javadoc) addresses one
     * specific occurrence of a shared nested port — e.g. Boardnet as seen through ComSuite's own
     * Power, distinct from the same Boardnet as seen through PowerUnit's. direction there is NOT
     * written to the shared child object at all (that would still couple every occurrence, exactly
     * the behavior this scheme exists to avoid) — it's stamped as a per-occurrence override tag on
     * the immediate PARENT (see NESTED_DIRECTION_OVERRIDE_TAG_PREFIX), which — being a genuine,
     * separate native object per Block — already has independent identity today. type/view are
     * unaffected by this split: they stay fully shared/Unikat, applied to the underlying child object
     * directly, same as ever — only direction is a per-occurrence concept here. A plain (non-
     * composite) guid — a genuine top-level port, or a nested one not yet addressed this way — keeps
     * the original, direct behavior unchanged. */
    @Override
    public synchronized Map<String, Object> updatePort(String portGuid, String direction, String type, String view) {
        int sep = portGuid.indexOf('|');
        if (sep < 0) {
            IRPModelElement el = findElement(portGuid);
            applyPortSpec(el, direction, type, view);
            save();
            return portNode(el, new HashSet<>());
        }
        IRPModelElement parentEl = findElement(portGuid.substring(0, sep));
        IRPModelElement childEl = findElement(portGuid.substring(sep + 1));
        if (direction != null && !direction.isEmpty()) {
            String childName = childEl.getDisplayName();
            if (childName == null || childName.isEmpty()) childName = childEl.getName();
            stampTagValue(parentEl, NESTED_DIRECTION_OVERRIDE_TAG_PREFIX + childName, direction);
        }
        applyPortSpec(childEl, null, type, view);
        save();
        return portNode(childEl, new HashSet<>(), parentEl);
    }

    /** For an EXISTING port as owner (nested/decomposed port creation only — see createPort, which
     * handles a Block/Actor owner separately): the new port belongs to owner's own plain
     * interfaceBlock contract (auto-creating one if owner doesn't have one yet). This is what makes
     * "nested ports" work: they aren't children of the port element, they're ports on a container
     * associated with it (see class javadoc).
     *
     * History: a private-per-instance-decomposition-container variant of this was tried (and
     * reverted once already, then briefly re-applied) to give a reused top-level port's own nested
     * decomposition (e.g. "Boardnet", shared by ComSuite/PowerUnit/CN/SN's own "Power") an
     * independent, per-element-persisted direction. Torn out for good per explicit live feedback —
     * "schmeisse die ganze powerDecomp logic raus das ist mist!" — back to a single shared contract,
     * same as every other nested port. */
    private IRPClassifier resolvePortContainer(IRPModelElement owner) {
        IRPClassifier contract = getContract(owner);
        if (contract instanceof IRPClass) {
            return contract;
        }
        // "External" exactly when owner itself is within an established external tree (see
        // isWithinExternalTree/createPort's nested-port branch for the same reasoning) — this
        // interfaceBlock is owner's OWN decomposition container, so it should inherit owner's own
        // externality for consistency, even though its synthetic name (ownerName+"Interface") makes
        // an actual cross-element name collision unlikely in practice.
        IRPClass ib = findOrCreateInterfaceBlock(owner.getName() + "Interface", viewOf(owner), isWithinExternalTree(owner), false);
        setContract(owner, ib);
        return ib;
    }

    /** Before owner is about to gain a nested child — possibly its own FIRST one, making it a
     * decomposition container for the first time — checks whether owner currently sits directly
     * inside a private "ibinternal_<cls>"/"ibexternal_<cls>" delegation collector (see
     * portGroupContainer) and, if so, migrates it OUT to become its own standalone, parallel
     * top-level ProxyPort BEFORE the new child is created. findOrCreateInterfaceBlock's own
     * "hasOwnDecomposition" check (see its javadoc) only ever runs at a TOP-LEVEL port's own
     * CREATION time — it can't catch a port that was legitimately still a leaf back then (so
     * correctly landed inside "internal"/"external") and only gains its first nested child LATER,
     * via an ordinary "+Nested Port" — requested live after that exact gap: "ich habe in logical
     * view eine ComSuite angelegt. darin habe ich ein PowerPort (toplevel) mit einem Unterport
     * 'Boardnet' angelegt. der proxyport ist aber nicht aus ibinternal_ComSuite verschoben worden.
     * das sollte doch automatisch geschen wenn ich ein netedProxypoer anlege!"
     * <p>
     * MUST be called BEFORE the new nested child is created, and the caller MUST continue using
     * this method's own RETURN VALUE as the owner from then on — an earlier version ran the
     * equivalent of this check AFTER the child was already created, on the theory that "owner's own
     * decomposition contract, which the just-created child is already a member of, is never
     * touched, only the wrapper port object, so the decomposition survives under owner's new GUID."
     * That was wrong in practice: Rhapsody only supports ONE level of nested ProxyPorts, so a port
     * that's ALREADY one level deep (inside "internal"/"external") can't validly host its OWN
     * nested child (a second level) without first being promoted out — creating the child before
     * promoting made the create-then-migrate sequence delete `owner` (the child's own new native
     * parent) while the child was still transiently attached to it, corrupting the whole operation
     * (confirmed live: "Rhapsody object deleted" — see createPort's own call site for the exact
     * report: "ich habe in powerUnit unter internalPower ein nested port V12 angelegt, aber es ist
     * kein child von internalPower").
     * <p>
     * Same delete+recreate migration as the earlier MissileLink/HMI/Plans cleanup (Rhapsody has no
     * port "move"). Idempotent/safe to call unconditionally on every nested-port creation (including
     * one that just reused an EXISTING nested port by name) — a no-op (returns owner unchanged) once
     * owner is already outside any delegation collector, and self-healing for any legacy port left
     * over from before this fix existed. */
    private IRPModelElement migrateOutOfDelegationCollectorIfNeeded(IRPModelElement owner) {
        IRPModelElement collectorContract = owner.getOwner();
        if (!(collectorContract instanceof IRPClass) || !hasStereotype(collectorContract, INTERFACE_BLOCK_STEREOTYPE)) return owner;
        String contractName = collectorContract.getName();
        boolean isDelegationCollector = contractName.startsWith("ib" + PORT_GROUP_INTERNAL + "_")
                || contractName.startsWith("ib" + PORT_GROUP_EXTERNAL + "_");
        if (!isDelegationCollector) return owner;
        IRPModelElement realOwner = collectorContract.getOwner();
        if (!(realOwner instanceof IRPClass)) return owner;
        String displayName = owner.getDisplayName();
        String ownName = displayName != null && !displayName.isEmpty() ? displayName : owner.getName();
        String direction = tagValue(owner, DIRECTION_TAG);
        String view = viewOf(owner);
        owner.deleteFromProject();
        Map<String, Object> recreated = createPort(realOwner.getGUID(), ownName, direction, "", view, null);
        return findElement((String) recreated.get("guid"));
    }

    /** Whether a brand-new top-level port on ownerClass (NOT itself a tree root — see createPort's
     * caller), typed with interfaceBlock ib, needs routing through PORT_GROUP_EXTERNAL or
     * PORT_GROUP_INTERNAL — see those constants' own javadoc for the underlying rule. NEVER returns
     * null (stay flat) for a non-root owner — every new top-level interface on a child element
     * always lands in its own grouping collector, from the very first creation, not only once a
     * second reuse elsewhere makes the sharing explicit: "neue interfaces im child sind immer in
     * ibinternal_child anzulegen!" (found live: creating "MissileLink" fresh on GroundStation — a
     * child of Flexis — stayed a flat direct member instead of landing in ibinternal_GroundStation,
     * since nothing else used that interface yet). An earlier version returned null specifically for
     * that "not used anywhere else yet" case, as a documented (now-fixed) known gap.
     *   1. ib already carries EXTERNAL_INTERFACE_STEREOTYPE — its identity traces back to a tree
     *      ROOT's own boundary port (e.g. System_F.HEU), so this is a delegation candidate.
     *   2. Otherwise PORT_GROUP_INTERNAL unconditionally — whether or not ib is already used
     *      elsewhere in the project, so a brand-new, still-private interface (e.g. intern1, before
     *      any second part ever reuses it) still gets its own ibinternal_<child> container right
     *      away instead of waiting for a second use to trigger it. */
    private String classifyDelegationGroup(IRPClass ownerClass, IRPClass ib) {
        if (hasStereotype(ib, EXTERNAL_INTERFACE_STEREOTYPE)) return PORT_GROUP_EXTERNAL;
        return PORT_GROUP_INTERNAL;
    }

    /** Find-or-create the "external"/"internal" collector port directly on cls — see
     * PORT_GROUP_EXTERNAL/_INTERNAL's own javadoc. Unlike a normal interface's contract (shared
     * project-wide by name via findOrCreateInterfaceBlock), this container's own contract is a
     * PRIVATE nested class owned directly by cls itself (cls.addClass("ib"+group)) — confirmed live
     * against the user's hand-built reference: PerformMission's and Planning's own "ibinternal"
     * classes are two DIFFERENT objects that merely happen to share the same literal Name, not one
     * shared contract — going through the normal shared-lookup mechanism here would have wrongly
     * merged every element's own internal/external grouping into one. The container port itself
     * carries no direction/view (matches the reference: both read back None/None) — it's purely
     * organizational, never itself a typed interface. */
    private IRPPort portGroupContainer(IRPClass cls, String group) {
        IRPModelElement existing = findPortByNameDirect(cls, group);
        if (existing instanceof IRPPort) return (IRPPort) existing;
        IRPPort created = (IRPPort) cls.addNewAggr(portMetaType, group);
        applyStereotypeSafely((IRPModelElement) created, PROXY_PORT_STEREOTYPE, portMetaType);
        // Qualified by cls's own name ("ibexternal_Planning", not bare "ibexternal") — found live:
        // a bare "ib"+group name did NOT stay private to cls the way cls.addClass(...) was assumed
        // to guarantee (two DIFFERENT elements' own "external" containers ended up sharing the
        // EXACT SAME contract object, owned by the view package rather than by either element —
        // "wenn ich in Planning ein externes Port hinzufüge wird das auch bei New hinzugefügt!").
        // Root cause not fully isolated; sidestepped the same way "ibHEU_Voice"/"ibHEU1_Voice"
        // already sidesteps an analogous collision elsewhere in this file — a per-owner-unique NAME
        // can never accidentally merge, regardless of why the collision happened.
        IRPClass ib = cls.addClass("ib" + group + "_" + cls.getName());
        applyStereotypeSafely(ib, INTERFACE_BLOCK_STEREOTYPE, levelMetaType);
        setContract((IRPModelElement) created, ib);
        return created;
    }

    /** After newPort has been created/found under containerPort (ownerClass's own "external"/
     * "internal" collector, typed with ib), finds and wires up the matching connector(s) elsewhere
     * in the project — see PORT_GROUP_EXTERNAL/_INTERNAL's javadoc for the delegation-vs-internal
     * distinction. Mirrors the exact addLink shape recovered from the user's hand-built reference
     * (see CLAUDE.md): fromPart/toPart are always the two COLLECTOR/boundary ports (never the leaf
     * interface itself), fromPort/toPort are the actual nested signal ports underneath them. Errors
     * are logged, not thrown — a connector is a diagram/model nicety layered on top of already-
     * successful port creation, so a failure here must never fail the port creation itself. */
    private void syncDelegationConnector(IRPClass ownerClass, IRPPort containerPort, IRPPort newPort, IRPClass ib, boolean external) {
        // "internal" no longer auto-creates a connector here at all — it now follows the stricter
        // sender/receiver broadcast validation (exactly one sender, at least one receiver — see
        // PORT_GROUP_INTERNAL's own extended requirement and collectInternalBroadcastPending),
        // which needs to see every port sharing the contract at once and can fail with a warning;
        // neither fits a silent auto-create at port-creation time. The pending-connectors panel is
        // now the only path for internal connectors, giving the user visibility into that warning.
        if (!external) return;
        try {
            for (ConnectorCandidate c : findConnectorCandidates(ownerClass, containerPort, newPort, ib, external)) {
                createConnectorIfAbsent(c.linkOwner, c.fromOwnerClass, c.toOwnerClass, c.fromPart, c.toPart, c.fromPort, c.toPort, true);
            }
        } catch (Exception ex) {
            System.err.println("[RhapsodyModelStore] connector sync failed: " + ex.getMessage());
        }
    }

    /** One potential (or already-existing — caller checks) connector between newPort (owned by
     * ownerClass, nested under containerPort) and some counterpart elsewhere sharing contract ib —
     * see PORT_GROUP_EXTERNAL/_INTERNAL's javadoc for what "counterpart" means in each case.
     * fromOwnerClass/toOwnerClass may be null (the container's own boundary port side of a
     * delegation connector — see setLinkContextTags's own javadoc for why that's a valid, expected
     * null, not a bug). */
    private static final class ConnectorCandidate {
        final IRPClass linkOwner;
        final IRPModelElement fromOwnerClass;
        final IRPModelElement toOwnerClass;
        final IRPPort fromPart;
        final IRPPort toPart;
        final IRPPort fromPort;
        final IRPPort toPort;

        ConnectorCandidate(IRPClass linkOwner, IRPModelElement fromOwnerClass, IRPModelElement toOwnerClass,
                IRPPort fromPart, IRPPort toPart, IRPPort fromPort, IRPPort toPort) {
            this.linkOwner = linkOwner;
            this.fromOwnerClass = fromOwnerClass;
            this.toOwnerClass = toOwnerClass;
            this.fromPart = fromPart;
            this.toPart = toPart;
            this.fromPort = fromPort;
            this.toPort = toPort;
        }
    }

    /** Shared by syncDelegationConnector (auto-create at port-creation time) and
     * getPendingConnectors (a read-only project-wide scan — see its own javadoc for why that's
     * needed: this matching only ever runs automatically for a brand-new TOP-LEVEL port, never for
     * a port added directly under an already-existing "external"/"internal" container, which has no
     * trigger point of its own). Does not check connectorExists itself — callers decide what to do
     * with an already-satisfied candidate (skip it, in both current callers). */
    private List<ConnectorCandidate> findConnectorCandidates(IRPClass ownerClass, IRPPort containerPort, IRPPort newPort, IRPClass ib, boolean external) {
        List<ConnectorCandidate> result = new ArrayList<>();
        List<IRPModelElement> matches = new ArrayList<>();
        collectPortsByContract(activeProject(), ib, matches);
        String newGuid = ((IRPModelElement) newPort).getGUID();
        for (IRPModelElement candidate : matches) {
            if (newGuid.equals(candidate.getGUID())) continue;
            if (external) {
                IRPClass root = topLevelAncestor(ownerClass);
                IRPModelElement candidateContract = candidate.getOwner();
                if (!(candidateContract instanceof IRPClassifier)) continue;
                IRPPort rootPort = findPortByContract(root, (IRPClassifier) candidateContract);
                if (rootPort == null) continue;
                // Child-first (fromPart/fromPort = this specific child's own external container and
                // its nested leaf; toPart/toPort = root's own boundary port and its nested leaf) —
                // the confirmed-working order recovered from the user's own hand-built reference.
                // A parent-first attempt ("links starten immer am parent port") was tried and
                // reverted live — reusing the SAME root leaf port (e.g. HEU.Voice) as fromPort
                // across multiple links broke both diagram-edge drawing and connectorExists
                // detection for the later ones (only the first survived; the rest silently failed
                // to persist). Root cause not fully isolated — reverted rather than risk more data
                // loss chasing it further; see CLAUDE.md for the open "hierarchie/swap" question.
                result.add(new ConnectorCandidate(root, ownerClass, root, containerPort, rootPort, newPort, (IRPPort) candidate));
            } else {
                IRPModelElement candidateContract = candidate.getOwner();
                if (!(candidateContract instanceof IRPClass) || !hasStereotype(candidateContract, INTERFACE_BLOCK_STEREOTYPE)) continue;
                IRPModelElement siblingClass = candidateContract.getOwner();
                if (!(siblingClass instanceof IRPClass)) continue;
                IRPModelElement parent = ownerClass.getOwner();
                IRPModelElement siblingParent = ((IRPClass) siblingClass).getOwner();
                if (!(parent instanceof IRPClass) || siblingParent == null || !parent.getGUID().equals(siblingParent.getGUID())) continue;
                IRPModelElement siblingContainerPort = findPortByNameDirect((IRPClass) siblingClass, PORT_GROUP_INTERNAL);
                if (!(siblingContainerPort instanceof IRPPort)) continue;
                result.add(new ConnectorCandidate((IRPClass) parent, ownerClass, siblingClass, containerPort, (IRPPort) siblingContainerPort, newPort, (IRPPort) candidate));
            }
        }
        return result;
    }

    /** Read-only, project-wide scan for every connector that classifyDelegationGroup/
     * findConnectorCandidates says SHOULD exist (a port nested under an "external"/"internal"
     * collector whose contract is shared with a valid counterpart) but doesn't yet — see
     * PORT_GROUP_EXTERNAL/_INTERNAL and findConnectorCandidates' own javadoc. Needed because the
     * automatic path (syncDelegationConnector) only ever fires at the moment a brand-new TOP-LEVEL
     * port gets auto-classified — a port added directly under an already-existing container (the
     * normal way to add a SECOND/THIRD reuse of an interface once the container already exists, see
     * PORT_GROUP_EXTERNAL's own "known gap" javadoc) has no trigger of its own. Requested live,
     * after exactly that gap was hit while debugging: "am besten bauen wir einen schalter auf der
     * GUI ein mit dem ich das erzeugen der links forcieren kann... eine view in dem ich alle zu
     * erzeugenden links sehe". Each entry carries enough GUIDs for createPendingConnector to
     * recreate the exact same candidate without re-deriving it — deliberately not a stored/IDed
     * concept (no caching, no staleness risk): a fresh scan runs on every GET. */
    @Override
    public synchronized List<Object> getPendingConnectors() {
        List<Object> out = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        collectPendingConnectors(activeProject(), out, seenPairs);
        collectInternalBroadcastPending(out, seenPairs);
        collectContextViewPending(out, seenPairs);
        return out;
    }

    /** New "Connectors" tab table — requested live: "im frontend brauchen wir... eine tabelle
     * aller connector[en]... View Name, fromPort.Owner, fromPort.Name, toPort.Owner, toPort.Name.
     * bitte den vollen End1Path und End2Path der ports verwenden." Existing connectors are read
     * from each IRPLink's own End1Path/End2Path context tags (see setLinkContextTags — already
     * stamped at connector-creation time); pending ones reuse getPendingConnectors' own candidates,
     * since those have no link/tags yet. Both use the raw, unresolved path text (no attempt to
     * prettify back to a DisplayName or strip Rhapsody's own "[n]" disambiguation suffix) — "den
     * vollen [Pfad]" was explicit. */
    @Override
    public synchronized List<Map<String, Object>> getConnectorTable() {
        List<Map<String, Object>> out = new ArrayList<>();
        collectExistingConnectorRows(activeProject(), out);
        for (Object pendingObj : getPendingConnectors()) {
            if (!(pendingObj instanceof Map)) continue; // skip {"warning": "..."} entries
            Map<?, ?> pending = (Map<?, ?>) pendingObj;
            if (pending.get("fromPortGuid") == null || pending.get("toPortGuid") == null) continue;
            out.add(pendingConnectorRow(pending));
        }
        return out;
    }

    private void collectExistingConnectorRows(IRPPackage pkg, List<Map<String, Object>> out) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            collectExistingConnectorRowsInClass((IRPClass) classes.getItem(i), out);
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectExistingConnectorRows((IRPPackage) nestedPkgs.getItem(i), out);
        }
    }

    private void collectExistingConnectorRowsInClass(IRPClass cls, List<Map<String, Object>> out) {
        if (hasStereotype(cls, INTERFACE_BLOCK_STEREOTYPE) || hasStereotype(cls, FUNCTION_STEREOTYPE)) return;
        IRPCollection links = cls.getLinks();
        if (links.getCount() > 0) {
            String view = connectorViewLabel(cls);
            for (int i = 1; i <= links.getCount(); i++) {
                IRPLink link = (IRPLink) links.getItem(i);
                if (!hasStereotype((IRPModelElement) link, "connector")) continue;
                Map<String, Object> row = existingConnectorRow(link, view);
                if (row != null) out.add(row);
            }
        }
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass) collectExistingConnectorRowsInClass((IRPClass) item, out);
        }
    }

    /** The "View Name" column — found live to NOT be the port-level view stereotype
     * (viewOf(fromPort)): a Functional-tree element's own external interfaces are legitimately
     * REUSED from Operational (see the "Interfaces are scoped per view" section — "Functional gets
     * BOTH Operational and Functional"), so viewOf(fromPort) reported "Operational" for a connector
     * that's actually owned by System_F, in the Functional tree — "System_F gibt es nur in der
     * funktionalen View." Instead: walk up from the class that OWNS the link (cls) until either a
     * Context View class is hit (its own NAME is the label, e.g. "Termal" — "die view heist
     * Termal!") or one of the four real view packages (Operational/Functional/Logical/Physical,
     * see viewPackage) is hit. cls itself may already BE the Context View class (Termal owns its
     * own connector directly) or a tree root (System_F/System_L/...) — the walk starts there, not
     * strictly at the parent, so either case resolves on the first matching step. */
    private String connectorViewLabel(IRPClass cls) {
        if (hasStereotype(cls, CONTEXT_VIEW_STEREOTYPE)) return contextViewDisplayName(cls);
        IRPModelElement cur = (IRPModelElement) cls;
        while (cur != null) {
            if (cur instanceof IRPClass && cur != cls && hasStereotype(cur, CONTEXT_VIEW_STEREOTYPE)) return contextViewDisplayName((IRPClass) cur);
            if (cur instanceof IRPPackage && PORT_VIEWS.contains(cur.getName())) return cur.getName();
            cur = cur.getOwner();
        }
        return null;
    }

    /** A Context View's real Name is sanitized (see createContextView/sanitizePackageName) —
     * DisplayName carries the user's original text ("Ground Control Context", not
     * "Ground_Control_Context"), same convention as elementRef. Requested live: "beim Context
     * bitte kontext_name ausgeben" — cls.getName() alone would silently show the sanitized form
     * for any context name containing spaces/punctuation. */
    private String contextViewDisplayName(IRPClass cls) {
        String displayName = ((IRPModelElement) cls).getDisplayName();
        return displayName != null && !displayName.isEmpty() ? displayName : cls.getName();
    }

    /** Reads Owner/Name straight off the model (link.getFromPort()/getToPort()/getFrom()/getTo())
     * instead of the End1Path/End2Path context tags — found live those tags are unreliable: EMPTY
     * for a Context View connector (setLinkContextTags assumes a "container port" shape that a
     * Composition-part instance like "itsTarget" doesn't have, so the write silently fails), and for
     * everything else, the raw path text includes intermediate container ports (e.g. "Power") that
     * aren't a meaningful "Owner" at all — see resolveEndpointOwnerName's own javadoc. view is
     * passed in (computed once per owning class by the caller, see connectorViewLabel) rather than
     * derived per-link here. */
    private Map<String, Object> existingConnectorRow(IRPLink link, String view) {
        IRPPort fromPort = link.getFromPort();
        IRPPort toPort = link.getToPort();
        if (fromPort == null || toPort == null) return null;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("view", view);
        row.put("fromOwner", resolveEndpointOwnerName((IRPModelElement) link.getFrom()));
        row.put("fromName", fromPort.getName());
        row.put("toOwner", resolveEndpointOwnerName((IRPModelElement) link.getTo()));
        row.put("toName", toPort.getName());
        return row;
    }

    /** Resolves one connector end's "Owner" column value by walking up from the link's own raw
     * endpoint reference (part — link.getFrom()/getTo()) rather than trusting a tag string. Three
     * cases, requested live ("HEU ist kein Owner -> Flexis ist der Owner, ausser es ist ein
     * actor"):
     *   - part is a genuine INSTANCE, not a port (Context View pattern — e.g. "itsTarget", an
     *     Actor's own composition-part instance, or "itsFlexis", the system-of-interest's) — that
     *     instance IS a real owning entity in its own right, used as-is.
     *   - part is a PORT whose native owner is a ROOT-LEVEL class (e.g. "HEU", a top-level boundary
     *     port owned directly by "Flexis" — never anyone's own "part", so no its-instance form
     *     exists for it) — the owner is the ROOT CLASS's own bare name ("Flexis"), not the port's.
     *   - part is a PORT whose native owner is a NESTED class (a genuine part inside its own
     *     parent, e.g. "Power"/"external" owned by "ComSuite") — the owner is "its" + that class's
     *     name, matching the instance-naming convention every other row already uses. */
    private String resolveEndpointOwnerName(IRPModelElement part) {
        if (part == null) return "";
        if (!(part instanceof IRPPort)) {
            return part.getName();
        }
        IRPModelElement nativeOwner = part.getOwner();
        if (!(nativeOwner instanceof IRPClass)) return part.getName();
        if (isRootLevelClass((IRPClass) nativeOwner)) {
            return nativeOwner.getName();
        }
        return itsInstanceName(nativeOwner.getName());
    }

    /** Pending-connector equivalent of existingConnectorRow — no link exists yet, so Owner/Name are
     * derived from the already-resolved GUIDs getPendingConnectors' own entries carry. fromOwnerGuid/
     * toOwnerGuid always resolve to a CLASS here (never a port), so the same root-vs-nested rule as
     * resolveEndpointOwnerName applies directly, without needing the instance-vs-port branch. view
     * is resolved the same way existingConnectorRow's caller does (connectorViewLabel, walking up
     * from the class the connector WOULD be owned by, linkOwnerGuid) rather than from fromPortEl's
     * own view stereotype — same "System_F gibt es nur in der funktionalen View" bug otherwise. */
    private Map<String, Object> pendingConnectorRow(Map<?, ?> pending) {
        IRPModelElement linkOwnerEl = findElementOrNull((String) pending.get("linkOwnerGuid"));
        IRPModelElement fromOwnerEl = findElementOrNull((String) pending.get("fromOwnerGuid"));
        IRPModelElement toOwnerEl = findElementOrNull((String) pending.get("toOwnerGuid"));
        IRPModelElement fromPortEl = findElementOrNull((String) pending.get("fromPortGuid"));
        IRPModelElement toPortEl = findElementOrNull((String) pending.get("toPortGuid"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("view", linkOwnerEl instanceof IRPClass ? connectorViewLabel((IRPClass) linkOwnerEl) : null);
        row.put("fromOwner", pendingOwnerName(fromOwnerEl));
        row.put("fromName", fromPortEl != null ? fromPortEl.getName() : "");
        row.put("toOwner", pendingOwnerName(toOwnerEl));
        row.put("toName", toPortEl != null ? toPortEl.getName() : "");
        return row;
    }

    private String pendingOwnerName(IRPModelElement ownerEl) {
        if (ownerEl == null) return "";
        if (ownerEl instanceof IRPClass && isRootLevelClass((IRPClass) ownerEl)) return ownerEl.getName();
        return itsInstanceName(ownerEl.getName());
    }

    /** For every Context View: a connector should exist between the system-of-interest's own
     * top-level port and a linked Actor's own top-level port whenever the two share the same
     * resolved contract (the "Unikat" mechanism) — and ONLY at that top level, never walking into
     * either port's own nested decomposition. Requested live: "in Rhapsody sind context views ibds
     * in denen auch die schnittstellen verbunden werden. aber nur die high-level ohne nested
     * ports!" fromPart/toPart are the REAL Composition-part instances (itsFlexis/itsActorName —
     * see addContextViewPart), not a wrapper port the way internal/external delegation candidates
     * are — a Context View has no such wrapper, the ports being connected genuinely ARE top-level. */
    private void collectContextViewPending(List<Object> out, Set<String> seenPairs) {
        IRPClass soi = systemOfInterest();
        if (soi == null) return;
        IRPCollection views = kontextPackage().getClasses();
        for (int i = 1; i <= views.getCount(); i++) {
            IRPClass cv = (IRPClass) views.getItem(i);
            if (!hasStereotype(cv, CONTEXT_VIEW_STEREOTYPE)) continue;
            List<IRPClassifier> parts = contextViewParts(cv);
            IRPInstance soiInstance = modelElementService.getInstance(cv, itsInstanceName(soi.getName()));
            if (soiInstance == null) continue;
            for (IRPClassifier part : parts) {
                if (((IRPModelElement) part).getGUID().equals(((IRPModelElement) soi).getGUID())) continue;
                IRPInstance partInstance = modelElementService.getInstance(cv, itsInstanceName(((IRPModelElement) part).getName()));
                if (partInstance == null) continue;
                collectContextViewPortPairs(cv, soi, soiInstance, part, partInstance, out, seenPairs);
            }
        }
    }

    /** soi's and part's own DIRECT (top-level, non-recursive — see collectContextViewPending's own
     * "keine nested ports" requirement) ports, paired up by shared contract. */
    private void collectContextViewPortPairs(IRPClass cv, IRPClass soi, IRPInstance soiInstance,
            IRPClassifier part, IRPInstance partInstance, List<Object> out, Set<String> seenPairs) {
        IRPCollection soiPorts = soi.getPorts();
        IRPCollection partPorts = part.getPorts();
        for (int i = 1; i <= soiPorts.getCount(); i++) {
            IRPModelElement soiPort = (IRPModelElement) soiPorts.getItem(i);
            IRPClassifier soiContract = getContract(soiPort);
            if (soiContract == null) continue;
            String soiContractGuid = ((IRPModelElement) soiContract).getGUID();
            for (int j = 1; j <= partPorts.getCount(); j++) {
                IRPModelElement partPort = (IRPModelElement) partPorts.getItem(j);
                IRPClassifier partContract = getContract(partPort);
                if (partContract == null || !soiContractGuid.equals(((IRPModelElement) partContract).getGUID())) continue;

                String pairKey = soiPort.getGUID().compareTo(partPort.getGUID()) <= 0
                        ? soiPort.getGUID() + "|" + partPort.getGUID()
                        : partPort.getGUID() + "|" + soiPort.getGUID();
                if (!seenPairs.add(pairKey)) continue;
                IRPStructureDiagram ibd = diagramService.getIBD(cv);
                if (ibd != null && connectorExists(ibd, soiInstance, partInstance, (IRPPort) soiPort, (IRPPort) partPort)) continue;

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("linkOwnerGuid", ((IRPModelElement) cv).getGUID());
                entry.put("fromPartGuid", ((IRPModelElement) soiInstance).getGUID());
                entry.put("toPartGuid", ((IRPModelElement) partInstance).getGUID());
                entry.put("fromPortGuid", soiPort.getGUID());
                entry.put("toPortGuid", partPort.getGUID());
                entry.put("fromOwnerGuid", ((IRPModelElement) soi).getGUID());
                entry.put("toOwnerGuid", ((IRPModelElement) part).getGUID());
                entry.put("description", soi.getName() + "." + soiPort.getName() + " ↔ " + ((IRPModelElement) part).getName() + "." + partPort.getName()
                        + " [" + cv.getName() + "]");
                out.add(entry);
            }
        }
    }

    /** Every Composition part contextView owns (see addContextViewPart) — the system-of-interest
     * and every linked Actor, found by walking its own Composition relations (the reverse direction
     * of hasCompositionTo's own single-pair check). */
    private List<IRPClassifier> contextViewParts(IRPClass contextView) {
        List<IRPClassifier> out = new ArrayList<>();
        IRPCollection relations = contextView.getRelations();
        for (int i = 1; i <= relations.getCount(); i++) {
            IRPRelation rel = (IRPRelation) relations.getItem(i);
            IRPClassifier other = rel.getOtherClass();
            if (other != null) out.add(other);
        }
        return out;
    }

    private void collectPendingConnectors(IRPPackage pkg, List<Object> out, Set<String> seenPairs) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            collectPendingConnectorsInClass((IRPClass) classes.getItem(i), out, seenPairs);
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectPendingConnectors((IRPPackage) nestedPkgs.getItem(i), out, seenPairs);
        }
    }

    private void collectPendingConnectorsInClass(IRPClass cls, List<Object> out, Set<String> seenPairs) {
        if (hasStereotype(cls, INTERFACE_BLOCK_STEREOTYPE) || hasStereotype(cls, FUNCTION_STEREOTYPE)) return;
        if (!isRootLevelClass(cls)) {
            // Only "external" goes through this simple pairwise scan — "internal" now follows the
            // stricter sender/receiver broadcast shape instead (see collectInternalBroadcastPending
            // and PORT_GROUP_INTERNAL's own extended requirement), which needs to see every port
            // sharing a contract AT ONCE (to count senders/receivers), not just pairwise.
            for (String group : new String[] {PORT_GROUP_EXTERNAL}) {
                IRPModelElement containerEl = findPortByNameDirect(cls, group);
                if (!(containerEl instanceof IRPPort)) continue;
                IRPPort containerPort = (IRPPort) containerEl;
                IRPClassifier contract = getContract(containerEl);
                if (!(contract instanceof IRPClass)) continue;
                IRPCollection nestedPorts = contract.getPorts();
                for (int i = 1; i <= nestedPorts.getCount(); i++) {
                    IRPModelElement nestedPort = (IRPModelElement) nestedPorts.getItem(i);
                    IRPClassifier ib = getContract(nestedPort);
                    if (!(ib instanceof IRPClass)) continue;
                    boolean external = PORT_GROUP_EXTERNAL.equals(group);
                    for (ConnectorCandidate c : findConnectorCandidates(cls, containerPort, (IRPPort) nestedPort, (IRPClass) ib, external)) {
                        String fromGuid = ((IRPModelElement) c.fromPort).getGUID();
                        String toGuid = ((IRPModelElement) c.toPort).getGUID();
                        String pairKey = fromGuid.compareTo(toGuid) <= 0 ? fromGuid + "|" + toGuid : toGuid + "|" + fromGuid;
                        if (!seenPairs.add(pairKey)) continue;
                        IRPStructureDiagram ibd = diagramService.getIBD(c.linkOwner);
                        if (ibd != null && connectorExists(ibd, (IRPModelElement) c.fromPart, (IRPModelElement) c.toPart, c.fromPort, c.toPort)) continue;
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("linkOwnerGuid", ((IRPModelElement) c.linkOwner).getGUID());
                        entry.put("fromPartGuid", ((IRPModelElement) c.fromPart).getGUID());
                        entry.put("toPartGuid", ((IRPModelElement) c.toPart).getGUID());
                        entry.put("fromPortGuid", fromGuid);
                        entry.put("toPortGuid", toGuid);
                        entry.put("fromOwnerGuid", c.fromOwnerClass == null ? null : c.fromOwnerClass.getGUID());
                        entry.put("toOwnerGuid", c.toOwnerClass == null ? null : c.toOwnerClass.getGUID());
                        entry.put("description", describeConnectorEnd(c.linkOwner, c.fromOwnerClass, c.fromPart, (IRPPort) c.fromPort)
                                + " ↔ " + describeConnectorEnd(c.linkOwner, c.toOwnerClass, c.toPart, (IRPPort) c.toPort));
                        out.add(entry);
                    }
                }
            }
        }
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass) collectPendingConnectorsInClass((IRPClass) item, out, seenPairs);
        }
    }

    // A port's own "Direction" tag value that marks it as a broadcast SENDER vs RECEIVER — see
    // collectInternalBroadcastPending's own javadoc.
    private static final String SENDER_DIRECTION = "Out";
    private static final String RECEIVER_DIRECTION = "In";
    // "InOut" is eligible for EITHER role — found live: "MissileLink" and "HMI", both with every
    // port explicitly set to "InOut" (not "In"/"Out"), were reported as having 0 senders/0
    // receivers, even though "beide Seiten auf IN/OUT stehen! also einen Sender haben" — an earlier
    // version only recognized the exact strings "Out"/"In", silently excluding "InOut" from both
    // counts entirely.
    private static final String BIDIRECTIONAL_DIRECTION = "InOut";

    /** Internal interfaces follow a stricter, different shape than external ones — a broadcast
     * pattern, not simple point-to-point delegation: exactly ONE sender and AT LEAST ONE receiver;
     * anything else is reported as a warning instead of link candidates. Requested live: "für die
     * internen schnittstellen muss immer mindesten 1 sender und 1 empfänger existieren (wenn nicht
     * warnung ausgeben) links werden immer vom sender ausgehnen erzeugt... nur ein Sender, so wie
     * besprochen; ansonsten Warung!" A warning entry is distinguished from a normal pending-
     * connector entry by having a "warning" key instead of the 5 GUID fields, so the frontend can
     * render it without offering a "Create" button. Multiplicity (sender) and conjugation
     * (receiver) are applied later, at actual creation time — see createConnectorIfAbsent.
     *
     * Direction "Out" is sender-only, "In" is receiver-only, "InOut" is eligible for EITHER role —
     * confirmed live, after "MissileLink"/"HMI" (every port "InOut") were wrongly flagged as having
     * no sender at all. An explicit "Out" port always wins the sender slot when present (a real,
     * unambiguous single origin); only when there's no explicit "Out" does the FIRST "InOut" port
     * (in traversal order) get picked as the sender, with every other "InOut" port falling back to
     * being a receiver alongside the explicit "In" ones. Multiple explicit "Out" ports stay
     * ambiguous (still a warning) — "InOut" never resolves that kind of genuine conflict, only the
     * "found nothing at all because every port happened to be InOut" case. */
    private void collectInternalBroadcastPending(List<Object> out, Set<String> seenPairs) {
        Map<String, List<PortOccurrence>> byContract = new LinkedHashMap<>();
        collectInternalTreePortsByContract(activeProject(), byContract, new HashSet<>());
        for (List<PortOccurrence> occurrences : byContract.values()) {
            if (occurrences.size() < 2) continue;
            List<PortOccurrence> outPorts = new ArrayList<>();
            List<PortOccurrence> bidiPorts = new ArrayList<>();
            List<PortOccurrence> receivers = new ArrayList<>();
            for (PortOccurrence occ : occurrences) {
                if (SENDER_DIRECTION.equals(occ.direction)) outPorts.add(occ);
                else if (RECEIVER_DIRECTION.equals(occ.direction)) receivers.add(occ);
                else if (BIDIRECTIONAL_DIRECTION.equals(occ.direction)) bidiPorts.add(occ);
            }
            String ifaceName = occurrences.get(0).leafPort.getName();
            PortOccurrence sender = null;
            if (outPorts.size() == 1) {
                sender = outPorts.get(0);
                receivers.addAll(bidiPorts);
            } else if (outPorts.isEmpty() && !bidiPorts.isEmpty()) {
                sender = bidiPorts.get(0);
                receivers.addAll(bidiPorts.subList(1, bidiPorts.size()));
            }
            if (sender == null || receivers.isEmpty()) {
                int reportedSenders = sender != null ? 1 : (outPorts.size() > 1 ? outPorts.size() : 0);
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("warning", "Interface \"" + ifaceName + "\" needs exactly 1 sender and at least 1 receiver (found "
                        + reportedSenders + " sender(s), " + receivers.size() + " receiver(s)).");
                out.add(warning);
                continue;
            }
            IRPModelElement parent = ((IRPClass) sender.ownerClass).getOwner();
            if (!(sender.containerPort instanceof IRPPort) || !(parent instanceof IRPClass)) continue;
            for (PortOccurrence receiver : receivers) {
                // A shared leaf (e.g. Boardnet, reused verbatim across ComSuite/PowerUnit/CN/SN via
                // their own "Power") can appear as BOTH sender and receiver occurrences that trace
                // back to the SAME owner — skip pairing an owner with itself; that was the original
                // "Missile.Uplink → Missile.Uplink" bug, now prevented structurally (each occurrence
                // carries its OWN owner directly, see PortOccurrence) instead of via a blanket
                // blacklist that used to also throw out legitimate cross-owner pairs.
                if (receiver.ownerClass.getGUID().equals(sender.ownerClass.getGUID())) continue;
                String fromGuid = sender.leafPort.getGUID();
                String toGuid = receiver.leafPort.getGUID();
                // Keyed by OWNER pair, not just leaf-port-guid pair — fromGuid/toGuid alone would
                // collapse every owner pair sharing the same leaf object (e.g. every Power/Boardnet
                // pair) onto one seenPairs entry, only ever offering the FIRST such pair.
                String pairKey = sender.ownerClass.getGUID() + ">" + fromGuid + "|" + receiver.ownerClass.getGUID() + ">" + toGuid;
                if (!seenPairs.add(pairKey)) continue;
                IRPModelElement receiverParent = ((IRPClass) receiver.ownerClass).getOwner();
                if (receiverParent == null || !parent.getGUID().equals(receiverParent.getGUID())) continue;
                if (!(receiver.containerPort instanceof IRPPort)) continue;
                IRPStructureDiagram ibd = diagramService.getIBD((IRPClass) parent);
                if (ibd != null && connectorExists(ibd, sender.containerPort, receiver.containerPort,
                        (IRPPort) sender.leafPort, (IRPPort) receiver.leafPort)) continue;
                Map<String, Object> pending = new LinkedHashMap<>();
                pending.put("linkOwnerGuid", parent.getGUID());
                pending.put("fromPartGuid", ((IRPModelElement) sender.containerPort).getGUID());
                pending.put("toPartGuid", ((IRPModelElement) receiver.containerPort).getGUID());
                pending.put("fromPortGuid", fromGuid);
                pending.put("toPortGuid", toGuid);
                pending.put("fromOwnerGuid", sender.ownerClass.getGUID());
                pending.put("toOwnerGuid", receiver.ownerClass.getGUID());
                pending.put("description", sender.ownerClass.getName() + "." + sender.leafPort.getName()
                        + " → " + receiver.ownerClass.getName() + "." + receiver.leafPort.getName());
                out.add(pending);
            }
        }
    }

    /** One (owner, leaf-port) association discovered by collectInternalTreePortsByContractInClass —
     * leafPort is the actual (possibly SHARED, see NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own
     * javadoc) native port object; ownerClass is the specific Block that reaches it; containerPort is
     * the top-level port (ownerClass's own "internal", or e.g. "Power"/"Plans") whose contract
     * actually holds leafPort — used as the connector's own "part" endpoint. direction is leafPort's
     * EFFECTIVE direction as seen through containerPort (see effectiveDirection) — for a shared leaf
     * with a per-occurrence override, this can legitimately differ between two PortOccurrences that
     * both wrap the exact same leafPort. */
    private static final class PortOccurrence {
        final IRPModelElement leafPort;
        final IRPModelElement ownerClass;
        final IRPModelElement containerPort;
        final String direction;
        PortOccurrence(IRPModelElement leafPort, IRPModelElement ownerClass, IRPModelElement containerPort, String direction) {
            this.leafPort = leafPort;
            this.ownerClass = ownerClass;
            this.containerPort = containerPort;
            this.direction = direction;
        }
    }

    private void collectInternalTreePortsByContract(IRPPackage pkg, Map<String, List<PortOccurrence>> out, Set<String> seenOccurrences) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            collectInternalTreePortsByContractInClass((IRPClass) classes.getItem(i), out, seenOccurrences);
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectInternalTreePortsByContract((IRPPackage) nestedPkgs.getItem(i), out, seenOccurrences);
        }
    }

    /** Collects every "internal broadcast" candidate leaf interface owned by cls — both the
     * classic shape (a leaf nested directly inside cls's own private "internal" collector) AND a
     * newer one: a STANDALONE top-level port on cls that itself carries its own decomposition (e.g.
     * "Plans", migrated out of ibinternal_<cls> — see findOrCreateInterfaceBlock's own
     * "hasOwnDecomposition" javadoc for why such a port can no longer live inside "internal" at
     * all). "external"-classified top-level ports are skipped here — those go through the separate
     * pairwise external scan (collectPendingConnectorsInClass) instead.
     *
     * Each match is registered as its OWN PortOccurrence (owner + leaf + container), not deduplicated
     * by leaf-port-guid the way an earlier version did — that version treated a leaf reachable from a
     * SECOND, different owner as proof of ambiguity and blacklisted it entirely (worked around the
     * original "Missile.Uplink → Missile.Uplink" self-pair bug, but as a side effect also silently
     * discarded every genuine cross-owner candidate for an intentionally-shared leaf like Boardnet,
     * reused verbatim across ComSuite/PowerUnit/CN/SN via their own "Power" — "warum finde pending
     * connectors das boardnet nicht?"). The self-pair case is now prevented directly in
     * collectInternalBroadcastPending (skip a receiver whose owner equals the sender's owner) instead
     * of by discarding data here — seenOccurrences only dedupes an exact (owner,leaf) pair reached
     * twice via redundant traversal, never a second DIFFERENT owner. */
    private void collectInternalTreePortsByContractInClass(IRPClass cls, Map<String, List<PortOccurrence>> out, Set<String> seenOccurrences) {
        if (hasStereotype(cls, INTERFACE_BLOCK_STEREOTYPE) || hasStereotype(cls, FUNCTION_STEREOTYPE)) return;
        if (!isRootLevelClass(cls)) {
            IRPCollection topPorts = cls.getPorts();
            for (int i = 1; i <= topPorts.getCount(); i++) {
                IRPModelElement topPort = (IRPModelElement) topPorts.getItem(i);
                String topName = topPort.getName();
                if (PORT_GROUP_EXTERNAL.equals(topName)) continue;
                IRPClassifier contract = getContract(topPort);
                if (!(contract instanceof IRPClass)) continue;
                boolean isInternalCollector = PORT_GROUP_INTERNAL.equals(topName);
                if (!isInternalCollector && hasStereotype((IRPModelElement) contract, EXTERNAL_INTERFACE_STEREOTYPE)) {
                    continue; // an established external container's own decomposition — not "internal".
                }
                IRPCollection nestedPorts = contract.getPorts();
                // A role-split top-level port (see roleSplit's own javadoc in createPort — same
                // exclusions here as there and in portNode) has its contract shared by potentially
                // several owners, each of which should only ever contribute ITS OWN visible variant
                // (e.g. ComSuite → "Boardnet_Out", PowerUnit → "Boardnet_In") — without this, EVERY
                // owner picks up BOTH variants from the shared contract regardless of which is
                // actually theirs, miscounting senders/receivers (found live: "found 4 sender(s), 4
                // receiver(s))" for a case that's genuinely 1 sender + 3 receivers). Everything else
                // (external delegation, the older "internal"/"external" collector pattern) keeps the
                // original unfiltered behavior.
                boolean filterByVisibility = !isWithinExternalTree(topPort)
                        && !PORT_GROUP_EXTERNAL.equals(topName) && !PORT_GROUP_INTERNAL.equals(topName);
                Set<String> visibleGuids = filterByVisibility ? visibleChildGuidsUnder(topPort) : null;
                for (int j = 1; j <= nestedPorts.getCount(); j++) {
                    IRPModelElement leaf = (IRPModelElement) nestedPorts.getItem(j);
                    if (visibleGuids != null && !visibleGuids.contains(leaf.getGUID())) continue;
                    String occKey = cls.getGUID() + "|" + leaf.getGUID();
                    if (!seenOccurrences.add(occKey)) continue;
                    IRPClassifier ib = getContract(leaf);
                    if (!(ib instanceof IRPClass)) continue;
                    String direction = nestedEffectiveDirection(leaf, topPort);
                    out.computeIfAbsent(((IRPModelElement) ib).getGUID(), k -> new ArrayList<>())
                            .add(new PortOccurrence(leaf, cls, topPort, direction));
                }
            }
        }
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass) collectInternalTreePortsByContractInClass((IRPClass) item, out, seenOccurrences);
        }
    }

    /** Mirrors linkEndInstance's own "is this end the container's own boundary port, not a part
     * inside it" check — ownerClass may be non-null but still equal to linkOwner itself (see
     * findConnectorCandidates' external case, which passes root as toOwnerClass rather than null)
     * for that exact reason, so the two must be compared here too, not just null-checked. */
    private String describeConnectorEnd(IRPClass linkOwner, IRPModelElement ownerClass, IRPPort part, IRPPort port) {
        boolean isContainerBoundary = ownerClass == null || ownerClass.getGUID().equals(((IRPModelElement) linkOwner).getGUID());
        String owner = isContainerBoundary ? ((IRPModelElement) part).getName() : ownerClass.getName();
        return owner + "." + port.getName();
    }

    /** Re-resolves the GUIDs a getPendingConnectors entry carries back into live model objects and
     * creates that one connector — deliberately re-resolving rather than trusting cached
     * references, since the scan and this call can happen arbitrarily far apart (the GUI's own
     * "pending connectors" panel is exactly this gap). Silently returns if any GUID no longer
     * resolves to the expected kind (the model changed since the scan — the panel's own next
     * refresh will show the corrected list) rather than throwing and blocking the rest of a
     * "create all" sweep.
     *
     * fromPart/toPart are either PORTS (the existing internal/external delegation shapes — see
     * findConnectorCandidates, which reuses a container/boundary port as the "part" argument,
     * relying on IRPPort extending IRPInstance) or genuine Composition-part INSTANCES (the Context
     * View shape — see addContextViewPart/collectContextViewPending, e.g. "itsFlexis"). Either way
     * fromOwnerGuid/toOwnerGuid (the SAME fields getPendingConnectors' own entries already carry
     * for their description text) are the authoritative source for each end's owning classifier —
     * NOT re-derived via fromPart.getOwner() (which only means "the port's native owner" for the
     * Port case; an INSTANCE's own getOwner() returns linkOwner itself, not its classifier, so that
     * derivation would be wrong for Context View connectors). hubMultiplicity rules (see
     * createConnectorIfAbsent) only apply when both ends are genuinely Ports — a Context View
     * connector is a plain 1:1 interface pairing with no broadcast/delegation semantics. */
    @Override
    public synchronized void createPendingConnector(String linkOwnerGuid, String fromPartGuid, String toPartGuid, String fromPortGuid, String toPortGuid,
            String fromOwnerGuid, String toOwnerGuid) {
        IRPModelElement linkOwner = findElement(linkOwnerGuid);
        IRPModelElement fromPart = findElement(fromPartGuid);
        IRPModelElement toPart = findElement(toPartGuid);
        IRPModelElement fromPort = findElement(fromPortGuid);
        IRPModelElement toPort = findElement(toPortGuid);
        if (!(linkOwner instanceof IRPClass) || !(fromPart instanceof IRPInstance) || !(toPart instanceof IRPInstance)
                || !(fromPort instanceof IRPPort) || !(toPort instanceof IRPPort)) {
            return;
        }
        IRPModelElement fromOwnerClass = fromOwnerGuid != null ? findElement(fromOwnerGuid) : fromPart.getOwner();
        IRPModelElement toOwnerClass = toOwnerGuid != null ? findElement(toOwnerGuid) : toPart.getOwner();
        boolean applyHubMultiplicity = fromPart instanceof IRPPort && toPart instanceof IRPPort;
        createConnectorIfAbsent((IRPClass) linkOwner, fromOwnerClass, toOwnerClass,
                (IRPInstance) fromPart, (IRPInstance) toPart, (IRPPort) fromPort, (IRPPort) toPort, applyHubMultiplicity);
        save();
    }

    /** DIRECT (non-recursive) reverse lookup: the port among owner's own top-level ports whose OWN
     * contract is targetContract — used by syncDelegationConnector's external case to find e.g. the
     * root's own "HEU" port starting from its nested "Voice" child's contract ("ibHEU"). */
    private IRPPort findPortByContract(IRPClassifier owner, IRPClassifier targetContract) {
        String targetGuid = ((IRPModelElement) targetContract).getGUID();
        IRPCollection ports = owner.getPorts();
        for (int i = 1; i <= ports.getCount(); i++) {
            IRPModelElement p = (IRPModelElement) ports.getItem(i);
            IRPClassifier c = getContract(p);
            if (c != null && targetGuid.equals(((IRPModelElement) c).getGUID())) return (IRPPort) p;
        }
        return null;
    }

    /** Creates the connector (IRPLink, stereotyped "connector") between fromPort/toPort — owned by
     * linkOwner, the nearest common parent of both parts (see syncDelegationConnector's two call
     * sites) — unless one already connects this exact pair (in either direction), checked via
     * linkOwner's own IBD graph edges (the only place a Link's from/to ports are cheaply queryable —
     * see ShowIBD-style diagnostics used to reverse-engineer this feature). A missing IBD (shouldn't
     * happen — addAggregationPart already gives every composed parent one) just skips the
     * idempotency check rather than failing outright. */
    private void createConnectorIfAbsent(IRPClass linkOwner, IRPModelElement fromOwnerClass, IRPModelElement toOwnerClass,
            IRPInstance fromPart, IRPInstance toPart, IRPPort fromPort, IRPPort toPort, boolean applyHubMultiplicity) {
        IRPStructureDiagram ibd = diagramService.getIBD(linkOwner);
        if (ibd != null && connectorExists(ibd, fromPart, toPart, fromPort, toPort)) return;
        IRPLink link = linkOwner.addLink(fromPart, toPart, null, fromPort, toPort);
        link.addSpecificStereotype(stereotypeService.getConnectorStereotype());
        setLinkContextTags(link, linkOwner, fromOwnerClass, toOwnerClass, fromPart, toPart, fromPort, toPort);
        if (!applyHubMultiplicity) {
            // Context View connectors only — see revealTopLevelPortsOnly's own javadoc for why this
            // (not the generic refreshPortVisibility) is used here: "im ibd dürfen keine nested
            // proxyports sichtbar sein!"
            revealTopLevelPortsOnly(ibd);
            if (ibd != null) {
                ECADContext context = new ECADContext();
                context.getLinkCollection().add(link);
                diagramService.addConnectorsToIBD(ibd, context);
                resetDiagramColorsExcept(ibd, link, fromPort, toPort);
            }
            return;
        }
        // The "hub" port is whichever end has (potentially) MULTIPLE distinct connections and so
        // needs its own MULTIPLICITY (a real, native IRPRelation-inherited property — no Tag
        // workaround needed, unlike Direction) kept in sync with the current total count — recomputed
        // every time a new link is added, not just set once. For internal broadcast, that's fromPort
        // (the sole sender, multiple receivers). For external delegation (child-first order — see
        // findConnectorCandidates' external case; a parent-first attempt was tried live and reverted,
        // see that method's own note), that's toPort (the root's own boundary leaf, e.g. HEU.Voice,
        // which multiple children may delegate to) — "dort wird die multiplizity auch entsprechend
        // erhöht" / "wenn mehr als ein Link benötigt wird, muss die Multiplizität der Senderports an
        // die Anzahl der Verbindungen angepasst werden." Only internal RECEIVERS get CONJUGATED
        // (Rhapsody's native term is "Reversed", not "Conjugated" — IRPPort#setIsReversed) — external
        // children are never conjugated, delegation isn't a flow-reversal relationship the way
        // broadcast receive is.
        boolean internalReceiver = isPortWithinInternalTree(toPort);
        if (internalReceiver) {
            toPort.setIsReversed(1);
        }
        // refreshPortVisibility MUST run before drawing — the link only shows up on the diagram once
        // its actual fromPort/toPort graph nodes are drawn: found live via VS Code debugging ("das
        // anlegen der links geht, aber sie werden noch nicht im ibd dargestellt!").
        refreshPortVisibility(ibd);
        // refreshPortVisibility's own populateIBD pass just called showAllPorts() broadly across the
        // WHOLE diagram (all-or-nothing, no selective form) — silently re-revealing every role-split
        // sibling that a PREVIOUS link's own visibility sync had correctly hidden. Re-derive every
        // already-drawn link's own correct visibility right away, before this new one adds anything
        // further — found live: "wir rufen showAllPort immer wieder auf, sodass schon ausgebländete
        // ports wieder sichtbar werden." Only covers links that already have their own GraphEdge; the
        // current one (about to be drawn below) is handled by the explicit fromPort/toPort calls
        // further down, same as before.
        resyncRoleSplitVisibility(ibd);
        if (ibd != null && !linkAlreadyDrawn(ibd, link)) {
            // Deliberately NOT going through DiagramService's own addConnectorsToIBD here — its
            // graphNodeMap (see DiagramService.java) is keyed purely by PORT GUID, which breaks once
            // a port is the exact SAME shared object across multiple owners (e.g. Boardnet, reused
            // verbatim by ComSuite/PowerUnit/CN/SN via their own "Power" — see
            // NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own javadoc): there are then FOUR distinct
            // graph nodes all reporting the identical model GUID, and a plain GUID-keyed map can only
            // ever hold one of them — confirmed live, every connector ended up drawn as a self-loop
            // on whichever occurrence's node happened to overwrite the map last (all three attempted
            // ComSuite→X connectors landed on CN→CN instead). fromPart/toPart (the actual "Power"
            // occurrences this specific link connects — always unambiguous, since each is its own
            // native object) are what let findGraphNodeForPortUnderParent resolve the CORRECT one of
            // the several same-GUID Boardnet nodes for THIS link specifically, instead of leaving it
            // to a shared, order-dependent map. DiagramService#createConnector itself (only widened
            // from private to public, no logic touched) is still what actually draws the edge.
            IRPGraphNode fromParentNode = findGraphNodeForPort(ibd, (IRPModelElement) fromPart);
            IRPGraphNode toParentNode = findGraphNodeForPort(ibd, (IRPModelElement) toPart);
            // Per-occurrence role-split visibility: fromPort/toPort are already the exact,
            // unambiguous ports THIS link connects — no need to re-derive "what's in use" from a
            // broader link scan. See revealOnlyRoleSplitVariant's own javadoc for how this was found.
            if (fromParentNode != null) revealOnlyRoleSplitVariant(ibd, fromParentNode, fromPort);
            if (toParentNode != null) revealOnlyRoleSplitVariant(ibd, toParentNode, toPort);
            IRPGraphNode fromNode = fromParentNode != null
                    ? findGraphNodeForPortUnderParent(ibd, (IRPModelElement) fromPort, fromParentNode) : null;
            IRPGraphNode toNode = toParentNode != null
                    ? findGraphNodeForPortUnderParent(ibd, (IRPModelElement) toPort, toParentNode) : null;
            // fromPort/toPort might themselves BE the top-level part port (Context View shape, or a
            // top-level-to-top-level connector) rather than something nested under it — fall back to
            // the parent node itself in that case (mirrors isDirectionlessContainer-style "port IS
            // its own top-level occurrence" reasoning elsewhere in this file).
            if (fromNode == null) fromNode = fromParentNode;
            if (toNode == null) toNode = toParentNode;
            if (fromNode != null && toNode != null) {
                diagramService.createConnector(ibd, link, fromNode, toNode);
                resetDiagramColorsExcept(ibd, link, fromPort, toPort);
            }
        }
        // Multiplicity lives on fromPort/toPort directly (the actual leaf interface, e.g. Boardnet/
        // Voice) — we already hold the correct reference to it at THIS link's own creation time, no
        // separate resolution needed: "beim Link anlegen holen wir uns die beiden Ports, auch hier
        // wissen wir wer der Sender ist... dann können wir doch die Multiplizität vom fromPort
        // abfragen." linkOwner's own links already include the one just created above, so a plain
        // count (not a read-then-increment) naturally gets the right total without a special case for
        // "is this the first connection" — confirmed live that Rhapsody's own default multiplicity on
        // a never-touched port is already "1" (not empty/0), so a blind "+1" would have overshot the
        // very first real connection.
        IRPPort hubPort = internalReceiver ? fromPort : toPort;
        String hubGuid = ((IRPModelElement) hubPort).getGUID();
        long manySideCount = 0;
        IRPCollection ownerLinks = linkOwner.getLinks();
        for (int i = 1; i <= ownerLinks.getCount(); i++) {
            IRPLink candidate = (IRPLink) ownerLinks.getItem(i);
            IRPPort cfp = candidate.getFromPort();
            IRPPort ctp = candidate.getToPort();
            boolean touchesHub = (cfp != null && hubGuid.equals(((IRPModelElement) cfp).getGUID()))
                    || (ctp != null && hubGuid.equals(((IRPModelElement) ctp).getGUID()));
            if (touchesHub) manySideCount++;
        }
        if (manySideCount > 1) {
            hubPort.setMultiplicity(String.valueOf(manySideCount));
        }
        // Runs last, once this link's own edge is actually drawn (so it counts as "in use" instead
        // of being mistaken for an unlinked occurrence) — see hideUnlinkedRoleSplitPairs' own javadoc.
        hideUnlinkedRoleSplitPairs(ibd);
    }

    /** Whether link already has its own GraphEdge on ibd — re-drawing an already-drawn link would
     * otherwise create a second, duplicate edge for it (DiagramService's own createConnector has no
     * existence check of its own). */
    private boolean linkAlreadyDrawn(IRPStructureDiagram ibd, IRPLink link) {
        String linkGuid = ((IRPModelElement) link).getGUID();
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphEdge)) continue;
            IRPModelElement mo = ((IRPGraphEdge) o).getModelObject();
            if (mo != null && linkGuid.equals(mo.getGUID())) return true;
        }
        return false;
    }

    /** Finds the graph node on ibd whose own model object is portEl — unambiguous only when portEl
     * itself has no other occurrence sharing its GUID (a genuine top-level port, e.g. "Power" —
     * always its own separate native object per owner, see NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's
     * own javadoc). Returns the FIRST match found; do not use this for a port that can legitimately
     * have multiple same-GUID graph nodes (use findGraphNodeForPortUnderParent instead). */
    private IRPGraphNode findGraphNodeForPort(IRPStructureDiagram ibd, IRPModelElement portEl) {
        return findGraphNodeForPortUnderParent(ibd, portEl, null);
    }

    /** Finds the graph node on ibd representing portEl, SPECIFICALLY the one whose own graphical
     * parent is parentNode (or the first match anywhere, if parentNode is null) — see
     * findGraphNodeForPort's own javadoc for why this disambiguation is needed at all: a shared leaf
     * port (e.g. Boardnet) has one graph node per reusing owner, all reporting the identical
     * underlying model GUID; only the graphical PARENT (e.g. ComSuite's own "Power" node, vs.
     * PowerUnit's) tells them apart. */
    private IRPGraphNode findGraphNodeForPortUnderParent(IRPStructureDiagram ibd, IRPModelElement portEl, IRPGraphNode parentNode) {
        String portGuid = portEl.getGUID();
        IRPModelElement parentNodeModelObject = parentNode != null ? parentNode.getModelObject() : null;
        String parentGuid = parentNodeModelObject != null ? parentNodeModelObject.getGUID() : null;
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphNode)) continue;
            IRPGraphNode node = (IRPGraphNode) o;
            IRPModelElement mo;
            try {
                mo = node.getModelObject();
            } catch (Exception ex) {
                continue;
            }
            if (mo == null || !portGuid.equals(mo.getGUID())) continue;
            if (parentGuid == null) return node;
            IRPGraphElement parent = node.getGraphicalParent();
            IRPModelElement parentMo = null;
            try {
                if (parent != null) parentMo = parent.getModelObject();
            } catch (Exception ex) {
                // fall through — parentMo stays null, this candidate won't match
            }
            if (parentMo != null && parentGuid.equals(parentMo.getGUID())) return node;
        }
        return null;
    }

    /** ECAD's own createConnector (called by addConnectorsToIBD) colors the new edge and both its
     * endpoint nodes green ("indicates new/imported" — its own comment, from an ICD-import tool's
     * perspective). Requested live: reset every OTHER graphical element on the diagram back to
     * standard appearance, but the just-created connector (edge + its two endpoint nodes) should
     * STAY green — "nach create soll der/die neue connector/s grün werden" (an explicit correction
     * after an earlier version of this reset also wiped the new one's own color, which wasn't
     * wanted). The new edge is found by matching model object GUID against link; its endpoint nodes
     * via matching fromPort/toPort's own GUIDs. IRPGraphElement#applyDefaultFormat() is a real
     * native reset (not a manual ForegroundColor guess), so this doesn't need to know what
     * "standard" actually looks like for anything else on the diagram. Compared by underlying
     * model-object GUID, not COM proxy reference (== is unreliable for COM interop — a fresh
     * wrapper can come back from each getItem(i) call even for the same underlying object). */
    private void resetDiagramColorsExcept(IRPStructureDiagram ibd, IRPLink link, IRPPort fromPort, IRPPort toPort) {
        String linkGuid = ((IRPModelElement) link).getGUID();
        String fromPortGuid = ((IRPModelElement) fromPort).getGUID();
        String toPortGuid = ((IRPModelElement) toPort).getGUID();
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (o instanceof IRPGraphNode) {
                IRPModelElement mo = ((IRPGraphNode) o).getModelObject();
                if (mo != null && (fromPortGuid.equals(mo.getGUID()) || toPortGuid.equals(mo.getGUID()))) continue;
            } else if (o instanceof IRPGraphEdge) {
                IRPModelElement mo = ((IRPGraphEdge) o).getModelObject();
                if (mo != null && linkGuid.equals(mo.getGUID())) continue;
            }
            if (o instanceof IRPGraphElement) {
                ((IRPGraphElement) o).applyDefaultFormat();
            }
        }
    }

    /** Stamps the link's "End1Path"/"End2Path" context tags — required bookkeeping ECAD's own
     * XMLImporter#processNet always does immediately after addLink (vendored logic reproduced here,
     * not itself vendored as a class — it's a short private method in XMLImporter, not one of the
     * services already vendored into this app). Found live: a connector created via addLink alone
     * (without this) still exists as a valid IRPLink object, but Rhapsody's own diagram-refresh
     * logic (showAllPorts()/populateIBD, already called for every port/composition change — see
     * refreshPortVisibility) apparently depends on these tags to correctly resolve which part
     * instance each end belongs to; omitting them was the actual cause behind a connector-less
     * addLink call coinciding with Rhapsody re-flattening a related part's own port structure on
     * the next diagram refresh. ownerClass is null exactly when that end IS linkOwner itself (the
     * container's own boundary port, not a part inside it) — mirrors ECAD's own fromInstance/
     * toInstance being null for that same case (processNet's own "no its-instance" branch). */
    private void setLinkContextTags(IRPLink link, IRPClass linkOwner, IRPModelElement fromOwnerClass, IRPModelElement toOwnerClass,
            IRPInstance fromContainerPort, IRPInstance toContainerPort, IRPPort fromPort, IRPPort toPort) {
        IRPCollection elementCol = application.createNewCollection();
        IRPCollection multiCol = application.createNewCollection();

        IRPInstance fromInstance = linkEndInstance(linkOwner, fromOwnerClass);
        if (fromInstance != null) {
            multiCol.setSize(3);
            multiCol.setString(1, "");
            multiCol.setString(2, fromPort.getName());
            multiCol.setString(3, itsInstanceName(fromOwnerClass.getName()));
            elementCol.setSize(3);
            elementCol.setModelElement(1, fromPort);
            elementCol.setModelElement(2, fromContainerPort);
            elementCol.setModelElement(3, fromInstance);
        } else {
            multiCol.setSize(2);
            multiCol.setString(1, "");
            multiCol.setString(2, fromPort.getName());
            elementCol.setSize(2);
            elementCol.setModelElement(1, fromPort);
            elementCol.setModelElement(2, fromContainerPort);
        }
        try {
            link.setTagContextValue(link.getTag("End2Path"), elementCol, multiCol);
        } catch (Exception ex) {
            // Matches ECAD's own swallow-and-continue here — a project without this exact tag
            // definition (unlikely, given it's the same one ECAD relies on) shouldn't fail the
            // connector creation itself, only skip this specific bookkeeping.
        }

        multiCol.empty();
        elementCol.empty();

        IRPInstance toInstance = linkEndInstance(linkOwner, toOwnerClass);
        if (toInstance != null) {
            multiCol.setSize(3);
            multiCol.setString(1, "");
            multiCol.setString(2, toPort.getName());
            multiCol.setString(3, itsInstanceName(toOwnerClass.getName()));
            elementCol.setSize(3);
            elementCol.setModelElement(1, toPort);
            elementCol.setModelElement(2, toContainerPort);
            elementCol.setModelElement(3, toInstance);
        } else {
            multiCol.setSize(2);
            multiCol.setString(1, "");
            multiCol.setString(2, toPort.getName());
            elementCol.setSize(2);
            elementCol.setModelElement(1, toPort);
            elementCol.setModelElement(2, toContainerPort);
        }
        try {
            link.setTagContextValue(link.getTag("End1Path"), elementCol, multiCol);
        } catch (Exception ex) {
            // See above.
        }
    }

    /** The "its"+name instance representing ownerClass as a PART of linkOwner — null when ownerClass
     * IS linkOwner itself (that end of the connector is the container's own boundary port, not a
     * part inside it — see setLinkContextTags's own javadoc). */
    private IRPInstance linkEndInstance(IRPClass linkOwner, IRPModelElement ownerClass) {
        if (ownerClass == null || ownerClass.getGUID().equals(((IRPModelElement) linkOwner).getGUID())) return null;
        return modelElementService.getInstance(linkOwner, itsInstanceName(ownerClass.getName()));
    }

    /** Whether a link between (fromPart,a) and (toPart,b) already exists on ibd. Checks BOTH the
     * port pair AND the part pair — port identity alone is NOT enough once a port can be the exact
     * SAME shared object across multiple owners (e.g. Boardnet, reused verbatim by ComSuite/
     * PowerUnit/CN/SN via their own "Power" — see NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own
     * javadoc): checking port identity alone made ComSuite↔PowerUnit's link register as "already
     * exists" for ComSuite↔CN and ComSuite↔SN too (fromPort/toPort are literally the same GUID for
     * every one of those pairs), so createConnectorIfAbsent silently no-op'd on the second and third
     * — "das postprocessing muss immer nach dem anlegen eines links gemacht werden!" turned out to
     * be exactly right: two of the three links were never actually being created at all, not a
     * missing postprocessing step. Confirmed live: POST /api/connectors for all three pairs each
     * returned {"status":"ok"} (createPendingConnector always reports success regardless of whether
     * createConnectorIfAbsent's own early-return fired), but only ONE GraphEdge ever showed up on
     * ibdSystem_L. Part identity (IRPLink#getFrom/getTo — the actual Instance/Part endpoints, e.g.
     * ComSuite vs CN) is what genuinely distinguishes one pair from another when the ports
     * themselves can't. */
    private boolean connectorExists(IRPStructureDiagram ibd, IRPModelElement fromPart, IRPModelElement toPart, IRPPort a, IRPPort b) {
        String aGuid = ((IRPModelElement) a).getGUID();
        String bGuid = ((IRPModelElement) b).getGUID();
        String fromPartGuid = fromPart.getGUID();
        String toPartGuid = toPart.getGUID();
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphEdge)) continue;
            IRPModelElement mo = ((IRPGraphEdge) o).getModelObject();
            if (!(mo instanceof IRPLink)) continue;
            IRPLink link = (IRPLink) mo;
            IRPPort fp = link.getFromPort();
            IRPPort tp = link.getToPort();
            IRPInstance linkFromPart = link.getFrom();
            IRPInstance linkToPart = link.getTo();
            if (fp == null || tp == null || linkFromPart == null || linkToPart == null) continue;
            String fpGuid = ((IRPModelElement) fp).getGUID();
            String tpGuid = ((IRPModelElement) tp).getGUID();
            String linkFromPartGuid = ((IRPModelElement) linkFromPart).getGUID();
            String linkToPartGuid = ((IRPModelElement) linkToPart).getGUID();
            boolean forward = aGuid.equals(fpGuid) && bGuid.equals(tpGuid)
                    && fromPartGuid.equals(linkFromPartGuid) && toPartGuid.equals(linkToPartGuid);
            boolean backward = aGuid.equals(tpGuid) && bGuid.equals(fpGuid)
                    && fromPartGuid.equals(linkToPartGuid) && toPartGuid.equals(linkFromPartGuid);
            if (forward || backward) return true;
        }
        return false;
    }

    private void applyPortSpec(IRPModelElement el, String direction, String type, String view) {
        // A TOP-LEVEL interface is purely a grouping container ("Top-level Interface ist eine
        // Collection von nested Interfaces") — it has no signal direction of its own, only its
        // NESTED leaf interfaces do. Requested live: "können wir bei toplevel interfaces das in/out,
        // in und out weglassen!" — direction is now never stamped (or synced — see
        // syncInterfaceIdentity's own matching check) for a top-level port at all, regardless of
        // what a caller passes in.
        if (!isDirectionlessContainer(el) && direction != null && !direction.isEmpty()) {
            stampTagValue(el, DIRECTION_TAG, direction);
        }
        if (type != null && !type.isEmpty()) {
            // Physical is a genuine special case again: "type" there is a PHYSICAL REALIZATION
            // PROPERTY of this specific interface (mechanic/electric/radiofrequency, from config.ini's
            // [Physical] interfaceTypes — see WebServer's /api/config/physicalInterfaceTypes), not a
            // shared identity two different interfaces should be merged into — "der Punkt ist wir
            // müssen physicalische properties zu jedem interface zuweisen. damit können wir
            // unterscheiden welche Art von Physik angewendet wird. HEU.Link16 kann dann RF [sein]"
            // (a DIFFERENT nested interface under the same HEU, e.g. Voice, might be a different
            // type entirely — they must stay independently classified, not collapsed onto one shared
            // "RF" contract the way Operational/Functional/Logical types are). Every OTHER view still
            // resolves "type" as a shared interfaceBlock contract (see findOrCreateInterfaceBlock) —
            // that unification stays exactly as it was for Operational/Functional/Logical.
            // effectiveView falls back to el's own already-stamped view stereotype for a call that
            // only changes type, not view (e.g. a plain retype through the popover).
            String effectiveView = (view != null && PORT_VIEWS.contains(view)) ? view : viewOf(el);
            if ("Physical".equals(effectiveView)) {
                setPhysicalTypeStereotype(el, type);
            } else {
                // isPortWithinExternalTree (not the narrower isExternalPort) — el itself might be a
                // NESTED port already living inside an external interfaceBlock's own ports (e.g.
                // retyping HEU's own "Voice"), which isExternalPort alone would miss (it only
                // recognizes a tree root's own DIRECT top-level ports).
                IRPClass ib = findOrCreateInterfaceBlock(type, view, isPortWithinExternalTree(el), isTopLevelPort(el));
                setContract(el, ib);
            }
        }
        if (view != null && PORT_VIEWS.contains(view)) {
            setPortViewStereotype(el, view);
        }
        // "Unikat" interface reuse — see INTERFACE_DIRECTION_TAG/INTERFACE_VIEW_TAG's own javadoc.
        // Runs AFTER the above so it sees el's final, just-resolved contract regardless of which
        // branch above set it (or left it untouched, for a call that only changes one field).
        IRPClassifier contract = getContract(el);
        if (contract instanceof IRPClass) {
            syncInterfaceIdentity((IRPClass) contract, el, direction, view);
        }
    }

    private void setPortViewStereotype(IRPModelElement el, String view) {
        IRPCollection stereotypes = el.getStereotypes();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            IRPStereotype st = (IRPStereotype) stereotypes.getItem(i);
            if (PORT_VIEWS.contains(st.getName())) {
                el.removeStereotype(st);
            }
        }
        el.addStereotype(view, portMetaType);
    }

    /** A Physical port's "type" (mechanic/electric/radiofrequency/... — config.ini's [Physical]
     * interfaceTypes) is a classification STEREOTYPE on the port itself, not a shared interfaceBlock
     * name — see applyPortSpec's own javadoc for why. Removes whichever OTHER type stereotype might
     * already be applied first (a retype shouldn't stack "electric" and "mechanic" on the same port
     * simultaneously) — proxyPort/PORT_VIEWS/externalInterface are structural markers this method
     * never touches, matching physicalTypeOf's own read-back exclusion list exactly. */
    private void setPhysicalTypeStereotype(IRPModelElement el, String type) {
        IRPCollection stereotypes = el.getStereotypes();
        List<IRPStereotype> toRemove = new ArrayList<>();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            IRPStereotype st = (IRPStereotype) stereotypes.getItem(i);
            String name = st.getName();
            if (PROXY_PORT_STEREOTYPE.equals(name) || PORT_VIEWS.contains(name) || EXTERNAL_INTERFACE_STEREOTYPE.equals(name)) continue;
            toRemove.add(st);
        }
        for (IRPStereotype st : toRemove) el.removeStereotype(st);
        applyStereotypeSafely(el, type, portMetaType);
    }

    /** Keeps changedPort's interface (ib, its resolved contract) in sync in both directions:
     * PULLS direction/view from ib's own stored tags when THIS call didn't explicitly supply one
     * (so e.g. reusing an existing interface's name — which resolves to the same ib — without
     * re-specifying direction/view still adopts its already-established settings), then PUSHES
     * whichever ended up effective back onto ib and PROPAGATES it to every OTHER port anywhere in
     * the project that shares this same ib (see propagateToSiblingPorts) — so editing one port's
     * interface updates every other use of it, matching the requested "Unikat" (single canonical
     * instance) semantics despite a Port never being literally shared across two owners. */
    private void syncInterfaceIdentity(IRPClass ib, IRPModelElement changedPort, String newDirection, String newView) {
        IRPModelElement ibEl = (IRPModelElement) ib;
        boolean external = hasStereotype(ibEl, EXTERNAL_INTERFACE_STEREOTYPE);
        // Distinguishes the TWO different ways a port can end up on an external ib — found live,
        // via the "Voice" (nested under HEU) case: "HEU.JMessages ist eine externe Schnittstelle und
        // HEU.Voice ist die 2. externe Schnittstelle! HEU ist der Container!" — a NESTED port under
        // an external interface's decomposition is ITSELF external too (propagated down in
        // createPort's nested-port branch — see isWithinExternalTree), and per "Top-level Interface
        // ist eine Collection von nested Interfaces, die auf Subsystemen einzeln verwendet werden",
        // ANY element (not just another tree root) may reuse one of those individually BY NAME (see
        // findOrCreateInterfaceBlock's non-external fallback) — a NESTED port that ends up on an
        // external ib this way has no view of its own to protect, and must ADOPT the ib's already-
        // established view rather than keep whatever its own current tab/context implied.
        //
        // A TOP-LEVEL use of an external ib is the opposite — untouched, exactly like the original
        // TestScopeIF case (System_F/System_L each deliberately establishing their OWN view for the
        // SAME external interface) — but that exemption must key off isTopLevelPort (depth), NOT the
        // narrower isExternalPort (root-only): found live, a top-level "Truck" port on a NON-root
        // PhysicalNode ("container", under System_P) had its OWN "Physical" view force-flipped to
        // "Operational" by this exact adopt-path, right after findOrCreateInterfaceBlock's own fix
        // (see its javadoc) started letting a non-root top-level port link to the same canonical
        // external ib a root element's top-level port already established — "Truck ist wie jedes
        // ander toplevel Interface ein container für alle arten von interface typen... in
        // Physicalisch gibt es auch Truck aber nur mit seinen Physikalischen Schnittstellen": every
        // TOP-LEVEL occurrence of a container interface (root or not) keeps its own view, same as a
        // root element's; only an actually-NESTED port adopts the established one.
        boolean topLevelOwned = isTopLevelPort(changedPort);
        boolean adoptEstablishedView = external && !topLevelOwned;
        boolean viewIsSharedSetting = !external || adoptEstablishedView;
        // Direction is a shared, synced "Unikat" setting for every interface EXCEPT one nested
        // under an "internal" collector — see isPortWithinInternalTree's own javadoc. View sync is
        // unaffected by this — only direction. A genuine, direction-less GROUPING container (see
        // isDirectionlessContainer — NOT the broader topLevelOwned above: a QUALIFIED top-level port
        // like CN's own "Communication.CNNetwork" is structurally top-level but still needs its own
        // direction, same as an ordinary nested leaf would) has no direction concept at ALL (see
        // applyPortSpec's own matching check) — never pulled, pushed, or propagated, regardless of
        // directionIsSharedSetting's own value, which is otherwise meaningless for it anyway.
        boolean directionIsSharedSetting = !isDirectionlessContainer(changedPort) && !isPortWithinInternalTree(changedPort);

        boolean directionGiven = newDirection != null && !newDirection.isEmpty();
        boolean viewGiven = newView != null && PORT_VIEWS.contains(newView);

        String effectiveDirection = !directionIsSharedSetting ? null
                : directionGiven ? newDirection : tagValue(ibEl, INTERFACE_DIRECTION_TAG);
        // Found live: reusing an external ib from a DIFFERENT view than its first ROOT-owned use
        // (e.g. a Functional-view root port's interface name reused on a Logical-view root port)
        // previously force-flipped the ORIGINAL port's own view stereotype to match the new one via
        // this exact pull/push/propagate path — wrong, since the whole point of "external" (for a
        // root-owned use) is that different views legitimately differ. For a non-root port adopting
        // an established external interface, though, the CALLER's own given view is deliberately
        // IGNORED in favor of the ib's own canonical tag (falling back to the given one only the
        // very first time this ib is ever adopted this way, before a canonical value exists yet).
        String establishedView = tagValue(ibEl, INTERFACE_VIEW_TAG);
        String effectiveView;
        if (!viewIsSharedSetting) {
            effectiveView = null;
        } else if (adoptEstablishedView) {
            effectiveView = establishedView != null ? establishedView : newView;
        } else {
            effectiveView = viewGiven ? newView : establishedView;
        }

        if (!directionGiven && effectiveDirection != null) {
            stampTagValue(changedPort, DIRECTION_TAG, effectiveDirection);
        }
        boolean viewNeedsRestamp = adoptEstablishedView
                ? effectiveView != null && !effectiveView.equals(newView)
                : viewIsSharedSetting && !viewGiven && effectiveView != null;
        if (viewNeedsRestamp) {
            setPortViewStereotype(changedPort, effectiveView);
        }

        if (effectiveDirection != null) stampTagValue(ibEl, INTERFACE_DIRECTION_TAG, effectiveDirection);
        if (viewIsSharedSetting && effectiveView != null) stampTagValue(ibEl, INTERFACE_VIEW_TAG, effectiveView);
        boolean propagateView = viewIsSharedSetting && (adoptEstablishedView || viewGiven);
        if (directionGiven || propagateView) {
            propagateToSiblingPorts(ib, effectiveDirection, propagateView ? effectiveView : null, changedPort.getGUID());
        }
    }

    /** Applies direction/view to every OTHER port anywhere in the project (Blocks at any nesting
     * depth, Actors, and nested/decomposed ports — same traversal shape as
     * findBySourceGuidInPorts, guarded the same way against a cyclical contract chain) whose own
     * resolved contract is ib, excluding excludeGuid (the port that triggered this call, already
     * updated directly by its own applyPortSpec). */
    private void propagateToSiblingPorts(IRPClass ib, String direction, String view, String excludeGuid) {
        List<IRPModelElement> siblings = new ArrayList<>();
        collectPortsByContract(activeProject(), ib, siblings);
        for (IRPModelElement port : siblings) {
            if (excludeGuid.equals(port.getGUID())) continue;
            if (direction != null) stampTagValue(port, DIRECTION_TAG, direction);
            if (view != null) setPortViewStereotype(port, view);
        }
    }

    private void collectPortsByContract(IRPPackage pkg, IRPClass targetContract, List<IRPModelElement> out) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            collectPortsByContractInClassifier((IRPClass) classes.getItem(i), targetContract, out);
        }
        IRPCollection actors = pkg.getActors();
        for (int i = 1; i <= actors.getCount(); i++) {
            Object actor = actors.getItem(i);
            if (actor instanceof IRPClassifier) {
                collectPortsByContractInPorts((IRPClassifier) actor, targetContract, out, new HashSet<>());
            }
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectPortsByContract((IRPPackage) nestedPkgs.getItem(i), targetContract, out);
        }
    }

    private void collectPortsByContractInClassifier(IRPClass cls, IRPClass targetContract, List<IRPModelElement> out) {
        collectPortsByContractInPorts(cls, targetContract, out, new HashSet<>());
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass) {
                collectPortsByContractInClassifier((IRPClass) item, targetContract, out);
            }
        }
    }

    private void collectPortsByContractInPorts(IRPClassifier classifier, IRPClass targetContract, List<IRPModelElement> out, Set<String> visitedPath) {
        String targetGuid = ((IRPModelElement) targetContract).getGUID();
        IRPCollection ports = classifier.getPorts();
        for (int i = 1; i <= ports.getCount(); i++) {
            IRPModelElement portEl = (IRPModelElement) ports.getItem(i);
            IRPClassifier contract = getContract(portEl);
            if (contract != null && targetGuid.equals(((IRPModelElement) contract).getGUID())) {
                // Dedup by GUID — found live: a port could be reached via more than one traversal
                // path once portGroupContainer's own private "ib"+group+"_"+name contract classes
                // started being reparented INSIDE their owning block (see ReparentContracts — "im
                // zugehörigen Block automatisch eingehängt"), since collectPortsByContractInClassifier
                // ALSO walks every element's getNestedClassifiers() and that interfaceBlock is now
                // itself one of those nested classifiers, in addition to being reached normally via
                // its owning port's own contract chain below — same port ended up double-counted,
                // inflating a sender's computed receiver-count multiplicity (7 instead of 3 for 3
                // real receivers). Cheap at this scale (a handful of ports per call).
                boolean alreadyPresent = out.stream().anyMatch(p -> p.getGUID().equals(portEl.getGUID()));
                if (!alreadyPresent) out.add(portEl);
            }
            if (contract instanceof IRPClass) {
                String contractGuid = ((IRPModelElement) contract).getGUID();
                if (!visitedPath.contains(contractGuid)) {
                    Set<String> childPath = new HashSet<>(visitedPath);
                    childPath.add(contractGuid);
                    collectPortsByContractInPorts(contract, targetContract, out, childPath);
                }
            }
        }
    }

    /** Finds an existing classifier by name and ensures it carries the interfaceBlock stereotype,
     * or creates a fresh one if none exists by that name — under the view-named package (see
     * viewPackage) when view is one of PORT_VIEWS, otherwise under the hidden default package.
     * SCOPED to that same container, not a project-wide name search — requested live explicitly
     * ("wichtig ist die interfaces sind pro view/ebene operational, functional, logical und
     * physical") after an earlier version of this searched project-wide regardless of view, which
     * would have silently merged e.g. an Operational "optical" and a Physical "optical" into the
     * same interfaceBlock (same direction/view tags, wrong for two conceptually different
     * interfaces at different abstraction levels) had that scenario ever come up. Each view now
     * gets its own independent interfaceBlock even for the exact same name.
     *
     * external is the one exception to that scoping — requested live right after: "ausnahme sind
     * externe interfaces von System_F, die überall wiederverwendet werden dürfen", confirmed to
     * mean specifically a port whose owner is a tree ROOT element itself (Flexis/System_F/
     * System_L/System_P — see isRootLevelClass/isExternalPort), not any nested element and not
     * Actors. When true, the search widens to every OTHER view's own package (see
     * findInterfaceBlockAcrossAllViews — still never a deep project-wide walk — an interfaceBlock
     * is always a DIRECT child of exactly one such package, see findClassByNameDirect) before
     * falling back to the normal view-scoped container for where a brand-new one gets created.
     *
     * Logical and Physical resolve identically to Functional/Operational here — no kind-group
     * separation, no Physical-specific stereotype-based typing — per explicit request: "die
     * logische und physicalische architectur muss genau so funktionieren wie die funktionale
     * architectur" (an earlier version walled Physical off into its own kind-group, and gave a
     * Physical port's own "type" a completely different, stereotype-based mechanism instead of a
     * shared interfaceBlock — both removed). */
    /** topLevel: whether the port this interfaceBlock is being resolved for is itself a TOP-LEVEL
     * port (see isTopLevelPort) as opposed to a NESTED/decomposed one — distinct from `external`
     * (which additionally requires the owner to be a tree ROOT). A top-level port on ANY element
     * (root or not, e.g. a new "container" PhysicalNode's own top-level "Truck") searches the WHOLE
     * project for an existing same-named interfaceBlock, same as an actually-`external` call already
     * did — "Truck ist wie jedes ander toplevel Interface ein container für alle arten von interface
     * typen. Also muss ein kontainer interface immer selectierbar sein." A NESTED port reusing an
     * established external name (e.g. "HEU.Voice") goes through the narrower
     * findExternalInterfaceBlockAcrossAllViews below instead — same unscoped, every-view search, just
     * requiring EXTERNAL_INTERFACE_STEREOTYPE (see that method's own javadoc for why). */
    private IRPClass findOrCreateInterfaceBlock(String name, String view, boolean external, boolean topLevel) {
        // Search the WHOLE project FIRST (crossing every view/kind-group boundary) whenever this is
        // an actually-external call OR a top-level port on any element — not just as a fallback after
        // the local view-scoped lookup below, which would let a stray/orphaned same-named
        // interfaceBlock already sitting in the caller's OWN view package win by sheer physical
        // proximity and silently shadow the real, already-established one elsewhere. Found live: a
        // new non-root PhysicalNode "container"'s own top-level "Truck" port kept resolving to an
        // orphaned, EMPTY "ibTruck" sitting directly in the Physical package, instead of the real one
        // (5 nested ports) living in the Operational package, because the OLD local-package-first
        // lookup found that orphan before a topLevel-aware cross-view search ever ran — "ich habe
        // einen neue PhysicalNode 'container' angelegt und dort das Interface Truck hinzugenommen.
        // aber leider werden die nested ports nicht übernommen! gibt es 2 ibTruck interfaceblöcke?"
        // (yes, confirmed live: exactly 2 — one real, one orphaned).
        if (external || topLevel) {
            IRPClass existing = findInterfaceBlockAcrossAllViews(name);
            if (existing != null) {
                ensureStereotype(existing, INTERFACE_BLOCK_STEREOTYPE, levelMetaType);
                if (external) ensureStereotype(existing, EXTERNAL_INTERFACE_STEREOTYPE, levelMetaType);
                return existing;
            }
        }
        IRPPackage container = (view != null && PORT_VIEWS.contains(view))
                ? viewPackage(view)
                : containerFor(activeProject());
        IRPClass existing = findClassByNameDirect(container, name);
        if (existing != null) {
            ensureStereotype(existing, INTERFACE_BLOCK_STEREOTYPE, levelMetaType);
            if (external) ensureStereotype(existing, EXTERNAL_INTERFACE_STEREOTYPE, levelMetaType);
            return existing;
        }
        // Fallback for a NESTED port (non-top-level, non-external) call: even though THIS caller
        // isn't itself a tree root (or otherwise established as external), it may still be reusing a
        // name that's ALREADY external elsewhere — e.g. "Voice", nested under the external "HEU" port
        // (HEU.Voice IS itself an external interface, see isWithinExternalTree's own javadoc: "HEU
        // ist der Container", JMessages/Voice are the actual external interfaces it bundles) — "Top-
        // level Interface ist eine Collection von nested Interfaces, die auf Subsystemen einzeln
        // verwendet werden": a NESTED port may LINK to an already-established external interface by
        // name, from any view (no kind-group restriction — see findExternalInterfaceBlockAcrossAllViews's
        // own javadoc); it just can't MINT a brand-new external identity on its own (that still requires an actually-
        // external call — see syncInterfaceIdentity's own "adoptEstablishedView" for how the
        // resulting port's view gets forced to match once linked this way). A top-level call already
        // exhausted the wider, unscoped search above, so it never reaches this narrower one.
        if (!external && !topLevel) {
            IRPClass existingExternal = findExternalInterfaceBlockAcrossAllViews(name, view);
            if (existingExternal != null) return existingExternal;
        }
        IRPClass created = container.addClass(name);
        created.addStereotype(INTERFACE_BLOCK_STEREOTYPE, levelMetaType);
        if (external) created.addStereotype(EXTERNAL_INTERFACE_STEREOTYPE, levelMetaType);
        return created;
    }

    /** Searches for an EXISTING interfaceBlock already carrying EXTERNAL_INTERFACE_STEREOTYPE —
     * unlike findInterfaceBlockAcrossAllViews (used by an ACTUALLY-external call, which doesn't care
     * whether a same-named match happens to be external yet or not), this one is deliberately picky:
     * a plain, never-external interfaceBlock with a coincidentally-matching name must NOT be found
     * here, since a non-external caller should never accidentally merge into an unrelated same-named
     * interface (that's exactly the per-view-scoping this whole file otherwise enforces) — only an
     * interface that's ALREADY been established as external (whether by a root element's own
     * top-level port, or by nested-decomposition propagation — see isWithinExternalTree) is eligible
     * to be linked from anywhere. Only ever called for a NESTED port (findOrCreateInterfaceBlock's
     * `!external && !topLevel` fallback) — a TOP-LEVEL port's own wider search happens earlier via
     * findInterfaceBlockAcrossAllViews instead. No kind-group restriction here either, matching the
     * same "Logical/Physical resolve exactly like Functional" unification as findOrCreateInterfaceBlock's
     * own top-level search (see its javadoc) — a Physical nested interface may now be reused/linked
     * from a Functional/Logical/Operational context and vice versa, same as any two of those three
     * already could before this change. */
    private IRPClass findExternalInterfaceBlockAcrossAllViews(String name, String view) {
        for (String v : PORT_VIEWS) {
            IRPClass hit = findClassByNameDirect(viewPackage(v), name);
            if (hit != null && hasStereotype(hit, EXTERNAL_INTERFACE_STEREOTYPE)) return hit;
        }
        return null;
    }

    /** Searches every view's own package for an interfaceBlock by name — used for the external=true
     * case (a root-level port's own top-level name), so this widening never happens for a normal
     * per-view interface. Logical and Physical are searched the exact same way as Operational/
     * Functional — no kind-group restriction: "Truck ist wie jedes ander toplevel Interface ein
     * container für alle arten von interface typen" — a root-level port named "Truck" created on
     * System_P should ADOPT the very same ibTruck contract Flexis/System_F/System_L already share, so
     * its own nested decomposition (Mechanical/Power) shows up immediately, the same "Unikat" way
     * direction sync already works across every other reuse of that contract. A brand-new root port
     * whose name has never been used anywhere still gets a fresh, unshared contract (nothing here to
     * find) — this only matters once the name is already established. */
    private IRPClass findInterfaceBlockAcrossAllViews(String name) {
        for (String v : PORT_VIEWS) {
            IRPClass hit = findClassByNameDirect(viewPackage(v), name);
            if (hit != null) return hit;
        }
        return findClassByNameDirect(containerFor(activeProject()), name);
    }

    /** Whether cls itself sits directly under a package (a tree root — Flexis/System_F/System_L/
     * System_P — as opposed to being nested under another Block). Used by createPort's top-level
     * branch (before the port itself exists yet, so this checks the OWNER classifier directly) —
     * see findOrCreateInterfaceBlock's "external" javadoc. */
    private boolean isRootLevelClass(IRPClass cls) {
        return ((IRPModelElement) cls).getOwner() instanceof IRPPackage;
    }

    /** Whether portEl's own native owner classifier is itself a tree root — the same check as
     * isRootLevelClass, but starting from an EXISTING port (used by applyPortSpec, which runs for
     * both freshly-created and pre-existing ports alike). A nested/decomposed port's native owner
     * is its parent port's interfaceBlock contract (an ad-hoc classifier that also happens to sit
     * directly under a view package — see findOrCreateInterfaceBlock/resolvePortContainer) — that
     * would otherwise look "root-level" by the same owner-instanceof-IRPPackage test, so it's
     * explicitly excluded via the interfaceBlock stereotype check first. */
    private boolean isExternalPort(IRPModelElement portEl) {
        IRPModelElement owner = portEl.getOwner();
        if (!(owner instanceof IRPClass) || hasStereotype(owner, INTERFACE_BLOCK_STEREOTYPE)) {
            return false;
        }
        return isRootLevelClass((IRPClass) owner);
    }

    /** Whether portEl is a TOP-LEVEL port — owned directly by a genuine classifier (a Block or
     * Actor), as opposed to a NESTED/decomposed port whose owner is another port's own
     * interfaceBlock contract (INTERFACE_BLOCK_STEREOTYPE). Unlike isExternalPort/isRootLevelClass,
     * this makes NO requirement about the owner being a tree ROOT — ANY element's own top-level
     * port counts, e.g. "test" (a non-root PhysicalNode)'s own "J20", not just Flexis/System_F/
     * System_L/System_P's. Used by findOrCreateInterfaceBlock (a top-level port's own name search
     * widens across the whole project) and syncInterfaceIdentity (a top-level port always keeps its
     * own view, never adopts an established one) — requested live: "Top level interfaces sind
     * diejenigen die nested interfaces haben!" / confirmed explicitly to apply universally, not just
     * at System-level: "auch J20 auf 'test' muss auf interfaceblock umgestellt werden" — every
     * top-level port, anywhere in the tree, always has (and exposes for editing) an interfaceblock,
     * Physical included, resolved the exact same way as every other view. */
    private boolean isTopLevelPort(IRPModelElement portEl) {
        IRPModelElement owner = portEl.getOwner();
        return owner instanceof IRPClassifier && !hasStereotype(owner, INTERFACE_BLOCK_STEREOTYPE);
    }

    /** Whether portEl's own displayed name carries a "Parent.Name" qualifier (see qualifiedValue in
     * the frontend's utils/knownInterfaces.ts) — i.e. it was created by picking an already-nested
     * reuse suggestion like "Communication.CNNetwork" or "HEU.Voice" from the TOP-LEVEL "+Interface"
     * form specifically (see PortsSection's own doc comment: that form always creates a
     * STRUCTURALLY top-level port, isTopLevelPort()==true, regardless of what gets picked). */
    private boolean hasQualifiedName(IRPModelElement portEl) {
        String displayName = portEl.getDisplayName();
        String name = displayName != null && !displayName.isEmpty() ? displayName : portEl.getName();
        return name.contains(".");
    }

    /** Whether portEl is a genuine, direction-less GROUPING container — a top-level port (see
     * isTopLevelPort) whose own name is a plain, unqualified one (e.g. "Power", "Truck", "HEU").
     * Deliberately narrower than isTopLevelPort alone: a QUALIFIED top-level port (see
     * hasQualifiedName — e.g. "Communication.CNNetwork" on CN, or "HEU.Voice" picked directly from
     * the top-level form rather than via "+ Nested Port") is STRUCTURALLY flat but SEMANTICALLY a
     * leaf borrowed from an established container's own decomposition elsewhere, not a container in
     * its own right — it still needs its own direction, exactly like a genuinely nested one would.
     * Found live: "können wir bei toplevel interfaces das in/out, in und out weglassen!" was applied
     * using plain isTopLevelPort, which incorrectly also stripped direction from CN's own
     * "Communication.CNNetwork" and SN's own "Communication.SensorNetwork" — "Du hast nicht
     * berücksichtigt dass in CN Commication.CNNetwork und in SN communication.SensorNetwork keine
     * Toplevel interfaces sind!" Used ONLY for the direction-related decisions below
     * (applyPortSpec's stamp-skip, syncInterfaceIdentity's directionIsSharedSetting) — every OTHER
     * isTopLevelPort call site (interfaceBlock cross-view search widening, delegation routing,
     * adoptEstablishedView) is unrelated to direction and stays keyed off the broader, purely
     * structural isTopLevelPort, unchanged. */
    private boolean isDirectionlessContainer(IRPModelElement portEl) {
        return isTopLevelPort(portEl) && !hasQualifiedName(portEl);
    }

    /** Whether owner (an EXISTING port being nested UNDER, when creating a new decomposed child of
     * it — see createPort's nested-port branch) is itself part of an established EXTERNAL interface
     * tree — either because owner is directly a tree root's own top-level port (isExternalPort), or
     * because owner's OWN resolved contract already carries EXTERNAL_INTERFACE_STEREOTYPE (a deeper
     * level of decomposition under an already-external ancestor). Used to PROPAGATE externality DOWN
     * through a decomposition tree — found live: "HEU.JMessages ist eine externe Schnittstelle und
     * HEU.Voice ist die 2. externe Schnittstelle! HEU ist der Container!" — a top-level external
     * interface (HEU) is really just a CONTAINER; its own nested children (JMessages, Voice) are
     * themselves the actual external interfaces, individually reusable from anywhere (see
     * findOrCreateInterfaceBlock's non-external fallback), not merely inert decomposition detail. */
    private boolean isWithinExternalTree(IRPModelElement owner) {
        if (isExternalPort(owner)) return true;
        IRPClassifier contract = getContract(owner);
        return contract != null && hasStereotype((IRPModelElement) contract, EXTERNAL_INTERFACE_STEREOTYPE);
    }

    /** Whether portEl ITSELF (an existing port, e.g. being re-typed via applyPortSpec) is already
     * part of an established external interface tree — either directly (isExternalPort: portEl is a
     * tree root's own top-level port) or because portEl is a NESTED port whose own OWNER (the
     * interfaceBlock it's nested inside — e.g. "ibHEU" for Voice) already carries
     * EXTERNAL_INTERFACE_STEREOTYPE. This is the "am I myself already external" check for an
     * EXISTING port; isWithinExternalTree is the sibling check used when CREATING a brand-new child
     * NESTED UNDER some other port (looking at that OTHER port's own contract instead). */
    private boolean isPortWithinExternalTree(IRPModelElement portEl) {
        if (isExternalPort(portEl)) return true;
        IRPModelElement owner = portEl.getOwner();
        return owner instanceof IRPClass
                && hasStereotype(owner, INTERFACE_BLOCK_STEREOTYPE)
                && hasStereotype(owner, EXTERNAL_INTERFACE_STEREOTYPE);
    }

    /** Whether portEl is nested directly under a PORT_GROUP_INTERNAL ("internal") collector — its
     * direction is inherently a property of THIS one port instance, never a shared "Unikat" one, and
     * is recognized purely by naming convention (portGroupContainer's own
     * "ib"+group+"_"+ownerName) since it carries no distinguishing stereotype the way an external ib
     * does (see EXTERNAL_INTERFACE_STEREOTYPE). Used by syncInterfaceIdentity to exempt such a port
     * from direction sync entirely — each one keeps whatever direction it was independently given,
     * never overwritten by a sibling's own value. Requested live: "Direction-Sync abschalten für
     * internal ausschalten! external muss synchronisiert bleiben!" */
    private boolean isPortWithinInternalTree(IRPModelElement portEl) {
        IRPModelElement owner = portEl.getOwner();
        if (!(owner instanceof IRPClass) || !hasStereotype(owner, INTERFACE_BLOCK_STEREOTYPE)) return false;
        if (owner.getName().startsWith("ib" + PORT_GROUP_INTERNAL + "_")) return true;
        // A role-split nested port (e.g. "Boardnet_Out"/"Boardnet_In" — see roleSplit's own javadoc
        // in createPort) is its own genuinely independent native object, but still shares its
        // CONTRACT (e.g. "ibBoardnet") with its sibling variant — without this, applyPortSpec's own
        // syncInterfaceIdentity/propagateToSiblingPorts would push a direction change on one variant
        // onto the other, defeating the entire point of splitting them. Detected by the SAME name
        // suffix used to create it, combined with an actual DisplayName differing from that technical
        // name (confirms this went through the role-split path, not a coincidentally "_Out"/"_In"-
        // suffixed name some other port happens to have).
        String ownName = portEl.getName();
        String displayName = portEl.getDisplayName();
        boolean roleSuffixed = ownName.endsWith("_Out") || ownName.endsWith("_In");
        return roleSuffixed && displayName != null && !displayName.isEmpty() && !displayName.equals(ownName);
    }

    private void ensureStereotype(IRPModelElement el, String name, String metaType) {
        IRPCollection stereotypes = el.getStereotypes();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            if (name.equals(((IRPStereotype) stereotypes.getItem(i)).getName())) return;
        }
        el.addStereotype(name, metaType);
    }

    private IRPClassifier getContract(IRPModelElement portEl) {
        if (portEl instanceof IRPPort) return ((IRPPort) portEl).getContract();
        if (portEl instanceof IRPSysMLPort) return ((IRPSysMLPort) portEl).getType();
        return null;
    }

    private void setContract(IRPModelElement portEl, IRPClass ib) {
        if (portEl instanceof IRPPort) {
            ((IRPPort) portEl).setContract(ib);
        } else if (portEl instanceof IRPSysMLPort) {
            ((IRPSysMLPort) portEl).setType(ib);
        }
    }

    // ── Element lookup ───────────────────────────────────────────────────

    private synchronized IRPProject activeProject() {
        IRPProject project = application.activeProject();
        if (project == null) {
            throw new IllegalStateException("No active project — load a model first (POST /api/loadModel)");
        }
        return project;
    }

    /** Finds a model element by GUID, searching across all open projects (same approach as SPREAD's selectElementByGuid). */
    private IRPModelElement findElement(String guid) {
        requireNonEmpty(guid, "guid");
        IRPCollection projects = application.getProjects();
        for (int i = 1; i <= projects.getCount(); i++) {   // Rhapsody: 1-based
            IRPProject project = (IRPProject) projects.getItem(i);
            IRPModelElement found = project.findElementByGUID(guid);
            if (found != null) return found;
        }
        throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
    }

    /** Same as findElement, but null-safe both ways — null/empty guid or no match returns null
     * instead of throwing. For callers where a missing end (e.g. a pending connector's owner, which
     * getPendingConnectors' own entries don't guarantee non-null for every candidate shape) should
     * degrade gracefully rather than blow up the whole table. */
    private IRPModelElement findElementOrNull(String guid) {
        if (guid == null || guid.isEmpty()) return null;
        try {
            return findElement(guid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Stamps sourceGuid (an identity carried over from an XML import) onto el as a Tag, so a
     * future re-import can find this exact element again via findBySourceGuid. No-op if sourceGuid
     * is null (normal interactive creation never sets one). */
    private void stampSourceGuid(IRPModelElement el, String sourceGuid) {
        if (sourceGuid == null) return;
        stampTagValue(el, SOURCE_GUID_TAG, sourceGuid);
    }

    private void stampTagValue(IRPModelElement el, String tagName, String value) {
        IRPTag tag = el.getTag(tagName);
        if (tag == null) tag = (IRPTag) el.addNewAggr("Tag", tagName);
        tag.setValue(value);
    }

    /** Reads back a position previously stamped via setPosition, or null if this element has never
     * been manually positioned (frontend then falls back to its own auto-layout). */
    private Double doubleTagValue(IRPModelElement el, String tagName) {
        String v = tagValue(el, tagName);
        if (v == null) return null;
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** view -> {x,y} for every view el has ever been dragged in (see setPosition) — mirrors
     * LocalXmlModelStore#positionsToMap so both stores produce the same JSON shape.
     *
     * Originally checked only ModelStore#ARCHITECTURE_VIEWS's fixed 5 tag pairs rather than a
     * generic tag scan, on the assumption that was the closed set of views the frontend would ever
     * position an architecture element under — wrong once the system-of-interest's own Context-tab
     * box became draggable too (requested live: "die Größe geht jetzt, aber die Position noch
     * nicht", right after size got the same fix — see readSizes's own javadoc for the full
     * reasoning, identical here): its position is keyed by an open-ended {@code "Context:" +
     * contextViewGuid}, not a member of ARCHITECTURE_VIEWS, so a fixed-list scan would silently
     * never find it. Now scans el's own tags directly via getAllTags(), same as readSizes. */
    private Map<String, Object> readPositions(IRPModelElement el) {
        Map<String, Object> out = new LinkedHashMap<>();
        IRPCollection tags = el.getAllTags();
        for (int i = 1; i <= tags.getCount(); i++) {
            String tagName = ((IRPModelElement) tags.getItem(i)).getName();
            if (tagName == null || !tagName.startsWith(POS_X_TAG_PREFIX)) continue;
            String tagSuffix = tagName.substring(POS_X_TAG_PREFIX.length());
            Double x = doubleTagValue(el, tagName);
            Double y = doubleTagValue(el, POS_Y_TAG_PREFIX + tagSuffix);
            if (x != null && y != null) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("x", x);
                p.put("y", y);
                out.put(desanitizeTagNameSuffix(tagSuffix), p);
            }
        }
        return out;
    }

    /** view -> {width,height} for every view el has ever been manually resized under (see setSize)
     * — mirrors readPositions/LocalXmlModelStore#sizesToMap so both stores produce the same JSON
     * shape. Architecture-element-only, same as readPositions; Actors/Capabilities/Context Views
     * keep the flat WIDTH_TAG/HEIGHT_TAG read directly in elementRef.
     *
     * Unlike readPositions (a fixed, closed set of 5 Architecture-tab views, safe to enumerate by
     * name), this scans el's OWN tags directly via getAllTags() and pattern-matches the
     * WIDTH_TAG_PREFIX — a fixed list can't work here since one "view" is {@code "Context:" +
     * contextViewGuid} (see ModelStore#setSize's own javadoc), and Context Views are user-created
     * and open-ended, not a closed set this class could enumerate up front without depending on
     * getContextViews() (a layering entanglement not worth introducing just to read a size back). */
    private Map<String, Object> readSizes(IRPModelElement el) {
        Map<String, Object> out = new LinkedHashMap<>();
        IRPCollection tags = el.getAllTags();
        for (int i = 1; i <= tags.getCount(); i++) {
            String tagName = ((IRPModelElement) tags.getItem(i)).getName();
            if (tagName == null || !tagName.startsWith(WIDTH_TAG_PREFIX)) continue;
            String tagSuffix = tagName.substring(WIDTH_TAG_PREFIX.length());
            Double width = doubleTagValue(el, tagName);
            Double height = doubleTagValue(el, HEIGHT_TAG_PREFIX + tagSuffix);
            if (width != null && height != null) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("width", width);
                s.put("height", height);
                out.put(desanitizeTagNameSuffix(tagSuffix), s);
            }
        }
        return out;
    }

    /** Searches the whole active project — Blocks (recursively nested), Actors, UseCases, and
     * ports (including nested/decomposed ones) — for an element previously stamped with this
     * sourceGuid. Returns null if none found (i.e. this identity hasn't been imported here yet).
     * A full tree walk per lookup is O(n) with import being O(n) elements, so O(n^2) overall for a
     * full import — fine at the model sizes this app targets; Rhapsody's automation API has no
     * generic "find by tag value" query to do better. */
    private IRPModelElement findBySourceGuid(String sourceGuid) {
        return findBySourceGuidInPackage(activeProject(), sourceGuid);
    }

    private IRPModelElement findBySourceGuidInPackage(IRPPackage pkg, String sourceGuid) {
        // A Capability is itself a package (see capabilitiesPackage/createCapability), so its own
        // tag must be checked directly — unlike classes/actors/useCases below, a package has no
        // separate collection to iterate for "packages that are themselves the thing being matched".
        if (sourceGuid.equals(tagValue((IRPModelElement) pkg, SOURCE_GUID_TAG))) return (IRPModelElement) pkg;
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            IRPModelElement hit = findBySourceGuidInClassifier((IRPClass) classes.getItem(i), sourceGuid);
            if (hit != null) return hit;
        }
        IRPCollection actors = pkg.getActors();
        for (int i = 1; i <= actors.getCount(); i++) {
            IRPModelElement el = (IRPModelElement) actors.getItem(i);
            if (sourceGuid.equals(tagValue(el, SOURCE_GUID_TAG))) return el;
            if (el instanceof IRPClassifier) {
                IRPModelElement portHit = findBySourceGuidInPorts((IRPClassifier) el, sourceGuid, new HashSet<>());
                if (portHit != null) return portHit;
            }
        }
        IRPCollection useCases = pkg.getUseCases();
        for (int i = 1; i <= useCases.getCount(); i++) {
            IRPModelElement el = (IRPModelElement) useCases.getItem(i);
            if (sourceGuid.equals(tagValue(el, SOURCE_GUID_TAG))) return el;
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            IRPModelElement hit = findBySourceGuidInPackage((IRPPackage) nestedPkgs.getItem(i), sourceGuid);
            if (hit != null) return hit;
        }
        return null;
    }

    private IRPModelElement findBySourceGuidInClassifier(IRPClass cls, String sourceGuid) {
        IRPModelElement el = cls;
        if (sourceGuid.equals(tagValue(el, SOURCE_GUID_TAG))) return el;
        IRPModelElement portHit = findBySourceGuidInPorts(cls, sourceGuid, new HashSet<>());
        if (portHit != null) return portHit;
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass) {
                IRPModelElement hit = findBySourceGuidInClassifier((IRPClass) item, sourceGuid);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /** Mirrors portsOf's recursion (nested = the port's own interfaceBlock contract's ports, not
     * literal children), guarded the same way against a cyclical contract chain. */
    private IRPModelElement findBySourceGuidInPorts(IRPClassifier classifier, String sourceGuid, Set<String> visitedPath) {
        IRPCollection ports = classifier.getPorts();
        for (int i = 1; i <= ports.getCount(); i++) {
            IRPModelElement portEl = (IRPModelElement) ports.getItem(i);
            if (sourceGuid.equals(tagValue(portEl, SOURCE_GUID_TAG))) return portEl;
            IRPClassifier contract = getContract(portEl);
            if (contract instanceof IRPClass) {
                String contractGuid = ((IRPModelElement) contract).getGUID();
                if (!visitedPath.contains(contractGuid)) {
                    Set<String> childPath = new HashSet<>(visitedPath);
                    childPath.add(contractGuid);
                    IRPModelElement hit = findBySourceGuidInPorts(contract, sourceGuid, childPath);
                    if (hit != null) return hit;
                }
            }
        }
        return null;
    }

    private String tagValue(IRPModelElement el, String tagName) {
        IRPTag tag = el.getTag(tagName);
        return tag == null ? null : tag.getValue();
    }

    // ── Small JSON/map/list helpers used by getUseCaseDetail/updateUseCase's Tag-backed fields ──

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    /** Multi-line Tag values (preconditions/basicPath) use a plain "\n" separator — split/join pair. */
    private static List<String> splitLines(String s) {
        List<String> result = new ArrayList<>();
        if (s == null || s.isEmpty()) return result;
        for (String line : s.split("\n", -1)) result.add(line);
        return result;
    }

    private static String joinLines(List<Object> items) {
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(item == null ? "" : item.toString());
        }
        return sb.toString();
    }

    /** alternatives/extensions are stamped as a single JSON blob each (their own shape is more than
     * a flat string list) — delegates to the shared Json reader/writer already used for the HTTP API
     * itself, rather than reinventing serialization here. */
    @SuppressWarnings("unchecked")
    private static List<Object> parseJsonList(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        Object parsed = Json.parse(s);
        return parsed instanceof List ? (List<Object>) parsed : new ArrayList<>();
    }

    private static String toJson(Object value) {
        return Json.write(value);
    }

    private static final String DEFAULT_PACKAGE_NAME = "SysMLFrontendData";

    /** Rhapsody's Project root object doesn't implement addClass/addActor/addUseCase directly via
     * automation — confirmed against a live Rhapsody 10.0.3 instance ("Method addClass not
     * implemented for Project") — even though IRPProject extends IRPPackage, which declares them.
     * So top-level creation is redirected through one hidden, find-or-created package instead.
     * Its contents are flattened into the root's children/actors/useCases on read (see
     * collectArchitectureChildren / collectActors below), so it never appears as a
     * distinct node in the tree this app exposes — from the user's perspective there is still no
     * Package concept at all. */
    private IRPPackage containerFor(IRPPackage parent) {
        if (!(parent instanceof IRPProject)) {
            return parent; // a legacy nested package (rare) works directly
        }
        IRPProject project = (IRPProject) parent;
        IRPCollection nestedPkgs = project.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            IRPPackage p = (IRPPackage) nestedPkgs.getItem(i);
            if (DEFAULT_PACKAGE_NAME.equals(((IRPModelElement) p).getName())) return p;
        }
        return project.addNestedPackage(DEFAULT_PACKAGE_NAME);
    }

    /** Root-level architecture element creation target: a FunctionalNode/LogicalNode/PhysicalNode
     * — the top of one of the three aspect-view trees (see HierarchyLevels) — goes into that view's
     * own package (see viewPackage). Every other kind (System/Subsystem/Equipment/SystemOfSystem —
     * the System Structure tree) goes into the "Operational" package instead: Structure and
     * Operational render the exact same tree/instances (see the per-view `positions` design
     * above), and Operational is the one of the two that's an actual SysML architecture view, so
     * that's the view package this tree's Blocks belong under. Only ever matters for the single
     * topmost element of the whole tree — everything nested below it stays a nested classifier of
     * its own parent (`((IRPClass) parent).addClass(name)`, see createArchitectureElement), which
     * already transitively keeps it "inside" whichever package the top-level ancestor lives in. */
    private IRPPackage containerForKind(IRPPackage rootPkg, String kind) {
        switch (kind) {
            case HierarchyLevels.FUNCTIONAL_NODE: return viewPackage("Functional");
            case HierarchyLevels.LOGICAL_NODE: return viewPackage("Logical");
            case HierarchyLevels.PHYSICAL_NODE: return viewPackage("Physical");
            default: return viewPackage("Operational");
        }
    }

    /** Establishes proper SysML aggregation between parent and child, in addition to child already
     * being a nested classifier of parent (Rhapsody's own containment/browser-tree concept — a
     * namespace relationship, not the same as SysML "part" ownership): a Composition association,
     * plus a part for child in parent's own Internal Block Diagram (auto-creating it if it doesn't
     * exist yet), plus parent+child as Block nodes in parent's own Block Definition Diagram.
     *
     * ECAD's own XMLImporter creates its analogous "child assembly" composition via {@code
     * IRPPackage#addGlobalObject("its"+name, name, packageName)} first, then relates to {@code
     * relation.getOtherClass()} — that only works there because ECAD's assemblies are flat siblings
     * within one package, so addGlobalObject's own by-name class lookup (scoped to that package)
     * finds them. This app's own children are nested classifiers of their own parent (see
     * createArchitectureElement's {@code ((IRPClass) parent).addClass(name)}), not siblings in a
     * package, so that same by-name lookup can't find `child` there and addGlobalObject produces a
     * broken reference — reproduced live ("Rhapsody object deleted" on the very next call touching
     * it). Relating directly to `child` — already a live reference, no re-lookup needed — avoids
     * that lookup entirely and works: Rhapsody still auto-creates a default "its"+name instance for
     * the association end, found via ModelElementService#getInstance and placed onto the IBD via
     * DiagramService#addPartToIBD. */
    private void addAggregationPart(IRPClass parent, IRPClass child) {
        // Idempotent — safe to call for a pair that's already composed (e.g. re-running this for
        // an element moveElement previously moved here) without piling up duplicate Composition
        // associations. Not needed for create-time callers (a brand-new child never already has
        // one), but required now that moveElement calls this too on an EXISTING element that could
        // in principle be moved to the same parent more than once.
        if (!hasCompositionTo(parent, child)) {
            parent.addRelationTo(child, "", "Composition", "", "", "Association", "", "");
        }

        int offset = (parent.getNestedClassifiers().getCount() - 1) * 150;

        ECADContext context = new ECADContext();
        IRPStructureDiagram ibd = diagramService.createIBD(parent, context);
        IRPInstance instance = modelElementService.getInstance(parent, itsInstanceName(child.getName()));
        if (instance != null && !diagramService.isPartInIBD(ibd, instance)) {
            // Mirror the frontend's own canvas position when child has one (see
            // scaledFrontendPosition) — falls back to the auto-incrementing grid spot otherwise
            // (never manually positioned in the frontend, so there's nothing meaningful to mirror).
            int[] pos = scaledFrontendPosition(child);
            int x = pos != null ? pos[0] : 100 + offset;
            int y = pos != null ? pos[1] : 100;
            diagramService.addPartToIBD(ibd, instance, x, y);
        }
        refreshPortVisibility(ibd);
        // See createConnectorIfAbsent's own call to resyncRoleSplitVisibility for why this is needed
        // here too — refreshPortVisibility's populateIBD pass just broadly re-revealed every
        // role-split sibling on this diagram, including ones a previous link's visibility sync had
        // correctly hidden.
        resyncRoleSplitVisibility(ibd);
        // No connector is created in this method, so (unlike createConnectorIfAbsent) there's no
        // "current link not drawn yet" ordering concern — safe to run right away.
        hideUnlinkedRoleSplitPairs(ibd);

        // One BDD per tree, owned by the topmost System/SystemOfSystem ancestor (not per-parent,
        // not package-owned — an earlier version of this method did both of those before this was
        // corrected) — contains that root and every descendant added to the tree so far, growing
        // incrementally as more children are created anywhere under it.
        IRPClass root = topLevelAncestor(parent);
        IRPObjectModelDiagram bdd = createOrGetBDD(root);
        addBlockToBDD(bdd, root);
        addBlockToBDD(bdd, parent);
        addBlockToBDD(bdd, child);
        // Explicit edge for the Composition association — found live (via the Use Case Diagram
        // postprocessing work): a plain association DOESN'T auto-render just because both ends
        // share a diagram, contradicting what an EARLIER live test of THIS exact method concluded
        // ("addNewEdgeForElement(...) here reproducibly threw 'Rhapsody operation failed'", isolated
        // via a standalone diagnostic at the time). Re-tested live against this same real project/
        // relation just now and it succeeded cleanly — the original failure's actual cause was never
        // fully isolated beyond "this call, at this point in the method", so it may have been a
        // transient/ordering issue rather than a hard API limitation; regardless, drawing it
        // explicitly is now known to work and no longer optional, since "auto-renders" was the wrong
        // assumption all along (confirmed missing live: "diese werden noch nicht dargestellt").
        // Anchor-corrected (parent bottom-center / child top-center) via
        // drawCompositionEdgeIfMissing — see its own javadoc.
        drawCompositionEdgeIfMissing(bdd, parent, child);
    }

    /** The name Rhapsody itself auto-generates for the Composition association-end instance it
     * creates as a side effect of addRelationTo — "its" followed by the class name with its OWN
     * first letter forced to UPPERCASE, regardless of the class's actual casing. Found live: a
     * class named "test" (lowercase) got an auto-created instance named "itsTest" — NOT the naively
     * expected "itstest" — so `modelElementService.getInstance(parent, "its" + child.getName())`
     * silently found nothing for it (returned null, causing addPartToIBD/moveGraphNode to just
     * no-op) while working correctly for every other name in this project, which all happened to
     * already start with an uppercase letter, coincidentally never exposing the mismatch. Reported
     * live as "system_f/l/p bdd und ibd beinhalten nicht die entsprechenden parts" — `test`
     * specifically was silently missing from `ibdSystem_P`, while `bddSystem_P` (a separate, simpler
     * find-by-model-object-GUID lookup — see addBlockToBDD — not name-string-based at all) had it
     * correctly, which is what pointed at a NAME lookup being the actual defect, not a broader
     * placement failure. */
    private static String itsInstanceName(String className) {
        if (className.isEmpty()) return "its";
        return "its" + Character.toUpperCase(className.charAt(0)) + className.substring(1);
    }

    /** Makes ports actually VISIBLE on ibd — ECAD's own DiagramService#populateIBD (vendored
     * verbatim, only widened from private to public so it's callable per-diagram instead of only
     * via the ECADContext-driven bulk populateAllIBDs) calls IRPGraphNode#showAllPorts() on every
     * "DiagramFrame" node (the parent class's own frame, showing its top-level ports) and every
     * "Port"-typed node already on the diagram. Found live: the underlying port/nested-port MODEL
     * data was already confirmed correct many times over (see the "Ports" section above) — the
     * actual complaint ("ProxyPorts werden nicht mit all ihren nested ports angezeugt") was about
     * DIAGRAM RENDERING, since nothing ever called showAllPorts() at all, so ports existed in the
     * model but were simply never drawn. A single populateIBD pass only reveals ONE more level:
     * showAllPorts() on the frame synchronously creates new "Port"-typed graph nodes for the
     * parent's own top-level ports, but populateIBD's own node iteration is a snapshot taken BEFORE
     * that call, so those newly-created Port nodes' own nested sub-ports aren't picked up until a
     * SECOND pass sees them already present. Called twice here to reveal that one extra level of
     * decomposition per call site — not a generic fixed-point loop, since each call site (creating
     * a port vs. creating/moving an architecture element) only ever adds one new "frontier" of
     * ports to reveal at a time. */
    private void refreshPortVisibility(IRPStructureDiagram ibd) {
        if (ibd == null) return;
        diagramService.populateIBD(ibd);
        diagramService.populateIBD(ibd);
    }

    /** GUIDs of nestedPorts' own siblings that are ACTUALLY drawn (as graph nodes) directly under
     * portEl's own graph node, on portEl's native owner's IBD — used by portNode to filter a
     * role-split port's children down to just this specific occurrence's own resolved variant. See
     * revealOnlyThisNestedPortVariant's own javadoc for the write-side counterpart that establishes
     * this visibility at creation time. Returns an empty set (hiding everything) if portEl has no
     * diagram context yet — matches revealOnlyThisNestedPortVariant's own no-op in that case, so
     * read and write stay consistent. */
    private Set<String> visibleChildGuidsUnder(IRPModelElement portEl) {
        Set<String> result = new HashSet<>();
        IRPModelElement nativeOwner = portEl.getOwner();
        if (!(nativeOwner instanceof IRPClass)) return result;
        IRPStructureDiagram ibd = diagramService.getIBD((IRPClass) nativeOwner);
        if (ibd == null) return result;
        IRPGraphNode ownerNode = findGraphNodeForPort(ibd, portEl);
        if (ownerNode == null) return result;
        String ownerGuid = portEl.getGUID();
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphNode)) continue;
            IRPGraphNode node = (IRPGraphNode) o;
            IRPGraphElement parent = node.getGraphicalParent();
            if (parent == null) continue;
            IRPModelElement parentMo = parent.getModelObject();
            if (parentMo == null || !ownerGuid.equals(parentMo.getGUID())) continue;
            // A role-split sibling stays PRESENT on the diagram even when hidden (see
            // revealOnlyRoleSplitVariant's own javadoc — hiding toggles the "isVisible" graphical
            // property in place, it never removes the graph node itself). So mere presence in this
            // collection is no longer sufficient to mean "visible" the way it did when the old
            // remove-based mechanism was still in use — must also check isVisible isn't explicitly
            // "FALSE" (missing/null/"TRUE" all count as visible, matching every node's own default).
            IRPGraphicalProperty vis = node.getGraphicalProperty("isVisible");
            if (vis != null && "FALSE".equals(vis.getValue())) continue;
            IRPModelElement mo = node.getModelObject();
            if (mo != null) result.add(mo.getGUID());
        }
        return result;
    }

    /** Per-occurrence role-split port visibility, driven by an ACTUAL LINK's own already-resolved
     * endpoint (keepPort) rather than creation-time guesswork — the durable fix for
     * revealOnlyThisNestedPortVariant below (kept for its own create-time call site, but that one
     * only ever reaches owner's OWN separate IBD, e.g. ibdComSuite — never the diagram that actually
     * matters for a shared/broadcast leaf like Boardnet, e.g. ibdSystem_L, where ComSuite/PowerUnit/
     * CN/SN all appear as PARTS with their own independent "Power" graph nodes. There's no native
     * Rhapsody relationship tying "ComSuite's Power" to "Boardnet_Out" specifically — both role-split
     * variants are just children of the one shared "ibPower" contract, reachable identically from
     * every occurrence — so the only real source of truth for which variant belongs to which
     * occurrence is the LINK data itself, recomputed fresh every time a connector is created (per the
     * user: "das postprocessing muss immer nach dem anlegen eines links gemacht werden").
     * <p>
     * Mechanism found via a live experiment (not guessed): dumped every Port graph node's full
     * IRPGraphNode#getAllGraphicalProperties() on ibdSystem_L, asked the user to manually toggle one
     * port's visibility off in the Rhapsody GUI, then diffed the two dumps. Result: the graph node
     * itself stayed in the diagram's own collection, completely unchanged, except for exactly one
     * property — "isVisible" flipped from "TRUE" to "FALSE". So unlike revealOnlyThisNestedPortVariant
     * below (which has to physically remove/re-add graph nodes via IRPDiagram#removeGraphElements,
     * since it predates this finding), this method just toggles that same property directly — no
     * node removal, safely idempotent, matches exactly what a manual GUI hide/show does. */
    private void revealOnlyRoleSplitVariant(IRPStructureDiagram ibd, IRPGraphNode containerNode, IRPPort keepPort) {
        if (ibd == null || containerNode == null) return;
        containerNode.showAllPorts();
        String containerGraphGuid = graphNodeGuid(containerNode);
        if (containerGraphGuid == null) return;
        String keepGuid = ((IRPModelElement) keepPort).getGUID();
        String keepDisplayName = keepPort.getDisplayName();
        if (keepDisplayName == null || keepDisplayName.isEmpty()) keepDisplayName = keepPort.getName();
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphNode)) continue;
            IRPGraphNode node = (IRPGraphNode) o;
            IRPGraphElement parent = node.getGraphicalParent();
            if (!(parent instanceof IRPGraphNode)) continue;
            if (!containerGraphGuid.equals(graphNodeGuid((IRPGraphNode) parent))) continue;
            IRPModelElement mo = node.getModelObject();
            if (mo == null) continue;
            if (keepGuid.equals(mo.getGUID())) {
                node.setGraphicalProperty("isVisible", "TRUE");
                continue;
            }
            // Only touch a ROLE-SPLIT SIBLING of keepPort (same DisplayName, different underlying
            // GUID — e.g. Boardnet_Out vs Boardnet_In) — an unrelated nested port that happens to
            // share this same container is left untouched, there's no ambiguity to resolve for it.
            String dn = mo.getDisplayName();
            if (dn == null || dn.isEmpty()) dn = mo.getName();
            if (keepDisplayName.equals(dn)) {
                node.setGraphicalProperty("isVisible", "FALSE");
            }
        }
    }

    /** The graph node's OWN identity (a graphical "GUID" property, distinct from its model object's
     * native GUID) — found via the same live experiment as revealOnlyRoleSplitVariant: needed because
     * IRPGraphNode has no getGUID() of its own, and comparing IRPGraphNode COM proxy references
     * directly (parent == containerNode) isn't safe across separately-fetched instances. */
    private String graphNodeGuid(IRPGraphNode node) {
        IRPGraphicalProperty prop = node.getGraphicalProperty("GUID");
        return prop != null ? prop.getValue() : null;
    }

    /** Re-applies revealOnlyRoleSplitVariant for EVERY link already drawn on ibd, not just the one
     * most recently created — the durable fix for showAllPorts()'s all-or-nothing nature: any broad
     * populateIBD/refreshPortVisibility pass on ibd (drawing a DIFFERENT connector, adding a new
     * part, etc.) re-reveals every nested child of every already-visible container's contract,
     * silently undoing whatever selective hiding was done for OTHER, unrelated occurrences on the
     * same diagram. Found live: "wir rufen showAllPort immer wieder auf, sodass schon ausgebländete
     * ports wieder sichtbar werden." Since links are the single durable source of truth for "which
     * variant belongs to which occurrence" (see revealOnlyRoleSplitVariant's own javadoc),
     * re-deriving visibility from every drawn link after any broad reveal keeps the diagram
     * consistent no matter how many times showAllPorts() gets called elsewhere.
     * <p>
     * Iterates the diagram's own GraphEdges (not linkOwner.getLinks() directly) and reads
     * getFrom()/getFromPort()/getTo()/getToPort() off THAT freshly-resolved link reference — matches
     * connectorExists' own already-proven pattern; a link reference obtained by iterating a class's
     * getLinks() collection directly was found unreliable for getFrom()/getTo() in earlier testing
     * (always resolving to whichever occurrence was last), so this avoids that path entirely.
     * <p>
     * Ports that exist but have no link yet aren't covered here — they rely on the create-time fix
     * (revealOnlyThisNestedPortVariant) only, and could still be re-revealed by an unrelated broad
     * refresh elsewhere on the same diagram before they're ever linked. Not handled — a narrower edge
     * case than the one this method fixes. */
    private void resyncRoleSplitVisibility(IRPStructureDiagram ibd) {
        if (ibd == null) return;
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphEdge)) continue;
            IRPModelElement mo = ((IRPGraphEdge) o).getModelObject();
            if (!(mo instanceof IRPLink)) continue;
            IRPLink link = (IRPLink) mo;
            IRPPort fromPort = link.getFromPort();
            IRPPort toPort = link.getToPort();
            IRPInstance fromPart = link.getFrom();
            IRPInstance toPart = link.getTo();
            if (fromPort == null || toPort == null || fromPart == null || toPart == null) continue;
            IRPGraphNode fromParentNode = findGraphNodeForPort(ibd, (IRPModelElement) fromPart);
            IRPGraphNode toParentNode = findGraphNodeForPort(ibd, (IRPModelElement) toPart);
            if (fromParentNode != null) revealOnlyRoleSplitVariant(ibd, fromParentNode, fromPort);
            if (toParentNode != null) revealOnlyRoleSplitVariant(ibd, toParentNode, toPort);
        }
    }

    /** Companion to resyncRoleSplitVisibility: that method only corrects occurrences an EXISTING link
     * actually touches — an occurrence with no link at all (e.g. SN's own "Power" node when nothing
     * has ever connected SN's HighPower) has nothing telling it which variant is "correct", so it's
     * left showing whatever a broad showAllPorts()/populateIBD pass happened to reveal — normally
     * BOTH role-split siblings, since that's the whole reason role-splitting needed a hide step in
     * the first place. Requested live: "ja, SN auch fixen." Hides every role-split sibling GROUP
     * (same container + same DisplayName, mirroring revealOnlyRoleSplitVariant's own sibling test)
     * where NEITHER variant is referenced by any link touching that specific container — a group with
     * only one variant present isn't touched (nothing ambiguous to hide), and a group where a link
     * DOES use one of them is left to resyncRoleSplitVisibility's own handling.
     * <p>
     * Found live (via a real bug in this method's own first version): for these internal-broadcast
     * links, IRPLink#getFrom()/getTo() do NOT return the owning-CLASS instance ("itsComSuite") the
     * way they do for a Context View connector — they return the CONTAINER PORT itself ("Power",
     * since IRPPort extends IRPInstance and can be a link endpoint in its own right), whose GUID is
     * the same one already used everywhere else in this file as the "part" for this pattern (see
     * createConnectorIfAbsent's own fromPart/toPart, which for internal broadcast ARE the container
     * ports). The first version instead compared that against the graph NODE's own graphical "GUID"
     * property (a completely different namespace, see graphNodeGuid's own javadoc) via an
     * unnecessary grandparent walk — nothing ever matched, so EVERY group looked unlinked and got
     * hidden, including the correctly-linked ones. Fixed by keying/grouping on the container's own
     * MODEL object GUID throughout — no grandparent lookup needed at all, since fromPart/toPart
     * already ARE the container. */
    private void hideUnlinkedRoleSplitPairs(IRPStructureDiagram ibd) {
        if (ibd == null) return;
        IRPCollection elems = ibd.getGraphicalElements();
        Map<String, Set<String>> inUseByContainer = new HashMap<>();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphEdge)) continue;
            IRPModelElement mo = ((IRPGraphEdge) o).getModelObject();
            if (!(mo instanceof IRPLink)) continue;
            IRPLink link = (IRPLink) mo;
            IRPPort fromPort = link.getFromPort();
            IRPPort toPort = link.getToPort();
            IRPInstance fromPart = link.getFrom();
            IRPInstance toPart = link.getTo();
            if (fromPort != null && fromPart != null) {
                inUseByContainer.computeIfAbsent(((IRPModelElement) fromPart).getGUID(), k -> new HashSet<>())
                        .add(((IRPModelElement) fromPort).getGUID());
            }
            if (toPort != null && toPart != null) {
                inUseByContainer.computeIfAbsent(((IRPModelElement) toPart).getGUID(), k -> new HashSet<>())
                        .add(((IRPModelElement) toPort).getGUID());
            }
        }

        Map<String, List<IRPGraphNode>> groups = new HashMap<>();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (!(o instanceof IRPGraphNode)) continue;
            IRPGraphNode node = (IRPGraphNode) o;
            IRPModelElement mo = node.getModelObject();
            if (mo == null || !"Port".equals(mo.getMetaClass())) continue;
            IRPGraphElement parent = node.getGraphicalParent();
            if (!(parent instanceof IRPGraphNode)) continue;
            IRPModelElement parentMo = ((IRPGraphNode) parent).getModelObject();
            if (parentMo == null) continue;
            String dn = mo.getDisplayName();
            if (dn == null || dn.isEmpty()) dn = mo.getName();
            groups.computeIfAbsent(parentMo.getGUID() + "|" + dn, k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<String, List<IRPGraphNode>> entry : groups.entrySet()) {
            List<IRPGraphNode> siblings = entry.getValue();
            if (siblings.size() < 2) continue;
            String key = entry.getKey();
            String containerGuid = key.substring(0, key.indexOf('|'));
            Set<String> inUse = inUseByContainer.get(containerGuid);
            boolean anyInUse = false;
            if (inUse != null) {
                for (IRPGraphNode sib : siblings) {
                    IRPModelElement mo = sib.getModelObject();
                    if (mo != null && inUse.contains(mo.getGUID())) {
                        anyInUse = true;
                        break;
                    }
                }
            }
            if (!anyInUse) {
                for (IRPGraphNode sib : siblings) {
                    sib.setGraphicalProperty("isVisible", "FALSE");
                }
            }
        }
    }

    /** After creating/resolving createdVariant (the role-specific "Boardnet_Out"/"Boardnet_In"
     * variant just resolved for THIS specific occurrence — see roleSplit's own javadoc in
     * createPort), ensures ONLY it is visible under owner's own graph node (e.g. ComSuite's "Power"
     * box) — never the OTHER same-contract variant. Requested live: "wir können die Sichtbarkeit von
     * Ports steuern... im IBD... d.h. bei PowerUnit ist [nur] Boardnet_In sichtbar, bei den anderen
     * [ComSuite] Boardnet_Out."
     * <p>
     * A port's own occurrence can legitimately be drawn on TWO different diagrams — its native
     * owner's OWN IBD (the "DiagramFrame" view of that element itself, showing its own direct ports)
     * AND that owner's PARENT's IBD (the "part" view, showing the owner as a nested box inside its
     * parent's composition — this is the one that actually matters for a shared/broadcast leaf, since
     * that's where multiple parts sharing the same leaf's contract are all visible together, e.g.
     * ibdSystem_L showing ComSuite/PowerUnit/CN/SN side by side). Originally this only ever touched
     * the first of those (owner.getOwner()'s own IBD) — found live to leave the SECOND, more
     * important one completely unfixed: "in Sn werden noch beide ports angezeigt und in ComSuit ist
     * _out auch noch an" (before any connector existed yet to trigger revealOnlyRoleSplitVariant's
     * own connector-time call). Now applies to both. No-op on whichever one owner has no graph node
     * on yet. Uses the same isVisible-toggle mechanism as revealOnlyRoleSplitVariant above (found via
     * the same live before/after diff experiment) instead of removing/re-adding graph nodes. */
    private void revealOnlyThisNestedPortVariant(IRPModelElement owner, IRPPort createdVariant) {
        IRPModelElement nativeOwner = owner.getOwner();
        if (!(nativeOwner instanceof IRPClass)) return;
        revealOnlyThisNestedPortVariantOnDiagramOf((IRPClass) nativeOwner, owner, createdVariant);
        IRPModelElement partParent = nativeOwner.getOwner();
        if (partParent instanceof IRPClass) {
            revealOnlyThisNestedPortVariantOnDiagramOf((IRPClass) partParent, owner, createdVariant);
        }
    }

    private void revealOnlyThisNestedPortVariantOnDiagramOf(IRPClass diagramOwnerClass, IRPModelElement owner, IRPPort createdVariant) {
        IRPStructureDiagram ibd = diagramService.getIBD(diagramOwnerClass);
        if (ibd == null) return;
        IRPGraphNode containerNode = findGraphNodeForPort(ibd, owner);
        revealOnlyRoleSplitVariant(ibd, containerNode, createdVariant);
    }

    /** Reveals ONLY each part's own DIRECT (top-level) ports on ibd — never their nested
     * decomposition, no matter how many times this is called over the diagram's lifetime. Unlike
     * refreshPortVisibility (which deliberately calls ECAD's own populateIBD twice specifically TO
     * reveal one level of nesting, for the internal/external delegation cases that need it), this
     * calls showAllPorts() ONLY on "DiagramFrame"-typed nodes, never on already-present "Port"-typed
     * ones — populateIBD's own OTHER branch is what would otherwise reveal a Port node's nested
     * children on any call made AFTER that port was already on the diagram (e.g. adding a second
     * Actor to an already-populated Context View). Used exclusively for Context View IBDs —
     * requested live: "im ibd dürfen keine nested proxyports sichtbar sein!" */
    private void revealTopLevelPortsOnly(IRPStructureDiagram ibd) {
        if (ibd == null) return;
        IRPCollection elems = ibd.getGraphicalElements();
        for (int i = 1; i <= elems.getCount(); i++) {
            Object o = elems.getItem(i);
            if (o instanceof IRPGraphNode) {
                IRPGraphNode node = (IRPGraphNode) o;
                if ("DiagramFrame".equals(node.getGraphicalProperty("Type").getValue())) {
                    node.showAllPorts();
                }
            }
        }
    }

    /** Whether parent already has a relation (any type — Composition is the only kind this app
     * ever creates via addAggregationPart, so no need to filter by getRelationType) whose other end
     * is child, by GUID. Used to make addAggregationPart idempotent — see its own javadoc. */
    private boolean hasCompositionTo(IRPClass parent, IRPClassifier child) {
        String childGuid = ((IRPModelElement) child).getGUID();
        IRPCollection relations = parent.getRelations();
        for (int i = 1; i <= relations.getCount(); i++) {
            IRPRelation rel = (IRPRelation) relations.getItem(i);
            IRPClassifier other = rel.getOtherClass();
            if (other != null && childGuid.equals(((IRPModelElement) other).getGUID())) return true;
        }
        return false;
    }

    /** Walks up el's own containment chain (IRPModelElement#getOwner, nested-classifier
     * containment — see createArchitectureElement) until reaching the topmost element that's still
     * an IRPClass — the root System or SystemOfSystem of this whole tree, whose owner is the
     * "Operational" package itself (see containerForKind), not another class. */
    private IRPClass topLevelAncestor(IRPClass el) {
        IRPModelElement owner = el.getOwner();
        while (owner instanceof IRPClass) {
            el = (IRPClass) owner;
            owner = el.getOwner();
        }
        return el;
    }

    /** Find-or-create a Block Definition Diagram (Rhapsody: IRPObjectModelDiagram, stereotyped
     * "Block Definition Diagram" — confirmed live to already exist as a real stereotype in this
     * project, same "Internal Block Diagram"-style naming convention StereotypeService's own
     * ibdStereotype uses, applied the same reuse-not-recreate way as Block/proxyPort/level
     * stereotypes via applyStereotypeSafely) owned by root — hangs directly under the top-level
     * System/SystemOfSystem block itself, not the surrounding package (mirroring how
     * DiagramService#createIBD owns each IBD by its own class via addNewAggr, not by a package) —
     * searched the same way DiagramService#getIBD searches for an existing IBD, via root's own
     * getReferences(). */
    private IRPObjectModelDiagram createOrGetBDD(IRPClass root) {
        String name = "bdd" + root.getName();
        IRPCollection refs = root.getReferences();
        for (int i = 1; i <= refs.getCount(); i++) {
            Object obj = refs.getItem(i);
            if (obj instanceof IRPObjectModelDiagram && name.equals(((IRPModelElement) obj).getName())) {
                return (IRPObjectModelDiagram) obj;
            }
        }
        IRPObjectModelDiagram bdd = (IRPObjectModelDiagram) root.addNewAggr("ObjectModelDiagram", name);
        applyStereotypeSafely((IRPModelElement) bdd, "Block Definition Diagram", "ObjectModelDiagram");
        return bdd;
    }

    /** Find-or-add cls as a graph node on bdd — idempotent, so calling this every time a new
     * descendant is added anywhere in the tree doesn't duplicate nodes already placed. New nodes
     * mirror the frontend's own canvas position when cls has one (see scaledFrontendPosition),
     * falling back to an auto-incrementing grid spot for one that was never manually positioned in
     * the frontend (there's nothing meaningful to mirror then) — exact fallback layout doesn't
     * matter much since Rhapsody users routinely rearrange diagram nodes by hand anyway. */
    private IRPGraphNode addBlockToBDD(IRPObjectModelDiagram bdd, IRPClass cls) {
        IRPCollection elements = bdd.getGraphicalElements();
        int nodeCount = 0;
        for (int i = 1; i <= elements.getCount(); i++) {
            Object obj = elements.getItem(i);
            if (obj instanceof IRPGraphNode) {
                if (cls.equals(((IRPGraphNode) obj).getModelObject())) return (IRPGraphNode) obj;
                nodeCount++;
            }
        }
        int[] pos = scaledFrontendPosition(cls);
        int x = pos != null ? pos[0] : 100 + (nodeCount % 5) * 150;
        int y = pos != null ? pos[1] : 100 + (nodeCount / 5) * 150;
        return bdd.addNewNodeForElement(cls, x, y, 100, 100);
    }

    /** Whether view is one this kind's own manually-dragged position (see setPosition/
     * readPositions) would be mirrored onto the BDD/IBD Rhapsody diagrams from (see
     * scaledFrontendPosition/setPosition's live-update trigger). Aspect kinds mirror only their own
     * matching view name (Functional/Logical/Physical — there's no second view showing that same
     * aspect tree). Every System-of-Systems-chain kind mirrors from EITHER "Structure" or
     * "Operational" — found live: originally only "Operational" counted (same reasoning as
     * view-package routing, where Operational is specifically the one of the two that's an actual
     * SysML architecture view), but System Structure and Operational both render the exact same
     * tree/guids, so a user dragging in either naturally expects it to reflect on the Rhapsody
     * side, not just one specific one of the two — reported live as "I moved elements in the System
     * Structure diagram, but I see nothing in Rhapsody." Each view still keeps its own independent
     * position for the frontend's own layout purposes (unaffected by this); this only controls
     * which of the two a live drag also pushes onto the single shared Rhapsody diagram — a drag in
     * whichever view was touched most recently wins there. */
    private boolean isDiagramPositionView(String kind, String view) {
        String family = HierarchyLevels.kindFamily(kind);
        if ("Structure".equals(family)) {
            return "Structure".equals(view) || "Operational".equals(view);
        }
        return family.equals(view);
    }

    /** cls's own frontend canvas position, scaled by config.ini's [Rhapsody] diagramPositionScale —
     * null if cls was never manually positioned in a view that mirrors onto the Rhapsody diagram
     * (see isDiagramPositionView; the frontend falls back to its own auto-layout in that case, so
     * there's no meaningful position here to mirror). For the Structure family specifically, this
     * prefers "Operational" (the one of the two that's an actual SysML architecture view) but falls
     * back to "Structure" if only that one was ever dragged. */
    private int[] scaledFrontendPosition(IRPClass cls) {
        String kind = levelOf(cls);
        String family = HierarchyLevels.kindFamily(kind);
        if ("Structure".equals(family)) {
            int[] pos = readScaledPosition(cls, "Operational");
            return pos != null ? pos : readScaledPosition(cls, "Structure");
        }
        return readScaledPosition(cls, family);
    }

    private int[] readScaledPosition(IRPClass cls, String view) {
        Double x = doubleTagValue((IRPModelElement) cls, POS_X_TAG_PREFIX + view);
        Double y = doubleTagValue((IRPModelElement) cls, POS_Y_TAG_PREFIX + view);
        if (x == null || y == null) return null;
        double scale = diagramPositionScale();
        return new int[]{(int) Math.round(x * scale), (int) Math.round(y * scale)};
    }

    /** Read fresh from config.ini on every call — see the field-level javadoc on `config` above for
     * why this is deliberately NOT cached. */
    private double diagramPositionScale() {
        try {
            double scale = Double.parseDouble(config.get("Rhapsody", "diagramPositionScale", "1.0"));
            return scale > 0 ? scale : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /** Find-or-create a named top-level package directly under the project root — a real,
     * immediately visible package in Rhapsody's own Model Browser, NOT nested inside the hidden
     * default package (unlike containerFor/findOrCreateInterfaceBlock's fallback). Despite the
     * name, not strictly limited to the four architecture views ("Operational"/"Functional"/
     * "Logical"/"Physical" — PORT_VIEWS) — also reused as-is for "Context" (Actors, see
     * createActor), since the underlying find-or-create-by-name mechanism is identical either way.
     * The addClass/addActor/addUseCase restriction that makes the hidden-package workaround
     * necessary for those (see containerFor's javadoc — "Method X not implemented for Project",
     * found live) does not apply to addNestedPackage, which IRPProject inherits from IRPPackage
     * same as any other package — confirmed live, a package added directly to the project works
     * fine. Still reached by collectArchitectureChildren's existing recursion into every
     * sub-package (unconditional, regardless of nesting depth or which package it's directly
     * under), so nothing else needs to change to keep this app's own "no Package concept"
     * flattening intact. */
    private IRPPackage viewPackage(String viewName) {
        IRPProject project = activeProject();
        IRPCollection nestedPkgs = project.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            IRPPackage p = (IRPPackage) nestedPkgs.getItem(i);
            if (viewName.equals(((IRPModelElement) p).getName())) return p;
        }
        return project.addNestedPackage(viewName);
    }

    /** Find-or-create "Context" (Actors — Context tab) as a package nested *under* "Operational"
     * (see viewPackage), not a standalone top-level one — corrected after standalone was tried
     * first. Still reached by collectArchitectureChildren's existing unconditional recursion into
     * every sub-package regardless of nesting depth, same as any other package here.
     *
     * Renamed live from the original German "Kontext" — requested: "die Pakete Capabilities und
     * Kontext müssen auf englisch übersetzt werden!" ("Capabilities" was already English, so only
     * this one needed changing). A project that already has the legacy "Kontext" package (every
     * project created before this fix) is migrated IN PLACE — same object/GUID, so every Actor and
     * Context View already living inside it stays reachable — rather than silently creating a new,
     * empty "Context" package alongside the old one and stranding all its existing contents. */
    private IRPPackage kontextPackage() {
        IRPPackage operational = viewPackage("Operational");
        IRPCollection nested = operational.getPackages();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPPackage p = (IRPPackage) nested.getItem(i);
            if ("Context".equals(((IRPModelElement) p).getName())) return p;
        }
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPPackage p = (IRPPackage) nested.getItem(i);
            if ("Kontext".equals(((IRPModelElement) p).getName())) {
                ((IRPModelElement) p).setName("Context");
                return p;
            }
        }
        return operational.addNestedPackage("Context");
    }

    /** Find-or-create "Capabilities" (top-level Capability groupings — Capabilities tab) as a
     * package nested under "Operational", same placement rationale as kontextPackage. Each
     * Capability is itself a nested package directly under this one (see createCapability/
     * getCapabilities), owning its own UseCases via native IRPPackage#addUseCase. */
    private IRPPackage capabilitiesPackage() {
        IRPPackage operational = viewPackage("Operational");
        IRPCollection nested = operational.getPackages();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPPackage p = (IRPPackage) nested.getItem(i);
            if ("Capabilities".equals(((IRPModelElement) p).getName())) return p;
        }
        return operational.addNestedPackage("Capabilities");
    }

    /** Find-or-create a single Capability package by its (sanitized) name, directly under
     * capabilitiesPackage() — mirrors capabilitiesPackage/viewPackage/kontextPackage's own
     * find-or-create-by-name pattern. Required, not just an optimization: an interactively-created
     * Capability is never Tag-stamped (see SOURCE_GUID_TAG's javadoc), so re-importing an XML
     * previously exported from this same already-populated project can't match it by sourceGuid — a
     * blind addNestedPackage would then collide with the still-live original, and Rhapsody rejects
     * that outright for Packages ("Cannot add Package due to a clash with an existing element" —
     * found live importing an export back into the same project it came from). Same "two different
     * original names can sanitize to the same Package name" ambiguity as findClassByNameDirect's own
     * first-match-wins limitation — accepted for the same reason. */
    private IRPPackage findOrCreateCapabilityPackage(String sanitizedName) {
        IRPPackage capsPkg = capabilitiesPackage();
        IRPCollection nested = capsPkg.getPackages();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPPackage p = (IRPPackage) nested.getItem(i);
            if (sanitizedName.equals(((IRPModelElement) p).getName())) return p;
        }
        return capsPkg.addNestedPackage(sanitizedName);
    }

    /** Find-or-create a single Context View CLASS by its (sanitized) name, directly under
     * kontextPackage() — a FLAT structure, no separate "ContextViews" sub-package (requested live:
     * "das Package ContextViews brauchen wir nicht eine flache Struktur ist ausreichend!" — see
     * CONTEXT_VIEW_STEREOTYPE's own javadoc for how it's told apart from a real architecture
     * element, now that location alone can't do that). Mirrors findOrCreateCapabilityPackage's own
     * by-name reasoning exactly (an interactively-created Context View is never Tag-stamped, so
     * re-importing an XML previously exported from this same already-populated project needs to
     * match by name, not sourceGuid, or Rhapsody rejects the resulting name clash outright) — except
     * a Context View is a Class (addClass), not a Package. */
    private IRPClass findOrCreateContextViewClass(String sanitizedName) {
        IRPPackage kontext = kontextPackage();
        IRPCollection nested = kontext.getClasses();
        for (int i = 1; i <= nested.getCount(); i++) {
            IRPClass c = (IRPClass) nested.getItem(i);
            if (sanitizedName.equals(((IRPModelElement) c).getName())) return c;
        }
        return kontext.addClass(sanitizedName);
    }

    // ── Tree / node builders ─────────────────────────────────────────────

    /** The model root — "kind":"Model" (not "Package": there is no user-facing Package concept).
     * Any legacy IRPPackage already nested in the project (from other tooling) is flattened
     * transparently — its classes/subpackages are merged straight into the surrounding children
     * list, so no "Package" node ever appears in the tree this app exposes. */
    private Map<String, Object> rootNode(IRPProject project) {
        IRPModelElement el = project;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("guid", el.getGUID());
        node.put("name", el.getName());
        node.put("kind", "Model");
        List<Object> children = new ArrayList<>();
        collectArchitectureChildren(project, children);
        node.put("children", children);
        node.put("ports", new ArrayList<>());
        return node;
    }

    private void collectArchitectureChildren(IRPPackage pkg, List<Object> childrenOut) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            IRPClass cls = (IRPClass) classes.getItem(i);
            // interfaceBlocks are port-typing auxiliaries (findOrCreateInterfaceBlock), not
            // navigable architecture elements — without this they'd double up in the tree,
            // appearing both nested under the port that uses them AND as a spurious top-level
            // sibling (found live: exporting a model with decomposed ports before this filter
            // produced duplicate <element kind="interfaceBlock"> entries alongside the real tree).
            // The FUNCTION_STEREOTYPE check is legacy-only now — Functions are native Operations
            // (see createFunction's javadoc), never their own class going forward, but a project
            // may still have Function-as-class elements left over from before that change.
            // CONTEXT_VIEW_STEREOTYPE: Context Views live directly under kontextPackage() (a FLAT
            // structure — see that constant's own javadoc for why there's no separate sub-package
            // to exclude by name/location the way this used to work), so without this they'd leak
            // into the visible Architecture tree as spurious top-level elements, same bug class as
            // interfaceBlocks needed their own filter for above.
            if (hasStereotype(cls, INTERFACE_BLOCK_STEREOTYPE) || hasStereotype(cls, FUNCTION_STEREOTYPE) || hasStereotype(cls, CONTEXT_VIEW_STEREOTYPE)) continue;
            childrenOut.add(blockNode(cls));
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectArchitectureChildren((IRPPackage) nestedPkgs.getItem(i), childrenOut);
        }
    }

    private boolean hasStereotype(IRPModelElement el, String name) {
        IRPCollection stereotypes = el.getStereotypes();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            if (name.equals(((IRPStereotype) stereotypes.getItem(i)).getName())) return true;
        }
        return false;
    }

    private Map<String, Object> blockNode(IRPClass cls) {
        IRPModelElement el = (IRPModelElement) cls;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("guid", el.getGUID());
        node.put("name", el.getName());
        node.put("kind", levelOf(el));
        node.put("positions", readPositions(el));
        node.put("sizes", readSizes(el));

        List<Object> children = new ArrayList<>();
        IRPCollection nested = cls.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            // Same interfaceBlock/legacy-function filter collectArchitectureChildren already
            // applies at the top level — this recursive walk (a node's own descendants, as opposed
            // to a package's direct children) was missing it, so a port-group container's private
            // contract class (portGroupContainer's "ib"+group, created via the same addClass(...)
            // call real architecture children use) leaked into the visible tree as a spurious child
            // — found live: "warum werden ibinternal und ibexternal als child unter PerformMission
            // und Planning dargestellt?".
            if (item instanceof IRPClass && !hasStereotype((IRPClass) item, INTERFACE_BLOCK_STEREOTYPE)
                    && !hasStereotype((IRPClass) item, FUNCTION_STEREOTYPE)) {
                children.add(blockNode((IRPClass) item));
            }
        }
        node.put("children", children);
        node.put("ports", portsOf(cls, new HashSet<>()));
        node.put("capabilities", getCapabilitiesOf(el.getGUID()));
        node.put("functions", getFunctionsOf(el.getGUID()));
        node.put("allocatedLogicalNodes", getAllocatedLogicalNodesOf(el.getGUID()));
        node.put("allocatedPhysicalNodes", getAllocatedPhysicalNodesOf(el.getGUID()));
        return node;
    }

    /** A classifier's own directly-owned ports (native Rhapsody containment — see createPort's
     * javadoc: a port is always a real, direct member of its owner, never redirected through a
     * separate owner-tagged container). */
    private List<Object> portsOf(IRPClassifier classifier, Set<String> visitedPath) {
        List<Object> ports = new ArrayList<>();
        IRPCollection col = classifier.getPorts();
        for (int i = 1; i <= col.getCount(); i++) {
            ports.add(portNode((IRPModelElement) col.getItem(i), visitedPath));
        }
        return ports;
    }

    /** "children" here is interface decomposition, NOT ports owned by the port element itself —
     * it's the ports owned by this port's interfaceBlock contract (see class javadoc). visitedPath
     * guards against a cyclical contract chain (mirrors ECAD's ICDExporter visitedPath pattern).
     * Top-level call (no immediate parent) — see the 3-arg overload for nested reads, which need
     * their own immediate parent for the composite-guid/direction-override scheme (see
     * NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own javadoc). */
    private Map<String, Object> portNode(IRPModelElement portEl, Set<String> visitedPath) {
        return portNode(portEl, visitedPath, null);
    }

    /** portEl's own effective direction, as seen through immediateParent — used both when reading a
     * port node (portNode) and when discovering broadcast connector candidates
     * (collectInternalTreePortsByContractInClass), so the two stay consistent. null immediateParent
     * (a genuine top-level port) has no override concept — its own Direction tag is always
     * authoritative. See NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own javadoc. */
    private String nestedEffectiveDirection(IRPModelElement portEl, IRPModelElement immediateParent) {
        if (immediateParent != null) {
            String portDisplayName = portEl.getDisplayName();
            String portName = portDisplayName != null && !portDisplayName.isEmpty() ? portDisplayName : portEl.getName();
            String override = tagValue(immediateParent, NESTED_DIRECTION_OVERRIDE_TAG_PREFIX + portName);
            if (override != null && !override.isEmpty()) return override;
        }
        IRPTag directionTag = portEl.getTag(DIRECTION_TAG);
        return directionTag == null ? null : directionTag.getValue();
    }

    /** immediateParent is the port whose OWN contract's getPorts() this portEl was found in (null
     * for a genuine top-level port, owned directly by a Block/Actor) — see
     * NESTED_DIRECTION_OVERRIDE_TAG_PREFIX's own javadoc for why this is threaded through instead of
     * just being an implementation detail of the recursion: it's what lets a nested port's "guid"
     * and "direction" both be resolved PER OCCURRENCE despite portEl itself being one shared native
     * object. */
    private Map<String, Object> portNode(IRPModelElement portEl, Set<String> visitedPath, IRPModelElement immediateParent) {
        Map<String, Object> p = new LinkedHashMap<>();
        // A genuine top-level port keeps its own plain native guid (unchanged, still the single
        // source of truth for that Block's own port). A nested one is addressed as
        // "<immediateParent's guid>|<portEl's own native guid>" — unique per occurrence even though
        // portEl itself is the same shared object everywhere it's reused — see updatePort's own
        // matching decomposition of this composite form.
        p.put("guid", immediateParent == null ? portEl.getGUID() : immediateParent.getGUID() + "|" + portEl.getGUID());
        // Prefers DisplayName over Name — same reasoning/pattern as elementRef: a port's actual Name
        // is sanitized (see createPort's own sanitizedName) whenever the requested name contained
        // characters illegal for Rhapsody's Class-naming rules (e.g. "." from a disambiguated
        // "HEU1.Voice" pick), with the original text kept as DisplayName.
        String portDisplayName = portEl.getDisplayName();
        String portName = portDisplayName != null && !portDisplayName.isEmpty() ? portDisplayName : portEl.getName();
        p.put("name", portName);
        p.put("direction", nestedEffectiveDirection(portEl, immediateParent));
        String view = viewOf(portEl);
        p.put("view", view);

        IRPClassifier contract = getContract(portEl);
        // For a Physical port, "type" is a classification stereotype on portEl itself, not the
        // contract's own name — see applyPortSpec/setPhysicalTypeStereotype's own javadoc. The
        // contract is still resolved/read normally below for decomposition purposes (children) —
        // only the reported "type" value differs.
        p.put("type", "Physical".equals(view) ? physicalTypeOf(portEl) : typeOf(contract));

        List<Object> children = new ArrayList<>();
        if (contract instanceof IRPClass) {
            String contractGuid = ((IRPModelElement) contract).getGUID();
            if (!visitedPath.contains(contractGuid)) {
                Set<String> childPath = new HashSet<>(visitedPath);
                childPath.add(contractGuid);
                IRPCollection nestedPorts = contract.getPorts();
                // A genuine top-level, role-split port (see roleSplit's own javadoc in createPort —
                // same exclusions: not "internal"/"external" collector, not within an external tree)
                // has its contract shared by MULTIPLE occurrences, each of which should only ever see
                // ITS OWN resolved variant (e.g. ComSuite sees "Boardnet_Out", PowerUnit sees
                // "Boardnet_In") — never both. Everything else (external delegation, the "internal"/
                // "external" collector pattern) keeps the original, unfiltered behavior — those never
                // had this two-variant shape to begin with.
                boolean filterByVisibility = immediateParent == null
                        && !isWithinExternalTree(portEl)
                        && !PORT_GROUP_EXTERNAL.equals(portEl.getName()) && !PORT_GROUP_INTERNAL.equals(portEl.getName());
                Set<String> visibleGuids = filterByVisibility ? visibleChildGuidsUnder(portEl) : null;
                for (int i = 1; i <= nestedPorts.getCount(); i++) {
                    IRPModelElement nestedEl = (IRPModelElement) nestedPorts.getItem(i);
                    if (visibleGuids != null && !visibleGuids.contains(nestedEl.getGUID())) continue;
                    children.add(portNode(nestedEl, childPath, portEl));
                }
            }
        }
        p.put("children", children);
        return p;
    }

    private String viewOf(IRPModelElement el) {
        IRPCollection stereotypes = el.getStereotypes();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            String name = ((IRPStereotype) stereotypes.getItem(i)).getName();
            if (PORT_VIEWS.contains(name)) return name;
        }
        return null;
    }

    /** A port's "type" is its resolved interfaceBlock contract's own name — for every view EXCEPT
     * Physical, which reads via physicalTypeOf instead (see portNode's own call site and
     * applyPortSpec's javadoc for why Physical is a classification stereotype, not a shared
     * contract). */
    private String typeOf(IRPClassifier contract) {
        return contract == null ? null : ((IRPModelElement) contract).getName();
    }

    /** The physical realization type (mechanic/electric/radiofrequency/...) stamped on a Physical
     * port by setPhysicalTypeStereotype — whichever stereotype isn't one of the structural markers
     * every port might also carry (proxyPort/PORT_VIEWS/externalInterface). Mirrors that method's
     * own exclusion list exactly, so whatever it stamps is always found back here. */
    private String physicalTypeOf(IRPModelElement portEl) {
        IRPCollection stereotypes = portEl.getStereotypes();
        for (int i = 1; i <= stereotypes.getCount(); i++) {
            String name = ((IRPStereotype) stereotypes.getItem(i)).getName();
            if (PROXY_PORT_STEREOTYPE.equals(name) || PORT_VIEWS.contains(name) || EXTERNAL_INTERFACE_STEREOTYPE.equals(name)) continue;
            return name;
        }
        return null;
    }

    /** The hierarchy level applied via addStereotype in createArchitectureElement, or "Block" for an
     * untyped/legacy Block that predates this app's stereotype-based level tagging. Uses the first
     * applied stereotype if more than one is present. */
    private String levelOf(IRPModelElement el) {
        IRPCollection stereotypes = el.getStereotypes();
        if (stereotypes.getCount() == 0) return "Block";
        return ((IRPStereotype) stereotypes.getItem(1)).getName();   // Rhapsody: 1-based
    }

    private Map<String, Object> elementRef(IRPModelElement el, String kind) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("guid", el.getGUID());
        // A Capability's real Name is sanitized (see sanitizePackageName) — DisplayName carries the
        // user's original text, so it wins whenever one has been set. Every other kind here never
        // sets a DisplayName, so getDisplayName() is empty/null and this just falls back to Name.
        String displayName = el.getDisplayName();
        node.put("name", displayName != null && !displayName.isEmpty() ? displayName : el.getName());
        node.put("kind", kind);
        node.put("x", doubleTagValue(el, POS_X_TAG));
        node.put("y", doubleTagValue(el, POS_Y_TAG));
        node.put("width", doubleTagValue(el, WIDTH_TAG));
        node.put("height", doubleTagValue(el, HEIGHT_TAG));
        return node;
    }

    /** Package names are far more restricted than Class/Actor/UseCase names in Rhapsody — spaces
     * and punctuation are rejected outright (found live: creating a Capability named "defend static
     * area" threw "Name 'defend static area' is illegal for element of type Package"). Since a
     * Capability is backed by an IRPPackage (see capabilitiesPackage), the user's original text is
     * kept as the native DisplayName (setDisplayName, see setDisplayName below) while the actual
     * Package Name is this sanitized form — every character in " .,/" replaced with "_". Read back
     * via elementRef, which prefers DisplayName over Name whenever one is set. */
    private static String sanitizePackageName(String name) {
        return name.replaceAll("[ .,/]", "_");
    }

    /** Rhapsody Tag names reject the same class of character Package names do (see
     * sanitizePackageName above) — PLUS ':' and, confirmed live in a second round after the first
     * fix attempt still failed, '-' too (a raw Rhapsody GUID is full of them). Original failure:
     * "Can't add aggregate of type Tag. Name 'SysMLFrontendX_Context:GUID 4ed9405d-...' is illegal
     * for element of type Tag." Thrown by setPosition/setSize's own POS_X_TAG_PREFIX/
     * WIDTH_TAG_PREFIX + view construction once view became dynamic ("Context:" + a raw Rhapsody
     * GUID — see setPosition's own javadoc for the "Context:" + contextViewGuid convention). Unlike
     * sanitizePackageName (whose original text survives separately via DisplayName, so a lossy
     * replacement is fine there), a Tag's own "view" suffix has nowhere else to keep the original
     * string — readPositions/readSizes need to reconstruct the EXACT original view string as their
     * map key — so this is a reversible per-character encoding instead of a lossy one, replacing
     * only the three characters confirmed illegal (':', ' ', '-') with markers that never otherwise
     * occur in a view string (one of the fixed ARCHITECTURE_VIEWS words, or "Context:" + a GUID's
     * own hex/dash characters). */
    private static String sanitizeTagNameSuffix(String view) {
        return view.replace(":", "_C_").replace(" ", "_S_").replace("-", "_H_");
    }

    private static String desanitizeTagNameSuffix(String sanitized) {
        return sanitized.replace("_H_", "-").replace("_S_", " ").replace("_C_", ":");
    }

    private void setDisplayName(IRPModelElement el, String displayName) {
        el.setDisplayName(displayName);
        el.setIsShowDisplayName(1);
    }

    /** Recursively walks the package tree collecting every Actor, wherever it's nested. UseCases
     * are no longer collected this way — they're only ever listed per-Capability now (see
     * getUseCasesOf), not flattened across the whole project. */
    private void collectActors(IRPPackage pkg, List<Object> actorsOut) {
        IRPCollection actors = pkg.getActors();
        for (int i = 1; i <= actors.getCount(); i++) {
            actorsOut.add(elementRef((IRPModelElement) actors.getItem(i), "Actor"));
        }
        IRPCollection nestedPkgs = pkg.getPackages();
        for (int i = 1; i <= nestedPkgs.getCount(); i++) {
            collectActors((IRPPackage) nestedPkgs.getItem(i), actorsOut);
        }
    }

    /** DIRECT (non-recursive) port-by-name lookup, scoped to one classifier's own immediate ports —
     * used by createPort's find-or-create-by-name fallback (see its javadoc). */
    private IRPModelElement findPortByNameDirect(IRPClassifier classifier, String name) {
        IRPCollection ports = classifier.getPorts();
        for (int i = 1; i <= ports.getCount(); i++) {
            IRPModelElement p = (IRPModelElement) ports.getItem(i);
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    /** DIRECT (non-recursive) child-class-by-name lookup, scoped to one package — used by
     * createArchitectureElement's find-or-create-by-name fallback (see its javadoc) and by
     * findOrCreateInterfaceBlock (an interface's identity is scoped to its own view's package —
     * see that method's own javadoc for why a project-wide search was wrong here). Deliberately
     * NOT recursive — each caller only needs to check the ONE specific container a new element
     * would land in, matching container-scoped semantics for what "the same element" means at
     * that level, not a project-wide "any classifier with this name anywhere" search. */
    private IRPClass findClassByNameDirect(IRPPackage pkg, String name) {
        IRPCollection classes = pkg.getClasses();
        for (int i = 1; i <= classes.getCount(); i++) {
            IRPClass cls = (IRPClass) classes.getItem(i);
            if (name.equals(((IRPModelElement) cls).getName())) return cls;
        }
        return null;
    }

    /** DIRECT (non-recursive) nested-classifier-by-name lookup, scoped to one parent's own
     * immediate children — same reasoning as findClassByNameDirect, for the nested (non-root)
     * branch of createArchitectureElement's find-or-create-by-name fallback. */
    private IRPClass findNestedClassByNameDirect(IRPClass parent, String name) {
        IRPCollection nested = parent.getNestedClassifiers();
        for (int i = 1; i <= nested.getCount(); i++) {
            Object item = nested.getItem(i);
            if (item instanceof IRPClass && name.equals(((IRPModelElement) item).getName())) {
                return (IRPClass) item;
            }
        }
        return null;
    }

    private static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }
}
