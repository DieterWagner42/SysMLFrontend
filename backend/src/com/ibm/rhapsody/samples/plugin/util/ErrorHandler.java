package com.ibm.rhapsody.samples.plugin.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JOptionPane;

import com.telelogic.rhapsody.core.IRPApplication;

/**
 * Zentrale Fehlerbehandlung mit detailliertem Logging
 */
public class ErrorHandler {
    
    private final IRPApplication application;
    private final String componentName;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public ErrorHandler(IRPApplication application, String componentName) {
        this.application = application;
        this.componentName = componentName;
    }
    
    /**
     * Log error with full stack trace and line numbers
     */
    public void logError(String message, Exception e) {
        logError(message, e, -1);
    }
    
    /**
     * Log error with line number context
     */
    public void logError(String message, Exception e, int lineNumber) {
        StringBuilder errorMsg = new StringBuilder();
        
        // Header
        errorMsg.append("================================================================================\n");
        errorMsg.append("ERROR in ").append(componentName);
        if (lineNumber > 0) {
            errorMsg.append(" at line ").append(lineNumber);
        }
        errorMsg.append("\n");
        errorMsg.append("Time: ").append(DATE_FORMAT.format(new Date())).append("\n");
        errorMsg.append("================================================================================\n");
        
        // Message
        errorMsg.append("Message: ").append(message).append("\n\n");
        
        // Exception details
        errorMsg.append("Exception Type: ").append(e.getClass().getName()).append("\n");
        errorMsg.append("Exception Message: ").append(e.getMessage()).append("\n\n");
        
        // Stack trace with line numbers
        errorMsg.append("Stack Trace:\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        for (int i = 0; i < Math.min(15, stackTrace.length); i++) {
            StackTraceElement element = stackTrace[i];
            errorMsg.append("  [").append(i).append("] ");
            errorMsg.append(element.getClassName()).append(".");
            errorMsg.append(element.getMethodName()).append("(");
            errorMsg.append(element.getFileName()).append(":");
            errorMsg.append(element.getLineNumber()).append(")\n");
        }
        
        // Caused by
        Throwable cause = e.getCause();
        if (cause != null) {
            errorMsg.append("\nCaused by: ").append(cause.getClass().getName()).append("\n");
            errorMsg.append("  Message: ").append(cause.getMessage()).append("\n");
            
            StackTraceElement[] causeTrace = cause.getStackTrace();
            if (causeTrace.length > 0) {
                errorMsg.append("  at ").append(causeTrace[0].getClassName()).append(".")
                       .append(causeTrace[0].getMethodName()).append("(")
                       .append(causeTrace[0].getFileName()).append(":")
                       .append(causeTrace[0].getLineNumber()).append(")\n");
            }
        }
        
        errorMsg.append("================================================================================\n");
        
        // Log to Rhapsody output window
        writeToOutput(errorMsg.toString());
        
        // Print to console
        e.printStackTrace();
    }
    
    /**
     * Log warning
     */
    public void logWarning(String message) {
        logWarning(message, -1);
    }
    
    /**
     * Log warning with line number
     */
    public void logWarning(String message, int lineNumber) {
        StringBuilder warnMsg = new StringBuilder();
        warnMsg.append("WARNING in ").append(componentName);
        if (lineNumber > 0) {
            warnMsg.append(" at line ").append(lineNumber);
        }
        warnMsg.append(": ").append(message).append("\n");
        
        writeToOutput(warnMsg.toString());
    }
    
    /**
     * Log info
     */
    public void logInfo(String message) {
        writeToOutput("INFO: " + message + "\n");
    }
    
    /**
     * Log debug (only if debug mode enabled)
     */
    public void logDebug(String message) {
        if (isDebugMode()) {
            writeToOutput("DEBUG: " + message + "\n");
        }
    }
    
    /**
     * Show error dialog to user
     */
    public void showErrorDialog(String title, String message, Exception e) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(message).append("\n\n");
        
        if (e != null) {
            userMessage.append("Technical details:\n");
            userMessage.append(e.getClass().getSimpleName()).append(": ");
            userMessage.append(e.getMessage()).append("\n\n");
            
            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace.length > 0) {
                StackTraceElement firstElement = stackTrace[0];
                userMessage.append("Location: ");
                userMessage.append(firstElement.getFileName()).append(":");
                userMessage.append(firstElement.getLineNumber()).append("\n\n");
            }
        }
        
        userMessage.append("See Output Window for full details.");
        
        JOptionPane.showMessageDialog(null, userMessage.toString(), 
                                     title, JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Show warning dialog to user
     */
    public void showWarningDialog(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
    }
    
    /**
     * Show info dialog to user
     */
    public void showInfoDialog(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Get full stack trace as string
     */
    public String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Check if debug mode is enabled
     */
    private boolean isDebugMode() {
        String debugMode = System.getProperty("ecad.debug", "false");
        return "true".equalsIgnoreCase(debugMode);
    }
    
    /**
     * Write to Rhapsody output window
     */
    private void writeToOutput(String message) {
        if (application != null) {
            application.writeToOutputWindow(componentName, message);
        } else {
            System.out.println(message);
        }
    }
    
    /**
     * Custom validation exception
     */
    public static class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}