package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksUnreadTest {

    @Test
    void stopsTheTaskWithTheReadsOwnWordsOnTheThirdReadRunningThatCouldNotListTheChecks() {
        List<ChecksUnread> running = List.of(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"),
                new ChecksUnread(2_000, "list_pipelines: 403 Forbidden"), new ChecksUnread(3_000, ""));

        Optional<TaskStop> stop = ChecksUnread.stop(running, RoundTrail.none(), 4_000);

        assertThat(stop).contains(new TaskStop(TaskStop.Trigger.CHECKS, 4_000,
                "3 reads running could not list the checks: list_pipelines: 403 Forbidden; the read gave no cause"));
    }

    @Test
    void leavesTheTaskRunningWhileOnlyTwoReadsRunningCouldNotListTheChecks() {
        List<ChecksUnread> running = List.of(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"),
                new ChecksUnread(2_000, "list_pipelines: 403 Forbidden"));

        Optional<TaskStop> stop = ChecksUnread.stop(running, RoundTrail.none(), 3_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void countsAnewFromTheLift() {
        List<ChecksUnread> running = List.of(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"),
                new ChecksUnread(2_000, "list_pipelines: 403 Forbidden"),
                new ChecksUnread(3_000, "list_pipelines: 403 Forbidden"));

        Optional<TaskStop> stop = ChecksUnread.stop(running, RoundTrail.liftedAt(1_500), 4_000);

        assertThat(stop).isEmpty();
    }
}
