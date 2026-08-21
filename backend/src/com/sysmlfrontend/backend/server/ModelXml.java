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
 *       &lt;size view="Operational" width="220" height="140"/&gt;
 *       &lt;documentation&gt;Free-text notes for this element&lt;/documentation&gt;
 *       &lt;port guid="..." name="PowerIn" direction="In" type="ElectricalPower"/&gt;
 *       &lt;capabilityLink guid="..." name="Defend Static Areas"/&gt;
 *       &lt;element guid="..." kind="Subsystem" name="Engine"/&gt;
 *     &lt;/element&gt;
 *     &lt;element guid="..." kind="FunctionalNode" name="Navigate"&gt;
 *       &lt;port guid="..." name="PositionIn" direction="In"/&gt;
 *       &lt;function guid="..." name="ComputeRoute"/&gt;
 *     &lt;/element&gt;
 *   &lt;/architecture&gt;
 *   &lt;contextViews&gt;
 *     &lt;contextView guid="..." name="Maintenance"/&gt;
 *   &lt;/contextViews&gt;
 *   &lt;context&gt;
 *     &lt;actor guid="..." name="GroundControl"&gt;
 *       &lt;port .../&gt;
 *       &lt;contextViewLink guid="..." name="Maintenance"/&gt;
 *     &lt;/actor&gt;
 *   &lt;/context&gt;
 *   &lt;capabilities&gt;
 *     &lt;capability guid="..." name="Defend Static Areas" x="80" y="40"&gt;
 *       &lt;useCase guid="..." name="Detect Threat"/&gt;
 *     &lt;/capability&gt;
 *   &lt;/capabilities&gt;
 * &lt;/sysmlModel&gt;
 * </pre>
 *
 * A Context View is a top-level, user-created grouping of Actors (see ModelStore#getContextViews) —
 * written once under {@code <contextViews>}, not per-actor. An Actor only ever carries a
 * *reference* to an existing Context View ({@code <contextViewLink>}, see
 * ModelStore#getContextViewsOf/linkContextView), so those are imported after the
 * {@code <contextViews>} section has created every Context View's guid to link against — same
 * pattern as {@code <capabilityLink>} vs {@code <capabilities>}. This section was missing entirely
 * until found live: a Context View created in local mode had no export/import support at all, so it
 * was silently lost on every backend restart (never actually reached local-model.xml's auto-save,
 * despite appearing to work fine for as long as that one process stayed running).
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
 * A {@code <documentation>} child (free-text notes) may appear on ANY element/actor/capability/
 * useCase/port/function/contextView — see {@link ModelStore#getDocumentation}/{@code
 * setDocumentation} — omitted entirely when empty rather than always written as an empty tag.
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
                archXml.appendChild(nodeToXml(doc, (Map<String, Object>) child, store));
            }

            // Context Views are top-level and referenced by guid from <contextViewLink> inside each
            // <actor> below (same reasoning/ordering as <capabilities> vs <capabilityLink>) — found
            // missing entirely live: a Context View created in local mode (LocalXmlModelStore's own
            // in-memory contextViews list) had NO export/import support at all, so it silently never
            // reached local-model.xml's auto-save — surviving only as long as the backend process
            // stayed running, invisibly lost on every restart. Reported live only indirectly, as
            // Context Views the user had created (visible in the running session) being gone after an
            // unrelated backend restart — root-caused by grepping local-model.xml directly and finding
            // no <contextViews> section at all despite getContextViews() returning entries pre-restart.
            Element contextViewsXml = doc.createElement("contextViews");
            root.appendChild(contextViewsXml);
            for (Object ref : store.getContextViews()) {
                Map<String, Object> r = (Map<String, Object>) ref;
                Element cvXml = doc.createElement("contextView");
                cvXml.setAttribute("guid", String.valueOf(r.get("guid")));
                cvXml.setAttribute("name", String.valueOf(r.get("name")));
                documentationToXml(doc, cvXml, store, (String) r.get("guid"));
                contextViewsXml.appendChild(cvXml);
            }

            Element contextXml = doc.createElement("context");
            root.appendChild(contextXml);
            for (Object ref : store.getContext()) {
                Map<String, Object> r = (Map<String, Object>) ref;
                Element actorXml = doc.createElement("actor");
                actorXml.setAttribute("guid", String.valueOf(r.get("guid")));
                actorXml.setAttribute("name", String.valueOf(r.get("name")));
                setPositionAttrs(actorXml, r);
                setSizeAttrs(actorXml, r);
                documentationToXml(doc, actorXml, store, (String) r.get("guid"));
                for (Object port : store.getPorts((String) r.get("guid"))) {
                    actorXml.appendChild(portToXml(doc, (Map<String, Object>) port, store));
                }
                for (Object cvRef : store.getContextViewsOf((String) r.get("guid"))) {
                    actorXml.appendChild(contextViewLinkToXml(doc, (Map<String, Object>) cvRef));
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
                setSizeAttrs(capXml, r);
                documentationToXml(doc, capXml, store, (String) r.get("guid"));
                for (Object ucRef : store.getUseCasesOf((String) r.get("guid"))) {
                    Map<String, Object> uc = (Map<String, Object>) ucRef;
                    Element ucXml = doc.createElement("useCase");
                    ucXml.setAttribute("guid", String.valueOf(uc.get("guid")));
                    ucXml.setAttribute("name", String.valueOf(uc.get("name")));
                    exportUseCaseDetail(doc, ucXml, store.getUseCaseDetail((String) uc.get("guid")));
                    documentationToXml(doc, ucXml, store, (String) uc.get("guid"));
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
            int[] counts = {0, 0, 0, 0, 0}; // elements, actors, useCases, capabilities, contextViews

            // Capabilities are top-level and referenced by guid from <capabilityLink> inside
            // <architecture> below, so they must exist first.
            for (Element capsXml : childElements(root, "capabilities")) {
                for (Element capXml : childElements(capsXml, "capability")) {
                    Map<String, Object> created = store.createCapability(capXml.getAttribute("name"), attrOrNull(capXml, "guid"));
                    counts[3]++;
                    String capGuid = (String) created.get("guid");
                    applyPositionIfPresent(store, capXml, capGuid);
                    applySizeIfPresent(store, capXml, capGuid);
                    applyDocumentationIfPresent(store, capXml, capGuid);
                    for (Element ucXml : childElements(capXml, "useCase")) {
                        Map<String, Object> ucCreated = store.createUseCase(capGuid, ucXml.getAttribute("name"), attrOrNull(ucXml, "guid"));
                        counts[2]++;
                        String ucGuid = (String) ucCreated.get("guid");
                        importUseCaseDetail(store, ucXml, ucGuid);
                        applyDocumentationIfPresent(store, ucXml, ucGuid);
                    }
                }
            }
            // Context Views are likewise top-level and referenced by guid from <contextViewLink>
            // inside each <actor> below, so they must exist before <context> is processed.
            for (Element contextViewsXml : childElements(root, "contextViews")) {
                for (Element cvXml : childElements(contextViewsXml, "contextView")) {
                    Map<String, Object> cvCreated = store.createContextView(cvXml.getAttribute("name"), attrOrNull(cvXml, "guid"));
                    counts[4]++;
                    applyDocumentationIfPresent(store, cvXml, (String) cvCreated.get("guid"));
                }
            }
            // Collected during the architecture walk, resolved only once it's fully done — see
            // collectLogicalNodeLinks' own javadoc for why a FunctionalNode-to-LogicalNode (or
            // LogicalNode-to-PhysicalNode) link can't be resolved inline the way a capabilityLink can.
            List<String[]> pendingLogicalNodeLinks = new ArrayList<>();
            List<String[]> pendingPhysicalNodeLinks = new ArrayList<>();
            for (Element archXml : childElements(root, "architecture")) {
                importArchitectureChildren(store, archXml, rootGuid, counts, pendingLogicalNodeLinks, pendingPhysicalNodeLinks);
            }
            for (String[] link : pendingLogicalNodeLinks) {
                store.linkLogicalNode(link[0], link[1]);
            }
            for (String[] link : pendingPhysicalNodeLinks) {
                store.linkPhysicalNode(link[0], link[1]);
            }
            for (Element contextXml : childElements(root, "context")) {
                for (Element actorXml : childElements(contextXml, "actor")) {
                    Map<String, Object> created = store.createActor(rootGuid, actorXml.getAttribute("name"), attrOrNull(actorXml, "guid"));
                    counts[1]++;
                    String actorGuid = (String) created.get("guid");
                    applyPositionIfPresent(store, actorXml, actorGuid);
                    applySizeIfPresent(store, actorXml, actorGuid);
                    applyDocumentationIfPresent(store, actorXml, actorGuid);
                    importPorts(store, actorXml, actorGuid);
                    importContextViewLinks(store, actorXml, actorGuid);
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("elementsCreated", counts[0]);
            summary.put("actorsCreated", counts[1]);
            summary.put("useCasesCreated", counts[2]);
            summary.put("capabilitiesCreated", counts[3]);
            summary.put("contextViewsCreated", counts[4]);
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
    private static Element nodeToXml(Document doc, Map<String, Object> node, ModelStore store) {
        // Only ever called on architecture-root children, never the root itself (see export()) —
        // "Package"/"Model" never appear here, only the four hierarchy levels (or a legacy "Block").
        Element elXml = doc.createElement("element");
        String guid = String.valueOf(node.get("guid"));
        elXml.setAttribute("guid", guid);
        elXml.setAttribute("kind", String.valueOf(node.get("kind")));
        elXml.setAttribute("name", String.valueOf(node.get("name")));
        setPositionsChildren(doc, elXml, node);
        setSizesChildren(doc, elXml, node);
        documentationToXml(doc, elXml, store, guid);
        List<Object> ports = (List<Object>) node.get("ports");
        if (ports != null) {
            for (Object port : ports) elXml.appendChild(portToXml(doc, (Map<String, Object>) port, store));
        }
        List<Object> capabilities = (List<Object>) node.get("capabilities");
        if (capabilities != null) {
            for (Object cap : capabilities) elXml.appendChild(capabilityLinkToXml(doc, (Map<String, Object>) cap));
        }
        List<Object> functions = (List<Object>) node.get("functions");
        if (functions != null) {
            for (Object fn : functions) elXml.appendChild(functionToXml(doc, (Map<String, Object>) fn, store));
        }
        List<Object> allocatedLogicalNodes = (List<Object>) node.get("allocatedLogicalNodes");
        if (allocatedLogicalNodes != null) {
            for (Object ln : allocatedLogicalNodes) elXml.appendChild(logicalNodeLinkToXml(doc, (Map<String, Object>) ln));
        }
        List<Object> allocatedPhysicalNodes = (List<Object>) node.get("allocatedPhysicalNodes");
        if (allocatedPhysicalNodes != null) {
            for (Object pn : allocatedPhysicalNodes) elXml.appendChild(physicalNodeLinkToXml(doc, (Map<String, Object>) pn));
        }
        for (Object child : (List<Object>) node.get("children")) {
            elXml.appendChild(nodeToXml(doc, (Map<String, Object>) child, store));
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

    /** A reference from a FunctionalNode to a LogicalNode it allocates to (see
     * ModelStore#getAllocatedLogicalNodesOf/linkLogicalNode) — same shape/reasoning as
     * capabilityLinkToXml, but UNLIKE that one, the target is another architecture element within
     * this same {@code <architecture>} block rather than an already-created top-level Capability,
     * so it can't be resolved immediately on import — see importLogicalNodeLinks' own javadoc. */
    @SuppressWarnings("unchecked")
    private static Element logicalNodeLinkToXml(Document doc, Map<String, Object> ln) {
        Element linkXml = doc.createElement("logicalNodeLink");
        linkXml.setAttribute("guid", String.valueOf(ln.get("guid")));
        linkXml.setAttribute("name", String.valueOf(ln.get("name")));
        return linkXml;
    }

    /** A reference from a LogicalNode to a PhysicalNode it allocates to (see
     * ModelStore#getAllocatedPhysicalNodesOf/linkPhysicalNode) — same shape/reasoning as
     * logicalNodeLinkToXml, just Logical→Physical instead of Functional→Logical. */
    @SuppressWarnings("unchecked")
    private static Element physicalNodeLinkToXml(Document doc, Map<String, Object> pn) {
        Element linkXml = doc.createElement("physicalNodeLink");
        linkXml.setAttribute("guid", String.valueOf(pn.get("guid")));
        linkXml.setAttribute("name", String.valueOf(pn.get("name")));
        return linkXml;
    }

    /** A reference from an Actor to an existing top-level Context View (see
     * ModelStore#getContextViewsOf/linkContextView) — same shape/reasoning as capabilityLinkToXml,
     * just for Actor↔ContextView instead of element↔Capability. */
    @SuppressWarnings("unchecked")
    private static Element contextViewLinkToXml(Document doc, Map<String, Object> cv) {
        Element linkXml = doc.createElement("contextViewLink");
        linkXml.setAttribute("guid", String.valueOf(cv.get("guid")));
        linkXml.setAttribute("name", String.valueOf(cv.get("name")));
        return linkXml;
    }

    /** A function attached to a FunctionalNode (see ModelStore#getFunctionsOf) — same shape as a
     * capability (just a name), but a separate XML tag since they're a distinct domain concept. */
    @SuppressWarnings("unchecked")
    private static Element functionToXml(Document doc, Map<String, Object> fn, ModelStore store) {
        Element fnXml = doc.createElement("function");
        String guid = String.valueOf(fn.get("guid"));
        fnXml.setAttribute("guid", guid);
        fnXml.setAttribute("name", String.valueOf(fn.get("name")));
        documentationToXml(doc, fnXml, store, guid);
        return fnXml;
    }

    @SuppressWarnings("unchecked")
    private static Element portToXml(Document doc, Map<String, Object> port, ModelStore store) {
        Element portXml = doc.createElement("port");
        String guid = String.valueOf(port.get("guid"));
        portXml.setAttribute("guid", guid);
        portXml.setAttribute("name", String.valueOf(port.get("name")));
        if (port.get("direction") != null) portXml.setAttribute("direction", String.valueOf(port.get("direction")));
        if (port.get("type") != null) portXml.setAttribute("type", String.valueOf(port.get("type")));
        if (port.get("view") != null) portXml.setAttribute("view", String.valueOf(port.get("view")));
        documentationToXml(doc, portXml, store, guid);
        List<Object> children = (List<Object>) port.get("children");
        if (children != null) {
            for (Object child : children) portXml.appendChild(portToXml(doc, (Map<String, Object>) child, store));
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

    /** Writes a UseCase's full detail (goal, actors, preconditions, basicPath, alternatives,
     * extensions, postCondition) as children of a {@code <useCase>} element. Actor references are
     * stored as guid+name (mirroring <contextViewLink guid name>) so both local and Rhapsody
     * round-trips resolve them. */
    @SuppressWarnings("unchecked")
    private static void exportUseCaseDetail(Document doc, Element ucXml, Map<String, Object> detail) {
        if (detail.get("goal") != null) {
            Element goal = doc.createElement("goal");
            goal.setTextContent(String.valueOf(detail.get("goal")));
            ucXml.appendChild(goal);
        }
        if (detail.get("actors") instanceof List) {
            Element actors = doc.createElement("actors");
            for (Object a : (List<Object>) detail.get("actors")) {
                Map<String, Object> actor = (Map<String, Object>) a;
                Element ref = doc.createElement("actorRef");
                ref.setAttribute("guid", String.valueOf(actor.get("guid")));
                ref.setAttribute("name", String.valueOf(actor.get("name")));
                actors.appendChild(ref);
            }
            ucXml.appendChild(actors);
        }
        if (detail.get("preconditions") instanceof List) {
            ucXml.appendChild(stringList(doc, "preconditions", "precondition", (List<Object>) detail.get("preconditions")));
        }
        if (detail.get("basicPath") instanceof List) {
            ucXml.appendChild(stringList(doc, "basicPath", "step", (List<Object>) detail.get("basicPath")));
        }
        if (detail.get("alternatives") instanceof List) {
            Element alts = doc.createElement("alternatives");
            for (Object o : (List<Object>) detail.get("alternatives")) {
                Map<String, Object> alt = (Map<String, Object>) o;
                Element a = doc.createElement("alternative");
                Element altTitle = doc.createElement("title");
                altTitle.setTextContent(String.valueOf(alt.get("title")));
                a.appendChild(altTitle);
                a.appendChild(stringList(doc, "stepRefs", "ref", (List<Object>) alt.get("stepRefs")));
                Element what = doc.createElement("whatHappens");
                what.setTextContent(String.valueOf(alt.get("whatHappens")));
                a.appendChild(what);
                a.appendChild(stringList(doc, "subSteps", "step", (List<Object>) alt.get("subSteps")));
                Element post = doc.createElement("postCondition");
                post.setTextContent(String.valueOf(alt.get("postCondition")));
                a.appendChild(post);
                alts.appendChild(a);
            }
            ucXml.appendChild(alts);
        }
        if (detail.get("extensions") instanceof List) {
            Element exts = doc.createElement("extensions");
            for (Object o : (List<Object>) detail.get("extensions")) {
                Map<String, Object> ext = (Map<String, Object>) o;
                Element e = doc.createElement("extension");
                Element extTitle = doc.createElement("title");
                extTitle.setTextContent(String.valueOf(ext.get("title")));
                e.appendChild(extTitle);
                Element trig = doc.createElement("triggerText");
                trig.setTextContent(String.valueOf(ext.get("triggerText")));
                e.appendChild(trig);
                e.appendChild(stringList(doc, "subSteps", "step", (List<Object>) ext.get("subSteps")));
                exts.appendChild(e);
            }
            ucXml.appendChild(exts);
        }
        if (detail.get("postCondition") != null) {
            Element post = doc.createElement("postCondition");
            post.setTextContent(String.valueOf(detail.get("postCondition")));
            ucXml.appendChild(post);
        }
    }

    private static Element stringList(Document doc, String listTag, String itemTag, List<Object> items) {
        Element list = doc.createElement(listTag);
        for (Object item : items) {
            Element el = doc.createElement(itemTag);
            el.setTextContent(String.valueOf(item));
            list.appendChild(el);
        }
        return list;
    }

    private static void importArchitectureChildren(ModelStore store, Element parentXml, String parentGuid, int[] counts,
            List<String[]> pendingLogicalNodeLinks, List<String[]> pendingPhysicalNodeLinks) {
        for (Element child : childElements(parentXml, null)) {
            if ("element".equals(child.getTagName())) {
                Map<String, Object> created = store.createArchitectureElement(
                        parentGuid, child.getAttribute("name"), child.getAttribute("kind"), attrOrNull(child, "guid"));
                counts[0]++;
                String newGuid = (String) created.get("guid");
                applyPositionsOrLegacy(store, child, newGuid);
                applySizesOrLegacy(store, child, newGuid);
                applyDocumentationIfPresent(store, child, newGuid);
                importPorts(store, child, newGuid);
                importCapabilityLinks(store, child, newGuid);
                importFunctions(store, child, newGuid);
                collectLogicalNodeLinks(child, newGuid, pendingLogicalNodeLinks);
                collectPhysicalNodeLinks(child, newGuid, pendingPhysicalNodeLinks);
                importArchitectureChildren(store, child, newGuid, counts, pendingLogicalNodeLinks, pendingPhysicalNodeLinks);
            } else if ("package".equals(child.getTagName())) {
                importArchitectureChildren(store, child, parentGuid, counts, pendingLogicalNodeLinks, pendingPhysicalNodeLinks);
            }
        }
    }

    /** Collects <logicalNodeLink> children of an <element> as {functionalNodeGuid, logicalNodeGuid}
     * pairs, instead of resolving them immediately the way importCapabilityLinks does — UNLIKE a
     * Capability, the linked LogicalNode is another element within this SAME {@code <architecture>}
     * block, which may not have been created yet at this point in the (single, top-down) walk (e.g.
     * a FunctionalNode under System_F linking to a LogicalNode under System_L, a sibling subtree
     * processed later). Resolved by importInto itself, via linkLogicalNode, only once the ENTIRE
     * architecture tree has finished importing and every guid is guaranteed to exist. */
    private static void collectLogicalNodeLinks(Element ownerXml, String ownerGuid, List<String[]> pending) {
        for (Element linkXml : childElements(ownerXml, "logicalNodeLink")) {
            String logicalNodeGuid = attrOrNull(linkXml, "guid");
            if (logicalNodeGuid != null) pending.add(new String[]{ownerGuid, logicalNodeGuid});
        }
    }

    /** Collects <physicalNodeLink> children of an <element> as {logicalNodeGuid, physicalNodeGuid}
     * pairs — same shape/reasoning as collectLogicalNodeLinks, resolved by importInto via
     * linkPhysicalNode once the whole architecture tree has finished importing. */
    private static void collectPhysicalNodeLinks(Element ownerXml, String ownerGuid, List<String[]> pending) {
        for (Element linkXml : childElements(ownerXml, "physicalNodeLink")) {
            String physicalNodeGuid = attrOrNull(linkXml, "guid");
            if (physicalNodeGuid != null) pending.add(new String[]{ownerGuid, physicalNodeGuid});
        }
    }

    private static void importPorts(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element portXml : childElements(ownerXml, "port")) {
            Map<String, Object> created = store.createPort(ownerGuid, portXml.getAttribute("name"),
                    attrOrNull(portXml, "direction"), attrOrNull(portXml, "type"), attrOrNull(portXml, "view"),
                    attrOrNull(portXml, "guid"));
            String portGuid = (String) created.get("guid");
            applyDocumentationIfPresent(store, portXml, portGuid);
            // Nested <port> children are this port's own decomposition — recurse using the new
            // port's guid as owner (both stores redirect that to the port's interfaceBlock, or an
            // equivalent nested list, so the same createPort() call works uniformly here).
            importPorts(store, portXml, portGuid);
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

    /** <contextViewLink> children of an <actor> — re-links actorGuid to an already-imported
     * top-level Context View by guid (see contextViewLinkToXml; Context Views are created up front
     * in importInto, before <context>, specifically so this guid always resolves). */
    private static void importContextViewLinks(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element linkXml : childElements(ownerXml, "contextViewLink")) {
            String cvGuid = attrOrNull(linkXml, "guid");
            if (cvGuid != null) store.linkContextView(ownerGuid, cvGuid);
        }
    }

    /** <function> children of an <element> — attached to ownerGuid (a FunctionalNode) the same way
     * importCapabilities attaches <capability> children (see functionToXml). */
    private static void importFunctions(ModelStore store, Element ownerXml, String ownerGuid) {
        for (Element fnXml : childElements(ownerXml, "function")) {
            Map<String, Object> created = store.createFunction(ownerGuid, fnXml.getAttribute("name"), attrOrNull(fnXml, "guid"));
            applyDocumentationIfPresent(store, fnXml, (String) created.get("guid"));
        }
    }

    /** Reads a <useCase>'s full detail children and pushes them into the store via
     * store.updateUseCase(guid, detail) after translating them into the Map shape the store expects. */
    @SuppressWarnings("unchecked")
    private static void importUseCaseDetail(ModelStore store, Element ucXml, String ucGuid) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (Element goalXml : childElements(ucXml, "goal")) detail.put("goal", goalXml.getTextContent());
        for (Element postXml : childElements(ucXml, "postCondition")) detail.put("postCondition", postXml.getTextContent());
        for (Element actorsXml : childElements(ucXml, "actors")) {
            List<Object> actorRefs = new ArrayList<>();
            for (Element ref : childElements(actorsXml, "actorRef")) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("guid", ref.getAttribute("guid"));
                a.put("name", ref.getAttribute("name"));
                actorRefs.add(a);
            }
            detail.put("actors", actorRefs);
        }
        for (Element preXml : childElements(ucXml, "preconditions")) {
            detail.put("preconditions", textList(childElements(preXml, "precondition")));
        }
        for (Element bpXml : childElements(ucXml, "basicPath")) {
            detail.put("basicPath", textList(childElements(bpXml, "step")));
        }
        for (Element altsXml : childElements(ucXml, "alternatives")) {
            List<Object> alts = new ArrayList<>();
            for (Element a : childElements(altsXml, "alternative")) {
                Map<String, Object> alt = new LinkedHashMap<>();
                alt.put("title", "");
                for (Element t : childElements(a, "title")) alt.put("title", t.getTextContent());
                for (Element refs : childElements(a, "stepRefs")) alt.put("stepRefs", textList(childElements(refs, "ref")));
                for (Element wh : childElements(a, "whatHappens")) alt.put("whatHappens", wh.getTextContent());
                for (Element subs : childElements(a, "subSteps")) alt.put("subSteps", textList(childElements(subs, "step")));
                for (Element post : childElements(a, "postCondition")) alt.put("postCondition", post.getTextContent());
                alts.add(alt);
            }
            detail.put("alternatives", alts);
        }
        for (Element extsXml : childElements(ucXml, "extensions")) {
            List<Object> exts = new ArrayList<>();
            for (Element e : childElements(extsXml, "extension")) {
                Map<String, Object> ext = new LinkedHashMap<>();
                ext.put("title", "");
                for (Element t : childElements(e, "title")) ext.put("title", t.getTextContent());
                for (Element trig : childElements(e, "triggerText")) ext.put("triggerText", trig.getTextContent());
                for (Element subs : childElements(e, "subSteps")) ext.put("subSteps", textList(childElements(subs, "step")));
                exts.add(ext);
            }
            detail.put("extensions", exts);
        }
        if (!detail.isEmpty()) store.updateUseCase(ucGuid, detail);
    }

    /** Writes a {@code <documentation>} child for guid (see ModelStore#getDocumentation) — omitted
     * entirely when empty, so a model with no documentation anywhere doesn't grow a forest of empty
     * tags. Called for every element/port/actor/capability/useCase/contextView/function, the same
     * single generic mechanism covering every kind (see the ModelStore interface's own javadoc). */
    private static void documentationToXml(Document doc, Element xml, ModelStore store, String guid) {
        String text = store.getDocumentation(guid);
        if (text != null && !text.isEmpty()) {
            Element docXml = doc.createElement("documentation");
            docXml.setTextContent(text);
            xml.appendChild(docXml);
        }
    }

    /** Reads back a {@code <documentation>} child (if present) and applies it to the just-created/
     * matched element. No-op when the source element never had one. */
    private static void applyDocumentationIfPresent(ModelStore store, Element xml, String guid) {
        List<Element> docEls = childElements(xml, "documentation");
        if (!docEls.isEmpty()) {
            store.setDocumentation(guid, docEls.get(0).getTextContent());
        }
    }

    private static List<String> textList(List<Element> elements) {
        List<String> result = new ArrayList<>();
        for (Element el : elements) result.add(el.getTextContent());
        return result;
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

    /** node's "width"/"height" entries are Double, present only once a box has actually been
     * manually resized (see ModelStore#setSize) — absent (null) means "use the default size". FLAT
     * — used for Actors/Capabilities/Context Views (no view concept, unlike architecture elements,
     * which use setSizesChildren/applySizesOrLegacy below instead — see ModelStore#setSize's own
     * javadoc for why those need one size per view). Requested live: "kann ich alle boxen auch in
     * der breite/höhe ändern? wenn ja müssen wir das auch in der xml datei speichern." */
    private static void setSizeAttrs(Element xml, Map<String, Object> node) {
        Object width = node.get("width");
        Object height = node.get("height");
        if (width != null && height != null) {
            xml.setAttribute("width", String.valueOf(width));
            xml.setAttribute("height", String.valueOf(height));
        }
    }

    /** Applies a previously-exported flat size (if the XML carries one) to the just-created/matched
     * actor/capability. No-op when the source element was never manually resized. */
    private static void applySizeIfPresent(ModelStore store, Element xml, String guid) {
        String width = attrOrNull(xml, "width");
        String height = attrOrNull(xml, "height");
        if (width != null && height != null) {
            try {
                store.setSize(guid, Double.parseDouble(width), Double.parseDouble(height), null);
            } catch (NumberFormatException ignored) {
                // Malformed/hand-edited XML — skip rather than fail the whole import over cosmetics.
            }
        }
    }

    /** An architecture element's "sizes" entry is {view: {width,height}} — one saved size per view
     * (see ModelStore#setSize), mirroring setPositionsChildren exactly. "view" isn't drawn from a
     * fixed enum the way position's is — it's either one of ModelStore#ARCHITECTURE_VIEWS or a
     * dynamic {@code "Context:" + contextViewGuid} — but that distinction is opaque here; this just
     * writes/reads whatever view keys the map actually contains. Written as one nested &lt;size
     * view="..." width="..." height="..."/&gt; child per view that actually has a saved size, rather
     * than flat width/height attributes on &lt;element&gt;. */
    @SuppressWarnings("unchecked")
    private static void setSizesChildren(Document doc, Element elXml, Map<String, Object> node) {
        Object sizesObj = node.get("sizes");
        if (!(sizesObj instanceof Map)) return;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) sizesObj).entrySet()) {
            Map<String, Object> s = (Map<String, Object>) e.getValue();
            Element sizeXml = doc.createElement("size");
            sizeXml.setAttribute("view", e.getKey());
            sizeXml.setAttribute("width", String.valueOf(s.get("width")));
            sizeXml.setAttribute("height", String.valueOf(s.get("height")));
            elXml.appendChild(sizeXml);
        }
    }

    /** Reads back an architecture element's &lt;size&gt; children (the normal, current format) and
     * applies each to the just-created/matched element — mirrors applyPositionsOrLegacy exactly.
     * Falls back to a legacy flat width/height attribute pair directly on &lt;element&gt; itself
     * (the format written before size became per-view, or before size existed as an XML concept at
     * all) — applied to EVERY view in {@link ModelStore#ARCHITECTURE_VIEWS} so a model exported
     * before this change doesn't visually jump on its first import after upgrading; a later resize in
     * any one view then naturally splits off just that view's size going forward. Deliberately does
     * NOT also seed any {@code "Context:"} slot — a legacy flat size predates Context Views entirely,
     * there's no guid it could plausibly belong to, and the system-of-interest's Context-tab box
     * simply falls back to its own default size once, same as any element that was never resized. */
    private static void applySizesOrLegacy(ModelStore store, Element ownerXml, String guid) {
        List<Element> sizeEls = childElements(ownerXml, "size");
        if (!sizeEls.isEmpty()) {
            for (Element sizeXml : sizeEls) {
                String view = attrOrNull(sizeXml, "view");
                String width = attrOrNull(sizeXml, "width");
                String height = attrOrNull(sizeXml, "height");
                if (view == null || width == null || height == null) continue;
                try {
                    store.setSize(guid, Double.parseDouble(width), Double.parseDouble(height), view);
                } catch (NumberFormatException ignored) {
                    // Malformed/hand-edited XML — skip rather than fail the whole import over cosmetics.
                }
            }
            return;
        }
        String width = attrOrNull(ownerXml, "width");
        String height = attrOrNull(ownerXml, "height");
        if (width != null && height != null) {
            try {
                double wd = Double.parseDouble(width);
                double hd = Double.parseDouble(height);
                for (String view : ModelStore.ARCHITECTURE_VIEWS) store.setSize(guid, wd, hd, view);
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
