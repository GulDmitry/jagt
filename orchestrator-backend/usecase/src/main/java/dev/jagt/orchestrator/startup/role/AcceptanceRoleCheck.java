package dev.jagt.orchestrator.startup.role;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** An acceptance role that did not compose stops the start only where jagt.yml turns it on. */
@Component
@RequiredArgsConstructor
public class AcceptanceRoleCheck implements StartupCheck {

  private static final String KEY = "roles/" + Checkers.ACCEPTANCE;

  private final InstalledRoles roles;
  private final ConfigService config;

  @Override
  public List<Problem> problems() {
    return missing() && enabled() ? List.of(why()) : List.of();
  }

  @Override
  public List<Problem> notes() {
    boolean skipped = roles.skipped().stream().anyMatch(note -> note.key().equals(KEY));
    return missing() && !skipped ? List.of(why()) : List.of();
  }

  @Override
  public boolean machine() {
    return false;
  }

  private boolean missing() {
    return roles.find(Checkers.ACCEPTANCE).isEmpty();
  }

  private boolean enabled() {
    return config.fileExists() && config.load().role(Checkers.ACCEPTANCE).enabledOrDefault();
  }

  private Problem why() {
    return roles.skipped().stream()
        .filter(skipped -> skipped.key().equals(KEY))
        .findFirst()
        .orElse(
            new Problem(
                KEY,
                "no roles/acceptance/role.yml or role.yml.dist is installed, so acceptance runs"
                    + " nothing"));
  }
}
