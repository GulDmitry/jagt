package dev.jagt.orchestrator.surface.agent;

import static dev.jagt.orchestrator.surface.agent.TestProjectConfigBuilder.projectConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.agent.ToolGateEnvelope;
import dev.jagt.orchestrator.port.agent.ToolGateEnvelope.Kind;
import dev.jagt.orchestrator.protocol.ToolCall;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.gate.ToolGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.stop.GuardKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentToolGateControllerTest {

  private final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StateService stateService = mock(StateService.class);
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC);
  private final ConfigService configService = mock(ConfigService.class);
  private final ToolGateEnvelope envelope = mock(ToolGateEnvelope.class);
  private final AgentToolGateController controller =
      new AgentToolGateController(
          stateService, clock, configService, envelope, new ToolGate(List.of()));
  private final TaskState inProgress =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

  @BeforeEach
  void theProjectSharesItsBranchesThroughOrigin() {
    when(configService.project("proj"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
    when(envelope.kindOf(any())).thenReturn(Kind.OTHER);
    when(envelope.kindOf("Bash")).thenReturn(Kind.SHELL);
    when(envelope.kindOf("Write")).thenReturn(Kind.FILE_WRITE);
    when(envelope.kindOf("Edit")).thenReturn(Kind.FILE_WRITE);
    when(envelope.kindOf("MultiEdit")).thenReturn(Kind.FILE_WRITE);
    when(envelope.kindOf("NotebookEdit")).thenReturn(Kind.FILE_WRITE);
    when(envelope.denial(any())).thenAnswer(call -> Map.of("denied", call.getArgument(0)));
  }

  @Test
  void refusesPushToAnotherBranchInTheShapeTheModelReads() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    Map<String, Object> answered =
        controller
            .gate(
                "/wt/ABC-1-proj",
                new ToolCall("Bash", "git push origin dev", null),
                new MockHttpServletRequest())
            .getBody();

    assertThat(answered).extractingByKey("denied").asString().contains("ABC-1");
  }

  @Test
  void countsRefusedLineOnItsTask() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .lastActiveTimestamp(100_000)
                        .build())));

    controller.gate(
        "/wt/ABC-1-proj",
        new ToolCall("Bash", "git push origin dev", null),
        new MockHttpServletRequest());

    ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
    verify(stateService).updateTask(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals())
        .containsExactly(Map.entry(GuardKind.SESSION_LINE, 1));
  }

  @Test
  void answersPushOfTheTasksOwnBranchWithNothing() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    var answered =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall("Bash", "git push origin ABC-1", null),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void refusesNothingFromDirectoryNoTaskOwns() {
    when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

    var answered =
        controller.gate(
            "/elsewhere",
            new ToolCall("Bash", "git push origin dev", null),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void answersNothingForToolThatCannotPush() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    var answered =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall("Read", "git push origin dev", null),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void answersCallWithNoPayloadWithNothing() {
    var answered = controller.gate("/wt/ABC-1-proj", null, new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void refusesSourceFileWriteWhileThePlanIsNotApprovedAndCountsIt() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED)
                        .lastActiveTimestamp(100_000)
                        .build())));

    var answered =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall("Write", null, "/wt/ABC-1-proj/src/Foo.java"),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).extractingByKey("denied").asString().contains("plan first");
    ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
    verify(stateService).updateTask(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals())
        .containsExactly(Map.entry(GuardKind.PLAN_FIRST, 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Edit", "MultiEdit", "Write"})
  void letsThePlanAndTheVisualThroughWhileThePlanIsNotApproved(String tool) {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED)
                        .build())));

    var plan =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall(tool, null, "/wt/ABC-1-proj/plan.md"),
            new MockHttpServletRequest());
    var visual =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall(tool, null, "/wt/ABC-1-proj/task_visual.md"),
            new MockHttpServletRequest());

    assertThat(plan.getBody()).isNull();
    assertThat(visual.getBody()).isNull();
  }

  @Test
  void refusesNotebookEditWhileThePlanIsNotApproved() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.CLOSED)
                        .build())));

    var answered =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall("NotebookEdit", null, "/wt/ABC-1-proj/analysis.ipynb"),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNotNull();
  }

  @Test
  void letsEveryWriteThroughOnceThePlanIsApproved() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    var answered =
        controller.gate(
            "/wt/ABC-1-proj",
            new ToolCall("Edit", null, "/wt/ABC-1-proj/src/Foo.java"),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void refusesNoWriteFromDirectoryNoTaskOwns() {
    when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

    var answered =
        controller.gate(
            "/elsewhere",
            new ToolCall("Write", null, "/elsewhere/Foo.java"),
            new MockHttpServletRequest());

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void signsTheSessionUsedToolWhateverTheToolAnswers() {
    TaskState held =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1_000)
            .build();
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", held)));

    controller.gate(
        "/wt/ABC-1-proj",
        new ToolCall("Read", null, "/wt/ABC-1-proj/a.txt"),
        new MockHttpServletRequest());

    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();
    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(write.getValue().apply(reducer, held).session().movedAt()).isEqualTo(100_000);
  }

  @Test
  void signsNothingWhenTheTaskWasActiveSecondsAgo() {
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .lastActiveTimestamp(95_000)
                        .build())));

    controller.gate(
        "/wt/ABC-1-proj",
        new ToolCall("Read", null, "/wt/ABC-1-proj/a.txt"),
        new MockHttpServletRequest());

    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void namesTheProjectsOwnRemoteInTheHintOfRefusedPush() {
    when(configService.project("proj"))
        .thenReturn(projectConfig().withBase("upstream/main").withRemote("upstream").build());
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    Map<String, Object> answered =
        controller
            .gate(
                "/wt/ABC-1-proj",
                new ToolCall("Bash", "git push --no-verify upstream ABC-1", null),
                new MockHttpServletRequest())
            .getBody();

    assertThat(answered).extractingByKey("denied").asString().contains("`git push upstream ABC-1`");
  }

  @Test
  void namesOriginInTheHintWhereTheTasksProjectIsNoLongerConfigured() {
    when(configService.project("proj"))
        .thenThrow(new IllegalArgumentException("Unknown project 'proj'"));
    when(stateService.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(Optional.of(Map.entry("ABC-1", inProgress)));

    Map<String, Object> answered =
        controller
            .gate(
                "/wt/ABC-1-proj",
                new ToolCall("Bash", "git push --no-verify origin ABC-1", null),
                new MockHttpServletRequest())
            .getBody();

    assertThat(answered).extractingByKey("denied").asString().contains("`git push origin ABC-1`");
  }
}
