package dev.jagt.orchestrator.startup;

import java.util.List;

/** Everything a start found missing, in ONE message and with no stack: no frame here is one a reader could act on. */
public class Misconfigured extends RuntimeException {

    public Misconfigured(List<String> problems) {
        super(describe(problems), null, false, false);
    }

    private static String describe(List<String> problems) {
        StringBuilder text = new StringBuilder("jagt cannot start — ").append(problems.size())
                .append(" problems:");
        for (int i = 0; i < problems.size(); i++) {
            text.append("\n  ").append(i + 1).append(". ").append(problems.get(i));
        }
        return text.toString();
    }
}
