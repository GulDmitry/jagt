package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ScenarioWindows;
import dev.jagt.orchestrator.service.SessionProbe;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenarioEndingJob implements Job {

  private final ScenarioWindows windows;
  private final SessionProbe probe;

  @Override
  public String id() {
    return "scenario-ending";
  }

  @Override
  public String describe() {
    return "log how an interactive acceptance session ended: finished, failed or gone";
  }

  @Override
  public Duration every() {
    return probe.every();
  }

  @Override
  public void run() {
    windows.watch();
  }
}
