package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Map;

/**
 * Everything a router is given to place one item. {@code projects} is keyed by project, each value one line
 * saying what that repository is; {@code suggested} is what the item's labels matched and {@code precedents}
 * what was done with items like it before — both are evidence to weigh, neither is the answer.
 */
public record RoutingQuestion(TicketFacts item, Map<String, String> projects, List<String> suggested,
                              List<String> precedents) {

    public RoutingQuestion {
        projects = projects == null ? Map.of() : Map.copyOf(projects);
        suggested = suggested == null ? List.of() : List.copyOf(suggested);
        precedents = precedents == null ? List.of() : List.copyOf(precedents);
    }

    public static RoutingQuestion defaults() {
        return new RoutingQuestion(TicketFacts.defaults(), Map.of(), List.of(), List.of());
    }

    public RoutingQuestion withItem(TicketFacts item) {
        return new RoutingQuestion(item, projects, suggested, precedents);
    }

    public RoutingQuestion withProjects(Map<String, String> projects) {
        return new RoutingQuestion(item, projects, suggested, precedents);
    }

    public RoutingQuestion withSuggested(List<String> suggested) {
        return new RoutingQuestion(item, projects, suggested, precedents);
    }

    public RoutingQuestion withPrecedents(List<String> precedents) {
        return new RoutingQuestion(item, projects, suggested, precedents);
    }

    public boolean answerable() {
        return item != null && !item.key().isBlank() && !projects.isEmpty();
    }
}
