package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.notify.PointWatch;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointSweep implements Job {

  private final PointWatch watch;

  @Override
  public String id() {
    return "points";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "tell the points a task stands at that no write announced";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(5);
  }

  @Override
  public void run() {
    watch.sweep();
  }
}
