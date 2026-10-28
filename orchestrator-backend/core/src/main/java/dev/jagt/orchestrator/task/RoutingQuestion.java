package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Map;

/**
 * Everything a router is given to place one item. {@code projects} is keyed by project, each value one line
 * saying what that repository is; {@code precedents} is what was done with items like it before, and
 * {@code rules} what the install has written down — evidence to weigh, none of it the answer.
 */
public record RoutingQuestion(TicketFacts item, Map<String, String> projects, List<String> precedents,
                              List<String> rules) {

    public RoutingQuestion {
        projects = projects == null ? Map.of() : Map.copyOf(projects);
        precedents = precedents == null ? List.of() : List.copyOf(precedents);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static RoutingQuestion defaults() {
        return new RoutingQuestion(TicketFacts.defaults(), Map.of(), List.of(), List.of());
    }

    public RoutingQuestion withItem(TicketFacts item) {
        return new RoutingQuestion(item, projects, precedents, rules);
    }

    public RoutingQuestion withProjects(Map<String, String> projects) {
        return new RoutingQuestion(item, projects, precedents, rules);
    }

    public RoutingQuestion withPrecedents(List<String> precedents) {
        return new RoutingQuestion(item, projects, precedents, rules);
    }

    public RoutingQuestion withRules(List<String> rules) {
        return new RoutingQuestion(item, projects, precedents, rules);
    }

    public boolean answerable() {
        return item != null && !item.key().isBlank() && !projects.isEmpty();
    }
}
