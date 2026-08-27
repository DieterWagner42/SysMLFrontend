package com.sysmlfrontend.backend.server;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Native OS file-picker dialogs (Swing JFileChooser) — used by WebServer's POST /api/dialog, which
 * the frontend calls when a Load/Save path field was left empty. This backend already runs as a
 * plain process on the user's own Windows desktop (same machine as the browser, same assumption
 * Rhapsody automation itself relies on), so popping a native dialog here is safe — no rhapsody.jar
 * dependency, works in both tiers.
 *
 * Blocks the calling (HTTP handler) thread until the user responds; fine given this app's local,
 * single-user nature — there's never a second concurrent request to starve.
 */
final class FileDialogHelper {

    private FileDialogHelper() {}

    /** mode: "open" or "save". filterType: "xml", "rpyx", "folder", or null (no filter).
     * initialDirectory (nullable): where the dialog starts browsing — see WebServer#handleDialog,
     * which passes the local model's own current XML folder (LocalXmlModelStore#stateFolder) so
     * "Load Model"/"Save XML"/"Load XML" open where the user already told this app their model
     * lives, instead of wherever Swing/the OS would otherwise default to. Overridden by
     * suggestedName's own parent directory when that's a full path, same as before. Returns the
     * chosen absolute path, or null if the user cancelled. */
    static synchronized String show(String mode, String filterType, String title, String suggestedName, String initialDirectory) {
        String[] result = new String[1];
        Runnable task = () -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Cosmetic only — falls back to Swing's default look if the native L&F isn't available.
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title != null ? title : ("save".equals(mode) ? "Save As" : "Open File"));
            if (initialDirectory != null && !initialDirectory.isEmpty()) {
                chooser.setCurrentDirectory(new File(initialDirectory));
            }
            if ("folder".equals(filterType)) {
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else if ("xml".equals(filterType)) {
                chooser.setFileFilter(new FileNameExtensionFilter("SysML XML (*.xml)", "xml"));
            } else if ("rpyx".equals(filterType)) {
                chooser.setFileFilter(new FileNameExtensionFilter("Rhapsody Project (*.rpyx)", "rpyx"));
            }
            if (suggestedName != null && !suggestedName.isEmpty()) {
                chooser.setSelectedFile(new File(suggestedName));
            }

            // JFileChooser needs an owner window to reliably come to the foreground instead of
            // opening behind the browser — an invisible always-on-top frame does the job without
            // showing anything of its own. setLocationRelativeTo(null) would center it on the
            // PRIMARY monitor, which on a multi-monitor setup can be a different screen than the
            // one the user (and browser) is actually on — the dialog then opens out of sight.
            // Centering on whichever screen currently has the mouse pointer is a much better guess,
            // since the pointer is presumably still near the browser right after the button click
            // that triggered this.
            JFrame owner = new JFrame();
            owner.setUndecorated(true);
            owner.setAlwaysOnTop(true);
            positionOnCurrentScreen(owner);
            owner.setVisible(true);

            int outcome = "save".equals(mode) ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
            if (outcome == JFileChooser.APPROVE_OPTION) {
                result[0] = chooser.getSelectedFile().getAbsolutePath();
            }
            owner.dispose();
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeAndWait(task);
            }
        } catch (Exception e) {
            throw new RuntimeException("File dialog failed: " + e.getMessage(), e);
        }
        return result[0];
    }

    private static void positionOnCurrentScreen(JFrame frame) {
        try {
            Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
            for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle bounds = screen.getDefaultConfiguration().getBounds();
                if (bounds.contains(mouseLoc)) {
                    frame.setBounds(bounds.x + bounds.width / 2 - 1, bounds.y + bounds.height / 2 - 1, 1, 1);
                    return;
                }
            }
        } catch (Exception ignored) {
            // Fall through to default centering below (e.g. headless-ish edge cases).
        }
        frame.setLocationRelativeTo(null);
    }
}
