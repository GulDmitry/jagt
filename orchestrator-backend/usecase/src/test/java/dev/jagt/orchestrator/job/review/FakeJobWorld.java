package dev.jagt.orchestrator.job.review;

import static dev.jagt.orchestrator.job.TestConflictedTaskBuilder.conflictedTask;
import static dev.jagt.orchestrator.job.TestPollingTaskBuilder.pollingTask;
import static dev.jagt.orchestrator.job.TestRolesRootBuilder.rolesRoot;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.job.TestYourMoveTaskBuilder.yourMoveTask;
import static dev.jagt.orchestrator.job.review.TestAutoReviewSchedulerBuilder.autoReviewScheduler;
import static dev.jagt.orchestrator.job.review.TestYourMoveJobBuilder.yourMoveJob;
import static dev.jagt.orchestrator.notify.TestPointWatchBuilder.pointWatch;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import dev.jagt.orchestrator.job.conflict.FakeConflictWatch;
import dev.jagt.orchestrator.job.session.AliveStamp;
import dev.jagt.orchestrator.job.session.RestartAskJob;
import dev.jagt.orchestrator.job.verdict.MasterReviewJob;
import dev.jagt.orchestrator.job.verdict.MasterRuling;
import dev.jagt.orchestrator.notify.ChannelPosts;
import dev.jagt.orchestrator.notify.FakeChannelDriver;
import dev.jagt.orchestrator.notify.Messages;
import dev.jagt.orchestrator.notify.NextMove;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.notify.PointWatch;
import dev.jagt.orchestrator.notify.Routes;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CarriedBlock;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.service.assistant.KeptTicketTexts;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.service.git.TrustedGitState;
import dev.jagt.orchestrator.service.git.WorktreeInspection;
import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.master.MasterBriefs;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.master.RoundFacts;
import dev.jagt.orchestrator.service.master.RoundQuotes;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.usage.UsageLedger;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict.Progress;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.testing.FakeAgentPresence;
import dev.jagt.orchestrator.testing.FakeAgentRuntime;
import dev.jagt.orchestrator.testing.FakeAssistants;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeProcesses;
import dev.jagt.orchestrator.testing.FakeReach;
import dev.jagt.orchestrator.testing.FakeSessionHost;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/**
 * Three tasks over one state file, the jobs that tell about them, and a clock; a restart rebuilds
 * every service over the same file.
 */
public final class FakeJobWorld implements AutoCloseable {

  public enum Step {
    AUTO_REVIEW,
    YOUR_MOVE,
    POINT_WATCH,
    RESTART_ASK,
    OPEN_TURN,
    SESSION_WRITES,
    SWEEP_PENDING,
    SWEEP_UNREADABLE,
    SWEEP_GONE,
    SWEEP_UNCHANGED,
    MASTER_REVIEW,
    CONFLICT_HAND,
    CONFLICT_EDIT,
    ROUND_BACK,
    NEW_ROUND,
    CONFLICT,
    WORKED,
    ASK,
    ANSWER,
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    RESTART
  }

  public record Start(TaskStatus polling, long ageMinutes, TaskStatus waiting) {}

  public record Told(String task, String point, int version) {}

  public record Said(String task, int start, int version, String line) {}

  private record Jobs(
      AutoReviewScheduler review,
      YourMoveJob yourMove,
      PointWatch points,
      RestartAskJob restartAsk,
      AliveStamp alive,
      MasterReviewJob master,
      FakeConflictWatch conflicts,
      StateService state) {}

  private final Path root;
  private final Path sessionLog;
  private final FakeClock clock = new FakeClock();
  private final FakeChannelDriver driver = new FakeChannelDriver();
  private final FakeSessionHost host = new FakeSessionHost();
  private final FakeAssistants assistants = new FakeAssistants();
  private final FakeSweepKind sweep;
  private final ReviewSweepService sweeps;
  private final MasterRuling ruling;
  private final CommandService commands;
  private int reviewed;
  private int edits;
  private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor();
  private final List<Told> sent = new ArrayList<>();
  private final List<Said> said = new ArrayList<>();
  private Jobs jobs;
  private int version;
  private int starts;
  private boolean restartAsked;
  private int delivered;
  private int typed;

  public FakeJobWorld(
      Start start,
      FakeSweepKind sweep,
      ReviewSweepService sweeps,
      MasterRuling ruling,
      CommandService commands) {
    this.sweep = sweep;
    sweep.answers(SweepResult.Kind.PENDING);
    this.sweeps = sweeps;
    this.ruling = ruling;
    this.commands = commands;
    try {
      root = Files.createTempDirectory("jagt-jobs");
      sessionLog = Files.createFile(root.resolve("session.log"));
      Files.setLastModifiedTime(sessionLog, FileTime.fromMillis(0));
      Files.writeString(root.resolve("jagt.yml"), config());
      rolesRoot().withRoot(root).build();
      Files.createDirectories(root.resolve("wt1"));
      Files.createDirectories(root.resolve("wt2"));
      Files.createDirectories(root.resolve("wt4"));
      Files.createDirectories(root.resolve("wt5"));
      Files.createDirectories(root.resolve("wt3"));
      Files.createDirectories(root.resolve(".fake"));
      Files.copy(
          Path.of("../../roles/master/brief.md.dist"), root.resolve("roles/master/brief.md.dist"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    StateService state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-1",
        pollingTask()
            .withStatus(start.polling())
            .withWorktree(root.resolve("wt1").toString())
            .withMrCreatedAt(clock.millis() - Duration.ofMinutes(start.ageMinutes()).toMillis())
            .build());
    state.putTask(
        "ABC-2",
        yourMoveTask()
            .withStatus(start.waiting())
            .withWorktree(root.resolve("wt2").toString())
            .build());
    state.putTask("ABC-3", conflictedTask().withWorktree(root.resolve("wt3").toString()).build());
    state.putTask("ABC-4", yourMoveTask().withWorktree(root.resolve("wt4").toString()).build());
    state.putTask(
        "ABC-5",
        TaskState.builder("proj", root.resolve("wt5").toString(), TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(2_000L)
            .build());
    jobs = rebuilt(state);
  }

  private String config() {
    String routes =
        Points.builtIn().stream()
            .map(Point::id)
            .map(id ->
                "      " + id + ": [ { channel: fake, title: \"{title}\", text: \"{body}\" } ]")
            .collect(Collectors.joining("\n"));
    return "orchestrator:\n"
        + "  autoReview: { enabled: true }\n"
        + "  roles:\n"
        + "    master: { mode: judge }\n"
        + "  projects:\n"
        + "    proj: { path: "
        + root
        + ", baseBranch: origin/main }\n"
        + "  notifications:\n"
        + "    channels:\n"
        + "      fake: { kind: fake }\n"
        + "    points:\n"
        + routes
        + "\n";
  }

  private Jobs rebuilt(StateService state) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.toString())
                .withStateFile(root.resolve("state.json").toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    FakeAgentRuntime runtime = new FakeAgentRuntime(sessionLog);
    FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));
    ConfigService config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            runtime,
            new ActStops(new MasterBrake(paths), new BoardStop(paths, clock)),
            new Checkers(new TaskChart(Composition.builtIn())));
    GitCommands git =
        new GitCommands(
            new FakeProcesses(root.resolve(".fake")),
            worktree -> {},
            repository -> Optional.empty(),
            worktree -> {});
    WorktreeChanges changes = new WorktreeChanges(config, new WorktreeInspection(git, runtime));
    HandBack handBack =
        new HandBack(
            changes,
            new Rounds(
                config,
                ignored -> false,
                () -> false,
                () -> false,
                new Checkers(new TaskChart(Composition.builtIn()))),
            config);
    Notifications notifications =
        new Notifications(
            new Routes(config),
            new Messages(new BoardUrl("8290", ""), new NextMove(handBack, rules)),
            new ChannelPosts(List.of(driver), new FakeReach(), Runnable::run, retries),
            state);
    AgentSessions sessions =
        new AgentSessions(
            config,
            state,
            new SessionHosting(host, runtime, new CarriedBlock(changes, runtime)),
            rules);
    UsageTracker usage = new UsageTracker(state, new UsageLedger(new JsonMapper(), paths, clock));
    Assistants models = assistants.assistants();
    MasterReview stored = new MasterReview(state);
    MasterPanel panel =
        new MasterPanel(
            new ChargedReviews(
                models, usage, new TrustedGitState(state, new GitWorktrees(git)), config),
            new MasterBriefs(paths, new SessionBrief(paths)),
            new RoundQuotes(
                new RoundFacts(
                    new TicketTexts(models, usage, new KeptTicketTexts(paths)), changes, runtime),
                new MasterDecisions(changes),
                stored),
            stored);
    MasterReviewJob master = new MasterReviewJob(state, config, panel, stored, ruling);
    StepRelay relay = stepRelay().withRoot(root).build(state, sessions, clock);
    AliveStamp alive = new AliveStamp(paths, clock);
    return new Jobs(
        autoReviewScheduler().withRoot(root).build(state, config, sweeps, Runnable::run, clock),
        yourMoveJob().build(state, notifications, changes, config, clock),
        pointWatch().withRoot(root).build(state, notifications),
        new RestartAskJob(sessions, new FakeAgentPresence(), relay, alive),
        alive,
        master,
        new FakeConflictWatch(
            state,
            config,
            clock,
            relay,
            sessions,
            commands,
            taskStops().withRoot(root).build(state, clock)),
        state);
  }

  public void run(List<Step> steps) {
    steps.forEach(this::run);
  }

  public void run(Step step) {
    StateService state = jobs.state();
    jobs.alive().run();
    switch (step) {
      case AUTO_REVIEW -> jobs.review().run();
      case YOUR_MOVE -> jobs.yourMove().run();
      case POINT_WATCH -> jobs.points().sweep();
      case RESTART_ASK -> askAfterStart(state);
      case MASTER_REVIEW -> jobs.master().run();
      case CONFLICT_HAND -> handConflict(state);
      case CONFLICT_EDIT -> edits++;
      case ROUND_BACK -> changed(() -> handedBack(state));
      case OPEN_TURN ->
          state.updateTask(
              "ABC-4",
              task ->
                  state.reducer().relayed(task, "working", "round" + starts, "", clock.millis()));
      case SESSION_WRITES -> touchSessionLog();
      case SWEEP_PENDING -> sweep.answers(SweepResult.Kind.PENDING);
      case SWEEP_UNREADABLE -> sweep.answers(SweepResult.Kind.UNREADABLE);
      case SWEEP_GONE -> sweep.answers(SweepResult.Kind.GONE);
      case SWEEP_UNCHANGED -> sweep.answers(SweepResult.Kind.UNCHANGED);
      case NEW_ROUND ->
          changed(() ->
              state.updateTask(
                  "ABC-1", task -> task.withReviewRound("http://mr/1", clock.millis())));
      case CONFLICT ->
          changed(() ->
              state.updateTask(
                  "ABC-3",
                  task ->
                      state
                          .reducer()
                          .deployStepped(
                              state
                                  .reducer()
                                  .deployStepped(
                                      task,
                                      new DeployStep.Opened(
                                          List.of(Deployment.Target.of("web", "dev"))),
                                      clock.millis()),
                              new DeployStep.Conflicted("web", Deployment.Cause.MERGE),
                              clock.millis())));
      case ASK ->
          changed(() -> state.updateTask("ABC-2", task -> task.withReport(AgentReport.QUESTION)));
      case ANSWER ->
          changed(() -> state.updateTask("ABC-2", task -> task.withReport(AgentReport.PLAIN)));
      case WORKED ->
          changed(() ->
              state.updateTask(
                  "ABC-3",
                  task ->
                      state
                          .reducer()
                          .deployStepped(
                              task, new DeployStep.Fixed("web", "sha"), clock.millis())));
      case SECOND -> clock.advance(Duration.ofSeconds(7));
      case MINUTE -> clock.advance(Duration.ofMinutes(3));
      case HOUR -> clock.advance(Duration.ofHours(5));
      case DAY -> clock.advance(Duration.ofHours(25));
      default -> restart();
    }
    recorded();
  }

  private void changed(Runnable change) {
    change.run();
    version++;
  }

  private void askAfterStart(StateService state) {
    if (restartAsked) {
      return;
    }
    restartAsked = true;
    jobs.restartAsk().runOn(state.tasks());
  }

  private void handConflict(StateService state) {
    if (state.task("ABC-3").flatMap(TaskState::currentConflict).isPresent()) {
      jobs.conflicts().waits("ABC-3", root.resolve("wt3"), new Progress(1, 0, "edit" + edits));
    }
  }

  private void handedBack(StateService state) {
    try {
      Files.writeString(root.resolve("wt5").resolve(Artifact.NOTES.fileName()), "round " + version);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    state.updateTask(
        "ABC-5",
        task -> state.reducer().relayed(task, "round", "back" + version, "", clock.millis()));
  }

  private void touchSessionLog() {
    try {
      Files.setLastModifiedTime(sessionLog, FileTime.fromMillis(clock.millis() + 1_000));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void restart() {
    clock.advance(Duration.ofSeconds(90));
    starts++;
    restartAsked = false;
    jobs = rebuilt(stateService().withRoot(root).build());
  }

  private void recorded() {
    List<dev.jagt.orchestrator.port.channel.ChannelMessage> messages = driver.delivered();
    messages
        .subList(delivered, messages.size())
        .forEach(message ->
            sent.add(new Told(String.valueOf(message.taskId()), message.point(), version)));
    delivered = messages.size();
    List<String> asked = assistants.reviewed();
    asked.subList(reviewed, asked.size()).stream()
        .distinct()
        .forEach(worktree ->
            sent.add(new Told("ABC-" + worktree.substring(2), "master-review", version)));
    reviewed = asked.size();
    List<FakeSessionHost.Typed> lines = host.typed();
    lines
        .subList(typed, lines.size())
        .forEach(line -> said.add(new Said(line.taskId(), starts, version, line.message())));
    typed = lines.size();
  }

  public List<Told> sent() {
    return List.copyOf(sent);
  }

  public List<Said> said() {
    return List.copyOf(said);
  }

  @Override
  public void close() {
    retries.shutdownNow();
    try (Stream<Path> files = Files.walk(root)) {
      files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
