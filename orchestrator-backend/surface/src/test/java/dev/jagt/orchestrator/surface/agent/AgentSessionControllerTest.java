package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.SessionReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionControllerTest {

    @Test
    void recordsTheReportAgainstTheTaskRunningInThatWorktree() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports).report("waiting", "/wt/ABC-1-proj",
                new AgentSessionController.Session("/logs/session.jsonl", "compact", null));

        verify(reports).record("ABC-1", SessionProbe.State.WAITING,
                SessionReports.Report.defaults().withSessionLog(Path.of("/logs/session.jsonl"))
                        .withStartedBy("compact"));
    }

    /** Deriving where a session writes its log is a guess, and a payload that named none must not become one. */
    @Test
    void namesNoLogWhenThePayloadCarriedNone() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports).report("working", "/wt/ABC-1-proj", null);

        verify(reports).record("ABC-1", SessionProbe.State.WORKING,
                SessionReports.Report.defaults());
    }

    @Test
    void answersTheSessionWithWhateverTheReportProduced() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionReports reports = mock(SessionReports.class);
        when(reports.record("ABC-1", SessionProbe.State.WORKING,
                SessionReports.Report.defaults().withStartedBy("compact")))
                .thenReturn("re-read task_context.md");

        String answered = new AgentSessionController(state, reports).report("working", "/wt/ABC-1-proj",
                new AgentSessionController.Session(null, "compact", null));

        assertThat(answered).isEqualTo("re-read task_context.md");
    }

    /** The one event that covers two waits: what the CLI told the human is what separates them. */
    @Test
    void carriesWhatTheCliToldTheHumanSoOneEventCanMeanTwoDifferentWaits() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports).report("idle", "/wt/ABC-1-proj",
                new AgentSessionController.Session(null, null, "Claude needs your permission to use Bash"));

        verify(reports).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash"));
    }

    @Test
    void refusesAReportFromADirectoryNoTaskRunsIn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionReports.class))
                .report("waiting", "/elsewhere", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/elsewhere");
    }

    /** A mistyped hook would otherwise be answered with a 200 and report nothing for the life of the install. */
    @Test
    void refusesAStateThatIsNotOneOfJagtsOwn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionReports.class))
                .report("busy", "/wt/ABC-1-proj", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("busy");
    }
}
