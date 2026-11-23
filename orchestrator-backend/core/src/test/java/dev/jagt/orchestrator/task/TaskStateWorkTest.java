package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateWorkTest {

  @Test
  void keepsStopWrittenInsideTheLoopBeforeTheStopStoodOnTheTask() {
    TaskState task =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"loop":{"rounds":[{"at":1,"threads":4,"failing":"","digest":"a"}],"since":1,
                         "stop":{"trigger":"ROUNDS","at":2,"evidence":"verify sent 3 rounds running in 24h"}}}\
                """,
                TaskState.class);

    assertThat(task.work().stop())
        .isEqualTo(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running in 24h"));
    assertThat(task.round().trailOrNone().rounds()).hasSize(1);
  }

  @Test
  void addsTheRepositorySessionAskedForAfterItsOwnAndEndsTheRequest() {
    TaskState asked =
        TaskState.builder("api", "/wt-api", TaskStatus.IN_PROGRESS).repoRequest("registry").build();

    TaskState added = asked.withRepo(TaskRepo.of("registry", "/wt-registry"));

    assertThat(added.projects()).containsExactly("api", "registry");
    assertThat(added.work().repoRequest()).isNull();
  }

  @Test
  void asksForNoRepositoryUntilTheSessionNamesOne() {
    TaskState working = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    assertThat(working.work().asksForRepository()).isFalse();
  }

  @Test
  void holdsNoTurnUntilOneIsRecordedAndThenTheOneRecorded() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    Turn relayed = Turn.none().relayed("round", "d1", "", 100);

    assertThat(task.work().turnOrNone()).isEqualTo(Turn.none());
    assertThat(task.withTurn(relayed).work().turnOrNone()).isEqualTo(relayed);
  }

  @Test
  void keepsTheStopAndTheTrailItWasGivenAndClearsTheStopOnLift() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 2, "verify sent 3 rounds running");
    RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 4, "", "a", ""));

    TaskState stopped = task.withStop(stop).withTrail(trail);
    TaskState lifted = stopped.lifted(9);

    assertThat(stopped.work().stopped()).isTrue();
    assertThat(stopped.work().stop()).isEqualTo(stop);
    assertThat(stopped.round().trailOrNone()).isEqualTo(trail);
    assertThat(lifted.work().stopped()).isFalse();
    assertThat(lifted.round().trailOrNone()).isEqualTo(RoundTrail.liftedAt(9));
  }

  @Test
  void recordsRequestWithoutCountingItAsAgentActivity() {
    TaskState working =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(7_000).build();

    TaskState asked = working.withRepoRequest("registry");

    assertThat(asked.work().asksForRepository()).isTrue();
    assertThat(asked.standing().lastActiveTimestamp()).isEqualTo(7_000);
  }

  @Test
  void keepsStopWhereHumansDeployLands() {
    TaskState stopped =
        TaskState.builder("api", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.MISS, 5, "no case"))
            .build();

    TaskState deployed = stopped.withStatus(TaskStatus.DEPLOYED, "deployed", true, 2_000);

    assertThat(deployed.work().stopped()).isTrue();
  }

  @Test
  void readsAnOldConflictWatchAsTheConflictOfTheDeploymentItsTaskStandsIn() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"conflict":{"conflictSince":4,"worktree":"/wt-deploy",
                 "progress":{"unmerged":7,"staged":0,"digest":"d"},"movedAt":6,"told":{"at":8,"reason":"stuck"}}}\
                """,
                TaskState.class);

    assertThat(legacy.currentConflict())
        .hasValueSatisfying(conflict -> {
          assertThat(conflict.progress()).isEqualTo(new Conflict.Progress(7, 0, "d"));
          assertThat(conflict.told()).isEqualTo(new Conflict.Told(8, "stuck"));
          assertThat(conflict.movedAt()).isEqualTo(6);
        });
  }

  @Test
  void readsTaskInDeployConflictThatNothingRecordedAsOneNotYetHanded() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(legacy.currentConflict())
        .hasValueSatisfying(conflict -> assertThat(conflict.handed()).isFalse());
  }

  @Test
  void readsTheMergeAndFixesOfRepositoryIntoTheTargetOfItsProject() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/wt/php","deployCommit":"cafe1","deployFixes":["f1x1","f1x2"]},
                          {"project":"java","worktreePath":"/wt/java","deployCommit":"beef2"}],
                 "status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(Objects.requireNonNull(old.work().deployment()).targets())
        .extracting(
            Deployment.Target::project,
            target -> target.landing().merge(),
            target -> target.landing().fixes())
        .containsExactly(
            tuple("php", "cafe1", List.of("f1x1", "f1x2")), tuple("java", "beef2", List.of()));
    assertThat(old.work().deployment().live()).isTrue();
  }

  @Test
  void placesAnOldConflictOnTheTargetWhoseDeployWorktreeItStoodIn() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/one/ABC-1-php"},
                          {"project":"java","worktreePath":"/two/ABC-1-java"}],
                 "status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "conflict":{"conflictSince":3,"worktree":"/two/ABC-1-deploy",
                             "progress":{"unmerged":1,"staged":0,"digest":"d"},"movedAt":4}}\
                """,
                TaskState.class);

    assertThat(Objects.requireNonNull(old.work().deployment()).conflictedTarget())
        .hasValueSatisfying(target -> {
          assertThat(target.project()).isEqualTo("java");
          assertThat(Objects.requireNonNull(target.progress().conflict()).since()).isEqualTo(3);
        });
  }

  @Test
  void placesAnOldConflictOnThePrimaryProjectWhenNoDeployWorktreeMatches() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"php","worktreePath":"/one/ABC-1-php"},
                          {"project":"java","worktreePath":"/two/ABC-1-java"}],
                 "status":"DEPLOY_CONFLICT","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "conflict":{"conflictSince":3,"worktree":"/else/ABC-1-deploy","movedAt":4}}\
                """,
                TaskState.class);

    assertThat(
            Objects.requireNonNull(old.work().deployment())
                .conflictedTarget()
                .orElseThrow()
                .project())
        .isEqualTo("php");
  }

  @Test
  void keepsTheConflictOfDeployTargetAcrossSave() {
    TaskState conflicted =
        TaskState.builder("demo", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("demo", "dev")
                            .withConflict(
                                Conflict.of(Deployment.Cause.RED_PIPELINE, 2)
                                    .seen(new Conflict.Progress(1, 2, "d"), 3)
                                    .toldAt(4, "stuck")
                                    .heldBy(5))),
                    0,
                    null));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(conflicted), TaskState.class);

    assertThat(reread.work().deployment()).isEqualTo(conflicted.work().deployment());
  }

  @Test
  void keepsTheSessionSignsAcrossSave() {
    TaskState signed =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                new Session(
                    5,
                    4,
                    3,
                    new Session.Halt(Session.Halt.Kind.FAILED, 6, "overloaded"),
                    2,
                    "/logs/s.jsonl",
                    9,
                    new Session.Start(
                        3, new Session.Window(Session.Window.State.RUNNING, 7, 8), null)));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(signed), TaskState.class);

    assertThat(reread.work().session()).isEqualTo(signed.work().session());
  }

  @Test
  void readsTaskWrittenBeforeTheSessionWasRecordedAsOneWithNoSign() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(old.work().session()).isEqualTo(Session.defaults());
  }

  @Test
  void readsSilenceAnOlderFileStampedOnTheTaskAsTheSessionsQuiet() {
    TaskState old =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"silentSince":4000,"silentBecause":"its turn failed"}\
                """,
                TaskState.class);

    assertThat(old.work().session().quietSince()).isEqualTo(4_000);
  }

  @Test
  void endsStoppedSessionWhenTheTaskMovesOnToStatusOfItsOwn() {
    TaskState stopped =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.GONE, 4_000, "boom"))
                    .withQuietSince(3_000));

    TaskState moved = stopped.withStatus(TaskStatus.REVIEW_PENDING, "shipped", 5_000);

    assertThat(moved.work().session().halt()).isNull();
    assertThat(moved.work().session().quietSince()).isZero();
  }

  @Test
  void keepsAnIdleHaltWhenTheTaskMovesOnToStatusOfItsOwn() {
    TaskState idle =
        TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 4_000, null))
                    .withQuietSince(3_000));

    TaskState moved = idle.withStatus(TaskStatus.REVIEW_PENDING, "shipped", 5_000);

    assertThat(moved.work().session().halt()).isEqualTo(idle.work().session().halt());
    assertThat(moved.work().session().quietSince()).isZero();
  }
}
