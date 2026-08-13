package com.sysmlfrontend.backend.server;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Store-agnostic XML snapshot format — works purely against the {@link ModelStore} interface
 * (Map/List shapes only), so it's shared by both {@link RhapsodyModelStore} and
 * {@link LocalXmlModelStore} instead of duplicated per store. Also used internally by
 * LocalXmlModelStore for its own auto-persist/auto-load.
 *
 * <pre>
 * &lt;sysmlModel&gt;
 *   &lt;architecture&gt;
 *     &lt;element guid="..." kind="System" name="Vehicle"&gt;
 *       &lt;position view="Structure" x="120" y="40"/&gt;
 *       &lt;position view="Operational" x="300" y="40"/&gt;
 *       &lt;port guid="..." name="PowerIn" direction="In" type="ElectricalPower"/&gt;
 *       &lt;capabilityLink guid="..." name="Defend Static Areas"/&gt;
 *       &lt;element guid="..." kind="Subsystem" name="Engine"/&gt;
 *     &lt;/element&gt;
 *     &lt;element guid="..." kind="FunctionalNode" name="Navigate"&gt;
 *       &lt;port guid="..." name="PositionIn" direction="In"/&gt;
 *       &lt;function guid="..." name="ComputeRoute"/&gt;
 *     &lt;/element&gt;
 *   &lt;/architecture&gt;
 *   &lt;context&gt;&lt;actor guid="..." name="GroundControl"&gt;&lt;port .../&gt;&lt;/actor&gt;&lt;/context&gt;
 *   &lt;capabilities&gt;
 *     &lt;capability guid="..." name="Defend Static Areas" x="80" y="40"&gt;
 *       &lt;useCase guid="..." name="Detect Threat"/&gt;
 *     &lt;/capability&gt;
 *   &lt;/capabilities&gt;
 * &lt;/sysmlModel&gt;
 * </pre>
 *
 * A "FunctionalNode" element is a second, separate root-level tree living alongside the ordinary
 * System-of-Systems one (see {@link HierarchyLevels}) — shown only in the frontend's Functional
 * architecture view. Its {@code <function>} children (see ModelStore#getFunctionsOf) are the
 * functions it performs — a distinct domain concept attached only to FunctionalNodes, not to
 * System-tree elements.
 *
 * A Capability is a top-level grouping (like a FunctionalNode, but for the Capabilities tab) that
 * owns a list of UseCases (its {@code <useCase>} children, see ModelStore#getUseCasesOf) — written
 * once under {@code <capabilities>}, not per-element. An architecture element only ever carries a
 * *reference* to an existing Capability ({@code <capabilityLink>}, see ModelStore#getCapabilitiesOf/
 * linkCapability), so those are imported after the {@code <capabilities>} section has created every
 * Capability's guid to link against.
 *
 * Every element/actor/useCase/port carries the source store's own {@code guid} so re-importing
 * the same (or a previously-exported) XML updates matching elements in place instead of
 * duplicating them — see {@link ModelStore}'s sourceGuid javadoc for how each store implements
 * that match. A legacy XML without {@code guid} attributes still imports fine (as brand-new
 * elements every time, the old behavior) since it's simply treated as unmatched. There is no user
 * facing "Package" concept — {@code kind} is always one of the four hierarchy levels (see
 * {@link HierarchyLevels}), automatically assigned by the target store from nesting depth, not
 * read from this file. Import still tolerates a legacy {@code <package>} element (from an
 * older export, or hand-edited XML) by flattening its children into its parent — export itself
 * never produces one anymore.
 */
public final class ModelXml {

    private ModelXml() {}

    @SuppressWarnings("unchecked")
    public static String export(ModelStore store) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement("sysmlModel");
            doc.appendChild(root);
            String linkedPath = store.linkedRhapsodyPath();
            if (linkedPath != null && !linkedPath.isEmpty()) {
                root.setAttribute("rhapsodyPath", linkedPath);
            }

            Element archXml = doc.createElement("architecture");
            root.appendChild(archXml);
            Map<String, Object> archRoot = store.getArchitecture();
            for (Object child : (List<Object>) archRoot.get("children")) {
                archXml.appendChild(nodeToXml(doc, (Map<String, Object>) child));
            }

            Element contextXml = doc.createElement("context");
            root.appendChild(contextXml);
            for (Object ref : store.getContext()) {
                Map<String, Object> r = (Map<String, Object>) ref;
                Element actorXml = doc.createElement("actor");
                actorXml.setAttribute("guid", String.valueOf(r.get("guid")));
                actorXml.setAttribute("name", String.valueOf(r.get("name")));
                setPositionAttrs(actorXml, r);
                for (Object port : store.getPorts((String) r.get("guid"))) {
                    actorXml.appendChild(portToXml(doc, (Map<String, Object>) port));
                }
                contextXml.appendChild(actorXml);
            }

            Element capsXml = doc.createElement("capabilities");
            root.appendChild(capsXml);
            for (Object ref : store.getCapabilities()) {
                Map<String, Object> r = (Map<String, Object>) ref;
                Element capXml = doc.createElement("capability");
                capXml.setAttribute("guid", String.valueOf(r.get("guid")));
                capXml.setAttribute("name", String.valueOf(r.get("name")));
                setPositionAttrs(capXml, r);
                for (Object ucRef : store.getUseCasesOf((String) r.get("guid"))) {
                    Map<String, Object> uc = (Map<String, Object>) ucRef;
                    Element ucXml = doc.createElement("useCase");
                    ucXml.setAttribute("guid", String.valueOf(uc.get("guid")));
                    ucXml.setAttribute("name", String.valueOf(uc.get("name")));
                    capXml.appendChild(ucXml);
                }
                capsXml.appendChild(capXml);
            }

            return serialize(doc);
        } catch (Exception e) {
            throw new RuntimeException("XML export failed: " + e.getMessage(), e);
        }
    }

    /** Recreates a previously exported model under {@code rootGuid} in {@code store}. Returns a summary of what was created. */
    public static Map<String, Object> importInto(ModelStore store, String rootGuid, String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("xml must not be empty");
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            int[] counts = {0, 0, 0, 0}; // elements, actors, useCases, capabilities

            // Capabilities are top-level and referenced by guid from <capabilityLink> inside
            // <architecture> below, so they must exist first.
            for (Element capsXml : childElements(root, "capabilities")) {
                for (Element capXml : childElements(capsXml, "capability")) {
                    Map<String, Object> created = store.createCapability(capXml.getAttribute("name"), attrOrNull(capXml, "guid"));
                    counts[3]++;
                    String capGuid = (String) created.get("guid");
                    applyPositionIfPresent(store, capXml, capGuid);
                    for (Element ucXml : childElements(capXml, "useCase")) {
                        store.createUseCase(capGuid, ucXml.getAttribute("name"), attrOrNull(ucXml, "guid"));
                        counts[2]++;
                    }
                }
            }
            for (Element archXml : childElements(root, "architecture")) {
                importArchitectureChildren(store, archXml, rootGuid, counts);
            }
            for (Element contextXml : childElements(root, "context")) {
                for (Element actorXml : childElements(contextXml, "actor")) {
                    Map<String, Object> created = store.createActor(rootGuid, actorXml.getAttribute("name"), attrOrNull(actorXml, "guid"));
                    counts[1]++;
                    String actorGuid = (String) created.get("guid");
                    applyPositionIfPresent(store, actorXml, actorGuid);
                    importPorts(store, actorXml, actorGuid);
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("elementsCreated", counts[0]);
            summary.put("actorsCreated", counts[1]);
            summary.put("useCasesCreated", counts[2]);
            summary.put("capabilitiesCreated", counts[3]);
            String rhapsodyPath = root.getAttribute("rhapsodyPath");
            if (!rhapsodyPath.isEmpty()) summary.put("rhapsodyPath", rhapsodyPath);
            return summary;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("XML import failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Element nodeToXml(Document doc, Map<String, Object> node) {
        // Only ever called on architecture-root children, never the root itself (see export()) —
        // "Package"/"Model" never appear here, only the four hierarchy levels (or a legacy "Block").
        Element elXml = doc.createElement("element");
        elXml.setAttribute("guid", String.valueOf(node.get("guid")));
        elXml.setAttribute("kind", String.valueOf(node.get("kind")));
        elXml.setAttribute("name", String.valueOf(node.get("name")));
        setPositionsChildren(doc, elXml, node);
        List<Object> ports = (List<Object>) node.get("ports");
        if (ports != null) {
            for (Object port : ports) elXml.appendChild(portToXml(doc, (Map<String, Object>) port));
        }
        List<Object> capabilities = (List<Object>) node.get("capabilities");
        if (capabilities != null) {
            for (Object cap : capabilities) elXml.appendChild(capabilityLinkToXml(doc, (Map<String, Object>) cap));
        }
        List<Object> functions = (List<Object>) node.get("functions");
        if (functions != null) {
            for (Object fn : functions) elXml.appendChild(functionToXml(doc, (Map<String, Object>) fn));
        }
        for (Object child : (List<Object>) node.get("children")) {
            elXml.appendChild(nodeToXml(doc, (Map<String, Object>) child));
        }
        return elXml;
    }

    /** A reference from an architecture element to an existing top-level Capability (see
     * ModelStore#getCapabilitiesOf/linkCapability) — guid is what matters for re-linking on
     * import; name is written only for XML readability. */
    @SuppressWarnings("unchecked")
    private static Element capabilityLinkToXml(Document doc, Map<String, Object> cap) {
        Element linkXml = doc.createElement("capabilityLink");
        linkXml.setAttribute("guid", String.valueOf(cap.get("guid")));
        linkXml.setAttribute("name", String.valueOf(cap.get("name")));
        return linkXml;
    }

    /** A function attached to a FunctionalNode (see ModelStore#getFunctionsOf) — same shape as a
     * capability (just a name), but a separate XML tag since they're a distinct domain concept. */
    @SuppressWarnings("unchecked")
    private static Element functionToXml(Document doc, Map<String, Object> fn) {
        Element fnXml = doc.createElement("function");
        fnXml.setAttribute("guid", String.valueOf(fn.get("guid")));
        fnXml.setAttribute("name", String.valueOf(fn.get("name")));
        return fnXml;
    }

    @SuppressWarnings("unchecked")
    private static Element portToXml(Document doc, Map<String, Object> port) {
        Element portXml = doc.createElement("port");
        portXml.setAttribute("guid", String.valueOf(port.get("guid")));
        portXml.setAttribute("name", String.valueOf(port.get("name")));
        if (port.get("direction") != null) portXml.setAttribute("direction", String.valueOf(port.get("direction")));
        if (port.get("type") != null) portXml.setAttribute("type", String.valueOf(port.get("type")));
        if (port.get("view") != null) portXml.setAttribute("view", String.valueOf(port.get("view")));
        List<Object> children = (List<Object>) port.get("children");
        if (children != null) {
            for (Object child : children) portXml.appendChild(portToXml(doc, (Map<String, Object>) child));
        }
        return portXml;
    }

    private static String serialize(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    private static void importArchitectureChildren(ModelStore store, Element parentXml, String parentGuid, int[] counts) {
        for (Element child : childElements(parentXml, null)) {
            if ("element".equals(child.getTagName())) {
                Map<String, Object> created = store.createArchitectureElement(
                        parentGuid, child.getAttribute("name"), child.getAttribute("kind"), attrOrNull(child, "guid"));
                counts[0]++;
                String newGuid = (String) created.get("guid");
                applyPositionsOrLegacy(store, child, newGuid);
                importPorts(store, child, newGuid);
                importCapabilityLinks(store, child, newGuid);
                importFunctions(store, child, newGuid);
                importArchitectureChildren(store, child, newGuid, counts);
            } else if ("package".equals(child.getTagName())) {
                importArchitectureChildren(store, child, parentGuid, counts);
            }
        }
    }

    private static void importPorts(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element portXml : childElements(ownerXml, "port")) {
            Map<String, Object> created = store.createPort(ownerGuid, portXml.getAttribute("name"),
                    attrOrNull(portXml, "direction"), attrOrNull(portXml, "type"), attrOrNull(portXml, "view"),
                    attrOrNull(portXml, "guid"));
            // Nested <port> children are this port's own decomposition — recurse using the new
            // port's guid as owner (both stores redirect that to the port's interfaceBlock, or an
            // equivalent nested list, so the same createPort() call works uniformly here).
            importPorts(store, portXml, (String) created.get("guid"));
        }
    }

    /** <capabilityLink> children of an <element> — re-links ownerGuid to an already-imported
     * top-level Capability by guid (see capabilityLinkToXml; capabilities are created up front in
     * importInto, before architecture, specifically so this guid always resolves). */
    private static void importCapabilityLinks(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element linkXml : childElements(ownerXml, "capabilityLink")) {
            String capGuid = attrOrNull(linkXml, "guid");
            if (capGuid != null) store.linkCapability(ownerGuid, capGuid);
        }
    }

    /** <function> children of an <element> — attached to ownerGuid (a FunctionalNode) the same way
     * importCapabilities attaches <capability> children (see functionToXml). */
    private static void importFunctions(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element fnXml : childElements(ownerXml, "function")) {
            store.createFunction(ownerGuid, fnXml.getAttribute("name"), attrOrNull(fnXml, "guid"));
        }
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && (tagName == null || tagName.equals(((Element) n).getTagName()))) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static String attrOrNull(Element el, String name) {
        String v = el.getAttribute(name);
        return v.isEmpty() ? null : v;
    }

    /** node's "x"/"y" entries are Double, present only once an actor/useCase has actually been
     * dragged (see ModelStore#setPosition) — absent (null) means "no saved position, let the
     * frontend's auto-layout place it", which is why these attributes are only written when both
     * are set. Actors/useCases have no view concept (unlike architecture elements, which use
     * setPositionsChildren/applyPositionsOrLegacy below instead), so they keep this flat form. */
    private static void setPositionAttrs(Element xml, Map<String, Object> node) {
        Object x = node.get("x");
        Object y = node.get("y");
        if (x != null && y != null) {
            xml.setAttribute("x", String.valueOf(x));
            xml.setAttribute("y", String.valueOf(y));
        }
    }

    /** Applies a previously-exported position (if the XML carries one) to the just-created/matched
     * actor/useCase. No-op when the source element was never manually positioned. */
    private static void applyPositionIfPresent(ModelStore store, Element xml, String guid) {
        String x = attrOrNull(xml, "x");
        String y = attrOrNull(xml, "y");
        if (x != null && y != null) {
            try {
                store.setPosition(guid, null, Double.parseDouble(x), Double.parseDouble(y));
            } catch (NumberFormatException ignored) {
                // Malformed/hand-edited XML — skip rather than fail the whole import over cosmetics.
            }
        }
    }

    /** An architecture element's "positions" entry is {view: {x,y}} — one saved position per
     * Architecture-tab view (see ModelStore#setPosition), since System Structure and Operational
     * both render the same tree/guids and must not share a single position. Written as one nested
     * &lt;position view="..." x="..." y="..."/&gt; child per view that actually has a saved
     * position, rather than flat x/y attributes on &lt;element&gt; itself. */
    @SuppressWarnings("unchecked")
    private static void setPositionsChildren(Document doc, Element elXml, Map<String, Object> node) {
        Object positionsObj = node.get("positions");
        if (!(positionsObj instanceof Map)) return;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) positionsObj).entrySet()) {
            Map<String, Object> p = (Map<String, Object>) e.getValue();
            Element posXml = doc.createElement("position");
            posXml.setAttribute("view", e.getKey());
            posXml.setAttribute("x", String.valueOf(p.get("x")));
            posXml.setAttribute("y", String.valueOf(p.get("y")));
            elXml.appendChild(posXml);
        }
    }

    /** Reads back an architecture element's &lt;position&gt; children (the normal, current format)
     * and applies each to the just-created/matched element. Falls back to a legacy flat x/y
     * attribute pair directly on &lt;element&gt; itself (the format written before positions became
     * per-view) — applied to EVERY view in {@link ModelStore#ARCHITECTURE_VIEWS} so a model
     * exported before this change doesn't visually jump on its first import after upgrading; a
     * later drag in any one view then naturally splits off just that view's position going
     * forward. */
    private static void applyPositionsOrLegacy(ModelStore store, Element ownerXml, String guid) {
        List<Element> posEls = childElements(ownerXml, "position");
        if (!posEls.isEmpty()) {
            for (Element posXml : posEls) {
                String view = attrOrNull(posXml, "view");
                String x = attrOrNull(posXml, "x");
                String y = attrOrNull(posXml, "y");
                if (view == null || x == null || y == null) continue;
                try {
                    store.setPosition(guid, view, Double.parseDouble(x), Double.parseDouble(y));
                } catch (NumberFormatException ignored) {
                    // Malformed/hand-edited XML — skip rather than fail the whole import over cosmetics.
                }
            }
            return;
        }
        String x = attrOrNull(ownerXml, "x");
        String y = attrOrNull(ownerXml, "y");
        if (x != null && y != null) {
            try {
                double xd = Double.parseDouble(x);
                double yd = Double.parseDouble(y);
                for (String view : ModelStore.ARCHITECTURE_VIEWS) store.setPosition(guid, view, xd, yd);
            } catch (NumberFormatException ignored) {
                // Malformed/hand-edited XML — skip rather than fail the whole import over cosmetics.
            }
        }
    }
}
