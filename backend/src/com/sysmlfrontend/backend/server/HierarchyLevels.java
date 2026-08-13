package com.sysmlfrontend.backend.server;

/**
 * The fixed, automatic architecture hierarchy — there is no "Package" element and no free choice
 * of level: System of Systems (SoS, optional, level -1) → System (0) → Subsystem (1) →
 * Equipment (2, leaf, no further children). Shared by both {@link RhapsodyModelStore} and
 * {@link LocalXmlModelStore} so the rule lives in exactly one place.
 *
 * FUNCTIONAL_NODE/LOGICAL_NODE/PHYSICAL_NODE are three more separate root-level trees living
 * alongside the System-of-Systems one (same root, distinguished by kind — see App.tsx's
 * layoutArchitectureTree, which shows exactly one tree depending on the selected architecture
 * view: System Structure/Operational show the SoS tree, Functional/Logical/Physical each show
 * only their own aspect-node tree). Each of these decomposes into sub-nodes of its own kind at
 * arbitrary depth — unlike the SoS→System→Subsystem→Equipment chain, there's no fixed number of
 * levels or leaf level to cap it at. A FunctionalNode additionally owns an attached list of
 * Functions (see ModelStore#getFunctionsOf) — LogicalNode/PhysicalNode currently own only ports,
 * no analogous attached list.
 */
final class HierarchyLevels {

    private HierarchyLevels() {}

    static final String SYSTEM_OF_SYSTEM = "SystemOfSystem";
    static final String SYSTEM = "System";
    static final String SUBSYSTEM = "Subsystem";
    static final String EQUIPMENT = "Equipment";
    static final String FUNCTIONAL_NODE = "FunctionalNode";
    static final String LOGICAL_NODE = "LogicalNode";
    static final String PHYSICAL_NODE = "PhysicalNode";

    /**
     * Computes the level for a new child element.
     *
     * @param isRoot        true if the parent is the model root itself (or a legacy Package) —
     *                      the only point where SoS-vs-System (or an aspect node kind) is a real
     *                      choice
     * @param parentLevel   the parent's own level; ignored when isRoot is true
     * @param requestedKind what the client asked for; honored at the root for SYSTEM_OF_SYSTEM and
     *                      the three aspect node kinds — everywhere else the level is fully
     *                      automatic (an aspect node parent always yields a same-kind child, same
     *                      as the SoS chain does for its own four levels)
     */
    static String childLevel(boolean isRoot, String parentLevel, String requestedKind) {
        if (isRoot) {
            if (FUNCTIONAL_NODE.equals(requestedKind)) return FUNCTIONAL_NODE;
            if (LOGICAL_NODE.equals(requestedKind)) return LOGICAL_NODE;
            if (PHYSICAL_NODE.equals(requestedKind)) return PHYSICAL_NODE;
            return SYSTEM_OF_SYSTEM.equals(requestedKind) ? SYSTEM_OF_SYSTEM : SYSTEM;
        }
        if (FUNCTIONAL_NODE.equals(parentLevel)) return FUNCTIONAL_NODE;
        if (LOGICAL_NODE.equals(parentLevel)) return LOGICAL_NODE;
        if (PHYSICAL_NODE.equals(parentLevel)) return PHYSICAL_NODE;
        switch (parentLevel) {
            case SYSTEM_OF_SYSTEM: return SYSTEM;
            case SYSTEM: return SUBSYSTEM;
            case SUBSYSTEM: return EQUIPMENT;
            default:
                throw new IllegalArgumentException(
                        "'" + parentLevel + "' is the lowest hierarchy level — no further sub-elements possible.");
        }
    }

    /** Which of the four separate root-level trees a kind belongs to — the three aspect kinds are
     * each their own family (a FunctionalNode's family is disjoint from a LogicalNode's, even
     * though both are "aspect nodes"), and every System-of-Systems-chain kind (SoS/System/
     * Subsystem/Equipment, plus the legacy "Block" fallback) shares one "Structure" family. Used by
     * {@link #requireCompatibleMove} to keep an existing element's move (see ModelStore#moveElement)
     * within its own tree — moving a FunctionalNode under a System, or vice versa, would mix two
     * trees that otherwise never intersect. */
    static String kindFamily(String kind) {
        switch (kind) {
            case FUNCTIONAL_NODE: return "Functional";
            case LOGICAL_NODE: return "Logical";
            case PHYSICAL_NODE: return "Physical";
            default: return "Structure";
        }
    }

    /** Validates a move (see ModelStore#moveElement) of an existing element of nodeKind to become a
     * child of newParentKind (or the model root, if newParentIsRoot). Unlike childLevel, this never
     * COMPUTES a level — an existing element's kind is already fixed from when it was created, and
     * a move never changes it, only its containment parent. Throws with a clear message if the move
     * isn't allowed:
     * <ul>
     *   <li>At the root, only the kinds a fresh root-level creation could itself produce are
     *       allowed — SystemOfSystem/System for the Structure family, any of the three aspect kinds
     *       for their own family (an existing Subsystem/Equipment can never sit directly at root,
     *       matching how {@link #childLevel} itself never assigns those at the root either).</li>
     *   <li>Under an existing element, the two must share the same family (see {@link #kindFamily})
     *       — e.g. a FunctionalNode can only move under the root or another FunctionalNode, never
     *       under a System/Subsystem or a Logical/PhysicalNode.</li>
     *   <li>Equipment is a leaf (see {@link #childLevel}'s own leaf handling) — never a valid new
     *       parent, existing element or not.</li>
     * </ul>
     * Cycle prevention (moving an element under itself or one of its own descendants) is each
     * store's own responsibility — it needs to walk that store's actual tree structure, which this
     * class has no access to. */
    static void requireCompatibleMove(String nodeKind, boolean newParentIsRoot, String newParentKind) {
        String nodeFamily = kindFamily(nodeKind);
        if (newParentIsRoot) {
            boolean validAtRoot = !"Structure".equals(nodeFamily)
                    || SYSTEM_OF_SYSTEM.equals(nodeKind) || SYSTEM.equals(nodeKind);
            if (!validAtRoot) {
                throw new IllegalArgumentException(
                        "'" + nodeKind + "' cannot be moved directly under the model root — only "
                                + "SystemOfSystem/System (or an aspect node) can sit at the root.");
            }
            return;
        }
        if (EQUIPMENT.equals(newParentKind)) {
            throw new IllegalArgumentException("Equipment is a leaf — it cannot have children.");
        }
        String parentFamily = kindFamily(newParentKind);
        if (!nodeFamily.equals(parentFamily)) {
            throw new IllegalArgumentException(
                    "Cannot move a '" + nodeKind + "' under a '" + newParentKind
                            + "' — they belong to different architecture trees.");
        }
    }
}
