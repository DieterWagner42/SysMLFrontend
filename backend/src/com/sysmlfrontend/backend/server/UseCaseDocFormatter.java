package com.sysmlfrontend.backend.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Formats a UseCase's full structured detail (goal, actors, preconditions, basic path,
 * alternative/extension paths, post condition — same shape {@link ModelStore#getUseCaseDetail}
 * returns) into plain, human-readable text. Both stores write the result into the UseCase's own
 * free-text documentation ({@link ModelStore#setDocumentation} — Rhapsody's native Description
 * property there) every time {@link ModelStore#updateUseCase} saves, so opening the UseCase
 * directly in Rhapsody (or anywhere else that only shows the native documentation field, not this
 * app's own structured editor) still shows the full narrative. Requested live: "wir müssen den UC
 * als Text in die UC dokumentation eintragen (formatierter text)".
 *
 * Pure Java, no Rhapsody dependency — shared verbatim by both {@link ModelStore} implementations.
 * Rhapsody's Description property is plain text, so this uses indentation/blank lines/simple
 * numbering for readability rather than any markup syntax.
 */
final class UseCaseDocFormatter {

    private UseCaseDocFormatter() { }

    @SuppressWarnings("unchecked")
    static String format(Map<String, Object> detail) {
        StringBuilder sb = new StringBuilder();

        String goal = str(detail, "goal");
        sb.append("GOAL\n");
        sb.append(goal.isEmpty() ? "  (not specified)" : indent(goal)).append("\n\n");

        List<Object> actors = list(detail, "actors");
        sb.append("ACTORS\n");
        if (actors.isEmpty()) {
            sb.append("  (none)\n\n");
        } else {
            for (Object a : actors) {
                Map<String, Object> ref = (Map<String, Object>) a;
                sb.append("  - ").append(ref.get("name")).append("\n");
            }
            sb.append("\n");
        }

        List<Object> preconditions = list(detail, "preconditions");
        sb.append("PRECONDITIONS\n");
        if (preconditions.isEmpty()) {
            sb.append("  (none)\n\n");
        } else {
            int i = 1;
            for (Object p : preconditions) sb.append("  ").append(i++).append(". ").append(p).append("\n");
            sb.append("\n");
        }

        List<Object> basicPath = list(detail, "basicPath");
        sb.append("BASIC PATH\n");
        if (basicPath.isEmpty()) {
            sb.append("  (not specified)\n\n");
        } else {
            for (int i = 0; i < basicPath.size(); i++) {
                sb.append("  B").append(i + 1);
                if (i == 0) sb.append(" (Trigger)");
                sb.append(": ").append(basicPath.get(i)).append("\n");
            }
            sb.append("\n");
        }

        List<Object> alternatives = list(detail, "alternatives");
        if (!alternatives.isEmpty()) {
            sb.append("ALTERNATIVE PATHS\n");
            for (int i = 0; i < alternatives.size(); i++) {
                Map<String, Object> alt = (Map<String, Object>) alternatives.get(i);
                String label = "A" + (i + 1);
                String title = str(alt, "title");
                sb.append("  ").append(label);
                if (!title.isEmpty()) sb.append(" - ").append(title);
                List<Object> stepRefs = list(alt, "stepRefs");
                if (!stepRefs.isEmpty()) sb.append("  [branches from: ").append(join(stepRefs)).append("]");
                sb.append("\n");
                String whatHappens = str(alt, "whatHappens");
                if (!whatHappens.isEmpty()) sb.append("    ").append(whatHappens).append("\n");
                List<Object> subSteps = list(alt, "subSteps");
                for (int j = 0; j < subSteps.size(); j++) {
                    sb.append("    ").append(label).append(".").append(j + 1).append(": ").append(subSteps.get(j)).append("\n");
                }
                String postCondition = str(alt, "postCondition");
                if (!postCondition.isEmpty()) sb.append("    Post-condition: ").append(postCondition).append("\n");
                sb.append("\n");
            }
        }

        List<Object> extensions = list(detail, "extensions");
        if (!extensions.isEmpty()) {
            sb.append("EXTENSION PATHS\n");
            for (int i = 0; i < extensions.size(); i++) {
                Map<String, Object> ext = (Map<String, Object>) extensions.get(i);
                String label = "E" + (i + 1);
                String trigger = str(ext, "triggerText");
                sb.append("  ").append(label);
                if (!trigger.isEmpty()) sb.append("  [trigger: ").append(trigger).append("]");
                sb.append("\n");
                List<Object> subSteps = list(ext, "subSteps");
                for (int j = 0; j < subSteps.size(); j++) {
                    sb.append("    ").append(label).append(".").append(j + 1).append(": ").append(subSteps.get(j)).append("\n");
                }
                sb.append("\n");
            }
        }

        String postCondition = str(detail, "postCondition");
        sb.append("POST CONDITION\n");
        sb.append(postCondition.isEmpty() ? "  (not specified)" : indent(postCondition)).append("\n");

        return sb.toString();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? Collections.emptyList() : (List<Object>) v;
    }

    private static String indent(String text) {
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append("\n");
            out.append("  ").append(lines[i]);
        }
        return out.toString();
    }

    private static String join(List<Object> values) {
        StringBuilder out = new StringBuilder();
        for (Object v : values) {
            if (out.length() > 0) out.append(", ");
            out.append(v);
        }
        return out.toString();
    }
}
