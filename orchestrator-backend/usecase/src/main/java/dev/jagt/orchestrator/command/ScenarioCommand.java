package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ScenarioSessions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenarioCommand implements GlobalCommand {

  private final ScenarioSessions sessions;
  private final ConfigService config;

  @Override
  public String id() {
    return "acceptance";
  }

  @Override
  public int rank() {
    return 185;
  }

  @Override
  public String hint() {
    return "talk to acceptance in its workspace: write scenarios, answer its questions";
  }

  @Override
  public List<String> usage() {
    return List.of("acceptance [project]");
  }

  @Override
  public String run(String tail) {
    List<String> known = List.copyOf(config.load().projects().keySet());
    String named = tail == null ? "" : tail.strip();
    if (named.isEmpty() && known.size() == 1) {
      named = known.getFirst();
    }
    if (!known.contains(named)) {
      throw Refusal.byState(
          "acceptance runs one session per project: name one of " + String.join(", ", known));
    }
    return sessions.open(named, "");
  }
}
