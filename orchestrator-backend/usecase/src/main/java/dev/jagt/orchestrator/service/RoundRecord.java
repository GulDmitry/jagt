package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps what the host said about a round: a later poll saying the same thing writes nothing, or an unattended sweep
 * would rewrite the state on a loop. ONE write, all three facts coming off one
 * read. A round that could not read them leaves the older round's word standing, flagged, and the reads running
 * that could not stop the task at {@link ChecksUnread#CEILING}.
 */
@Service
@RequiredArgsConstructor
public class RoundRecord {

    /** {@code stop} is the one a round that could not read the checks tripped; {@code red} whose fault a red run is. */
    public record Recorded(Optional<TaskStop> stop, RedCauses.Verdict red) {
    }

    private final StateService stateService;
    private final TaskStops stops;
    private final Clock clock;
    private final RedCauses causes;

    public Recorded record(String taskId, ReviewFacts facts) {
        if (gotNoListing(facts.pipelineStatus())) {
            long now = clock.millis();
            stateService.updateTask(taskId, task -> task.withChecksUnread(new ChecksUnread(now, facts.pipelineFailure()))
                    .withApproved(facts.approved()).withRequestOpenedAt(facts.openedAt()));
            return new Recorded(stops.trip(taskId, task -> ChecksUnread.stop(task.checksUnread(), task.trailOrNone(), now)),
                    RedCauses.Verdict.CODE);
        }
        recordListed(taskId, facts);
        return new Recorded(Optional.empty(), causes.judge(taskId, facts, Pipeline.of(orUnknown(facts.pipelineStatus()))));
    }

    /** Writes what a read that listed the checks said and nothing else; a read that listed none writes nothing. */
    public void recordListed(String taskId, ReviewFacts facts) {
        if (gotNoListing(facts.pipelineStatus())) {
            return;
        }
        Optional<TaskState> before = stateService.task(taskId);
        String read = orUnknown(facts.pipelineStatus());
        boolean newChecks = !Objects.equals(before.map(TaskState::pipelineStatus).orElse(null), read)
                || before.map(TaskState::pipelineUnread).orElse(false);
        boolean newApproval = !Objects.equals(before.map(TaskState::approved).orElse(null), facts.approved());
        boolean newOpened = facts.openedAt() > 0
                && before.map(TaskState::requestOpenedAt).orElse(0L) != facts.openedAt();
        if (newChecks || newApproval || newOpened) {
            stateService.updateTask(taskId, task -> task.withChecksRead(read)
                    .withApproved(facts.approved()).withRequestOpenedAt(facts.openedAt()));
        }
    }

    /**
     * Whether the round got no listing at all — the one word the reader writes for that. NOT the verdict:
     * {@code Pipeline.UNKNOWN} also covers a word the parser does not recognise, which IS a read.
     */
    private static boolean gotNoListing(String read) {
        return read == null || read.isBlank() || read.strip().equalsIgnoreCase("unknown");
    }

    /** A round that answered nothing still has to say so in a word, or every line quoting it renders a hole. */
    static String orUnknown(String read) {
        return read == null || read.isBlank() ? "unknown" : read;
    }
}
