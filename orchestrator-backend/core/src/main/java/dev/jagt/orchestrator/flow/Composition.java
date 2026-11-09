package dev.jagt.orchestrator.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The roles a task's chart is composed of, in the order their regions are declared. */
record Composition(List<Role> roles) {

  Composition {
    refuseSharedReadSteps(roles);
    roles =
        roles.stream().anyMatch(role -> role.kind() == Role.Kind.ARBITER)
            ? List.copyOf(roles)
            : Stream.concat(roles.stream(), Stream.of(new MasterRegion())).toList();
  }

  private static void refuseSharedReadSteps(List<Role> roles) {
    Map<String, String> owners = new HashMap<>();
    for (CheckerRegion region : checkerRegions(roles)) {
      for (String step : region.role().readSteps()) {
        String other = owners.putIfAbsent(step, region.id());
        if (other != null && !other.equals(region.id())) {
          throw new IllegalArgumentException(
              "read step " + step + " is named by both " + other + " and " + region.id());
        }
      }
    }
  }

  private static List<CheckerRegion> checkerRegions(List<Role> roles) {
    return roles.stream()
        .filter(CheckerRegion.class::isInstance)
        .map(CheckerRegion.class::cast)
        .toList();
  }

  static Composition builtIn() {
    return new Composition(List.of(CheckerRegion.ACCEPTANCE));
  }

  List<String> withJobs() {
    return checkerRegions(roles).stream()
        .filter(region -> region.role().jobs())
        .map(CheckerRegion::id)
        .toList();
  }

  List<String> checkers() {
    return roles.stream().filter(role -> role.kind() == Role.Kind.CHECKER).map(Role::id).toList();
  }
}
