package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginCommand implements GlobalCommand {

  private final Reach reach;
  private final SessionHost sessions;
  private final ConfigService configService;

  @Override
  public String id() {
    return "login";
  }

  @Override
  public int rank() {
    return 187;
  }

  @Override
  public String hint() {
    return "log in where jagt lost its reach, in a window of its own";
  }

  @Override
  public String run(String tail) {
    ReachLost lost =
        reach.standing().stream()
            .filter(one -> !one.fix().isEmpty())
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("no login is waiting: jagt reaches what it reads"));
    List<String> fix = lost.fix();
    String session = sessions.sessionName(configService.load().viewer().session());
    sessions.openCommandWindow(session, session, OpenedWindows.LOGIN, fix);
    return "`"
        + String.join(" ", fix)
        + "` is open in the "
        + OpenedWindows.LOGIN
        + " window; the next read that succeeds clears the mark";
  }
}
