package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.BoardStop;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StopCommand implements GlobalCommand {

  private final BoardStop board;
  private final StateService stateService;
  private final AgentSessions sessions;

  @Override
  public String id() {
    return "stop";
  }

  @Override
  public int rank() {
    return 183;
  }

  @Override
  public String hint() {
    return "stop all work: interrupt every session, pause jobs and intake, until `unstop`";
  }

  @Override
  public Optional<String> confirmation() {
    return Optional.of(
        "Interrupt every working session and pause all jobs and intake until you lift the stop?");
  }

  @Override
  public String run(String tail) {
    boolean engaged = board.engage();
    long interrupted = stateService.tasks().keySet().stream().filter(this::interrupted).count();
    return (engaged ? "stopped" : "already stopped")
        + ": "
        + interrupted
        + " session(s) interrupted, jobs and intake wait for `unstop`";
  }

  private boolean interrupted(String taskId) {
    try {
      return sessions.interrupt(taskId);
    } catch (RuntimeException unread) {
      log.atWarn()
          .setMessage("session not interrupted")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .addKeyValue("effect", "its turn runs on; the board stop still holds jobs and intake")
          .log();
      return false;
    }
  }
}
