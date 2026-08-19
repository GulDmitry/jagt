package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.job.Jobs;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobsCommandTest {

    @Test
    void showsHowOftenAJobRunsAndHowLongUntilItsNextRun() {
        long now = 1_700_000_000_000L;
        Jobs.Status status = new Jobs.Status("poll-reviews", "reads open review requests", Duration.ofMinutes(5),
                now - 120_000, null, now + 180_000, false);

        String rendered = JobsCommand.render(List.of(status), now);

        assertThat(rendered).contains("poll-reviews", "every 5m", "ran 2m ago", "next in 3m",
                "reads open review requests");
    }

    @Test
    void saysAStartupOnlyJobHasNoNextRun() {
        long now = 1_700_000_000_000L;
        Jobs.Status status = new Jobs.Status("scan-orphans", "warns about worktrees no task owns", null,
                now - 3_600_000, null, null, false);

        String rendered = JobsCommand.render(List.of(status), now);

        assertThat(rendered).contains("at startup", "ran 1h ago").doesNotContain("next in");
    }

    @Test
    void saysAJobIsRunningNowInsteadOfHowLongAgoItRan() {
        long now = 1_700_000_000_000L;
        Jobs.Status status = new Jobs.Status("poll-reviews", "reads open review requests", Duration.ofMinutes(5),
                now - 120_000, null, now + 180_000, true);

        String rendered = JobsCommand.render(List.of(status), now);

        assertThat(rendered).contains("running now").doesNotContain("ran 2m ago");
    }

    @Test
    void putsTheErrorOfAFailedRunOnItsOwnLineUnderTheRow() {
        long now = 1_700_000_000_000L;
        Jobs.Status status = new Jobs.Status("archive-logs", "trims the log activity is read back from",
                Duration.ofHours(1), now - 60_000, "java.lang.IllegalStateException: disk full",
                now + 3_540_000, false);

        String rendered = JobsCommand.render(List.of(status), now);

        assertThat(rendered).contains("archive-logs")
                .contains("\n                last run failed: java.lang.IllegalStateException: disk full");
    }
}
