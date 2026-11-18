package dev.jagt.orchestrator.service.log;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Every stop jagt put on a task, one line each beside `state.json`; a lift leaves them. */
@Service
@Slf4j
public class StopLog {

  public record Stop(
      long at,
      String task,
      String alias,
      String trigger,
      TaskStatus status,
      String evidence,
      Steps steps) {}

  /**
   * The task's steps up to the stop, newest last, so the stop can be read once the task is gone.
   */
  public record Steps(List<StatusChange> history, List<RoundTrail.Round> trail) {

    public static final int MAX = RoundTrail.MAX_ROUNDS;

    public Steps {
      history = history.subList(Math.max(0, history.size() - MAX), history.size());
      trail = trail.subList(Math.max(0, trail.size() - MAX), trail.size());
    }
  }

  private final ObjectMapper mapper;
  private final RollingFile file;

  public StopLog(ObjectMapper mapper, OrchestratorPaths paths) {
    this.mapper = mapper;
    this.file = new RollingFile(paths.stops());
  }

  public void record(Stop stop) {
    try {
      file.append(mapper.writeValueAsString(stop) + "\n");
    } catch (IOException e) {
      log.atWarn()
          .setMessage("stop not recorded")
          .addKeyValue("file", file.path())
          .addKeyValue("task", stop.task())
          .addKeyValue("cause", e.toString())
          .log();
    }
  }
}
