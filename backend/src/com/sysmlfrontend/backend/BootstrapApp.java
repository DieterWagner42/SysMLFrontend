package com.sysmlfrontend.backend;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.sysmlfrontend.backend.server.LocalXmlModelStore;
import com.sysmlfrontend.backend.server.ModelStore;
import com.sysmlfrontend.backend.server.RhapsodyConnector;
import com.sysmlfrontend.backend.server.WebServer;

/**
 * Bootstrap launcher — has NO dependency on rhapsody.jar.
 *
 * "On the fly" Rhapsody sync is opt-in: it's only used when config.ini's [Rhapsody] installDir
 * points at a real Rhapsody install with rhapsody.jar under it. If installDir isn't set (or the
 * jar isn't found there), this app runs entirely in THIS JVM against LocalXmlModelStore — no
 * second JVM, no native library path, no rhapsody.jar on the classpath at all. Only when a
 * Rhapsody install is actually configured does BootstrapApp relaunch into ModelServer, which
 * itself falls back to LocalXmlModelStore too if that configured Rhapsody can't actually be
 * reached (see ModelServer) — so "found a Rhapsody server" is required at two points: is one
 * configured, and does connecting to it actually succeed.
 *
 * The Rhapsody native bridge (rhapsody.jar) requires the Rhapsody bin directory on
 * java.library.path, and this flag cannot be changed after JVM startup on Java 17+ (the module
 * system blocks the reflection hack older code used to mutate it at runtime) — hence the relaunch
 * rather than just adding the jar to this JVM's classpath when Rhapsody is wanted. Same two-JVM
 * pattern as the sibling SPREAD project (D:\KI\plugin\SPREAD).
 *
 * Build (no rhapsody.jar needed — this is everything BootstrapApp itself needs to run standalone
 * in local mode):
 *   javac -d out src/com/sysmlfrontend/backend/BootstrapApp.java src/com/sysmlfrontend/backend/AppConfig.java src/com/sysmlfrontend/backend/ServerRunner.java src/com/sysmlfrontend/backend/server/Json.java src/com/sysmlfrontend/backend/server/ModelStore.java src/com/sysmlfrontend/backend/server/ModelXml.java src/com/sysmlfrontend/backend/server/HierarchyLevels.java src/com/sysmlfrontend/backend/server/RhapsodyConnector.java src/com/sysmlfrontend/backend/server/LocalXmlModelStore.java src/com/sysmlfrontend/backend/server/WebServer.java
 *
 * Run:
 *   java -cp out com.sysmlfrontend.backend.BootstrapApp [config.ini]
 */
public class BootstrapApp {

    public static void main(String[] args) throws Exception {
        File iniFile = args.length > 0 ? new File(args[0]) : new File("config.ini");
        System.out.println("Loading config: " + iniFile.getAbsolutePath());
        AppConfig config = AppConfig.load(iniFile);

        String installDir = config.get("Rhapsody", "installDir", null);
        int port = Integer.parseInt(config.get("Server", "port", "0"));

        if (installDir == null || installDir.isEmpty()) {
            System.out.println("No 'installDir' configured — running in local mode (XML only, no Rhapsody).");
            runLocal(config, iniFile, port);
            return;
        }

        String rhapsodyJar = findRhapsodyJar(installDir);
        if (rhapsodyJar == null) {
            System.out.println("'installDir' is set but rhapsody.jar wasn't found under it — running in local mode (XML only).");
            runLocal(config, iniFile, port);
            return;
        }

        relaunchWithRhapsody(iniFile, installDir, rhapsodyJar);
    }

    private static void runLocal(AppConfig config, File iniFile, int port) throws Exception {
        String statePath = config.get("Local", "statePath", "local-model.xml");
        ModelStore localStore = new LocalXmlModelStore(statePath);
        RhapsodyConnector unavailable = new RhapsodyConnector() {
            @Override public boolean isAvailable() { return false; }
            @Override public ModelStore connect(String path) {
                throw new UnsupportedOperationException(
                        "Rhapsody is not configured — set 'installDir' in config.ini and restart the backend.");
            }
        };
        CompletableFuture<String> stopSignal = new CompletableFuture<>();
        WebServer server = new WebServer(localStore, unavailable, stopSignal, config, iniFile);
        ServerRunner.run(server, stopSignal, port);
    }

    private static void relaunchWithRhapsody(File iniFile, String installDir, String rhapsodyJar) throws Exception {
        // rhapsody.dll (the actual JNI bridge RhapsodyAppServer.loadLibrary("rhapsody") needs) does
        // NOT live in the install root on Rhapsody 10.0.3 — it ships next to rhapsody.jar itself
        // (e.g. <installDir>\Share\JavaAPI\rhapsody.dll). Pass both that directory and the install
        // root on java.library.path (path-separator-joined, same as classpath) so this works across
        // whichever layout the installed version actually uses.
        String jarDir = new File(rhapsodyJar).getParent();
        String nativeLibPath = jarDir + File.pathSeparator + installDir;

        String currentCp = System.getProperty("java.class.path", ".");
        String newCp = currentCp.contains("rhapsody")
                ? currentCp
                : rhapsodyJar + File.pathSeparator + currentCp;

        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File javaExeFile = new File(javaExe + ".exe");
        if (javaExeFile.exists()) javaExe = javaExeFile.getAbsolutePath();

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Djava.library.path=" + nativeLibPath);
        cmd.add("-cp");
        cmd.add(newCp);
        cmd.add("com.sysmlfrontend.backend.ModelServer");
        cmd.add(iniFile.getAbsolutePath());

        System.out.println("Rhapsody install configured — launching model server with native path: " + nativeLibPath);
        int exit = new ProcessBuilder(cmd)
                .inheritIO()
                .start()
                .waitFor();
        System.exit(exit);
    }

    private static String findRhapsodyJar(String installDir) {
        String binDir = installDir + File.separator;
        String[] candidates = {
            new File(installDir, "rhapsody.jar").getPath(),
            new File(binDir, "rhapsody.jar").getPath(),
            new File(new File(installDir, "share" + File.separator + "JavaAPI"), "rhapsody.jar").getPath(),
        };
        for (String c : candidates) {
            if (new File(c).exists()) return c;
        }
        for (String entry : System.getProperty("java.class.path", "").split(
                java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.endsWith("rhapsody.jar") && new File(entry).exists()) return entry;
        }
        return null;
    }
}
