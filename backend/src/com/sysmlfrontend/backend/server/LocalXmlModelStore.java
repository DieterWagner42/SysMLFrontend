package com.sysmlfrontend.backend.server;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ModelStore} backed by a plain in-memory tree, used whenever Rhapsody isn't configured or
 * isn't reachable. Deliberately has NO dependency on rhapsody.jar or anything Rhapsody-specific —
 * this is the class {@link BootstrapApp} constructs directly, in its own JVM, without ever
 * relaunching into a second process.
 *
 * "On the fly" here means every mutation is immediately (auto-)persisted to {@code statePath} as
 * XML via {@link ModelXml#export}, mirroring how {@link RhapsodyModelStore} syncs every mutation
 * straight into Rhapsody — this store's equivalent of "the live model" is that XML file. On
 * construction, if the file already exists, its contents are loaded back in via
 * {@link ModelXml#importInto} so state survives a backend restart. Explicit Save/Load XML
 * (POST /api/export, /api/import) still work as ad-hoc snapshot/restore to any other path.
 *
 * Produces the exact same Map/List JSON shapes as RhapsodyModelStore (guid/name/kind/children/
 * ports, guid/name/kind for actors and use cases, guid/name/direction/type for ports) so
 * WebServer and ModelXml stay store-agnostic. GUIDs are locally generated (UUID), meaningful only
 * within this store's own lifetime — they are not Rhapsody GUIDs and won't match anything if the
 * model is later imported into a real Rhapsody project.
 */
public class LocalXmlModelStore implements ModelStore {

    private final ArchNode root = new ArchNode(newGuid(), "Model", "Model");
    private final List<ActorEntry> actors = new ArrayList<>();
    private final List<CapabilityEntry> capabilities = new ArrayList<>();
    private final List<UseCaseEntry> useCases = new ArrayList<>();
    private final List<ContextViewEntry> contextViews = new ArrayList<>();
    private final List<FunctionEntry> functions = new ArrayList<>();
    private final String statePath;
    private String rhapsodyPath;

    public LocalXmlModelStore(String statePath) {
        this.statePath = statePath;
        if (statePath != null) {
            File f = new File(statePath);
            if (f.exists()) {
                try {
                    String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    Map<String, Object> summary = ModelXml.importInto(this, rootGuid(), xml);
                    Object linkedPath = summary.get("rhapsodyPath");
                    if (linkedPath != null) this.rhapsodyPath = (String) linkedPath;
                    System.out.println("[backend] Loaded local state from " + statePath);
                } catch (Exception e) {
                    System.err.println("[backend] Failed to load local state from " + statePath + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public String mode() {
        return "local";
    }

    @Override
    public synchronized String rootGuid() {
        return root.guid;
    }

    @Override
    public synchronized String linkedRhapsodyPath() {
        return rhapsodyPath;
    }

    /** Records where this model was last exported to (see WebServer's /api/exportToRhapsody) so a
     * future export can default to the same target. */
    public synchronized void setLinkedRhapsodyPath(String path) {
        this.rhapsodyPath = path;
        persist();
    }

    /** "New Model" — clears all architecture/context/capabilities and renames the root. The
     * root's own GUID is kept stable across a reset (nothing external needs to re-fetch it). Every
     * fresh model also gets one default top-level node per aspect view (Functional/Logical/
     * Physical) — "System_F"/"System_L"/"System_P" — so those views aren't empty until the user
     * manually adds one; System Structure/Operational have no such default since SoS-vs-System is
     * a real, deliberate choice there (see HierarchyLevels), not a fixed starting point. A model
     * later promoted into Rhapsody via "Export to Rhapsody" carries these over automatically, same
     * as any other element in the local tree at that point. */
    @Override
    public synchronized void reset(String name) {
        root.children.clear();
        root.name = (name == null || name.trim().isEmpty()) ? "Model" : name.trim();
        actors.clear();
        capabilities.clear();
        useCases.clear();
        rhapsodyPath = null;
        persist();
        createArchitectureElement(root.guid, "System_F", HierarchyLevels.FUNCTIONAL_NODE);
        createArchitectureElement(root.guid, "System_L", HierarchyLevels.LOGICAL_NODE);
        createArchitectureElement(root.guid, "System_P", HierarchyLevels.PHYSICAL_NODE);
    }

    @Override
    public synchronized String loadModel(String path) {
        throw new UnsupportedOperationException(
                "No Rhapsody connected — in local mode, use 'Load XML' instead of 'Load Model'.");
    }

    @Override
    public synchronized String selectElement(String guid) {
        throw new UnsupportedOperationException(
                "No Rhapsody connected — selection/highlighting isn't available in local mode.");
    }

    // ── Architecture ─────────────────────────────────────────────────────

    @Override
    public synchronized Map<String, Object> getArchitecture() {
        return toMap(root);
    }

    /** The level is automatic (see {@link HierarchyLevels}), not freely chosen — requestedKind is
     * only honored when parentGuid is the model root, and only as an opt-in for "SystemOfSystem".
     * sourceGuid (see ModelStore's javadoc) is this store's own guid space, so a match is a direct
     * equality check; a fresh creation simply adopts sourceGuid as its own guid instead of minting
     * a random one, so re-importing the same XML lines up with what's already here. */
    @Override
    public synchronized Map<String, Object> createArchitectureElement(String parentGuid, String name, String requestedKind, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            ArchNode existing = findArchNode(root, sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return toMap(existing);
            }
        }
        ArchNode parent = findArchNode(root, parentGuid);
        if (parent == null) {
            throw new IllegalArgumentException("No element found with GUID '" + parentGuid + "'");
        }
        String kind = HierarchyLevels.childLevel(parent == root, parent.kind, requestedKind);
        ArchNode created = new ArchNode(sourceGuid != null ? sourceGuid : newGuid(), name, kind);
        parent.children.add(created);
        persist();
        return toMap(created);
    }

    @Override
    public synchronized void moveElement(String guid, String newParentGuid) {
        ArchNode node = findArchNode(root, guid);
        if (node == null) {
            throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
        }
        ArchNode newParent = findArchNode(root, newParentGuid);
        if (newParent == null) {
            throw new IllegalArgumentException("No element found with GUID '" + newParentGuid + "'");
        }
        if (node == newParent || containsDescendant(node, newParent)) {
            throw new IllegalArgumentException("Cannot move an element under itself or one of its own descendants");
        }
        HierarchyLevels.requireCompatibleMove(node.kind, newParent == root, newParent.kind);
        if (!removeArchNode(root, guid)) {
            throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
        }
        newParent.children.add(node);
        persist();
    }

    private boolean containsDescendant(ArchNode subtree, ArchNode candidate) {
        for (ArchNode c : subtree.children) {
            if (c == candidate || containsDescendant(c, candidate)) return true;
        }
        return false;
    }

    @Override
    public synchronized void renameElement(String guid, String name) {
        requireNonEmpty(name, "name");
        ArchNode n = findArchNode(root, guid);
        if (n != null) {
            n.name = name;
            persist();
            return;
        }
        ActorEntry a = findActor(guid);
        if (a != null) {
            a.name = name;
            persist();
            return;
        }
        CapabilityEntry cap = findCapability(guid);
        if (cap != null) {
            cap.name = name;
            persist();
            return;
        }
        UseCaseEntry u = findUseCase(guid);
        if (u != null) {
            u.name = name;
            persist();
            return;
        }
        ContextViewEntry cv = findContextView(guid);
        if (cv != null) {
            cv.name = name;
            persist();
            return;
        }
        FunctionEntry f = findFunction(guid);
        if (f != null) {
            f.name = name;
            persist();
            return;
        }
        throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
    }

    @Override
    public synchronized void setPosition(String guid, String view, double x, double y) {
        ArchNode n = findArchNode(root, guid);
        if (n != null) {
            if (view == null || view.trim().isEmpty()) {
                throw new IllegalArgumentException("view is required to position an architecture element");
            }
            n.positions.put(view, new double[]{x, y});
            persist();
            return;
        }
        ActorEntry a = findActor(guid);
        if (a != null) {
            a.x = x;
            a.y = y;
            persist();
            return;
        }
        CapabilityEntry cap = findCapability(guid);
        if (cap != null) {
            cap.x = x;
            cap.y = y;
            persist();
            return;
        }
        throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
    }

    @Override
    public synchronized void deleteElement(String guid) {
        if (root.guid.equals(guid)) {
            throw new IllegalArgumentException("The model root cannot be deleted.");
        }
        if (removeArchNode(root, guid) || removePortAnywhere(guid)) {
            persist();
            return;
        }
        if (actors.removeIf(a -> a.guid.equals(guid))) {
            persist();
            return;
        }
        if (capabilities.removeIf(c -> c.guid.equals(guid))) {
            useCases.removeIf(u -> guid.equals(u.capabilityGuid));
            persist();
            return;
        }
        if (useCases.removeIf(u -> u.guid.equals(guid))) {
            persist();
            return;
        }
        if (contextViews.removeIf(c -> c.guid.equals(guid))) {
            persist();
            return;
        }
        if (functions.removeIf(f -> f.guid.equals(guid))) {
            persist();
            return;
        }
        throw new IllegalArgumentException("No element found with GUID '" + guid + "'");
    }

    // ── Context (external systems) ──────────────────────────────────────

    @Override
    public synchronized List<Object> getContext() {
        List<Object> out = new ArrayList<>();
        for (ActorEntry a : actors) out.add(ref(a.guid, a.name, "Actor", a.x, a.y));
        return out;
    }

    @Override
    public synchronized Map<String, Object> createActor(String parentGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            ActorEntry existing = findActor(sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return ref(existing.guid, existing.name, "Actor", existing.x, existing.y);
            }
        }
        if (findArchNode(root, parentGuid) == null) {
            throw new IllegalArgumentException("No element found with GUID '" + parentGuid + "'");
        }
        ActorEntry a = new ActorEntry(sourceGuid != null ? sourceGuid : newGuid(), name);
        actors.add(a);
        persist();
        return ref(a.guid, a.name, "Actor", a.x, a.y);
    }

    // ── Capabilities (top-level grouping; each Capability owns a list of UseCases) ──────

    @Override
    public synchronized List<Object> getCapabilities() {
        List<Object> out = new ArrayList<>();
        for (CapabilityEntry c : capabilities) out.add(ref(c.guid, c.name, "Capability", c.x, c.y));
        return out;
    }

    @Override
    public synchronized Map<String, Object> createCapability(String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            CapabilityEntry existing = findCapability(sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return ref(existing.guid, existing.name, "Capability", existing.x, existing.y);
            }
        }
        CapabilityEntry c = new CapabilityEntry(sourceGuid != null ? sourceGuid : newGuid(), name);
        capabilities.add(c);
        persist();
        return ref(c.guid, c.name, "Capability", c.x, c.y);
    }

    @Override
    public synchronized List<Object> getUseCasesOf(String capabilityGuid) {
        List<Object> out = new ArrayList<>();
        for (UseCaseEntry u : useCases) {
            if (capabilityGuid.equals(u.capabilityGuid)) out.add(ref(u.guid, u.name, "UseCase", null, null));
        }
        return out;
    }

    @Override
    public synchronized Map<String, Object> createUseCase(String capabilityGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            UseCaseEntry existing = findUseCase(sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return ref(existing.guid, existing.name, "UseCase", null, null);
            }
        }
        if (findCapability(capabilityGuid) == null) {
            throw new IllegalArgumentException("No Capability found with GUID '" + capabilityGuid + "'");
        }
        UseCaseEntry u = new UseCaseEntry(sourceGuid != null ? sourceGuid : newGuid(), name);
        u.capabilityGuid = capabilityGuid;
        useCases.add(u);
        persist();
        return ref(u.guid, u.name, "UseCase", null, null);
    }

    // ── Capabilities linked to an architecture element (reference, not ownership) ───────

    @Override
    public synchronized List<Object> getCapabilitiesOf(String ownerGuid) {
        List<Object> out = new ArrayList<>();
        for (CapabilityEntry c : capabilities) {
            if (c.linkedOwners.contains(ownerGuid)) out.add(ref(c.guid, c.name, "Capability", c.x, c.y));
        }
        return out;
    }

    @Override
    public synchronized void linkCapability(String ownerGuid, String capabilityGuid) {
        CapabilityEntry c = findCapability(capabilityGuid);
        if (c == null) {
            throw new IllegalArgumentException("No Capability found with GUID '" + capabilityGuid + "'");
        }
        if (findArchNode(root, ownerGuid) == null) {
            throw new IllegalArgumentException("No element found with GUID '" + ownerGuid + "'");
        }
        if (!c.linkedOwners.contains(ownerGuid)) {
            c.linkedOwners.add(ownerGuid);
            persist();
        }
    }

    @Override
    public synchronized void unlinkCapability(String ownerGuid, String capabilityGuid) {
        CapabilityEntry c = findCapability(capabilityGuid);
        if (c != null && c.linkedOwners.remove(ownerGuid)) {
            persist();
        }
    }

    // ── Context Views (user-defined, top-level groupings of Actors) ─────────────────────

    @Override
    public synchronized List<Object> getContextViews() {
        List<Object> out = new ArrayList<>();
        for (ContextViewEntry c : contextViews) out.add(ref(c.guid, c.name, "ContextView", null, null));
        return out;
    }

    @Override
    public synchronized Map<String, Object> createContextView(String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            ContextViewEntry existing = findContextView(sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return ref(existing.guid, existing.name, "ContextView", null, null);
            }
        }
        ContextViewEntry c = new ContextViewEntry(sourceGuid != null ? sourceGuid : newGuid(), name);
        contextViews.add(c);
        persist();
        return ref(c.guid, c.name, "ContextView", null, null);
    }

    @Override
    public synchronized List<Object> getContextViewsOf(String actorGuid) {
        List<Object> out = new ArrayList<>();
        for (ContextViewEntry c : contextViews) {
            if (c.linkedActors.contains(actorGuid)) out.add(ref(c.guid, c.name, "ContextView", null, null));
        }
        return out;
    }

    @Override
    public synchronized void linkContextView(String actorGuid, String contextViewGuid) {
        ContextViewEntry c = findContextView(contextViewGuid);
        if (c == null) {
            throw new IllegalArgumentException("No Context View found with GUID '" + contextViewGuid + "'");
        }
        if (!c.linkedActors.contains(actorGuid)) {
            c.linkedActors.add(actorGuid);
            persist();
        }
    }

    @Override
    public synchronized void unlinkContextView(String actorGuid, String contextViewGuid) {
        ContextViewEntry c = findContextView(contextViewGuid);
        if (c != null && c.linkedActors.remove(actorGuid)) {
            persist();
        }
    }

    // ── Functions (attached to a FunctionalNode) ─────────────────────────

    @Override
    public synchronized List<Object> getFunctionsOf(String ownerGuid) {
        List<Object> out = new ArrayList<>();
        for (FunctionEntry f : functions) {
            if (ownerGuid.equals(f.ownerGuid)) out.add(ref(f.guid, f.name, "Function", null, null));
        }
        return out;
    }

    @Override
    public synchronized Map<String, Object> createFunction(String parentGuid, String name, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            FunctionEntry existing = findFunction(sourceGuid);
            if (existing != null) {
                existing.name = name;
                persist();
                return ref(existing.guid, existing.name, "Function", null, null);
            }
        }
        if (findArchNode(root, parentGuid) == null) {
            throw new IllegalArgumentException("No element found with GUID '" + parentGuid + "'");
        }
        FunctionEntry f = new FunctionEntry(sourceGuid != null ? sourceGuid : newGuid(), name);
        f.ownerGuid = parentGuid;
        functions.add(f);
        persist();
        return ref(f.guid, f.name, "Function", null, null);
    }

    // ── Interfaces (ports) ──────────────────────────────────────────────

    @Override
    public synchronized List<Object> getPorts(String classifierGuid) {
        return portsToList(portsOwnerList(classifierGuid));
    }

    /** ownerGuid may be a Block/Actor GUID (top-level port) or an existing port's GUID — in the
     * latter case the new port is added directly to that port's own "children" list, representing
     * its decomposition (Rhapsody mode does the equivalent via an interfaceBlock indirection;
     * local mode doesn't need that, a plain nested list is enough). */
    @Override
    public synchronized Map<String, Object> createPort(String ownerGuid, String name, String direction, String type, String view, String sourceGuid) {
        requireNonEmpty(name, "name");
        if (sourceGuid != null) {
            PortEntry existing = findPort(sourceGuid);
            if (existing != null) {
                existing.name = name;
                if (direction != null) existing.direction = direction;
                if (type != null) existing.type = type;
                if (view != null) existing.view = view;
                persist();
                return portToMap(existing);
            }
        }
        List<PortEntry> owner = portsOwnerList(ownerGuid);
        PortEntry p = new PortEntry(sourceGuid != null ? sourceGuid : newGuid(), name, direction, type, view);
        owner.add(p);
        persist();
        return portToMap(p);
    }

    @Override
    public synchronized Map<String, Object> updatePort(String portGuid, String direction, String type, String view) {
        PortEntry p = findPort(portGuid);
        if (p == null) {
            throw new IllegalArgumentException("No element found with GUID '" + portGuid + "'");
        }
        if (direction != null) p.direction = direction;
        if (type != null && !type.isEmpty()) p.type = type;
        if (view != null && !view.isEmpty()) p.view = view;
        persist();
        return portToMap(p);
    }

    // ── Lookup / mutation helpers ────────────────────────────────────────

    private List<PortEntry> portsOwnerList(String ownerGuid) {
        ArchNode n = findArchNode(root, ownerGuid);
        if (n != null) return n.ports;
        ActorEntry a = findActor(ownerGuid);
        if (a != null) return a.ports;
        PortEntry p = findPort(ownerGuid);
        if (p != null) return p.children;
        throw new IllegalArgumentException("Element cannot own ports: " + ownerGuid);
    }

    private ArchNode findArchNode(ArchNode node, String guid) {
        if (node.guid.equals(guid)) return node;
        for (ArchNode c : node.children) {
            ArchNode hit = findArchNode(c, guid);
            if (hit != null) return hit;
        }
        return null;
    }

    private boolean removeArchNode(ArchNode node, String guid) {
        Iterator<ArchNode> it = node.children.iterator();
        while (it.hasNext()) {
            ArchNode c = it.next();
            if (c.guid.equals(guid)) {
                it.remove();
                return true;
            }
            if (removeArchNode(c, guid)) return true;
        }
        return false;
    }

    /** Removes a port anywhere it might be — a direct port of any ArchNode/Actor, or nested inside
     * another port's own decomposition ("children") at any depth. */
    private boolean removePortAnywhere(String guid) {
        if (removePortInArchTree(root, guid)) return true;
        for (ActorEntry a : actors) {
            if (removePortList(a.ports, guid)) return true;
        }
        return false;
    }

    private boolean removePortInArchTree(ArchNode node, String guid) {
        if (removePortList(node.ports, guid)) return true;
        for (ArchNode c : node.children) {
            if (removePortInArchTree(c, guid)) return true;
        }
        return false;
    }

    private boolean removePortList(List<PortEntry> ports, String guid) {
        Iterator<PortEntry> it = ports.iterator();
        while (it.hasNext()) {
            PortEntry p = it.next();
            if (p.guid.equals(guid)) {
                it.remove();
                return true;
            }
            if (removePortList(p.children, guid)) return true;
        }
        return false;
    }

    private ActorEntry findActor(String guid) {
        for (ActorEntry a : actors) if (a.guid.equals(guid)) return a;
        return null;
    }

    private CapabilityEntry findCapability(String guid) {
        for (CapabilityEntry c : capabilities) if (c.guid.equals(guid)) return c;
        return null;
    }

    private UseCaseEntry findUseCase(String guid) {
        for (UseCaseEntry u : useCases) if (u.guid.equals(guid)) return u;
        return null;
    }

    private ContextViewEntry findContextView(String guid) {
        for (ContextViewEntry c : contextViews) if (c.guid.equals(guid)) return c;
        return null;
    }

    private FunctionEntry findFunction(String guid) {
        for (FunctionEntry f : functions) if (f.guid.equals(guid)) return f;
        return null;
    }

    private PortEntry findPort(String guid) {
        PortEntry p = findPortInArchTree(root, guid);
        if (p != null) return p;
        for (ActorEntry a : actors) {
            PortEntry hit = findPortList(a.ports, guid);
            if (hit != null) return hit;
        }
        return null;
    }

    private PortEntry findPortInArchTree(ArchNode node, String guid) {
        PortEntry hit = findPortList(node.ports, guid);
        if (hit != null) return hit;
        for (ArchNode c : node.children) {
            PortEntry childHit = findPortInArchTree(c, guid);
            if (childHit != null) return childHit;
        }
        return null;
    }

    private PortEntry findPortList(List<PortEntry> ports, String guid) {
        for (PortEntry p : ports) {
            if (p.guid.equals(guid)) return p;
            PortEntry hit = findPortList(p.children, guid);
            if (hit != null) return hit;
        }
        return null;
    }

    // ── JSON-shaped map builders (must match RhapsodyModelStore's shapes) ─

    private Map<String, Object> toMap(ArchNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guid", node.guid);
        m.put("name", node.name);
        m.put("kind", node.kind);
        m.put("positions", positionsToMap(node.positions));
        List<Object> children = new ArrayList<>();
        for (ArchNode c : node.children) children.add(toMap(c));
        m.put("children", children);
        m.put("ports", portsToList(node.ports));
        m.put("capabilities", getCapabilitiesOf(node.guid));
        m.put("functions", getFunctionsOf(node.guid));
        return m;
    }

    private List<Object> portsToList(List<PortEntry> ports) {
        List<Object> out = new ArrayList<>();
        for (PortEntry p : ports) out.add(portToMap(p));
        return out;
    }

    private Map<String, Object> portToMap(PortEntry p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guid", p.guid);
        m.put("name", p.name);
        m.put("direction", p.direction);
        m.put("type", p.type);
        m.put("view", p.view);
        m.put("children", portsToList(p.children));
        return m;
    }

    /** view -> {x,y} for every view this element has ever been dragged in (see setPosition) —
     * mirrors RhapsodyModelStore#readPositions so both stores produce the same JSON shape. */
    private Map<String, Object> positionsToMap(Map<String, double[]> positions) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : positions.entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("x", e.getValue()[0]);
            p.put("y", e.getValue()[1]);
            out.put(e.getKey(), p);
        }
        return out;
    }

    private Map<String, Object> ref(String guid, String name, String kind, Double x, Double y) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guid", guid);
        m.put("name", name);
        m.put("kind", kind);
        m.put("x", x);
        m.put("y", y);
        return m;
    }

    private void persist() {
        if (statePath == null) return;
        try {
            String xml = ModelXml.export(this);
            Files.write(Paths.get(statePath), xml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[backend] Failed to auto-save local state to " + statePath + ": " + e.getMessage());
        }
    }

    private static String newGuid() {
        return UUID.randomUUID().toString();
    }

    private static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    // ── In-memory model ──────────────────────────────────────────────────

    private static final class ArchNode {
        final String guid;
        String name;
        final String kind;
        // view -> [x,y] — see ModelStore#setPosition for why architecture elements need one
        // position per view instead of a single shared one.
        final Map<String, double[]> positions = new LinkedHashMap<>();
        final List<ArchNode> children = new ArrayList<>();
        final List<PortEntry> ports = new ArrayList<>();

        ArchNode(String guid, String name, String kind) {
            this.guid = guid;
            this.name = name;
            this.kind = kind;
        }
    }

    private static final class PortEntry {
        final String guid;
        String name;
        String direction;
        String type;
        String view;
        final List<PortEntry> children = new ArrayList<>();

        PortEntry(String guid, String name, String direction, String type, String view) {
            this.guid = guid;
            this.name = name;
            this.direction = direction;
            this.type = type;
            this.view = view;
        }
    }

    private static final class ActorEntry {
        final String guid;
        String name;
        Double x;
        Double y;
        final List<PortEntry> ports = new ArrayList<>();

        ActorEntry(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }

    private static final class CapabilityEntry {
        final String guid;
        String name;
        Double x;
        Double y;
        // Architecture element GUIDs this Capability is linked to — see getCapabilitiesOf/
        // linkCapability/unlinkCapability. A reference list, not ownership (that's useCases below).
        final List<String> linkedOwners = new ArrayList<>();

        CapabilityEntry(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }

    private static final class ContextViewEntry {
        final String guid;
        String name;
        // Actor GUIDs linked to this Context View — see getContextViewsOf/linkContextView/
        // unlinkContextView. A reference list; a Context View owns nothing of its own.
        final List<String> linkedActors = new ArrayList<>();

        ContextViewEntry(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }

    private static final class UseCaseEntry {
        final String guid;
        String name;
        // The Capability this UseCase belongs to — see getUseCasesOf. Not re-parented on a
        // sourceGuid match (matches ports' "no reparent" limitation).
        String capabilityGuid;

        UseCaseEntry(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }

    private static final class FunctionEntry {
        final String guid;
        String name;
        // The FunctionalNode this function is attached to — see getFunctionsOf.
        String ownerGuid;

        FunctionEntry(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }
}
