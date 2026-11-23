package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.role.model.ModelRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MasterModelRole implements ModelRole {

  private final InstalledRoles installed;
  private final ConfigService config;

  @Override
  public String id() {
    return Arbiter.ID;
  }

  @Override
  public boolean running() {
    return installed.readers().contains(Arbiter.ID) && config.load().arbiter().running();
  }
}
