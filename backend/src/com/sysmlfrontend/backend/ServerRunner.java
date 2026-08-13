package com.sysmlfrontend.backend;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import com.sysmlfrontend.backend.server.WebServer;

/**
 * Shared server run-loop (start WebServer, wait for console/HTTP stop, clean shutdown) used by
 * both {@link BootstrapApp} (local mode — no rhapsody.jar) and {@link ModelServer} (Rhapsody mode,
 * or its fallback to local). Has no rhapsody.jar dependency itself; closing a live Rhapsody
 * session, if any, is the caller's responsibility after this method returns.
 *
 * Takes an already-constructed {@link WebServer} (and the same {@link CompletableFuture} it was
 * built with) rather than building one itself, so the caller can do any one-time setup — e.g.
 * ModelServer eagerly switching the active store to Rhapsody via {@code setActiveStore} when
 * config.ini's projectPath is set — before the HTTP server actually starts accepting requests.
 */
final class ServerRunner {

    private ServerRunner() {}

    static void run(WebServer server, CompletableFuture<String> stopSignal, int port) throws Exception {
        server.start(port);
        System.out.println("Backend running at " + server.getUrl());
        System.out.println("Type  stop  and press Enter, or POST /api/stop, to exit.");

        Thread console = new Thread(() -> readConsoleUntilStop(stopSignal), "backend-console");
        console.setDaemon(true);
        console.start();

        String reason = stopSignal.get();
        System.out.println("Stopping: " + reason);
        server.stop();
        System.out.println("Backend stopped.");
    }

    private static void readConsoleUntilStop(CompletableFuture<String> stopSignal) {
        try (Scanner sc = new Scanner(System.in)) {
            while (!stopSignal.isDone() && sc.hasNextLine()) {
                if ("stop".equalsIgnoreCase(sc.nextLine().trim())) {
                    stopSignal.complete("console stop command");
                    break;
                }
            }
        }
    }
}
