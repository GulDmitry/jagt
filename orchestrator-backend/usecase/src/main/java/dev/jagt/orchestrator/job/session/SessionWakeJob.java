package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.presence.SessionWakes;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.turn.Step;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionWakeJob implements Job {

  private static final Map<Step, String> LINES =
      Map.of(
          Step.WAKE, "The usage limit has reset.",
          Step.WAIT_OVER, "The wait you asked for is over.");

  private final SessionWakes wakes;
  private final AgentSessions sessions;
  private final StepRelay relay;
  private final JobFaults faults;

  @Override
  public String id() {
    return "session-wake";
  }

  @Override
  public String describe() {
    return "say one line to a session that waits, once its limit resets or its wait is over";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public void run() {
    for (SessionWakes.Due due : wakes.takeDue(sessions::composing)) {
      try {
        relay.say(due.taskId(), due.step(), LINES.getOrDefault(due.step(), ""), due.read());
      } catch (RuntimeException e) {
        faults.faulted(Causes.of(e));
        log.atWarn()
            .setMessage("session wake not sent")
            .addKeyValue("task", due.taskId())
            .addKeyValue("cause", Causes.of(e))
            .addKeyValue("effect", "the session stays stopped")
            .log();
      }
    }
  }
}
