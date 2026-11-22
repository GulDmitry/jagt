package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.ActStop.Cause;
import dev.jagt.orchestrator.service.role.HeaderChips;
import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MasterChips implements HeaderChips {

  private final InstalledRoles installed;
  private final ConfigService config;
  private final ActStops stops;

  @Override
  public List<HeaderChip> chips() {
    if (!installed.readers().contains(Arbiter.ID)) {
      return List.of();
    }
    if (stops.standing().filter(stop -> stop.cause() == Cause.STRIKES).isPresent()) {
      return List.of(new HeaderChip("act-stopped", "act", true, null));
    }
    return acting() ? List.of(new HeaderChip("header.acting", null, false, null)) : List.of();
  }

  private boolean acting() {
    try {
      return config.load().arbiter().acts();
    } catch (RuntimeException unread) {
      log.atWarn()
          .setMessage("config read failed")
          .addKeyValue("scope", "chips")
          .addKeyValue("cause", unread.toString())
          .log();
      return false;
    }
  }
}
