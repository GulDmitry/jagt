package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.InstalledRoles;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A role that did not compose is left out and said so; it stops the start only where jagt.yml turns
 * it on.
 */
@Component
@RequiredArgsConstructor
public class RoleCheck implements StartupCheck {

  private static final String ACCEPTANCE_KEY = "roles/" + Checkers.ACCEPTANCE;

  private final InstalledRoles roles;
  private final ConfigService config;
  private final OrchestratorPaths paths;

  @Override
  public List<Problem> problems() {
    return acceptanceMissing() && acceptanceEnabled() ? List.of(acceptanceWhy()) : List.of();
  }

  @Override
  public List<Problem> notes() {
    List<Problem> notes = new ArrayList<>(roles.skipped());
    if (acceptanceMissing()
        && roles.skipped().stream().noneMatch(skipped -> skipped.key().equals(ACCEPTANCE_KEY))) {
      notes.add(acceptanceWhy());
    }
    notes.addAll(unknownKeys());
    return notes;
  }

  @Override
  public boolean machine() {
    return false;
  }

  private boolean acceptanceMissing() {
    return roles.find(Checkers.ACCEPTANCE).isEmpty();
  }

  private boolean acceptanceEnabled() {
    return Files.exists(paths.configFile())
        && config.load().role(Checkers.ACCEPTANCE).enabledOrDefault();
  }

  private List<Problem> unknownKeys() {
    if (!Files.exists(paths.configFile())) {
      return List.of();
    }
    List<String> installed = roles.definitions().stream().map(RoleDefinition::id).toList();
    List<String> named = Stream.concat(installed.stream(), roles.readers().stream()).toList();
    return config.load().unknownRoles(named).stream()
        .map(
            id ->
                new Problem(
                    "roles." + id,
                    "orchestrator.roles."
                        + id
                        + " names no installed role; the installed ones are "
                        + installed))
        .toList();
  }

  private Problem acceptanceWhy() {
    return roles.skipped().stream()
        .filter(skipped -> skipped.key().equals(ACCEPTANCE_KEY))
        .findFirst()
        .orElse(
            new Problem(
                ACCEPTANCE_KEY,
                "no roles/acceptance/role.yml or role.yml.dist is installed, so acceptance runs"
                    + " nothing"));
  }
}
