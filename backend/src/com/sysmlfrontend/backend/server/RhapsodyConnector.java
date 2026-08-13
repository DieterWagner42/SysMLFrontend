package com.sysmlfrontend.backend.server;

/**
 * Lets {@link WebServer} trigger an on-demand Rhapsody connection (for "Load Model" and
 * "Export to Rhapsody") without WebServer itself depending on rhapsody.jar — the actual
 * connect/launch logic lives wherever this is implemented. {@code ModelServer} (which has
 * rhapsody.jar on its classpath) supplies a real implementation; {@code BootstrapApp}'s
 * local-only tier supplies one that always reports unavailable, so that build still compiles
 * and runs with zero rhapsody.jar on the classpath.
 */
public interface RhapsodyConnector {

    /** True if this connector can actually attempt a connection (i.e. Rhapsody was configured
     * via config.ini's installDir). Doesn't guarantee connecting will succeed. */
    boolean isAvailable();

    /**
     * Connects to Rhapsody (attaching to a running instance, or launching one), opens the
     * project at {@code path}, makes sure the SysML profile is applied to it, and returns a
     * ready {@link ModelStore}. Throws with a clear message on any failure (not available,
     * connection failed, project not found, ...).
     */
    ModelStore connect(String path) throws Exception;
}
