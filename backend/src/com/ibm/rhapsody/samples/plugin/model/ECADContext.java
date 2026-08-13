package com.ibm.rhapsody.samples.plugin.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.*;

/**
 * Context class holding shared state for ECAD operations
 * Replaces multiple scattered collections in original code
 */
public class ECADContext {
    
    // Top-level package being processed
    private IRPPackage topPackage;
    
    // Collections
    private List<IRPPackage> packageCollection = new ArrayList<>();
    private List<IRPLink> linkCollection = new ArrayList<>();
    private List<IRPModelElement> classCollection = new ArrayList<>();
    private List<IRPStructureDiagram> ibdList = new ArrayList<>();
    
    // Lookup maps
    private Map<String, IRPGraphNode> graphNodeMap = new HashMap<>();
    private Map<String, IRPType> valueTypesMap = new HashMap<>();
    private Map<String, IRPTag> tagsMap = new HashMap<>();
    private Map<String, IRPStereotype> stereotypesMap = new HashMap<>();
    private Map<String, IRPClass> signalMap = new HashMap<>();
    private Map<String, IRPClass> ownerMap = new HashMap<>();
    private Map<String, IRPPort> iBlockMap = new HashMap<>();
    private Map<String, Integer> hierarchyMap = new HashMap<>();
    private Map<String, IRPClass> signalGroupMap = new HashMap<>();
    private Map<String, IRPAttribute> signalGroupSignalMap = new HashMap<>();
    private Map<String, String> guidMap = new HashMap<>();
    
    /**
     * Clear all collections and maps
     */
    public void clear() {
        topPackage = null;
        packageCollection.clear();
        linkCollection.clear();
        classCollection.clear();
        ibdList.clear();
        graphNodeMap.clear();
        valueTypesMap.clear();
        tagsMap.clear();
        stereotypesMap.clear();
        signalMap.clear();
        ownerMap.clear();
        iBlockMap.clear();
        hierarchyMap.clear();
        signalGroupMap.clear();
        signalGroupSignalMap.clear();
        guidMap.clear();
    }
    
    // Getters and Setters
    
    public IRPPackage getTopPackage() {
        return topPackage;
    }
    
    public void setTopPackage(IRPPackage topPackage) {
        this.topPackage = topPackage;
    }
    
    public List<IRPPackage> getPackageCollection() {
        return packageCollection;
    }
    
    public List<IRPLink> getLinkCollection() {
        return linkCollection;
    }
    
    public List<IRPModelElement> getClassCollection() {
        return classCollection;
    }
    
    public List<IRPStructureDiagram> getIbdList() {
        return ibdList;
    }
    
    public Map<String, IRPGraphNode> getGraphNodeMap() {
        return graphNodeMap;
    }
    
    public Map<String, IRPType> getValueTypesMap() {
        return valueTypesMap;
    }
    
    public Map<String, IRPTag> getTagsMap() {
        return tagsMap;
    }
    
    public Map<String, IRPStereotype> getStereotypesMap() {
        return stereotypesMap;
    }
    
    public Map<String, IRPClass> getSignalMap() {
        return signalMap;
    }
    
    public Map<String, IRPClass> getOwnerMap() {
        return ownerMap;
    }
    
    public Map<String, IRPPort> getIBlockMap() {
        return iBlockMap;
    }
    
    public Map<String, Integer> getHierarchyMap() {
        return hierarchyMap;
    }
    
    public Map<String, IRPClass> getSignalGroupMap() {
        return signalGroupMap;
    }
    
    public Map<String, IRPAttribute> getSignalGroupSignalMap() {
        return signalGroupSignalMap;
    }
    
    public Map<String, String> getGuidMap() {
        return guidMap;
    }
}