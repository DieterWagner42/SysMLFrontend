package com.sysmlfrontend.backend;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

import com.sysmlfrontend.backend.server.LocalXmlModelStore;
import com.sysmlfrontend.backend.server.ModelStore;
import com.sysmlfrontend.backend.server.RhapsodyConnector;
import com.sysmlfrontend.backend.server.RhapsodyModelStore;
import com.sysmlfrontend.backend.server.WebServer;

/**
 * Worker JVM entry point — runs with rhapsody.jar on the classpath and the
 * Rhapsody bin directory on java.library.path (set by BootstrapApp).
 *
 * Launched automatically by {@link BootstrapApp}, and only when a Rhapsody install was actually
 * configured (installDir + rhapsody.jar found) — do not run directly unless you set
 * -Djava.library.path=<installDir>\bin yourself.
 *
 * Always starts with the local store active (the "offline first" workflow: build the model
 * locally, then explicitly promote it into Rhapsody via WebServer's /api/exportToRhapsody, or
 * jump straight into Rhapsody editing via /api/loadModel). The one exception: if config.ini's
 * projectPath is set, this eagerly connects and switches to Rhapsody mode at startup for
 * convenience, falling back to local mode if that eager connection fails — so "found a Rhapsody
 * server" still gates automatic live sync, just at startup instead of gating the whole process.
 *
 * Same connect/launch/serve/cleanup sequence as SPREAD's SpreadServer
 * (D:\KI\plugin\SPREAD), minus the UDP LAN-discovery beacon — this backend is
 * meant to be reached from a web frontend on the same machine, not
 * auto-discovered on the LAN.
 */
public class ModelServer {

    public static void main(String[] args) throws Exception {

        File iniFile = args.length > 0 ? new File(args[0]) : new File("config.ini");
        AppConfig config = AppConfig.load(iniFile);

        String installDir = config.get("Rhapsody", "installDir", null);
        String executable = config.get("Rhapsody", "executable", "rhapsody.exe");
        String instanceId = config.get("Rhapsody", "instanceId", null);
        String projectPath = config.get("Rhapsody", "projectPath", null);
        String launchArgs = config.get("Rhapsody", "launchArgs", "-dev_ed -lang=cpp");
        int timeoutSec = Integer.parseInt(config.get("Rhapsody", "startupTimeoutSec", "60"));
        String portMetaType = config.get("Rhapsody", "portMetaType", "Port");
        String levelMetaType = config.get("Rhapsody", "levelMetaType", "Class");
        String viewMetaType = config.get("Rhapsody", "viewMetaType", "Port");
        String sysmlProfile = config.get("Rhapsody", "sysmlProfile", "");
        int httpPort = Integer.parseInt(config.get("Server", "port", "0"));
        String statePath = config.get("Local", "statePath", "local-model.xml");

        ModelStore localStore = new LocalXmlModelStore(statePath);
        boolean[] rhapsodyEverConnected = {false};

        RhapsodyConnector connector = new RhapsodyConnector() {
            private IRPApplication cachedApp;

            @Override
            public boolean isAvailable() {
                return installDir != null && !installDir.isEmpty();
            }

            @Override
            public ModelStore connect(String path) throws Exception {
                if (cachedApp == null) {
                    cachedApp = connectToRhapsody(installDir, executable, instanceId, launchArgs, timeoutSec);
                    rhapsodyEverConnected[0] = true;
                    System.out.println("Rhapsody ready. Version: " + cachedApp.version());
                }
                IRPProject project = findAlreadyOpenProject(cachedApp, path);
                if (project != null) {
                    System.out.println("Reusing already-open Rhapsody project: " + project.getName());
                } else {
                    project = cachedApp.openProject(path);
                    if (project == null) {
                        throw new RuntimeException("Rhapsody-Projekt nicht gefunden: " + path
                                + " — bitte zuerst in Rhapsody anlegen (File > New Project) und den Pfad erneut angeben.");
                    }
                    System.out.println("Opened Rhapsody project: " + project.getName());
                }
                // Deliberately opt-in (empty by default — see config.ini) rather than always-on: the
                // whole port/proxyPort/interfaceBlock/view mechanism (RhapsodyModelStore) is already
                // ad-hoc — stamped via addStereotype/addNewAggr("Tag",...) — and explicitly does NOT
                // need the real SysML profile applied (see backend/CLAUDE.md's "Ports" section).
                // Calling addProfileToModel merges the actual profile's OWN read-only Port/Block
                // definitions into the project, which then collides with this app's plain
                // addNewAggr("Port", ...) port creation — reproduced live against a real project:
                // "Can't add aggregate of type Port. Cannot modify read only element (or element with
                // read only owner) $OMROOT\Profiles\SysML\SysMLProfile_rpy\SysML.sbs." on the very
                // first port created after the profile was applied.
                if (sysmlProfile != null && !sysmlProfile.isEmpty()) {
                    try {
                        cachedApp.addProfileToModel(sysmlProfile);
                        System.out.println("SysML profile ensured on project.");
                    } catch (Exception e) {
                        System.out.println("WARNING: could not apply SysML profile '" + sysmlProfile + "': " + e.getMessage());
                    }
                }
                return new RhapsodyModelStore(cachedApp, portMetaType, levelMetaType, viewMetaType, config);
            }
        };

        CompletableFuture<String> stopSignal = new CompletableFuture<>();
        WebServer server = new WebServer(localStore, connector, stopSignal, config, iniFile);

        if (projectPath != null && !projectPath.isEmpty()) {
            try {
                ModelStore eagerStore = connector.connect(projectPath);
                server.setActiveStore(eagerStore);
                System.out.println("Eagerly connected via configured projectPath — starting in Rhapsody mode.");
            } catch (Exception e) {
                System.out.println("Could not eagerly connect to configured projectPath (" + e.getMessage()
                        + ") — starting in local mode instead.");
            }
        }

        ServerRunner.run(server, stopSignal, httpPort);

        if (rhapsodyEverConnected[0]) {
            RhapsodyAppServer.CloseSession();
        }
    }

    /** Finds path among cachedApp's already-open projects by name (the path's own filename, minus
     * extension) instead of always calling IRPApplication#openProject — found live: repeatedly
     * calling openProject on a project that's already open is a recurring source of instability in
     * this app's session (see backend/CLAUDE.md's "Caution found live" notes — a stuck in-memory
     * session once, a wedged COM connection another time), so avoiding the call entirely when the
     * project is demonstrably already open removes one whole class of that risk. Matches by name,
     * not by comparing full paths — IRPProject has no exposed "get the file path back" accessor to
     * compare against. */
    private static IRPProject findAlreadyOpenProject(IRPApplication app, String path) {
        String fileName = new File(path).getName();
        String expectedName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        IRPCollection projects = app.getProjects();
        for (int i = 1; i <= projects.getCount(); i++) {   // Rhapsody: 1-based
            IRPProject p = (IRPProject) projects.getItem(i);
            if (expectedName.equals(p.getName())) return p;
        }
        return null;
    }

    // ── Rhapsody connection (same approach as SPREAD's SpreadServer) ──────

    private static IRPApplication connectToRhapsody(String installDir, String executable, String instanceId,
            String launchArgs, int timeoutSec) throws Exception {

        if (instanceId != null) {
            IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplicationByID(instanceId);
            if (app == null) {
                throw new RuntimeException("No Rhapsody instance with id '" + instanceId + "' found.");
            }
            System.out.println("Connected to Rhapsody instance: " + instanceId);
            return app;
        }

        IRPApplication app = tryGetActive();
        if (app != null) {
            System.out.println("Attached to running Rhapsody.");
            return app;
        }

        if (installDir == null) {
            throw new RuntimeException("No running Rhapsody found and 'installDir' is not set in config.ini.");
        }
        File exeFile = new File(installDir, executable);
        if (!exeFile.exists()) {
            throw new RuntimeException("Rhapsody executable not found: " + exeFile.getAbsolutePath());
        }

        List<String> launchCmd = new java.util.ArrayList<>();
        launchCmd.add(exeFile.getAbsolutePath());
        if (launchArgs != null && !launchArgs.trim().isEmpty()) {
            launchCmd.addAll(java.util.Arrays.asList(launchArgs.trim().split("\\s+")));
        }
        // Without an explicit -lang=..., rhapsody.exe shows an interactive perspective picker
        // (C/C++/Java/Ada/...) on first launch and never registers as a COM automation server,
        // so a fully automated launch just times out — this bit us during development.
        System.out.println("Launching: " + String.join(" ", launchCmd));
        new ProcessBuilder(launchCmd).directory(exeFile.getParentFile()).start();

        System.out.print("Waiting for Rhapsody to start");
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
            System.out.print(".");
            app = tryGetActive();
            if (app != null) {
                System.out.println(" connected.");
                return app;
            }
        }
        System.out.println();
        throw new RuntimeException("Rhapsody did not become available within " + timeoutSec + " seconds.");
    }

    private static IRPApplication tryGetActive() {
        try {
            List<?> ids = RhapsodyAppServer.getActiveRhapsodyApplicationIDList();
            if (ids == null || ids.isEmpty()) return null;
            return RhapsodyAppServer.getActiveRhapsodyApplication();
        } catch (Exception | Error ignored) {
            return null;
        }
    }
}
