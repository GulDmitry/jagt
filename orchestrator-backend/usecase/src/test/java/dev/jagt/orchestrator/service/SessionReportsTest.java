package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionReportsTest {

    private final SessionProbe probe = mock(SessionProbe.class);
    private final WatchdogService watchdog = mock(WatchdogService.class);
    private final AgentSpendReader agentSpend = mock(AgentSpendReader.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
        new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults());

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WORKING), anyLong());
        verify(watchdog).check("ABC-1");
    }

    @Test
    void believesTheLogFileTheSessionNamedAndCountsWhatItSpent() {
        new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .record("ABC-1", SessionProbe.State.WAITING,
                        SessionReports.Report.defaults().withSessionLog(Path.of("/logs/session.jsonl")));

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
        verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
    }

    @Test
    void callsASessionBlockedWhenItsNotificationNamesWhatThisCliSaysWhileBlocked() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(probe, watchdog, agentSpend, runtime, config).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash"));

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WAITING), anyLong());
    }

    @Test
    void leavesANotificationThisCliDoesNotUseWhileBlockedToTheThreshold() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(probe, watchdog, agentSpend, runtime, config).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude is waiting for your input"));

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.IDLE), anyLong());
    }

    @Test
    void handsACompactedSessionItsBriefBack() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).contains("sub-agent for ABC-1", "task_request.md", "task_context.md");
    }

    @Test
    void restatesTheTaskAndItsOpenQuestionToACompactedSession() {
        when(runtime.compactedStart()).thenReturn("compact");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .title("Add v3 beside v2").ticketUrl("https://tracker.example/ABC-1")
                .message("outcome=question — keep v2?").build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config).record("ABC-1",
                SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact").withTask(task));

        assertThat(answered).contains("Add v3 beside v2", "https://tracker.example/ABC-1", "IN_PROGRESS",
                "your open question: keep v2?");
    }

    @Test
    void sendsOnATurnThatEndsWithTheAgentsMoveUnreported() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(runtime.refusedTurnEnd(org.mockito.ArgumentMatchers.contains("update_agent_status")))
                .thenReturn("{\"decision\": \"block\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000L).build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void letsATurnEndThatReportedAfterItBegan() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        assertThat(answered).isEmpty();
    }

    @Test
    void tellsTheHumanARoundHandedBackIsWithTheMaster() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(runtime.toldTheHuman("→ with the Master for review · its verdict arrives here"))
                .thenReturn("{\"systemMessage\": \"with the Master\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        assertThat(answered).isEqualTo("{\"systemMessage\": \"with the Master\"}");
    }

    @Test
    void saysNothingAtAHandedBackRoundNoMasterReads() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        assertThat(answered).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void letsATurnEndThatWasAlreadySentOnOrWaitsOnBackgroundWork(boolean sentOn, boolean paused) {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000L).build();

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), sentOn, paused);

        assertThat(answered).isEmpty();
    }

    @Test
    void answersAnOrdinaryStartWithNothingAtAll() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("startup"));

        assertThat(answered).isEmpty();
    }

    @Test
    void briefsNothingWhenTheCliSaysNothingAboutWhyASessionStarted() {
        when(runtime.compactedStart()).thenReturn("");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime, config)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).isEmpty();
    }
}
