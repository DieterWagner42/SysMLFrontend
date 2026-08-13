package com.ibm.rhapsody.samples.plugin.services;

import java.util.Iterator;

import com.ibm.rhapsody.samples.plugin.model.ECADContext;
import com.telelogic.rhapsody.core.*;

/**
 * Service for model element operations
 */
@SuppressWarnings("unchecked")
public class ModelElementService {
    
    private final IRPApplication application;
    
    public ModelElementService(IRPApplication application) {
        this.application = application;
    }
    
    /**
     * Load all packages recursively
     */
    public void loadPackageHierarchy(IRPPackage rootPackage, ECADContext context) {
        context.getPackageCollection().clear();
        loadPackagesRecursive(rootPackage, context);
    }
    
    private void loadPackagesRecursive(IRPPackage currentPackage, ECADContext context) {
        for (Object obj : currentPackage.getAllNestedElements().toList()) {
            if (obj instanceof IRPPackage) {
                IRPPackage nestedPackage = (IRPPackage) obj;
                context.getPackageCollection().add(nestedPackage);
                loadPackagesRecursive(nestedPackage, context);
            }
        }
    }
    
    /**
     * Load profile types into context
     */
    public void loadProfileTypes(String packageName, ECADContext context) {
        context.getValueTypesMap().clear();
        
        IRPCollection profiles = application.activeProject().getProfiles();
        for (int i = 1; i <= profiles.getCount(); i++) {
            IRPProfile profile = (IRPProfile) profiles.getItem(i);
            
            if (profile.getName().contains("MBDAInterface")) {
                loadTypesFromProfile(profile, packageName, context);
            }
        }
    }
    
    private void loadTypesFromProfile(IRPProfile profile, String packageName, 
                                     ECADContext context) {
        IRPCollection packages = profile.getPackages();
        
        for (int j = 1; j <= packages.getCount(); j++) {
            IRPPackage pkg = (IRPPackage) packages.getItem(j);
            
            if (pkg.getName().equals(packageName)) {
                IRPCollection elements = pkg.getAllNestedElements();
                
                for (int k = 1; k <= elements.getCount(); k++) {
                    if (elements.getItem(k) instanceof IRPType) {
                        IRPType valueType = (IRPType) elements.getItem(k);
                        context.getValueTypesMap().put(valueType.getName(), valueType);
                    }
                }
            }
        }
    }
    
    /**
     * Load profile stereotypes into context
     */
    public void loadProfileStereotypes(String packageName, ECADContext context) {
        context.getStereotypesMap().clear();
        
        IRPCollection profiles = application.activeProject().getProfiles();
        for (int i = 1; i <= profiles.getCount(); i++) {
            IRPProfile profile = (IRPProfile) profiles.getItem(i);
            
            if (profile.getName().contains("MBDAInterface")) {
                loadStereotypesFromProfile(profile, packageName, context);
            }
        }
    }
    
    private void loadStereotypesFromProfile(IRPProfile profile, String packageName,
                                           ECADContext context) {
        IRPCollection packages = profile.getPackages();
        
        for (int j = 1; j <= packages.getCount(); j++) {
            IRPPackage pkg = (IRPPackage) packages.getItem(j);
            
            if (pkg.getName().equals(packageName)) {
                IRPCollection elements = pkg.getAllNestedElements();
                
                for (int k = 1; k <= elements.getCount(); k++) {
                    if (elements.getItem(k) instanceof IRPStereotype) {
                        IRPStereotype stereotype = (IRPStereotype) elements.getItem(k);
                        context.getStereotypesMap().put(stereotype.getName(), stereotype);
                    }
                }
            }
        }
    }
    
    /**
     * Get all assemblies (instances and links) from package
     */
    public void getAllAssemblies(IRPPackage targetPackage, ECADContext context) {
        context.getClassCollection().clear();
        
        Iterator<IRPClass> classIter = targetPackage.getClasses().toList().iterator();
        while (classIter.hasNext()) {
            IRPClass theClass = classIter.next();
            
            // Add instances
            Iterator<Object> nestedIter = theClass.getNestedElements().toList().iterator();
            while (nestedIter.hasNext()) {
                Object nestedElement = nestedIter.next();
                if (nestedElement instanceof IRPInstance) {
                    context.getClassCollection().add((IRPInstance) nestedElement);
                }
            }
            
            // Add links
            Iterator<IRPLink> linkIter = theClass.getLinks().toList().iterator();
            while (linkIter.hasNext()) {
                IRPLink link = linkIter.next();
                context.getClassCollection().add(link);
            }
        }
    }
    
    /**
     * Find class by GUID
     */
    public IRPClass findClassByGUID(IRPPackage targetPackage, String guid) {
        IRPCollection classes = targetPackage.getClasses();
        
        for (int i = 1; i <= classes.getCount(); i++) {
            IRPClass theClass = (IRPClass) classes.getItem(i);
            if (theClass.getGUID().equals(guid)) {
                return theClass;
            }
        }
        
        return null;
    }
    
    /**
     * Check if class exists by name
     */
    public IRPClass findClassByName(IRPPackage targetPackage, String name) {
        IRPCollection classes = targetPackage.getClasses();
        
        for (int i = 1; i <= classes.getCount(); i++) {
            IRPClass theClass = (IRPClass) classes.getItem(i);
            if (theClass.getName().equals(name)) {
                return theClass;
            }
        }
        
        return null;
    }
    
    /**
     * Find attribute by name
     */
    public IRPAttribute findAttribute(IRPClass theClass, String name) {
        IRPCollection attributes = theClass.getAttributes();
        
        for (int i = 1; i <= attributes.getCount(); i++) {
            IRPAttribute attribute = (IRPAttribute) attributes.getItem(i);
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }
        
        return null;
    }
    
    /**
     * Get instance (part) from class
     */
    public IRPInstance getInstance(IRPClass parentClass, String name) {
        IRPCollection elements = parentClass.getNestedElements();
        
        for (int i = 1; i <= elements.getCount(); i++) {
            if (elements.getItem(i) instanceof IRPInstance) {
                IRPInstance instance = (IRPInstance) elements.getItem(i);
                if (instance.getName().equals(name)) {
                    return instance;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Add or get port
     */
    public IRPPort addOrGetPort(IRPClass parentClass, IRPClass interfaceBlock,
                               StereotypeService stereotypeService) {
        String portName = interfaceBlock.getName().substring(2); // Remove "iB" prefix
        
        // Check if port exists
        Iterator<IRPPort> portIter = parentClass.getPorts().toList().iterator();
        while (portIter.hasNext()) {
            IRPPort port = portIter.next();
            if (port.getName().equals(portName)) {
                return port;
            }
        }
        
        // Create new port
        IRPPort port = (IRPPort) parentClass.addNewAggr("Port", portName);
        port.addSpecificStereotype(stereotypeService.getProxyPortStereotype());
        port.setContract(interfaceBlock);
        
        return port;
    }
    
    /**
     * Check if link exists between two ports
     */
    public IRPLink findLink(IRPClass ownerClass, IRPPort fromPort, IRPPort toPort) {
        Iterator<IRPLink> linkIter = ownerClass.getLinks().toList().iterator();
        
        while (linkIter.hasNext()) {
            IRPLink link = linkIter.next();
            if (link.getFromPort().equals(fromPort) && link.getToPort().equals(toPort)) {
                return link;
            }
        }
        
        return null;
    }
    
    /**
     * Add tag to model element
     */
    public void addOrUpdateTag(IRPModelElement element, String tagName, String value) {
        IRPTag tag = element.getTag(tagName);
        
        if (tag == null) {
            tag = (IRPTag) element.addNewAggr("Tag", tagName);
        }
        
        if (tag != null && !value.isEmpty()) {
            tag.setValue(value);
        }
    }
    
    /**
     * Get attribute value from XML node
     */
    public String getAttributeValue(org.w3c.dom.Node node, String attributeName) {
        try {
            org.w3c.dom.Node attr = node.getAttributes().getNamedItem(attributeName);
            return attr != null ? attr.getNodeValue() : "";
        } catch (Exception e) {
            return "";
        }
    }
       
}