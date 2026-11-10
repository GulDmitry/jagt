package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class BoardApiControllerTest {

  private record Declared(String id, String hint, boolean report, int rank)
      implements GlobalCommand {
    @Override
    public String run(String tail) {
      return tail.isBlank() ? id + " report" : id + " report about " + tail;
    }
  }

  private final TaskViews taskViews = mock(TaskViews.class);
  private final BoardApiController api =
      new BoardApiController(
          taskViews,
          mock(TaskEventStream.class),
          new GlobalCommands(
              List.of(
                  new Declared("stats", "what the calls cost", true, 100),
                  new Declared("do", "start a task", false, 40))),
          BoardFixtures.headerOf(List.of(), new ReachLedger()));

  @Test
  void servesEveryVerbThePaletteMustBeAbleToCompleteAndValidate() {
    var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

    assertThat(ids)
        .contains(
            "ship", "sweep", "ide", "diff", "deploy", "revert", "respawn", "done", "focus", "do",
            "stats");
  }

  @Test
  void saysWhichVerbsAreNothingWithoutTaskToApplyThemTo() {
    assertThat(
            api.commands().stream()
                .filter(CommandReference.Verb::takesTask)
                .map(CommandReference.Verb::id))
        .contains("ship")
        .doesNotContain("do", "stats");
  }

  @Test
  void offersTheEverydayVerbsBeforeTheRareOnes() {
    var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

    assertThat(ids).startsWith("sweep", "ship", "do");
    assertThat(ids.indexOf("ship")).isLessThan(ids.indexOf("focus"));
    assertThat(ids.indexOf("deploy")).isLessThan(ids.indexOf("done"));
  }

  @Test
  void servesReportUnderTheIdOfTheCommandThatProducesIt() {
    assertThat(api.report("stats", null)).isEqualTo("stats report");
  }

  @Test
  void passesWhatTheBoardAsksAboutToTheCommandItself() {
    assertThat(api.report("stats", "a1")).isEqualTo("stats report about a1");
  }

  @Test
  void refusesToRunCommandThatIsNotReport() {
    assertThatThrownBy(() -> api.report("do", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No report 'do'");
  }

  @Test
  void runsCommandThatIsNotReportWithWhatWasTypedAfterIt() {
    assertThat(api.execute("do", "a1").message()).isEqualTo("do report about a1");
  }

  @Test
  void refusesToRunReportAsCommand() {
    assertThatThrownBy(() -> api.execute("stats", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No command 'stats' to run");
  }

  @Test
  void reportsTheProjectsAlongsideTheTasks() {
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(), false, List.of("demo")));

    var board = api.tasks();

    assertThat(board.projects()).containsExactly("demo");
  }

  @Test
  void servesTheBoardAsOneFlatObject() {
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(), false, List.of()));

    var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(api.tasks()));

    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "tasks",
            "projects",
            "offers",
            "masterMode",
            "autoReview",
            "autoReviewEnabled",
            "jobs",
            "actStop",
            "reach",
            "branchStrategies",
            "phases");
  }

  @Test
  void tellsTheBoardWhereJagtLostItsReach() {
    var reach = new ReachLedger();
    var lost =
        new dev.jagt.orchestrator.port.ReachLost(
            "glab",
            "git.example.invalid",
            List.of("glab", "auth", "login"),
            "glab not logged in to git.example.invalid");
    reach.lost(lost);
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(), false, List.of()));
    BoardApiController board =
        new BoardApiController(
            taskViews,
            mock(TaskEventStream.class),
            new GlobalCommands(List.of()),
            BoardFixtures.headerOf(List.of(), reach));

    assertThat(board.tasks().header().reach()).containsExactly(lost);
  }

  @Test
  void servesTheStatusOfEveryJobTheBackendRuns() {
    Job poll = mock(Job.class);
    when(poll.id()).thenReturn("poll-reviews");
    BoardApiController board =
        new BoardApiController(
            taskViews,
            mock(TaskEventStream.class),
            new GlobalCommands(List.of()),
            BoardFixtures.headerOf(List.of(poll), new ReachLedger()));

    assertThat(board.jobs()).extracting(Jobs.Status::id).containsExactly("poll-reviews");
  }

  @Test
  void tellsTheBoardHowManyJobsTheBackendRuns() {
    Job poll = mock(Job.class);
    when(poll.id()).thenReturn("poll-reviews");
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(), false, List.of()));
    BoardApiController board =
        new BoardApiController(
            taskViews,
            mock(TaskEventStream.class),
            new GlobalCommands(List.of()),
            BoardFixtures.headerOf(List.of(poll), new ReachLedger()));

    assertThat(board.tasks().header().jobs().count()).isEqualTo(1);
  }

  @Test
  void handsTheBrowserTheStreamTheEventSourceOpened() {
    TaskEventStream stream = mock(TaskEventStream.class);
    SseEmitter browser = new SseEmitter(0L);
    when(stream.open()).thenReturn(browser);
    BoardApiController board =
        new BoardApiController(
            taskViews,
            stream,
            new GlobalCommands(List.of()),
            BoardFixtures.headerOf(List.of(), new ReachLedger()));

    assertThat(board.events()).isSameAs(browser);
  }

  @Test
  void saysWhetherTheUnattendedPollRunsAtAllEvenWithNoTasks() {
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(), true, List.of()));

    var board = api.tasks();

    assertThat(board.cadence().autoReviewEnabled()).isTrue();
    assertThat(board.cadence().autoReview()).isEqualTo("auto-review on");
  }

  @Test
  void offersOnCardTheReportThatDeclaresItselfForThatTask() {
    GlobalCommand drafts =
        new GlobalCommand() {
          @Override
          public String id() {
            return "replies";
          }

          @Override
          public String hint() {
            return "the drafted answers";
          }

          @Override
          public int rank() {
            return 20;
          }

          @Override
          public boolean report() {
            return true;
          }

          @Override
          public boolean offeredOn(TaskView task) {
            return task.draftedReplies();
          }

          @Override
          public String run(String tail) {
            return "";
          }
        };
    BoardApiController board =
        new BoardApiController(
            taskViews,
            mock(TaskEventStream.class),
            new GlobalCommands(List.of(drafts)),
            BoardFixtures.headerOf(List.of(), new ReachLedger()));
    TaskView drafted =
        TaskView.of(
            new FlowRules(new TaskChart(Composition.builtIn())),
            "ABC-1",
            TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build(),
            RoundState.of(AgentReport.PLAIN, true),
            AutoReviewWatch.none(),
            Map.of());
    TaskView quiet =
        TaskView.of(
            new FlowRules(new TaskChart(Composition.builtIn())),
            "ABC-2",
            TaskState.builder("demo", "/wt2", TaskStatus.REVIEW_PENDING).build(),
            RoundState.of(AgentReport.PLAIN, false),
            AutoReviewWatch.none(),
            Map.of());
    when(taskViews.snapshot())
        .thenReturn(
            BoardFixtures.snapshotOf(List.of(drafted, quiet), false, List.of("demo")));

    var offers = board.tasks().offers();

    assertThat(offers).containsExactly(Map.entry("ABC-1", List.of("replies")));
  }
}
