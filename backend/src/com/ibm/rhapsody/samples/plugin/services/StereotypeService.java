package com.ibm.rhapsody.samples.plugin.services;

import com.telelogic.rhapsody.core.*;

/**
 * Service for managing stereotypes
 */
public class StereotypeService {
    
    private final IRPApplication application;
    
    // Standard stereotypes
    private IRPStereotype blockStereotype;
    private IRPStereotype proxyPortStereotype;
    private IRPStereotype ibdStereotype;
    private IRPStereotype connectorStereotype;
    
    public StereotypeService(IRPApplication application) {
        this.application = application;
    }
    
    /**
     * Load standard stereotypes from the project
     */
    public void loadStandardStereotypes() {
        IRPProject project = application.activeProject();
        
        blockStereotype = findStereotype(project, "Block");
        proxyPortStereotype = findStereotype(project, "proxyPort");
        ibdStereotype = findStereotype(project, "Internal Block Diagram");
        connectorStereotype = findStereotype(project, "connector");
    }
    
    /**
     * Find stereotype recursively in project
     */
    private IRPStereotype findStereotype(IRPProject project, String name) {
        IRPStereotype stereotype = (IRPStereotype) project.findNestedElementRecursive(
            name, "Stereotype");
        
        if (stereotype == null) {
            application.writeToOutputWindow("ECAD_Plugin", 
                "WARNING: Stereotype '" + name + "' not found\n");
        }
        
        return stereotype;
    }
    
    /**
     * Check if element has specific stereotype
     */
    public boolean hasStereotype(IRPModelElement element, String stereotypeName) {
        for (Object obj : element.getStereotypes().toList()) {
            IRPStereotype stereotype = (IRPStereotype) obj;
            if (stereotype.getName().equals(stereotypeName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Apply stereotype to element
     */
    public void applyStereotype(IRPModelElement element, String stereotypeName, 
                               String metaClass) {
        if (!hasStereotype(element, stereotypeName)) {
            element.addStereotype(stereotypeName, metaClass);
        }
    }
    
    // Getters for standard stereotypes
    
    public IRPStereotype getBlockStereotype() {
        return blockStereotype;
    }
    
    public IRPStereotype getProxyPortStereotype() {
        return proxyPortStereotype;
    }
    
    public IRPStereotype getIbdStereotype() {
        return ibdStereotype;
    }
    
    public IRPStereotype getConnectorStereotype() {
        return connectorStereotype;
    }
}