package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.role.model.ModelRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcceptanceModelRole implements ModelRole {

  private static final String ID = "acceptance";

  private final InstalledRoles installed;
  private final ConfigService config;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean running() {
    return installed.find(ID).isPresent() && config.load().role(ID).enabledOrDefault();
  }
}
