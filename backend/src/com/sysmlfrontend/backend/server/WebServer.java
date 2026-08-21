package com.sysmlfrontend.backend.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.sysmlfrontend.backend.AppConfig;

/**
 * HTTP+JSON API for the SysML frontend. Holds a *swappable* active {@link ModelStore} — starts on
 * the always-available local store, and can switch to a Rhapsody-backed one via /api/loadModel or
 * /api/exportToRhapsody. WebServer itself has NO dependency on rhapsody.jar; the actual Rhapsody
 * connect logic is behind {@link RhapsodyConnector}, supplied by whoever constructs this (
 * {@code ModelServer}, which has rhapsody.jar on its classpath — or {@code BootstrapApp}'s
 * local-only tier, which supplies an "unavailable" connector).
 *
 * Single router under "/api/" (see routeAndDispatch) rather than one
 * createContext per resource, since com.sun.net.httpserver only prefix-matches
 * and this API needs path parameters (GUIDs) and verb-based dispatch
 * (GET/POST/PATCH/DELETE) on the same paths.
 *
 * Routes:
 *   GET    /api/status                                {"status","url","mode":"rhapsody"|"local",
 *                                                        "saveHealthy":true|false} — saveHealthy
 *                                                        false means the last mutation applied live
 *                                                        but failed to persist to disk (Rhapsody
 *                                                        mode only, see ModelStore#isSaveHealthy)
 *   POST   /api/newModel         {"name": "..."}       resets the local store (fresh title), switches
 *                                                        active store to it — "New Model"
 *   POST   /api/loadModel        {"path": "..."}       connects to Rhapsody (via RhapsodyConnector),
 *                                                        opens the given project, switches active
 *                                                        store to it — no data transfer, just "start
 *                                                        editing this Rhapsody project directly"
 *   POST   /api/exportToRhapsody {"path": "..."}       connects to Rhapsody, opens/attaches the given
 *                                                        project, pushes the CURRENT LOCAL model's
 *                                                        content into it, records the path on the
 *                                                        local model, then switches active store to
 *                                                        Rhapsody — "promote a local model into Rhapsody"
 *   POST   /api/selectElement    {"guid": "..."}        Rhapsody only — local mode throws
 *   POST   /api/dialog           {"mode":"open"|"save","filter":"xml"|"rpyx","title","suggestedName"}
 *                                                        pops a native OS file picker (this backend
 *                                                        runs locally, same machine as the browser)
 *                                                        — {"path": "..."} or {"path": null} if
 *                                                        cancelled. Used by the frontend when a
 *                                                        Load/Save path field was left empty.
 *   POST   /api/stop
 *   GET    /api/config/physicalInterfaceTypes           {"items": [...]} — the open, configurable
 *                                                        list of physical interface types offered
 *                                                        when adding a port in the Physical view
 *   PUT    /api/config/physicalInterfaceTypes            {"items": [...]} — persists to config.ini
 *                                                        (surgical single-line update, comments
 *                                                        and everything else untouched) — backs
 *                                                        the web config page (gear icon in header)
 *
 *   GET    /api/architecture                          full hierarchy tree
 *   POST   /api/architecture/elements                 {"parentGuid","name","kind":"SystemOfSystem"|"System"|"Subsystem"|"Equipment"}
 *   PATCH  /api/architecture/elements/{guid}           {"name": "..."}   (rename)
 *   DELETE /api/architecture/elements/{guid}
 *   PATCH  /api/architecture/elements/{guid}/parent    {"newParentGuid": "..."}   moves an existing
 *                                                        element under a new parent (or the model
 *                                                        root) — see ModelStore#moveElement
 *
 *   GET    /api/context                                list of Actors
 *   POST   /api/context/actors                         {"parentGuid","name"}
 *   DELETE /api/context/actors/{guid}
 *
 *   GET    /api/capabilities                           every top-level Capability, each its own box
 *                                                        (like a FunctionalNode) with its own UseCase
 *                                                        list — see GET .../useCases
 *   POST   /api/capabilities                           {"name"} — creates a new top-level Capability
 *   DELETE /api/capabilities/{guid}                     also deletes its owned UseCases
 *   GET    /api/capabilities/{guid}/useCases            UseCases owned by that Capability — mirrors
 *                                                        GET /api/elements/{guid}/functions
 *   POST   /api/capabilities/{guid}/useCases            {"name"}
 *   DELETE /api/useCases/{guid}
 *   GET    /api/elements/{guid}/capabilities            Capabilities LINKED to this architecture
 *                                                        element (a reference, not ownership) —
 *                                                        mirrors GET .../ports; also embedded
 *                                                        inline on each node from GET /api/architecture
 *   POST   /api/elements/{guid}/capabilities            {"capabilityGuid"} — links an existing
 *                                                        Capability to this element
 *   DELETE /api/elements/{guid}/capabilities/{capabilityGuid}   unlinks (does not delete the
 *                                                        Capability itself)
 *
 *   GET    /api/elements/{guid}/functions               functions owned by one FunctionalNode —
 *                                                        mirrors GET .../capabilities; also embedded
 *                                                        inline on each node from GET /api/architecture
 *   POST   /api/elements/{guid}/functions               {"name"} — {guid} is the owning FunctionalNode
 *   DELETE /api/functions/{guid}
 *
 *   GET    /api/elements/{guid}/logicalNodes             LogicalNodes ALLOCATED from this
 *                                                        FunctionalNode (Rhapsody: an "Allocate"
 *                                                        Dependency) — a reference, not ownership —
 *                                                        mirrors GET .../capabilities; also embedded
 *                                                        inline on each node from GET /api/architecture
 *   POST   /api/elements/{guid}/logicalNodes             {"logicalNodeGuid"} — links an existing
 *                                                        LogicalNode to this FunctionalNode
 *   DELETE /api/elements/{guid}/logicalNodes/{logicalNodeGuid}   unlinks (does not delete the
 *                                                        LogicalNode itself)
 *
 *   GET    /api/elements/{guid}/physicalNodes             PhysicalNodes ALLOCATED from this
 *                                                        LogicalNode — mirrors .../logicalNodes
 *   POST   /api/elements/{guid}/physicalNodes             {"physicalNodeGuid"} — links an existing
 *                                                        PhysicalNode to this LogicalNode
 *   DELETE /api/elements/{guid}/physicalNodes/{physicalNodeGuid}   unlinks (does not delete the
 *                                                        PhysicalNode itself)
 *
 *   GET    /api/elements/{guid}/documentation           {"documentation"} free-text notes — {guid}
 *                                                        may be ANY element kind (architecture
 *                                                        element, actor, capability, useCase, port,
 *                                                        function, contextView); "" if never set
 *   PATCH  /api/elements/{guid}/documentation           {"documentation"}
 *
 *   GET    /api/elements/{guid}/ports                  top-level ProxyPorts of a Block/Actor (each
 *                                                        with its own decomposition in "children")
 *   POST   /api/elements/{guid}/ports                  {"name","direction","type","view"} — {guid}
 *                                                        may be a Block/Actor OR an existing port,
 *                                                        in which case this creates a *nested*
 *                                                        (decomposed) port under it. view is one of
 *                                                        "Operational"|"Functional"|"Logical"|"Physical"
 *   PATCH  /api/ports/{guid}                           {"direction","type","view"}   (retype)
 *   DELETE /api/ports/{guid}
 *
 *   PATCH  /api/positions/{guid}                       {"x","y","view"}   saves a canvas position
 *                                                        (architecture element, actor, or useCase)
 *                                                        so a manual drag survives reloads instead
 *                                                        of being overwritten by auto-layout. "view"
 *                                                        (one of "Structure"|"Operational"|
 *                                                        "Functional"|"Logical"|"Physical") is
 *                                                        required for an architecture element — see
 *                                                        ModelStore#setPosition — and ignored for an
 *                                                        actor/useCase, which have no view concept
 *
 *   POST   /api/export                                 {"path": "..."}   writes a snapshot XML file
 *   POST   /api/import                                 {"path": "..."}   recreates a snapshot XML file's contents
 *
 * Responses are JSON; errors are {"status":"error","message":"..."} with a
 * non-2xx status code. CORS is wide open (Access-Control-Allow-Origin: *)
 * since the frontend dev server runs on a different port.
 */
public class WebServer {

    private final ModelStore localStore;
    private final RhapsodyConnector rhapsodyConnector;
    private final CompletableFuture<String> stopSignal;
    private final AppConfig config;
    private final File configFile;

    private ModelStore activeStore;
    private HttpServer httpServer;
    private String url;

    public WebServer(ModelStore localStore, RhapsodyConnector rhapsodyConnector, CompletableFuture<String> stopSignal,
            AppConfig config, File configFile) {
        this.localStore = localStore;
        this.rhapsodyConnector = rhapsodyConnector;
        this.activeStore = localStore;
        this.stopSignal = stopSignal;
        this.config = config;
        this.configFile = configFile;
    }

    /** Switches the active store, e.g. once ModelServer has eagerly connected at startup
     * (config.ini's projectPath) before the HTTP server is even up. */
    public void setActiveStore(ModelStore store) {
        this.activeStore = store;
    }

    public void start(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        httpServer.createContext("/api/", this::dispatch);
        httpServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "backend-HTTP");
            t.setDaemon(true);
            return t;
        }));
        httpServer.start();

        int boundPort = httpServer.getAddress().getPort();
        url = "http://localhost:" + boundPort + "/";
        log("Web server listening at " + url + " (mode: " + activeStore.mode() + ")");

        startConfigFileWatcher();
    }

    public void stop() {
        if (httpServer != null) { httpServer.stop(1); httpServer = null; }
    }

    /** Watches config.ini's directory for changes to that exact file (e.g. hand-editing
     * physicalInterfaceTypes, or any other key) and reloads it into the in-memory AppConfig on the
     * fly — so an external edit takes effect immediately, without restarting the backend. Only
     * meaningfully affects settings actually re-read at runtime (like physicalInterfaceTypes);
     * settings only consulted once at startup (installDir, port, ...) still need a restart, same
     * as before — a live-editable ini value doesn't retroactively change decisions already made
     * (e.g. which JVM tier is running). */
    private void startConfigFileWatcher() {
        File absFile = configFile.getAbsoluteFile();
        File dir = absFile.getParentFile();
        if (dir == null) return;
        Thread watcherThread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                dir.toPath().register(watchService, ENTRY_MODIFY, ENTRY_CREATE);
                while (true) {
                    WatchKey key;
                    try {
                        key = watchService.take(); // blocks until the directory changes
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Object ctx = event.context();
                        if (ctx instanceof Path && ((Path) ctx).toString().equals(absFile.getName())) {
                            try {
                                config.reload(absFile);
                                log("Reloaded " + absFile.getName() + " (changed on disk)");
                            } catch (Exception e) {
                                log("Warning: failed to reload " + absFile.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                    if (!key.reset()) break; // directory became inaccessible — stop watching
                }
            } catch (IOException e) {
                log("Warning: could not start config file watcher: " + e.getMessage());
            }
        }, "backend-config-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public String getUrl() { return url; }

    // ── Router ────────────────────────────────────────────────────────────

    private void dispatch(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PATCH,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.replaceFirst("^/api/?", "").split("/");
        // parts[0] == "" when path was exactly "/api" or "/api/"

        try {
            routeAndDispatch(exchange, method, parts);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            respond(exchange, 400, err(e));
        } catch (Exception e) {
            log("Unhandled error on " + method + " " + path + ": " + e);
            respond(exchange, 500, err(e));
        }
    }

    private void routeAndDispatch(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 0 || p[0].isEmpty()) { notFound(exchange); return; }

        switch (p[0]) {
            case "status":
                if (isGet(method)) { handleStatus(exchange); return; }
                break;
            case "newModel":
                if (isPost(method)) { handleNewModel(exchange); return; }
                break;
            case "loadModel":
                if (isPost(method)) { handleLoadModel(exchange); return; }
                break;
            case "exportToRhapsody":
                if (isPost(method)) { handleExportToRhapsody(exchange); return; }
                break;
            case "selectElement":
                if (isPost(method)) { handleSelectElement(exchange); return; }
                break;
            case "dialog":
                if (isPost(method)) { handleDialog(exchange); return; }
                break;
            case "config":
                handleConfig(exchange, method, p); return;
            case "stop":
                if (isPost(method)) { handleStop(exchange); return; }
                break;
            case "architecture":
                handleArchitecture(exchange, method, p); return;
            case "context":
                handleContext(exchange, method, p); return;
            case "capabilities":
                handleCapabilities(exchange, method, p); return;
            case "contextViews":
                handleContextViews(exchange, method, p); return;
            case "useCases":
                handleUseCases(exchange, method, p); return;
            case "elements":
                handleElementsPorts(exchange, method, p); return;
            case "ports":
                handlePorts(exchange, method, p); return;
            case "functions":
                handleFunctions(exchange, method, p); return;
            case "positions":
                handlePositions(exchange, method, p); return;
            case "sizes":
                handleSizes(exchange, method, p); return;
            case "connectors":
                handleConnectors(exchange, method, p); return;
            case "export":
                if (isPost(method)) { handleExport(exchange); return; }
                break;
            case "import":
                if (isPost(method)) { handleImport(exchange); return; }
                break;
            default:
                break;
        }
        notFound(exchange);
    }

    // ── /api/status, /api/newModel, /api/loadModel, /api/exportToRhapsody, /api/selectElement, /api/stop ──

    private void handleStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("url", url);
        body.put("mode", activeStore.mode());
        body.put("rhapsodyAvailable", rhapsodyConnector.isAvailable());
        body.put("saveHealthy", activeStore.isSaveHealthy());
        respond(exchange, 200, body);
    }

    private void handleNewModel(HttpExchange exchange) throws IOException {
        String name = Json.getString(readBody(exchange), "name");
        localStore.reset(name);
        activeStore = localStore;
        respond(exchange, 200, activeStore.getArchitecture());
    }

    private void handleLoadModel(HttpExchange exchange) throws IOException {
        String path = Json.getString(readBody(exchange), "path");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        ModelStore rhapsodyStore = connectRhapsody(path);
        activeStore = rhapsodyStore;
        respond(exchange, 200, ok("project", rhapsodyStore.getArchitecture().get("name")));
    }

    /** Promotes the current local model into a Rhapsody project: connect, open/attach the given
     * .rpyx, push every local architecture/context/capabilities element in via ModelXml, remember
     * the path on the local model (so a future export defaults to the same target), then make the
     * now-populated Rhapsody store active. */
    private void handleExportToRhapsody(HttpExchange exchange) throws IOException {
        String path = Json.getString(readBody(exchange), "path");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        String xml = ModelXml.export(localStore);
        ModelStore rhapsodyStore = connectRhapsody(path);
        Map<String, Object> summary = ModelXml.importInto(rhapsodyStore, rhapsodyStore.rootGuid(), xml);
        if (localStore instanceof LocalXmlModelStore) {
            ((LocalXmlModelStore) localStore).setLinkedRhapsodyPath(path);
        }
        activeStore = rhapsodyStore;
        respond(exchange, 200, summary);
    }

    private ModelStore connectRhapsody(String path) {
        try {
            return rhapsodyConnector.connect(path);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void handleDialog(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        String path = FileDialogHelper.show(str(body, "mode"), str(body, "filter"), str(body, "title"), str(body, "suggestedName"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        respond(exchange, 200, result);
    }

    // ── /api/config/... ───────────────────────────────────────────────────

    private void handleConfig(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "physicalInterfaceTypes".equals(p[1])) {
            if (isGet(method)) {
                respond(exchange, 200, wrapList(config.getList("Physical", "interfaceTypes", "")));
                return;
            } else if ("PUT".equalsIgnoreCase(method) || isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                Object itemsObj = body.get("items");
                if (!(itemsObj instanceof List)) {
                    throw new IllegalArgumentException("items must be a list of strings");
                }
                List<String> items = new ArrayList<>();
                for (Object o : (List<?>) itemsObj) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) items.add(s);
                }
                config.updateValue(configFile, "Physical", "interfaceTypes", String.join(",", items));
                respond(exchange, 200, ok("items", items));
                return;
            }
        }
        notFound(exchange);
    }

    private void handleSelectElement(HttpExchange exchange) throws IOException {
        String guid = Json.getString(readBody(exchange), "guid");
        String name = activeStore.selectElement(guid);
        respond(exchange, 200, ok("element", name));
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        respond(exchange, 200, ok("message", "stopping"));
        log("Stop requested via HTTP.");
        new Thread(() -> stopSignal.complete("HTTP /stop"), "backend-stop").start();
    }

    // ── /api/architecture[...] ──────────────────────────────────────────

    private void handleArchitecture(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 1) {
            if (isGet(method)) { respond(exchange, 200, activeStore.getArchitecture()); return; }
        } else if (p.length == 2 && "elements".equals(p[1]) && isPost(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            Map<String, Object> created = activeStore.createArchitectureElement(
                    str(body, "parentGuid"), str(body, "name"), str(body, "kind"));
            respond(exchange, 200, created);
            return;
        } else if (p.length == 3 && "elements".equals(p[1])) {
            String guid = p[2];
            if ("PATCH".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.renameElement(guid, str(body, "name"));
                respond(exchange, 200, ok("guid", guid));
                return;
            } else if ("DELETE".equalsIgnoreCase(method)) {
                activeStore.deleteElement(guid);
                respond(exchange, 200, ok("guid", guid));
                return;
            }
        } else if (p.length == 4 && "elements".equals(p[1]) && "parent".equals(p[3]) && "PATCH".equalsIgnoreCase(method)) {
            String guid = p[2];
            Map<String, Object> body = readJsonObject(exchange);
            activeStore.moveElement(guid, str(body, "newParentGuid"));
            respond(exchange, 200, ok("guid", guid));
            return;
        }
        notFound(exchange);
    }

    // ── /api/context[...] ────────────────────────────────────────────────

    private void handleContext(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 1 && isGet(method)) {
            respond(exchange, 200, wrapList(activeStore.getContext()));
            return;
        } else if (p.length == 2 && "actors".equals(p[1]) && isPost(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            respond(exchange, 200, activeStore.createActor(str(body, "parentGuid"), str(body, "name")));
            return;
        } else if (p.length == 3 && "actors".equals(p[1]) && "DELETE".equalsIgnoreCase(method)) {
            activeStore.deleteElement(p[2]);
            respond(exchange, 200, ok("guid", p[2]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/capabilities[...] ───────────────────────────────────────────

    private void handleCapabilities(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 1 && isGet(method)) {
            respond(exchange, 200, wrapList(activeStore.getCapabilities()));
            return;
        } else if (p.length == 1 && isPost(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            respond(exchange, 200, activeStore.createCapability(str(body, "name")));
            return;
        } else if (p.length == 2 && "DELETE".equalsIgnoreCase(method)) {
            activeStore.deleteElement(p[1]);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        } else if (p.length == 3 && "useCases".equals(p[2])) {
            String capabilityGuid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, wrapList(activeStore.getUseCasesOf(capabilityGuid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                respond(exchange, 200, activeStore.createUseCase(capabilityGuid, str(body, "name")));
                return;
            }
        }
        notFound(exchange);
    }

    // ── /api/contextViews[...] — user-defined Context tab groupings of Actors ───────────

    private void handleContextViews(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 1 && isGet(method)) {
            respond(exchange, 200, wrapList(activeStore.getContextViews()));
            return;
        } else if (p.length == 1 && isPost(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            respond(exchange, 200, activeStore.createContextView(str(body, "name")));
            return;
        } else if (p.length == 2 && "DELETE".equalsIgnoreCase(method)) {
            activeStore.deleteElement(p[1]);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/useCases/{guid} ─────────────────────────────────────────────

    private void handleUseCases(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "DELETE".equalsIgnoreCase(method)) {
            activeStore.deleteElement(p[1]);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        } else if (p.length == 3 && "detail".equals(p[2]) && "GET".equalsIgnoreCase(method)) {
            respond(exchange, 200, activeStore.getUseCaseDetail(p[1]));
            return;
        } else if (p.length == 3 && "detail".equals(p[2]) && "PATCH".equalsIgnoreCase(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            activeStore.updateUseCase(p[1], body);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/connectors — "pending connectors" panel (see ModelStore#getPendingConnectors' own
    // javadoc for why this exists: a port added directly under an existing "external"/"internal"
    // container has no automatic connector-creation trigger of its own, so this view/switch lets
    // the user force it). GET lists every currently-missing connector (a fresh scan every call, no
    // caching); POST {"linkOwnerGuid","fromPartGuid","toPartGuid","fromPortGuid","toPortGuid"}
    // creates one specific entry from that list, using the exact GUIDs it reported. Always empty/
    // no-op in local mode (see ModelStore's own default implementations). ────
    private void handleConnectors(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "table".equals(p[1]) && isGet(method)) {
            // GET /api/connectors/table — the "Connectors" tab's own table: one row per connector
            // (existing AND pending), {"view","fromOwner","fromName","toOwner","toName"}. See
            // ModelStore#getConnectorTable's own javadoc.
            respond(exchange, 200, wrapList(activeStore.getConnectorTable()));
            return;
        } else if (p.length == 1 && isGet(method)) {
            respond(exchange, 200, wrapList(activeStore.getPendingConnectors()));
            return;
        } else if (p.length == 1 && isPost(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            activeStore.createPendingConnector(str(body, "linkOwnerGuid"), str(body, "fromPartGuid"),
                    str(body, "toPartGuid"), str(body, "fromPortGuid"), str(body, "toPortGuid"),
                    str(body, "fromOwnerGuid"), str(body, "toOwnerGuid"));
            respond(exchange, 200, ok("status", "ok"));
            return;
        }
        notFound(exchange);
    }

    // ── /api/elements/{guid}/ports, /api/elements/{guid}/capabilities, /api/elements/{guid}/functions ────

    private void handleElementsPorts(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 3 && "ports".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, wrapList(activeStore.getPorts(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                respond(exchange, 200, activeStore.createPort(guid, str(body, "name"), str(body, "direction"), str(body, "type"), str(body, "view")));
                return;
            }
        } else if (p.length == 3 && "capabilities".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                // Embedded inline on architecture tree nodes already (see getArchitecture) — this
                // standalone endpoint mirrors getPorts's for symmetry/direct use.
                respond(exchange, 200, wrapList(activeStore.getCapabilitiesOf(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.linkCapability(guid, str(body, "capabilityGuid"));
                respond(exchange, 200, wrapList(activeStore.getCapabilitiesOf(guid)));
                return;
            }
        } else if (p.length == 4 && "capabilities".equals(p[2]) && "DELETE".equalsIgnoreCase(method)) {
            activeStore.unlinkCapability(p[1], p[3]);
            respond(exchange, 200, ok("guid", p[3]));
            return;
        } else if (p.length == 3 && "contextViews".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, wrapList(activeStore.getContextViewsOf(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.linkContextView(guid, str(body, "contextViewGuid"));
                respond(exchange, 200, wrapList(activeStore.getContextViewsOf(guid)));
                return;
            }
        } else if (p.length == 4 && "contextViews".equals(p[2]) && "DELETE".equalsIgnoreCase(method)) {
            activeStore.unlinkContextView(p[1], p[3]);
            respond(exchange, 200, ok("guid", p[3]));
            return;
        } else if (p.length == 3 && "functions".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                // Embedded inline on architecture tree nodes already — mirrors .../capabilities.
                respond(exchange, 200, wrapList(activeStore.getFunctionsOf(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                respond(exchange, 200, activeStore.createFunction(guid, str(body, "name")));
                return;
            }
        } else if (p.length == 3 && "logicalNodes".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, wrapList(activeStore.getAllocatedLogicalNodesOf(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.linkLogicalNode(guid, str(body, "logicalNodeGuid"));
                respond(exchange, 200, wrapList(activeStore.getAllocatedLogicalNodesOf(guid)));
                return;
            }
        } else if (p.length == 4 && "logicalNodes".equals(p[2]) && "DELETE".equalsIgnoreCase(method)) {
            activeStore.unlinkLogicalNode(p[1], p[3]);
            respond(exchange, 200, ok("guid", p[3]));
            return;
        } else if (p.length == 3 && "physicalNodes".equals(p[2])) {
            String guid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, wrapList(activeStore.getAllocatedPhysicalNodesOf(guid)));
                return;
            } else if (isPost(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.linkPhysicalNode(guid, str(body, "physicalNodeGuid"));
                respond(exchange, 200, wrapList(activeStore.getAllocatedPhysicalNodesOf(guid)));
                return;
            }
        } else if (p.length == 4 && "physicalNodes".equals(p[2]) && "DELETE".equalsIgnoreCase(method)) {
            activeStore.unlinkPhysicalNode(p[1], p[3]);
            respond(exchange, 200, ok("guid", p[3]));
            return;
        } else if (p.length == 3 && "documentation".equals(p[2])) {
            // Generic across every element kind (architecture element, actor, capability, useCase,
            // port, function, contextView) — activeStore.getDocumentation/setDocumentation dispatch
            // internally, so {guid} alone is enough regardless of what kind it belongs to.
            String guid = p[1];
            if (isGet(method)) {
                respond(exchange, 200, ok("documentation", activeStore.getDocumentation(guid)));
                return;
            } else if ("PATCH".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                activeStore.setDocumentation(guid, str(body, "documentation"));
                respond(exchange, 200, ok("guid", guid));
                return;
            }
        }
        notFound(exchange);
    }

    // ── /api/ports/{guid} ────────────────────────────────────────────────

    private void handlePorts(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2) {
            String guid = p[1];
            if ("PATCH".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonObject(exchange);
                respond(exchange, 200, activeStore.updatePort(guid, str(body, "direction"), str(body, "type"), str(body, "view")));
                return;
            } else if ("DELETE".equalsIgnoreCase(method)) {
                activeStore.deleteElement(guid);
                respond(exchange, 200, ok("guid", guid));
                return;
            }
        }
        notFound(exchange);
    }

    // ── /api/functions/{guid} ────────────────────────────────────────────

    private void handleFunctions(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "DELETE".equalsIgnoreCase(method)) {
            activeStore.deleteElement(p[1]);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/positions/{guid} ────────────────────────────────────────────

    private void handlePositions(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "PATCH".equalsIgnoreCase(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            Object x = body.get("x");
            Object y = body.get("y");
            if (!(x instanceof Number) || !(y instanceof Number)) {
                throw new IllegalArgumentException("x and y must be numbers");
            }
            String view = str(body, "view");
            activeStore.setPosition(p[1], view, ((Number) x).doubleValue(), ((Number) y).doubleValue());
            respond(exchange, 200, ok("guid", p[1]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/sizes/{guid} — manually-resized box width/height (see ModelStore#setSize) ──

    private void handleSizes(HttpExchange exchange, String method, String[] p) throws IOException {
        if (p.length == 2 && "PATCH".equalsIgnoreCase(method)) {
            Map<String, Object> body = readJsonObject(exchange);
            Object width = body.get("width");
            Object height = body.get("height");
            if (!(width instanceof Number) || !(height instanceof Number)) {
                throw new IllegalArgumentException("width and height must be numbers");
            }
            String view = str(body, "view");
            activeStore.setSize(p[1], ((Number) width).doubleValue(), ((Number) height).doubleValue(), view);
            respond(exchange, 200, ok("guid", p[1]));
            return;
        }
        notFound(exchange);
    }

    // ── /api/export, /api/import ─────────────────────────────────────────

    private void handleExport(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        String path = str(body, "path");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        String xml = ModelXml.export(activeStore);
        Files.write(Paths.get(path), xml.getBytes(StandardCharsets.UTF_8));
        respond(exchange, 200, ok("path", path));
    }

    private void handleImport(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        String path = str(body, "path");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        String xml = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        Map<String, Object> summary = ModelXml.importInto(activeStore, activeStore.rootGuid(), xml);
        respond(exchange, 200, summary);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static boolean isGet(String method) { return "GET".equalsIgnoreCase(method); }
    private static boolean isPost(String method) { return "POST".equalsIgnoreCase(method); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        Object parsed = Json.parse(readBody(exchange));
        if (parsed instanceof Map) return (Map<String, Object>) parsed;
        return new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private Map<String, Object> wrapList(Object list) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", list);
        return body;
    }

    private Map<String, Object> ok(String key, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put(key, value);
        return body;
    }

    private Map<String, Object> err(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
        return body;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        }
    }

    private void respond(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void notFound(HttpExchange exchange) throws IOException {
        respond(exchange, 404, ok("message", "not found"));
    }

    private void log(String text) {
        System.out.println("[backend] " + text);
    }
}
