package dev.jagt.orchestrator.job.tracker;

import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read while the session works, so the round it hands back starts with the ticket in every
 * reviewer's prompt.
 */
@Service
@RequiredArgsConstructor
public class TicketPrefetchJob implements TaskJob {

  private final ConfigService configService;
  private final TicketTexts tickets;

  @Override
  public String id() {
    return "ticket-prefetch";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "read each working task's ticket once, for the arbiter's next review of it";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(5);
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withStatuses(TaskStatus.IN_PROGRESS).withStops(StopKind.withoutPause());
  }

  @Override
  public void run(String taskId, TaskState task) {
    if (configService.load().arbiter().running() && due(taskId, task)) {
      tickets.read(
          taskId, Objects.requireNonNull(task.ticket().ticketUrl()), task.standing().statusSince());
    }
  }

  private boolean due(String taskId, TaskState task) {
    return task.ticket().ticketUrl() != null
        && !task.ticket().ticketUrl().isBlank()
        && !tickets.readSince(taskId, task.standing().statusSince());
  }
}
