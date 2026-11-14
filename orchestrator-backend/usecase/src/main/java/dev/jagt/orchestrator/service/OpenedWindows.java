package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.task.text.Ansi;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The windows jagt opens that are no task's: the login and each project's acceptance session. */
@Service
@RequiredArgsConstructor
public class OpenedWindows {

  public static final String LOGIN = "login";

  private final SessionHost host;
  private final ConfigService configService;

  /** The names of those that are open now. */
  public List<String> open() {
    String session = session();
    return candidates().stream().filter(name -> host.screenOf(session, name).isPresent()).toList();
  }

  public Optional<String> screen(String name) {
    return host.screenOf(session(), known(name)).map(shown -> Ansi.strip(shown).stripTrailing());
  }

  public String say(String name, String line) {
    if (!host.nudgeTaskWindow(session(), known(name), line)) {
      throw new IllegalStateException(
          "No window " + name + " is open — open it, then say it again.");
    }
    return "Said to " + name + ".";
  }

  private String session() {
    return host.sessionName(configService.load().viewer().session());
  }

  private String known(String name) {
    if (!candidates().contains(name)) {
      throw new IllegalArgumentException("Not a window jagt opens: " + name);
    }
    return name;
  }

  private List<String> candidates() {
    List<String> names = new ArrayList<>(List.of(LOGIN));
    configService
        .load()
        .projects()
        .keySet()
        .forEach(project -> names.add(ScenarioWindows.PREFIX + project));
    return names;
  }
}
