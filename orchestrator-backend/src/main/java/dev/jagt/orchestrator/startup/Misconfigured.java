package dev.jagt.orchestrator.startup;

import java.util.List;

/**
 * Everything a start found missing, in ONE message: a human fixes a list once, instead of one item per
 * restart.
 *
 * <p>It carries no stack: nothing here happened at a line of code the reader could act on, and the frames
 * would bury the list under the failure report Boot logs on the way down.
 */
public class Misconfigured extends RuntimeException {

    public Misconfigured(List<String> problems) {
        super(describe(problems), null, false, false);
    }

    private static String describe(List<String> problems) {
        StringBuilder text = new StringBuilder("jagt cannot start — ").append(problems.size())
                .append(problems.size() == 1 ? " thing it needs is" : " things it needs are")
                .append(" missing or misconfigured:");
        for (int i = 0; i < problems.size(); i++) {
            text.append("\n  ").append(i + 1).append(". ").append(problems.get(i));
        }
        return text.toString();
    }
}
