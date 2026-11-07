package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.ToolGateEnvelope;
import dev.jagt.orchestrator.port.ToolGateEnvelope.Kind;
import dev.jagt.orchestrator.protocol.ToolCall;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolGateControllerTest {

    private final StateService stateService = mock(StateService.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC);
    private final ConfigService configService = mock(ConfigService.class);
    private final ToolGateEnvelope envelope = mock(ToolGateEnvelope.class);

    @BeforeEach
    void theProjectSharesItsBranchesThroughOrigin() {
        when(configService.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(envelope.kindOf(any())).thenReturn(Kind.OTHER);
        when(envelope.kindOf("Bash")).thenReturn(Kind.SHELL);
        when(envelope.kindOf("Write")).thenReturn(Kind.FILE_WRITE);
        when(envelope.kindOf("Edit")).thenReturn(Kind.FILE_WRITE);
        when(envelope.kindOf("MultiEdit")).thenReturn(Kind.FILE_WRITE);
        when(envelope.kindOf("NotebookEdit")).thenReturn(Kind.FILE_WRITE);
        when(envelope.denial(any())).thenAnswer(call -> Map.of("denied", call.getArgument(0)));
    }

    @Test
    void refusesAPushToAnotherBranchInTheShapeTheModelReads() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        Map<String, Object> answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Bash", "git push origin dev", null),
                new MockHttpServletRequest())
                .getBody();

        assertThat(answered).extractingByKey("denied").asString().contains("ABC-1");
    }

    @Test
    void countsARefusedLineOnItsTask() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .lastActiveTimestamp(100_000).build())));

        new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Bash", "git push origin dev", null),
                new MockHttpServletRequest());

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals()).containsExactly(Map.entry(Guard.SESSION_LINE, 1));
    }

    @Test
    void answersAPushOfTheTasksOwnBranchWithNothing() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Bash", "git push origin ABC-1", null),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesNothingFromADirectoryNoTaskOwns() {
        when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/elsewhere",
                new ToolCall("Bash", "git push origin dev", null),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void answersNothingForAToolThatCannotPush() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Read", "git push origin dev", null),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void answersACallWithNoPayloadWithNothing() {
        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj", null,
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesASourceFileWriteWhileThePlanIsNotApprovedAndCountsIt() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED).lastActiveTimestamp(100_000).build())));

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Write", null, "/wt/ABC-1-proj/src/Foo.java"),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).extractingByKey("denied").asString().contains("plan first");
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals()).containsExactly(Map.entry(Guard.PLAN_FIRST, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Edit", "MultiEdit", "Write"})
    void letsThePlanAndTheVisualThroughWhileThePlanIsNotApproved(String tool) {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED).build())));

        var plan = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall(tool, null, "/wt/ABC-1-proj/plan.md"),
                new MockHttpServletRequest());
        var visual = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall(tool, null, "/wt/ABC-1-proj/task_visual.md"),
                new MockHttpServletRequest());

        assertThat(plan.getBody()).isNull();
        assertThat(visual.getBody()).isNull();
    }

    @Test
    void refusesANotebookEditWhileThePlanIsNotApproved() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.CLOSED).build())));

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("NotebookEdit", null, "/wt/ABC-1-proj/analysis.ipynb"),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNotNull();
    }

    @Test
    void letsEveryWriteThroughOnceThePlanIsApproved() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Edit", null, "/wt/ABC-1-proj/src/Foo.java"),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesNoWriteFromADirectoryNoTaskOwns() {
        when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        var answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/elsewhere",
                new ToolCall("Write", null, "/elsewhere/Foo.java"),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void signsTheSessionUsedAToolWhateverTheToolAnswers() {
        TaskState held = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(1_000).build();
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", held)));

        new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Read", null, "/wt/ABC-1-proj/a.txt"),
                new MockHttpServletRequest());

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(held).session().movedAt()).isEqualTo(100_000);
    }

    @Test
    void signsNothingWhenTheTaskWasActiveSecondsAgo() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .lastActiveTimestamp(95_000).build())));

        new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Read", null, "/wt/ABC-1-proj/a.txt"),
                new MockHttpServletRequest());

        verify(stateService, never()).updateTask(any(), any());
    }

    @Test
    void namesTheProjectsOwnRemoteInTheHintOfARefusedPush() {
        when(configService.project("proj")).thenReturn(new ProjectConfig(new ProjectConfig.Repository("/repo", List.of(), null, null), new ProjectConfig.Branches("upstream/main", "dev", "upstream"), new ProjectConfig.CodeHost(null, null, null, null, null, null), new ProjectConfig.Verify(List.of(), null)));
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        Map<String, Object> answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Bash", "git push --no-verify upstream ABC-1", null),
                new MockHttpServletRequest())
                .getBody();

        assertThat(answered).extractingByKey("denied").asString().contains("`git push upstream ABC-1`");
    }

    @Test
    void namesOriginInTheHintWhereTheTasksProjectIsNoLongerConfigured() {
        when(configService.project("proj")).thenThrow(new IllegalArgumentException("Unknown project 'proj'"));
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        Map<String, Object> answered = new AgentToolGateController(stateService, clock, configService, envelope).gate("/wt/ABC-1-proj",
                new ToolCall("Bash", "git push --no-verify origin ABC-1", null),
                new MockHttpServletRequest())
                .getBody();

        assertThat(answered).extractingByKey("denied").asString().contains("`git push origin ABC-1`");
    }
}
