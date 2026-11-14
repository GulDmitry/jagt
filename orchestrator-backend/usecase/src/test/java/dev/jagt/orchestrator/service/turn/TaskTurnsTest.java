package dev.jagt.orchestrator.service.turn;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

class TaskTurnsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private StateService state;
  private TaskTurns turns;

  @BeforeEach
  void opensTheTurnsOverTheTempState() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    turns =
        new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())));
  }

  @Nested
  class Logging {

    @BeforeEach
    void holdsTheDeployedTask() {
      state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    }

    @Test
    void logsTheStepAndTheTurnEachRelayOpens() {
      try (FakeLogCapture log = new FakeLogCapture(TaskTurns.class)) {
        turns.opened("ABC-42", "deploy", "a1b2", TaskStatus.DEPLOYED);

        assertThat(log.events())
            .filteredOn(event -> "turn opened".equals(event.getMessage()))
            .singleElement()
            .extracting(ILoggingEvent::getKeyValuePairs)
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .contains(
                new KeyValuePair("task", "ABC-42"),
                new KeyValuePair("step", "deploy"),
                new KeyValuePair("turn", 1L));
      }
    }

    @Test
    void logsTheStatusTheDoorAdmittedTheStepAtNotTheOneTheTaskHoldsByNow() {
      try (FakeLogCapture log = new FakeLogCapture(TaskTurns.class)) {
        turns.opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);

        assertThat(log.events())
            .filteredOn(event -> "turn opened".equals(event.getMessage()))
            .singleElement()
            .extracting(ILoggingEvent::getKeyValuePairs)
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .contains(new KeyValuePair("status", TaskStatus.REVIEWED));
      }
    }
  }

  @Test
  void doesNotReadRootSessionsMoveAsTheTaskSessionsAnswer() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1, ActionOrigin.ROOT)))
            .build());

    turns.opened("ABC-42", "deploy", "a1b2", TaskStatus.IN_PROGRESS);

    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().mark("deploy:answers"))
        .isEqualTo(Turn.digestOf(""));
  }

  @Test
  void leavesTheStepToTheHumanWhereTheMasterOfferedItInsteadOfPressingIt() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .master(Master.defaults().withLeft("deploy src/Login.java"))
            .build();

    assertThat(MasterPhase.of(task)).isEqualTo(MasterPhase.LEFT);
  }

  @Test
  void leavesQuestionToTheHumanOnceTheMasterGaveItNoUsableAnswer() {
    TaskState asking =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .message("which cache")
            .build();
    TaskState unanswered =
        asking.withMaster(Master.defaults().withUnanswered(TaskTurns.asked(asking)));

    assertThat(MasterPhase.of(unanswered)).isEqualTo(MasterPhase.LEFT);
  }

  @Test
  void leavesQuestionToTheMasterBeforeItHasFailedToAnswerIt() {
    TaskState asking =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .message("which cache")
            .build();

    assertThat(MasterPhase.of(asking)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void leavesAnotherQuestionToTheMasterThanTheOneItFailedToAnswer() {
    TaskState asking =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .message("which cache")
            .master(Master.defaults().withUnanswered(Turn.digestOf("which queue")))
            .build();

    assertThat(MasterPhase.of(asking)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void leavesMarkedAnswerToTheMasterWhereTheTaskIsNotAsking() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .message("which cache")
            .master(Master.defaults().withUnanswered(Turn.digestOf("which cache")))
            .build();

    assertThat(MasterPhase.of(working)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void leavesDeployTheMasterPressedToTheHumanWhileTheTaskStillWaitsForIt() {
    TaskState deployed =
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED).lastActiveTimestamp(7).build();
    TaskState pressed =
        deployed.withMaster(
            Master.defaults().withPressedDeploy(String.valueOf(deployed.statusSince())));

    assertThat(MasterPhase.of(pressed)).isEqualTo(MasterPhase.LEFT);
    assertThat(MasterPhase.of(deployed)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void keepsTheShippedTreeOnTheShipAsItsDigest() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.SHIPPING)
            .history(List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, null)))
            .build());

    turns.shippedOn("ABC-42", "tree-1;");

    assertThat(state.task("ABC-42").orElseThrow().history().getLast().tree())
        .isEqualTo("23189593ac33d3bc37d365b9805c4c248103ce3d9980acdee9801672208b9a44");
  }

  @Test
  void setsAndClearsMarkOnTheTasksTurn() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).message("which cache").build());
    turns.left("ABC-42", "deploy src/Login.java");
    turns.unanswered("ABC-42");
    String left = state.task("ABC-42").orElseThrow().masterOrNone().left();
    turns.took("ABC-42");

    assertThat(left).isEqualTo("deploy src/Login.java");
    assertThat(state.task("ABC-42").orElseThrow().masterOrNone().left()).isEmpty();
    assertThat(state.task("ABC-42").orElseThrow().masterOrNone().unanswered())
        .isEqualTo("28e31f5a9bb2534351673c735fe222a1");
  }
}
