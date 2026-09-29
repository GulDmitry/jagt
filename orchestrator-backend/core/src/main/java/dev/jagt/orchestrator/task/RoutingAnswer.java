package dev.jagt.orchestrator.task;

/**
 * Where one item goes, and what placing it taught. {@code rule} is the phrase that would place the NEXT item
 * like it, blank where this one taught nothing worth keeping — which is the usual answer, and the reason a
 * memory written this way stays short enough to read.
 */
public record RoutingAnswer(String project, String rule) {

    public RoutingAnswer {
        project = project == null ? "" : project.strip();
        rule = rule == null ? "" : rule.strip();
    }

    public boolean teaches() {
        return !rule.isEmpty();
    }
}
