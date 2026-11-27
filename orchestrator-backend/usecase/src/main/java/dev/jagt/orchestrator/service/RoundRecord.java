package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.deploy.Checks;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Keeps what the host said about a round: a later poll saying the same thing writes nothing, or an
 * unattended sweep would rewrite the state on a loop. ONE write, all three facts coming off one
 * read. A round that could not read them leaves the older round's word standing, flagged, and the
 * reads running that could not stop the task at {@link ChecksUnread#CEILING}.
 */
@Service
@RequiredArgsConstructor
public class RoundRecord {

  /**
   * {@code stop} is the one a round that could not read the checks tripped; {@code red} whose fault
   * a red run is; {@code stale} that the task moved past the read, which then moves nothing.
   */
  public record Recorded(Optional<TaskStop> stop, RedCauses.Verdict red, boolean stale) {}

  private final StateService stateService;
  private final TaskStops stops;
  private final Clock clock;
  private final RedCauses causes;

  public Recorded record(
      String taskId, String project, ReviewFacts facts, String tip, Generation readAt) {
    if (movedPast(taskId, readAt)) {
      return new Recorded(Optional.empty(), RedCauses.Verdict.CODE, true);
    }
    if (gotNoListing(facts.pipeline().status())) {
      long now = clock.millis();
      stateService.reduce(
          taskId,
          (reducer, task) ->
              reducer.observed(
                  task,
                  readAt,
                  current ->
                      reducer.headSeen(
                          reducer
                              .approvalRead(
                                  reducer.checksUnread(
                                      current, new ChecksUnread(now, facts.pipeline().failure())),
                                  facts.request().approved())
                              .withRequestOpenedAt(facts.request().openedAt()),
                          tip)));
      return new Recorded(
          stops.trip(
              taskId,
              task ->
                  ChecksUnread.stop(task.checks().checksUnread(), task.round().trailOrNone(), now)),
          RedCauses.Verdict.CODE,
          false);
    }
    recordListed(taskId, facts, tip, readAt);
    return new Recorded(
        Optional.empty(),
        causes.judge(taskId, project, facts, Pipeline.of(orUnknown(facts.pipeline().status()))),
        false);
  }

  private boolean movedPast(String taskId, Generation readAt) {
    Optional<TaskState> task = stateService.task(taskId);
    if (task.isEmpty()) {
      return false;
    }
    AtomicBoolean fresh = new AtomicBoolean();
    stateService
        .reducer()
        .observed(
            task.get(),
            readAt,
            landing -> {
              fresh.set(true);
              return landing;
            });
    return !fresh.get();
  }

  /**
   * Writes what a read that listed the checks said on {@code tip} and nothing else; a read that
   * listed none writes nothing.
   */
  public void recordListed(String taskId, ReviewFacts facts, String tip, Generation readAt) {
    if (gotNoListing(facts.pipeline().status())) {
      return;
    }
    Optional<TaskState> before = stateService.task(taskId);
    Optional<Checks> beforeChecks = before.map(TaskState::checks);
    String read = orUnknown(facts.pipeline().status());
    boolean newChecks =
        !Objects.equals(beforeChecks.map(Checks::pipelineStatus).orElse(null), read)
            || beforeChecks.map(Checks::unread).orElse(false);
    boolean newTip =
        !tip.isBlank() && !tip.equals(beforeChecks.map(Checks::checksTip).orElse(null));
    boolean newApproval =
        !Objects.equals(
            before.map(taskState -> taskState.round().approved()).orElse(null),
            facts.request().approved());
    boolean newOpened =
        facts.request().openedAt() > 0
            && before.map(taskState -> taskState.round().requestOpenedAt()).orElse(0L)
                != facts.request().openedAt();
    boolean newUrl =
        !facts.pipeline().url().isBlank()
            && !facts.pipeline().url().equals(beforeChecks.map(Checks::pipelineUrl).orElse(null));
    if (newChecks || newTip || newApproval || newOpened || newUrl) {
      stateService.reduce(
          taskId,
          (reducer, task) ->
              reducer.observed(
                  task,
                  readAt,
                  current ->
                      reducer
                          .approvalRead(
                              reducer.checksRead(reducer.headSeen(current, tip), read, tip),
                              facts.request().approved())
                          .withRequestOpenedAt(facts.request().openedAt())
                          .withPipelineUrl(facts.pipeline().url())));
    }
  }

  /**
   * Whether the round got no listing at all — the one word the reader writes for that. NOT the
   * verdict: {@code Pipeline.UNKNOWN} also covers a word the parser does not recognise, which IS a
   * read.
   */
  private static boolean gotNoListing(String read) {
    return read == null || read.isBlank() || read.strip().equalsIgnoreCase("unknown");
  }

  /**
   * A round that answered nothing still has to say so in a word, or every line quoting it renders a
   * hole.
   */
  static String orUnknown(String read) {
    return read == null || read.isBlank() ? "unknown" : read;
  }
}
