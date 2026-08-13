package com.ibm.rhapsody.samples.plugin.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Validator für XML und CSV Dateien
 * Prüft Format, Struktur und Konsistenz vor dem Import
 */
public class FileValidator {
    
    private List<ValidationError> errors = new ArrayList<>();
    private List<ValidationWarning> warnings = new ArrayList<>();
    
    /**
     * Validate XML file structure
     */
    public ValidationResult validateXML(File xmlFile) {
        errors.clear();
        warnings.clear();
        
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();
            
            // Check root element
            if (!doc.getDocumentElement().getNodeName().equals("ECAD")) {
                errors.add(new ValidationError(0, "Root element must be <ECAD>, found: " + 
                          doc.getDocumentElement().getNodeName()));
                return new ValidationResult(false, errors, warnings);
            }
            
            // Validate signal groups
            validateSignalGroups(doc);
            
            // Validate device
            validateDevice(doc);
            
            // Validate netlist
            validateNetlist(doc);
            
          
            // Check references
            validateReferences(doc);
            
        } catch (Exception e) {
            errors.add(new ValidationError(0, "XML parsing failed: " + e.getMessage()));
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate signal groups section
     */
    private void validateSignalGroups(Document doc) {
        NodeList signalGroupsList = doc.getElementsByTagName("signalgroups");
        
        if (signalGroupsList.getLength() == 0) {
            warnings.add(new ValidationWarning(0, "No <signalgroups> section found"));
            return;
        }
        
        Element signalGroupsElement = (Element) signalGroupsList.item(0);
        NodeList signalGroups = signalGroupsElement.getElementsByTagName("signalgroup");
        
        if (signalGroups.getLength() == 0) {
            warnings.add(new ValidationWarning(0, "No signal groups defined"));
            return;
        }
        
        for (int i = 0; i < signalGroups.getLength(); i++) {
            Element signalGroup = (Element) signalGroups.item(i);
            int lineNum = getApproximateLineNumber(i, "signalgroup");
            
            // Check required attributes
            if (!signalGroup.hasAttribute("name")) {
                errors.add(new ValidationError(lineNum, 
                    "Signal group missing 'name' attribute"));
            }
            if (!signalGroup.hasAttribute("RhGUID")) {
                errors.add(new ValidationError(lineNum, 
                    "Signal group '" + signalGroup.getAttribute("name") + 
                    "' missing 'RhGUID' attribute"));
            }
            
            // Validate signals in group
            NodeList signals = signalGroup.getElementsByTagName("signal");
            if (signals.getLength() == 0) {
                warnings.add(new ValidationWarning(lineNum, 
                    "Signal group '" + signalGroup.getAttribute("name") + 
                    "' has no signals"));
            }
            
            for (int j = 0; j < signals.getLength(); j++) {
                validateSignal((Element) signals.item(j), 
                              signalGroup.getAttribute("name"), lineNum + j);
            }
        }
    }
    
    /**
     * Validate single signal
     */
    private void validateSignal(Element signal, String groupName, int lineNum) {
        if (!signal.hasAttribute("name")) {
            errors.add(new ValidationError(lineNum, 
                "Signal in group '" + groupName + "' missing 'name' attribute"));
        }
        if (!signal.hasAttribute("type")) {
            errors.add(new ValidationError(lineNum, 
                "Signal '" + signal.getAttribute("name") + "' missing 'type' attribute"));
        }
        if (!signal.hasAttribute("RhGUID")) {
            errors.add(new ValidationError(lineNum, 
                "Signal '" + signal.getAttribute("name") + "' missing 'RhGUID' attribute"));
        }
        
        // Validate signal type
        String type = signal.getAttribute("type");
        if (!isValidSignalType(type)) {
            warnings.add(new ValidationWarning(lineNum, 
                "Signal '" + signal.getAttribute("name") + "' has unusual type: " + type));
        }
    }
    
    /**
     * Validate system section.
     * The root hardware element is now &lt;system&gt; (was &lt;device&gt;).
     * First-level assemblies live inside &lt;deviceList&gt;&lt;device&gt;,
     * deeper assemblies inside &lt;assemblyList&gt;&lt;assembly&gt;,
     * and ports inside &lt;portList&gt;&lt;port&gt;.
     */
    private void validateDevice(Document doc) {
        NodeList systems = doc.getElementsByTagName("system");

        if (systems.getLength() == 0) {
            errors.add(new ValidationError(0, "No <system> element found"));
            return;
        }
        if (systems.getLength() > 1) {
            errors.add(new ValidationError(0,
                "Multiple <system> elements found, expected only one"));
        }

        Element system = (Element) systems.item(0);

        // <system> has no name attribute (the package itself represents the system)
        if (!system.hasAttribute("RhGUID")) {
            errors.add(new ValidationError(0, "System missing 'RhGUID' attribute"));
        }

        // Validate first-level devices
        NodeList deviceNodes = doc.getElementsByTagName("device");
        for (int i = 0; i < deviceNodes.getLength(); i++) {
            Element device = (Element) deviceNodes.item(i);
            int lineNum = 100 + i;
            if (!device.hasAttribute("name")) {
                errors.add(new ValidationError(lineNum, "Device missing 'name' attribute"));
            }
            if (!device.hasAttribute("RhGUID")) {
                errors.add(new ValidationError(lineNum,
                    "Device '" + device.getAttribute("name") + "' missing 'RhGUID' attribute"));
            }
        }

        // Validate assemblies and ports (wrapper-aware)
        validateAssemblies(system, 0);
    }

    /**
     * Validate assemblies and ports recursively.
     *
     * Handles the new wrapper elements: assemblies are one level inside
     * &lt;deviceList&gt; or &lt;assemblyList&gt;, ports are inside &lt;portList&gt;.
     * The grandparent check ensures each element is only validated at the
     * correct nesting level and recursion does not double-count.
     */
    private void validateAssemblies(Element parent, int level) {
        // Validate <assembly> elements whose assemblyList parent is a direct child
        NodeList assemblies = parent.getElementsByTagName("assembly");
        for (int i = 0; i < assemblies.getLength(); i++) {
            Element assembly = (Element) assemblies.item(i);
            int lineNum = 100 + (level * 10) + i;

            if (!assembly.hasAttribute("name")) {
                errors.add(new ValidationError(lineNum, "Assembly missing 'name' attribute"));
            }
            if (!assembly.hasAttribute("RhGUID")) {
                errors.add(new ValidationError(lineNum,
                    "Assembly '" + assembly.getAttribute("name") + "' missing 'RhGUID' attribute"));
            }

            // Recurse only when this assembly's <assemblyList> wrapper is a direct child
            Node listWrapper = assembly.getParentNode();
            if (listWrapper != null && listWrapper.getParentNode() != null
                    && listWrapper.getParentNode().equals(parent)) {
                validateAssemblies(assembly, level + 1);
            }
        }

        // Validate <port> elements whose <portList> wrapper is a direct child
        NodeList ports = parent.getElementsByTagName("port");
        for (int i = 0; i < ports.getLength(); i++) {
            Element port = (Element) ports.item(i);
            Node listWrapper = port.getParentNode();
            if (listWrapper != null && listWrapper.getParentNode() != null
                    && listWrapper.getParentNode().equals(parent)) {
                if (!port.hasAttribute("name")) {
                    errors.add(new ValidationError(0,
                        "Port missing 'name' attribute in " + parent.getAttribute("name")));
                }
                if (!port.hasAttribute("RhGUID")) {
                    errors.add(new ValidationError(0,
                        "Port '" + port.getAttribute("name") + "' missing 'RhGUID'"));
                }
            }
        }
    }
    
    /**
     * Validate netlist section
     */
    private void validateNetlist(Document doc) {
        NodeList netlists = doc.getElementsByTagName("netlist");
        
        if (netlists.getLength() == 0) {
            warnings.add(new ValidationWarning(0, "No <netlist> section found"));
            return;
        }
        
        Element netlist = (Element) netlists.item(0);
        NodeList nets = netlist.getElementsByTagName("net");
        
        if (nets.getLength() == 0) {
            warnings.add(new ValidationWarning(0, "Netlist is empty"));
            return;
        }
        
        for (int i = 0; i < nets.getLength(); i++) {
            Element net = (Element) nets.item(i);
            int lineNum = 200 + i; // Approximate
            
            // Check required attributes
            String[] requiredAttrs = {"fromassembly", "fromport", "toassembly", 
                                     "toport", "signalgroup", "signal"};
            
            for (String attr : requiredAttrs) {
                if (!net.hasAttribute(attr) || net.getAttribute(attr).isEmpty()) {
                    errors.add(new ValidationError(lineNum, 
                        "Net missing or empty attribute: " + attr));
                }
            }
        }
    }
    
    /**
     * Validate references (netlist references assemblies, ports, signals)
     */
    private void validateReferences(Document doc) {
        // Collect all names that can appear in net fromassembly/toassembly:
        //   <system> (the top-level board)
        //   <device> elements inside <deviceList>  (first-level assemblies)
        //   <assembly> elements inside <assemblyList> (nested assemblies)
        Set<String> assemblyNames = new HashSet<>();

        // <system> has no name attribute – only devices and assemblies are valid net endpoints

        NodeList deviceNodes = doc.getElementsByTagName("device");
        for (int i = 0; i < deviceNodes.getLength(); i++) {
            assemblyNames.add(((Element) deviceNodes.item(i)).getAttribute("name"));
        }

        NodeList assemblies = doc.getElementsByTagName("assembly");
        for (int i = 0; i < assemblies.getLength(); i++) {
            assemblyNames.add(((Element) assemblies.item(i)).getAttribute("name"));
        }
        
        // Collect all signal group names and signals
        Set<String> signalGroupNames = new HashSet<>();
        Set<String> signalFullNames = new HashSet<>();
        NodeList signalGroups = doc.getElementsByTagName("signalgroup");
        for (int i = 0; i < signalGroups.getLength(); i++) {
            Element sg = (Element) signalGroups.item(i);
            String sgName = sg.getAttribute("name");
            signalGroupNames.add(sgName);
            
            NodeList signals = sg.getElementsByTagName("signal");
            for (int j = 0; j < signals.getLength(); j++) {
                Element signal = (Element) signals.item(j);
                signalFullNames.add(sgName + "." + signal.getAttribute("name"));
            }
        }
        
        // Validate netlist references
        NodeList nets = doc.getElementsByTagName("net");
        for (int i = 0; i < nets.getLength(); i++) {
            Element net = (Element) nets.item(i);
            int lineNum = i;
            
            String fromAssembly = net.getAttribute("fromassembly");
            String toAssembly = net.getAttribute("toassembly");
            String signalGroup = net.getAttribute("signalgroup");
            String signal = net.getAttribute("signal");
            
            // Check assembly references
            if (!assemblyNames.contains(fromAssembly)) {
                errors.add(new ValidationError(lineNum, 
                    "Net references unknown fromassembly: " + fromAssembly));
            }
            if (!assemblyNames.contains(toAssembly)) {
                errors.add(new ValidationError(lineNum, 
                    "Net references unknown toassembly: " + toAssembly));
            }
            
            // Check signal group reference
            if (!signalGroupNames.contains(signalGroup)) {
                errors.add(new ValidationError(lineNum, 
                    "Net references unknown signalgroup: " + signalGroup));
            }
            
            // Check signal reference
            if (!signal.isEmpty() && !signalFullNames.contains(signalGroup + "." + signal)) {
                warnings.add(new ValidationWarning(lineNum, 
                    "Net references unknown signal: " + signalGroup + "." + signal));
            }
        }
    }
    
    /**
     * Validate CSV file structure
     */
    public ValidationResult validateCSV(File csvFile) {
        errors.clear();
        warnings.clear();
        
        try (Scanner scanner = new Scanner(csvFile)) {
            int lineNumber = 0;
            String[] expectedHeaders = null;
            
            while (scanner.hasNextLine()) {
                lineNumber++;
                String line = scanner.nextLine();
                
                if (lineNumber == 1) {
                    // Validate header
                    expectedHeaders = parseCSVLine(line);
                    validateCSVHeaders(expectedHeaders);
                    continue;
                }
                
                // Validate data line
                String[] fields = parseCSVLine(line);
                validateCSVLine(fields, lineNumber, expectedHeaders.length);
            }
            
            if (lineNumber <= 1) {
                errors.add(new ValidationError(0, "CSV file is empty or has no data rows"));
            }
            
        } catch (Exception e) {
            errors.add(new ValidationError(0, "Failed to read CSV file: " + e.getMessage()));
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate CSV headers
     */
    private void validateCSVHeaders(String[] headers) {
        String[] requiredHeaders = {
            "#(SysML connector)", "Origin node", "Origin port", 
            "Destination node", "Destination port", "Category",
            "FromBlockGUID", "ToBlockGUID", "ParentBoard", 
            "SignalName", "InterfaceBlock"
        };
        
        Set<String> headerSet = new HashSet<>();
        for (String header : headers) {
            headerSet.add(header.trim().replace("\"", ""));
        }
        
        for (String required : requiredHeaders) {
            if (!headerSet.contains(required)) {
                errors.add(new ValidationError(1, 
                    "Missing required CSV header: " + required));
            }
        }
        
        if (headers.length < 38) {
            warnings.add(new ValidationWarning(1, 
                "CSV has fewer than expected 38 columns: " + headers.length));
        }
    }
    
    /**
     * Validate single CSV line
     */
    private void validateCSVLine(String[] fields, int lineNumber, int expectedFieldCount) {
        if (fields.length < expectedFieldCount) {
            errors.add(new ValidationError(lineNumber, 
                "CSV line has fewer fields than expected: " + 
                fields.length + " < " + expectedFieldCount));
        }
        
        // Check critical fields are not empty
        if (fields.length > 2 && fields[2].trim().isEmpty()) {
            errors.add(new ValidationError(lineNumber, "Origin node is empty"));
        }
        if (fields.length > 4 && fields[4].trim().isEmpty()) {
            errors.add(new ValidationError(lineNumber, "Destination node is empty"));
        }
    }
    
    /**
     * Parse CSV line respecting quotes
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' || c == ';') && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
    
    // Helper methods
    
    private boolean isValidSignalType(String type) {
        String[] validTypes = {"Power", "DataBus", "QuasiStatic", "Analog", "Digital"};
        for (String valid : validTypes) {
            if (valid.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }
    
    private int getApproximateLineNumber(int index, String elementType) {
        // Rough estimation based on typical XML structure
        if (elementType.equals("signalgroup")) {
            return 10 + (index * 15);
        }
        return index;
    }
    
    // Inner classes
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;
        private final List<ValidationWarning> warnings;
        
        public ValidationResult(boolean valid, List<ValidationError> errors, 
                              List<ValidationWarning> warnings) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<ValidationError> getErrors() {
            return errors;
        }
        
        public List<ValidationWarning> getWarnings() {
            return warnings;
        }
        
        public String getReport() {
            StringBuilder report = new StringBuilder();
            report.append("Validation Report\n");
            report.append("=================\n\n");
            
            if (valid) {
                report.append("✓ File is valid\n\n");
            } else {
                report.append("✗ File has errors\n\n");
            }
            
            if (!errors.isEmpty()) {
                report.append("ERRORS (").append(errors.size()).append("):\n");
                for (ValidationError error : errors) {
                    report.append("  Line ").append(error.lineNumber).append(": ");
                    report.append(error.message).append("\n");
                }
                report.append("\n");
            }
            
            if (!warnings.isEmpty()) {
                report.append("WARNINGS (").append(warnings.size()).append("):\n");
                for (ValidationWarning warning : warnings) {
                    report.append("  Line ").append(warning.lineNumber).append(": ");
                    report.append(warning.message).append("\n");
                }
            }
            
            return report.toString();
        }
    }
    
    public static class ValidationError {
        private final int lineNumber;
        private final String message;
        
        public ValidationError(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    public static class ValidationWarning {
        private final int lineNumber;
        private final String message;
        
        public ValidationWarning(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public String getMessage() {
            return message;
        }
    }
}