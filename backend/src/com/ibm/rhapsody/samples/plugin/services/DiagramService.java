package com.ibm.rhapsody.samples.plugin.services;

import java.util.Iterator;

import com.ibm.rhapsody.samples.plugin.model.ECADContext;
import com.telelogic.rhapsody.core.*;

/**
 * Service for diagram operations
 */
@SuppressWarnings("unchecked")
public class DiagramService {

	private final IRPApplication application;
	private final StereotypeService stereotypeService;

	public DiagramService(IRPApplication application, StereotypeService stereotypeService) {
		this.application = application;
		this.stereotypeService = stereotypeService;
	}

	/**
	 * Create or get IBD (Internal Block Diagram) for a class
	 */
	public IRPStructureDiagram createIBD(IRPClass parentClass, ECADContext context) {
		IRPStructureDiagram ibd = getIBD(parentClass);

		if (ibd == null) {
			ibd = (IRPStructureDiagram) parentClass.addNewAggr("StructureDiagram", "ibd" + parentClass.getName());
			ibd.addSpecificStereotype(stereotypeService.getIbdStereotype());
		}

		if (!context.getIbdList().contains(ibd)) {
			context.getIbdList().add(ibd);
		}

		return ibd;
	}

	/**
	 * Get all existing IBD for a class
	 */
	public void getIBDToList(ECADContext context,IRPPackage targetPackage) {
		
		context.getIbdList().clear();
		Iterator<IRPClass> myClassIter = targetPackage.getClasses().toList().iterator();
		while (myClassIter.hasNext()) {
			IRPClass theClass = myClassIter.next();
			IRPStructureDiagram ibd= getIBD(theClass);
			if(ibd!=null)
				context.getIbdList().add(ibd);
		}
	}

	/**
	 * Get existing IBD for a class
	 */
	public IRPStructureDiagram getIBD(IRPClass parentClass) {
		Iterator<Object> iter = parentClass.getReferences().toList().iterator();

		while (iter.hasNext()) {
			Object obj = iter.next();
			if (obj instanceof IRPStructureDiagram) {
				IRPStructureDiagram ibd = (IRPStructureDiagram) obj;
				if (ibd.getName().contains(parentClass.getName())) {
					return ibd;
				}
			}
		}

		return null;
	}

	/**
	 * Check if part exists in IBD
	 */
	public boolean isPartInIBD(IRPStructureDiagram ibd, IRPInstance instance) {
		Iterator<Object> myIter = ibd.getGraphicalElements().toList().iterator();
		while (myIter.hasNext()) {
			Object myObj = myIter.next();
			if (myObj instanceof IRPGraphNode) {
				IRPGraphNode myNode = (IRPGraphNode) myObj;
				if (myNode.getGraphicalProperty("Type").getValue().equals("ImplementationObject")) {
					if (myNode.getModelObject().equals(instance))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * Populate all IBDs with ports and elements
	 */
	public void populateAllIBDs(ECADContext context) {
		Iterator<IRPStructureDiagram> ibdIter = context.getIbdList().iterator();

		while (ibdIter.hasNext()) {
			IRPStructureDiagram ibd = ibdIter.next();
			populateIBD(ibd);
		}
	}

	/**
	 * Populate single IBD
	 */
	public void populateIBD(IRPStructureDiagram ibd) {
		Iterator<Object> iter = ibd.getGraphicalElements().toList().iterator();

		while (iter.hasNext()) {
			Object obj = iter.next();

			if (obj instanceof IRPGraphNode) {
				IRPGraphNode node = (IRPGraphNode) obj;
				String nodeType = node.getGraphicalProperty("Type").getValue();

				if (nodeType.equals("DiagramFrame") || nodeType.equals("Port")) {
					node.showAllPorts();

					if (nodeType.equals("Port")) {
						node.setGraphicalProperty("ShowStereotypeLabel", "FALSE");
						node.setGraphicalProperty("ShowName", "absolute");
					}
				}
			}
		}
	}

	/**
	 * Add connectors to all IBDs
	 */
	public void addConnectorsToIBDs(ECADContext context) {
		Iterator<IRPStructureDiagram> ibdIter = context.getIbdList().iterator();

		while (ibdIter.hasNext()) {
			IRPStructureDiagram ibd = ibdIter.next();
			addConnectorsToIBD(ibd, context);
		}
	}

	/**
	 * Add connectors to single IBD
	 */
	public void addConnectorsToIBD(IRPStructureDiagram ibd, ECADContext context) {
		// Build map of ports in this diagram
		context.getGraphNodeMap().clear();
		Iterator<Object> nodeIter = ibd.getGraphicalElements().toList().iterator();

		while (nodeIter.hasNext()) {
			Object obj = nodeIter.next();
			if (obj instanceof IRPGraphNode) {
				IRPGraphNode node = (IRPGraphNode) obj;
				if (node.getGraphicalProperty("Type").getValue().equals("Port")) {
					context.getGraphNodeMap().put(node.getModelObject().getGUID(), node);
				}
			}
		}

		// Create connectors for links
		Iterator<IRPLink> linkIter = context.getLinkCollection().iterator();

		while (linkIter.hasNext()) {
			IRPLink link = linkIter.next();
			IRPGraphNode fromNode = context.getGraphNodeMap().get(link.getFromPort().getGUID());
			IRPGraphNode toNode = context.getGraphNodeMap().get(link.getToPort().getGUID());

			if (fromNode != null && toNode != null) {
				createConnector(ibd, link, fromNode, toNode);
			}
		}
	}

	/**
	 * Create connector between two ports
	 */
	private void createConnector(IRPStructureDiagram ibd, IRPLink link, IRPGraphNode fromNode, IRPGraphNode toNode) {
		try {
			// Get positions
			int[] fromPos = getNodePosition(fromNode);
			int[] toPos = getNodePosition(toNode);

			// Create edge
			IRPGraphEdge edge = ibd.addNewEdgeForElement((IRPModelElement) link, fromNode, fromPos[0] - 10,
					fromPos[1] - 10, toNode, toPos[0] - 10, toPos[1] - 10);

			// Set colors to green (indicates new/imported)
			edge.setGraphicalProperty("ForegroundColor", "0,255,0");
			fromNode.setGraphicalProperty("ForegroundColor", "0,255,0");
			toNode.setGraphicalProperty("ForegroundColor", "0,255,0");

		} catch (Exception e) {
			String errorMsg = application.getErrorMessage();
			application.writeToOutputWindow("ECAD_Plugin", "Warning: Could not create connector: " + errorMsg + "\n");
		}
	}

	/**
	 * Get node position from graphical property
	 */
	private int[] getNodePosition(IRPGraphNode node) {
		IRPGraphicalProperty posProperty = node.getGraphicalProperty("Position");
		String[] values = posProperty.getValue().split(",");

		int x = Integer.parseInt(values[0]);
		int y = Integer.parseInt(values[1]);

		return new int[] { x, y };
	}

	/**
	 * Mark unused assemblies in red
	 */
	public void markUnusedAssemblies(ECADContext context) {
		Iterator<IRPStructureDiagram> ibdIter = context.getIbdList().iterator();

		while (ibdIter.hasNext()) {
			IRPStructureDiagram ibd = ibdIter.next();
			markUnusedInDiagram(ibd, context);
		}
	}

	/**
	 * Mark unused elements in specific diagram
	 */
	private void markUnusedInDiagram(IRPStructureDiagram ibd, ECADContext context) {
		Iterator<Object> iter = ibd.getGraphicalElements().toList().iterator();

		while (iter.hasNext()) {
			Object obj = iter.next();

			if (obj instanceof IRPGraphNode) {
				IRPGraphNode node = (IRPGraphNode) obj;
				if (context.getClassCollection().contains(node.getModelObject())) {
					node.setGraphicalProperty("ForegroundColor", "255,0,0");
				}
			} else if (obj instanceof IRPGraphEdge) {
				IRPGraphEdge edge = (IRPGraphEdge) obj;
				if (context.getClassCollection().contains(edge.getModelObject())) {
					edge.setGraphicalProperty("ForegroundColor", "255,0,0");
				}
			}
		}
	}

	/**
	 * Add part to IBD diagram
	 */
	public IRPGraphNode addPartToIBD(IRPStructureDiagram ibd, IRPInstance instance, int x, int y) {
		IRPGraphNode node = ibd.addNewNodeForElement(instance, x, y, 100, 100);
		node.setGraphicalProperty("ForegroundColor", "0,255,0");
		return node;
	}

	/**
	 * Remove green marking from all diagrams
	 */
	public void removeGreenMarking(IRPPackage targetPackage) {
		Iterator<IRPClass> classIter = targetPackage.getClasses().toList().iterator();

		while (classIter.hasNext()) {
			IRPClass theClass = classIter.next();
			IRPStructureDiagram ibd = getIBD(theClass);

			if (ibd != null) {
				resetDiagramColors(ibd);
			}
		}
	}

	/**
	 * Reset diagram element colors to default
	 */
	private void resetDiagramColors(IRPStructureDiagram ibd) {
		Iterator<Object> iter = ibd.getGraphicalElements().toList().iterator();

		while (iter.hasNext()) {
			Object myObj = iter.next();

			Iterator<Object> myGtaphIter = ibd.getGraphicalElements().toList().iterator();
			while (myGtaphIter.hasNext()) {
				myObj = myGtaphIter.next();
				if (myObj instanceof IRPGraphNode) {
					IRPGraphNode myNode = (IRPGraphNode) myObj;
					myNode.setGraphicalProperty("ForegroundColor", "109,163,217");
				}
				if (myObj instanceof IRPGraphEdge) {
					IRPGraphEdge newlink = (IRPGraphEdge) myObj;
					newlink.setGraphicalProperty("ForegroundColor", "128,128,128");
				}
			}

		}
	}
}