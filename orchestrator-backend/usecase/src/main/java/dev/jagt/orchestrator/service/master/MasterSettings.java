package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.project.Setting;
import dev.jagt.orchestrator.service.project.Setting.Kind;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.role.RoleSettingKeys;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MasterSettings implements RoleSettingKeys {

  private static final String SECTION = Arbiter.ID;
  private static final ConfigFile DEFAULTS = ConfigFile.defaults();

  private final InstalledRoles installed;

  @Override
  public List<Setting> settings() {
    if (!installed.readers().contains(Arbiter.ID)) {
      return List.of();
    }
    return List.of(
        Setting.choice(SECTION, "roles.master.mode", "act", "judge", "act", "off"),
        Setting.of(SECTION, "loopBreaker.stalledRounds", Kind.INTEGER)
            .defaulting(DEFAULTS.loopBreaker().stalledRoundsOrDefault()),
        Setting.of(SECTION, "loopBreaker.ships", Kind.INTEGER)
            .defaulting(DEFAULTS.loopBreaker().shipsOrDefault()),
        Setting.of(SECTION, "loopBreaker.rounds", Kind.INTEGER)
            .defaulting(DEFAULTS.loopBreaker().roundsOrDefault()),
        Setting.of(SECTION, "loopBreaker.perHour", Kind.INTEGER)
            .defaulting(DEFAULTS.loopBreaker().perHourOrDefault()),
        Setting.of(SECTION, "conflictStallMinutes", Kind.INTEGER)
            .defaulting(DEFAULTS.conflictStallOrDefault().toMinutes()));
  }
}
