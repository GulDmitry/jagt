package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.presence.SessionProbe;
import dev.jagt.orchestrator.service.scenario.ScenarioWindows;
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
