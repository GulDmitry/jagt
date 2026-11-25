package dev.jagt.orchestrator.startup.role;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A role that did not compose is left out and said so. */
@Component
@RequiredArgsConstructor
public class RoleCheck implements StartupCheck {

  private final InstalledRoles roles;
  private final ConfigService config;

  @Override
  public List<Problem> problems() {
    return List.of();
  }

  @Override
  public List<Problem> notes() {
    List<Problem> notes = new ArrayList<>(roles.skipped());
    notes.addAll(unknownKeys());
    return notes;
  }

  @Override
  public boolean machine() {
    return false;
  }

  private List<Problem> unknownKeys() {
    if (!config.fileExists()) {
      return List.of();
    }
    List<String> installed = roles.definitions().stream().map(RoleDefinition::id).toList();
    List<String> named = Stream.concat(installed.stream(), roles.readers().stream()).toList();
    return config.load().unknownRoles(named).stream()
        .map(id ->
            new Problem(
                "roles." + id,
                "orchestrator.roles."
                    + id
                    + " names no installed role; the installed ones are "
                    + installed))
        .toList();
  }
}
