package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.log.StopLog;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** What a newly tripped stop tells: the log and the journal. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StopAnnouncements {

  private final StopLog journal;

  public void announce(String taskId, TaskState task, TaskStop stop) {
    if (stop.trigger().ends() == TaskStop.Trigger.Ends.PAUSE) {
      return;
    }
    String alias = task.alias() == null ? taskId : task.alias();
    String trigger = stop.trigger().name();
    log.atWarn()
        .setMessage("task stopped")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", alias)
        .addKeyValue("cause", trigger.toLowerCase(Locale.ROOT))
        .addKeyValue("said", OneLine.clipped(stop.evidence()))
        .addKeyValue("effect", "auto-review and the master leave the task")
        .addKeyValue("fix", "lift " + alias)
        .log();
    journal.record(
        new StopLog.Stop(
            stop.at(),
            taskId,
            alias,
            trigger,
            task.status(),
            stop.evidence(),
            new StopLog.Steps(
                task.history(), task.trail() == null ? List.of() : task.trail().rounds())));
  }
}
