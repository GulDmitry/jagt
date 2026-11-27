package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class MasterBrakeTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @TempDir Path root;
  private OrchestratorPaths paths;
  private AnnotationConfigApplicationContext context;
  private TaskState reverted1;
  private TaskState reverted2;
  private TaskState reverted3;
  private TaskState reverted4;
  private TaskState reverted5;

  @BeforeEach
  void setUp() {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    context = new AnnotationConfigApplicationContext();
    context.registerBean(MasterBrake.class, () -> new MasterBrake(paths));
    context.refresh();
    reverted1 =
        TaskState.builder("proj", "/wt1", TaskStatus.REVERTED)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD)))
            .build();
    reverted2 =
        TaskState.builder("proj", "/wt2", TaskStatus.REVERTED)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD)))
            .build();
    reverted3 =
        TaskState.builder("proj", "/wt3", TaskStatus.REVERTED)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD)))
            .build();
    reverted4 =
        TaskState.builder("proj", "/wt4", TaskStatus.REVERTED)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 13, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVERTED, 23, ActionOrigin.BOARD)))
            .build();
    reverted5 =
        TaskState.builder("proj", "/wt5", TaskStatus.REVERTED)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 14, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVERTED, 24, ActionOrigin.BOARD)))
            .build();
  }

  @Test
  void stopsActWhenThreeTasksTheMasterMovedEndBadlyInRow() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1", reverted1,
                "ABC-2",
                    TaskState.builder("proj", "/wt2", TaskStatus.DEPLOY_CONFLICT)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                                new StatusChange(
                                    TaskStatus.DEPLOY_CONFLICT, 21, ActionOrigin.MASTER)))
                        .build(),
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.IN_PROGRESS, 12, ActionOrigin.MASTER)))
                        .session(
                            Session.defaults()
                                .withHalt(new Session.Halt(Session.Halt.Kind.GONE, 22, "crash")))
                        .build())));

    assertThat(root.resolve("act-stopped")).exists();
  }

  @Test
  void countsNoSilenceAfterTheSessionReportedItself() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1",
                    TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 20, ActionOrigin.MCP)))
                        .session(Session.defaults().withQuietSince(30))
                        .build(),
                "ABC-2",
                    TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 21, ActionOrigin.MCP)))
                        .session(Session.defaults().withQuietSince(31))
                        .build(),
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 22, ActionOrigin.MCP)))
                        .session(Session.defaults().withQuietSince(32))
                        .build())));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void stopsActWhenThreeSessionsFallSilentRightAfterTheMastersMove() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1",
                    TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.IN_PROGRESS, 10, ActionOrigin.MASTER)))
                        .session(Session.defaults().withQuietSince(30))
                        .build(),
                "ABC-2",
                    TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.IN_PROGRESS, 11, ActionOrigin.MASTER)))
                        .session(Session.defaults().withQuietSince(31))
                        .build(),
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.IN_PROGRESS, 12, ActionOrigin.MASTER)))
                        .session(Session.defaults().withQuietSince(32))
                        .build())));

    assertThat(root.resolve("act-stopped")).exists();
  }

  @Test
  void countsNoSilenceAfterTheHumansOwnMove() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1",
                    TaskState.builder("proj", "/wt1", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 20, ActionOrigin.BOARD)))
                        .session(Session.defaults().withQuietSince(30))
                        .build(),
                "ABC-2",
                    TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 11, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 21, ActionOrigin.BOARD)))
                        .session(Session.defaults().withQuietSince(31))
                        .build(),
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.IN_PROGRESS)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.SHIPPING, 12, ActionOrigin.MASTER),
                                new StatusChange(TaskStatus.IN_PROGRESS, 22, ActionOrigin.BOARD)))
                        .session(Session.defaults().withQuietSince(32))
                        .build())));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void keepsActWhereDeployCameBetweenTheBadEndings() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1", reverted1,
                "ABC-2", reverted2,
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.DEPLOYED)
                        .history(
                            List.of(new StatusChange(TaskStatus.DEPLOYED, 22, ActionOrigin.MASTER)))
                        .build(),
                "ABC-4", reverted4)));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void countsOneTaskEndingBadlyTwiceAsOne() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1",
                TaskState.builder("proj", "/wt1", TaskStatus.REVERTED)
                    .history(
                        List.of(
                            new StatusChange(TaskStatus.SHIPPING, 10, ActionOrigin.MASTER),
                            new StatusChange(TaskStatus.DEPLOY_CONFLICT, 20, ActionOrigin.MASTER),
                            new StatusChange(TaskStatus.REVERTED, 30, ActionOrigin.BOARD)))
                    .build(),
                "ABC-2",
                reverted2)));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void countsNoTaskTheMasterNeverMoved() {

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1",
                    TaskState.builder("proj", "/wt1", TaskStatus.REVERTED)
                        .history(
                            List.of(new StatusChange(TaskStatus.REVERTED, 20, ActionOrigin.BOARD)))
                        .build(),
                "ABC-2",
                    TaskState.builder("proj", "/wt2", TaskStatus.REVERTED)
                        .history(
                            List.of(new StatusChange(TaskStatus.REVERTED, 21, ActionOrigin.BOARD)))
                        .build(),
                "ABC-3",
                    TaskState.builder("proj", "/wt3", TaskStatus.REVERTED)
                        .history(
                            List.of(new StatusChange(TaskStatus.REVERTED, 22, ActionOrigin.BOARD)))
                        .build())));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void countsNothingThatHadAlreadyEndedWhenJagtStarted() {
    StateService before = new StateService(new JsonMapper(), paths, reducer, event -> {});
    before.putTask("ABC-1", reverted1);
    before.putTask("ABC-2", reverted2);
    AnnotationConfigApplicationContext started = new AnnotationConfigApplicationContext();
    started.registerBean(MasterBrake.class, () -> new MasterBrake(paths));
    started.refresh();
    StateService after = new StateService(new JsonMapper(), paths, reducer, started::publishEvent);

    after.putTask("ABC-3", reverted3);

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void givesActBackWhenTheHumanRestoresIt() throws Exception {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
    MasterBrake brake = new MasterBrake(paths);

    boolean restored = brake.restore();

    assertThat(restored).isTrue();
    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void stopsActOnEndingsWrittenToTheStateAfterItStartedFollowing() {
    StateService state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);

    state.putTask("ABC-1", reverted1);
    state.putTask("ABC-2", reverted2);
    state.putTask("ABC-3", reverted3);

    assertThat(root.resolve("act-stopped")).exists();
  }

  @Test
  void startsTheCountAgainWhereTheHumanRestoredActBetweenBadEndings() {
    MasterBrake brake = context.getBean(MasterBrake.class);
    context.publishEvent(
        new StateWritten(Map.of(), Map.of("ABC-1", reverted1, "ABC-2", reverted2)));
    brake.restore();

    context.publishEvent(
        new StateWritten(
            Map.of(), Map.of("ABC-1", reverted1, "ABC-2", reverted2, "ABC-3", reverted3)));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void startsTheCountAgainOnceItHasStoppedAct() throws Exception {
    context.publishEvent(
        new StateWritten(
            Map.of(), Map.of("ABC-1", reverted1, "ABC-2", reverted2, "ABC-3", reverted3)));
    Files.delete(root.resolve("act-stopped"));

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1", reverted1, "ABC-2", reverted2, "ABC-3", reverted3, "ABC-4", reverted4,
                "ABC-5", reverted5)));

    assertThat(root.resolve("act-stopped")).doesNotExist();
  }

  @Test
  void keepsTheCountAcrossRestart() {
    AnnotationConfigApplicationContext first = new AnnotationConfigApplicationContext();
    first.registerBean(MasterBrake.class, () -> new MasterBrake(paths));
    first.refresh();
    first.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-1", reverted1,
                "ABC-2", reverted2)));
    AnnotationConfigApplicationContext restarted = new AnnotationConfigApplicationContext();
    restarted.registerBean(MasterBrake.class, () -> new MasterBrake(paths));
    restarted.refresh();

    restarted.publishEvent(new StateWritten(Map.of(), Map.of("ABC-3", reverted3)));

    assertThat(root.resolve("act-stopped")).exists();
  }

  @Test
  void saysActWasNotStoppedWhereThereWasNothingToRestore() {
    MasterBrake brake = new MasterBrake(paths);

    boolean restored = brake.restore();

    assertThat(restored).isFalse();
  }
}
