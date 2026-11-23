package dev.jagt.orchestrator.job.triage;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.role.model.ModelRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TriageModelRole implements ModelRole {

  private static final String ID = "triage";

  private final InstalledRoles installed;
  private final ConfigService config;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean running() {
    return installed.readers().contains(ID) && config.load().role(ID).enabledOrDefault();
  }
}
