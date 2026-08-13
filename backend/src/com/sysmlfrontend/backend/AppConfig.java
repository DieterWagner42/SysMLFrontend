package com.sysmlfrontend.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal INI file reader for config.ini.
 *
 * Supports sections ([SectionName]), key=value pairs, and line comments
 * starting with ; or #. Inline comments after the value are also stripped.
 */
public class AppConfig {

    private final Map<String, Map<String, String>> sections = new LinkedHashMap<>();

    private AppConfig() {}

    /** Loads the given INI file; returns an empty config if the file does not exist. */
    public static AppConfig load(File file) throws IOException {
        AppConfig config = new AppConfig();
        config.reload(file);
        return config;
    }

    /** Re-parses the given file into this SAME instance, replacing its current contents —
     * everything previously returned by get()/getList() reflects the file's latest contents right
     * after this returns, no new AppConfig object needed. Used both by load() (parsing an initially
     * empty instance) and by WebServer's background file watcher (see startConfigFileWatcher), so
     * an external edit to config.ini (e.g. hand-editing physicalInterfaceTypes) is picked up
     * without restarting the backend. */
    public synchronized void reload(File file) throws IOException {
        sections.clear();
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String currentSection = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    sections.computeIfAbsent(currentSection, k -> new LinkedHashMap<>());
                } else {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        int ci = value.indexOf(';');
                        if (ci >= 0) {
                            value = value.substring(0, ci).trim();
                        }
                        sections
                                .computeIfAbsent(currentSection, k -> new LinkedHashMap<>())
                                .put(key, value);
                    }
                }
            }
        }
    }

    /**
     * Returns the value for key in section, or defaultValue if absent.
     * Empty strings are treated as absent.
     */
    public synchronized String get(String section, String key, String defaultValue) {
        Map<String, String> sec = sections.get(section);
        if (sec == null) {
            return defaultValue;
        }
        String val = sec.get(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    public synchronized boolean has(String section, String key) {
        Map<String, String> sec = sections.get(section);
        return sec != null && sec.containsKey(key);
    }

    /** Comma-separated list value (e.g. "data,electrical,mechanical") — NOT semicolon-separated,
     * since this parser already treats ';' as an inline-comment marker (see load() above), so a
     * semicolon-separated list would get silently truncated at the first entry. Empty entries
     * (from stray/doubled commas) are dropped. */
    public synchronized List<String> getList(String section, String key, String defaultCsv) {
        String raw = get(section, key, defaultCsv);
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Surgically updates a single key's value in an ini file on disk — preserves every other
     * line (comments, other sections/keys, formatting) untouched, unlike a naive "serialize the
     * whole parsed config back out" approach, which would destroy this project's extensively
     * hand-documented config.ini. Adds the section and/or key if either doesn't already exist.
     * Also updates this in-memory AppConfig's own copy of the value, so a subsequent get()/
     * getList() in the same process sees the change without a restart. */
    public synchronized void updateValue(File file, String section, String key, String value) throws IOException {
        List<String> lines = file.exists() ? Files.readAllLines(file.toPath()) : new ArrayList<>();
        int sectionStart = -1;
        int sectionEnd = lines.size(); // exclusive — index of the next "[...]" header, or EOF
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String name = trimmed.substring(1, trimmed.length() - 1).trim();
                if (sectionStart == -1 && name.equals(section)) {
                    sectionStart = i;
                } else if (sectionStart != -1) {
                    sectionEnd = i;
                    break;
                }
            }
        }
        String newLine = key + "=" + value;
        if (sectionStart == -1) {
            // Section doesn't exist yet — append a fresh one at the end of the file.
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
            lines.add("[" + section + "]");
            lines.add(newLine);
        } else {
            int keyLine = -1;
            for (int i = sectionStart + 1; i < sectionEnd; i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith(key + "=") || trimmed.startsWith(key + " =")) {
                    keyLine = i;
                    break;
                }
            }
            if (keyLine != -1) {
                lines.set(keyLine, newLine);
            } else {
                lines.add(sectionEnd, newLine);
            }
        }
        Files.write(file.toPath(), lines);
        sections.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, value);
    }
}
