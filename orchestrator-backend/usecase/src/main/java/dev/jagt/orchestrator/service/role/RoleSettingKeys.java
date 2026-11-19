package dev.jagt.orchestrator.service.role;

import dev.jagt.orchestrator.service.project.Setting;
import java.util.List;

/** The keys of {@code jagt.yml} one role adds to the board's settings; none where it is absent. */
public interface RoleSettingKeys {

  List<Setting> settings();
}
