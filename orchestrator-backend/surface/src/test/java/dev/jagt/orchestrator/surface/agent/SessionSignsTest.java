package dev.jagt.orchestrator.surface.agent;

import static dev.jagt.orchestrator.surface.board.task.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.job.session.WatchdogService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSignsTest {

  private final SessionLedger ledger = mock(SessionLedger.class);
  private final WatchdogService watchdog = mock(WatchdogService.class);
  private final AgentSpendReader agentSpend = mock(AgentSpendReader.class);
  private final Generation start = new Generation(Generation.Kind.SESSION_START, "3");

  @TempDir Path root;

  @Test
  void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
    SessionSign sign = new SessionSign.WorkStarted();

    new SessionSigns(ledger, watchdog, agentSpend).record("ABC-1", start, null, sign);

    verify(ledger).record("ABC-1", start, null, sign);
    verify(watchdog).check("ABC-1");
  }

  @Test
  void signsAnAskOnTheTaskAndHasItJudgedAtOnce() {
    SessionSign.Asked sign =
        new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK);

    new SessionSigns(ledger, watchdog, agentSpend).asked("ABC-1", start, sign);

    verify(ledger).sign("ABC-1", start, sign);
    verify(watchdog).check("ABC-1");
  }

  @Test
  void countsWhatTheSessionSpentInTheLogFileItNamed() {
    new SessionSigns(ledger, watchdog, agentSpend)
        .record(
            "ABC-1",
            start,
            Path.of("/logs/session.jsonl"),
            new SessionSign.TurnEnded("/logs/session.jsonl"));

    verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
  }

  @Test
  void leavesTheTaskAloneWhenTheAskCameFromSessionThatHasSinceRestarted() {
    StateService state = stateService().withRoot(root).build();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    state.updateTask(
        "ABC-1",
        new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {})::sessionStartedOver);
    SessionSigns signs =
        new SessionSigns(
            new SessionLedger(state, Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC)),
            watchdog,
            agentSpend);

    signs.asked(
        "ABC-1",
        new Generation(Generation.Kind.SESSION_START, "0"),
        new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK));

    assertThat(state.task("ABC-1").orElseThrow().standing().report())
        .isNotEqualTo(AgentReport.PERMISSION);
  }
}
