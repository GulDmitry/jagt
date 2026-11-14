package dev.jagt.orchestrator.flow.role;

import java.util.List;

/**
 * A checker as its {@code role.yml} declares it: whether it runs jobs, which brief steps open its
 * read, and which points it raises.
 */
public record CheckerRole(
    String id, boolean jobs, List<String> readSteps, List<RolePointKind> points) {

  public CheckerRole {
    readSteps = List.copyOf(readSteps);
    points = List.copyOf(points);
  }
}
