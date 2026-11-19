package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.service.role.RoleLog;
import org.springframework.stereotype.Component;

@Component
public class MasterLog implements RoleLog {

  @Override
  public String record() {
    return "master-log";
  }

  @Override
  public String fileKey() {
    return "logging.master.name";
  }
}
