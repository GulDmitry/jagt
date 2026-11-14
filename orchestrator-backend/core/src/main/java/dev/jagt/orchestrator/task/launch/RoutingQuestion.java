package dev.jagt.orchestrator.task.launch;

import java.util.List;
import java.util.Map;
import lombok.With;

/**
 * Everything a router is given to place one item. {@code projects} is keyed by project, each value
 * one line saying what that repository is; {@code precedents} is what was done with items like it
 * before, and {@code rules} what the install has written down — evidence to weigh, none of it the
 * answer.
 */
@With
public record RoutingQuestion(
    TicketFacts item, Map<String, String> projects, List<String> precedents, List<String> rules) {

  public RoutingQuestion {
    projects = Map.copyOf(projects);
    precedents = List.copyOf(precedents);
    rules = List.copyOf(rules);
  }

  public static RoutingQuestion defaults() {
    return new RoutingQuestion(TicketFacts.defaults(), Map.of(), List.of(), List.of());
  }

  public boolean answerable() {
    return !item.key().isBlank() && !projects.isEmpty();
  }
}
