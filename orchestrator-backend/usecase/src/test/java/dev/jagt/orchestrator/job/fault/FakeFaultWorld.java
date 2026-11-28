package dev.jagt.orchestrator.job.fault;

import static dev.jagt.orchestrator.job.TestAgentStatusReportsBuilder.agentStatusReports;
import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.job.master.TestMasterDeployJobBuilder.masterDeployJob;
import static dev.jagt.orchestrator.job.session.TestWatchdogServiceBuilder.watchdogService;
import static dev.jagt.orchestrator.service.TestConfigServiceBuilder.configService;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.master.MasterDeployJob;
import dev.jagt.orchestrator.job.session.SilentWake;
import dev.jagt.orchestrator.job.session.WatchdogService;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.service.RoundOutcome;
import dev.jagt.orchestrator.service.RoundRecord;
import dev.jagt.orchestrator.service.RoundRelay;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.ReviewReader;
import dev.jagt.orchestrator.service.deploy.InfraRetries;
import dev.jagt.orchestrator.service.presence.SessionProbe;
import dev.jagt.orchestrator.service.report.AgentStatusReports;
import dev.jagt.orchestrator.service.review.ReviewRound;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeAgentRuntime;
import dev.jagt.orchestrator.testing.FakeClock;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

/**
 * One task over a state file, the real jobs and fact writers that move it, and every port they
 * reach faked: a schedule of faults is played as written, a tick running each job once, and a
 * restart rebuilds every service over the same file. A port asked for what no schedule set up
 * throws {@link UnscriptedCall}.
 */
public final class FakeFaultWorld implements AutoCloseable {

  public enum Event {
    TICK,
    QUESTION,
    MASTER_RETRY,
    PUSH,
    SILENT,
    DIES,
    WINDOWS_UNREADABLE,
    GREEN,
    RED,
    SLOW,
    FLAKY,
    UNREAD,
    LATE,
    RESTART;

    public static Event named(String spelled) {
      return valueOf(spelled.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static List<Event> faults() {
      return Stream.of(values()).filter(event -> event != TICK).toList();
    }

    public String spelled() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  public record Case(String name, long seed, List<Event> events, String pinned) {

    @Override
    public String toString() {
      return name
          + " (seed "
          + seed
          + ": "
          + String.join(", ", events.stream().map(Event::spelled).toList())
          + ")";
    }
  }

  public static final class UnscriptedCall extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    UnscriptedCall(String port, String method) {
      super(port + "." + method + " was asked, and no schedule answers it");
    }
  }

  private record Services(
      StateService state,
      AgentStatusReports reports,
      FlowReports flow,
      ReviewRound round,
      RoundOutcome outcome,
      WatchdogService watchdog,
      MasterDeployJob deploy) {}

  private record Read(Answer<ReviewFacts> facts, String tip, Generation at) {}

  private record Unscripted(String port) implements org.mockito.stubbing.Answer<Object> {

    @Override
    public Object answer(InvocationOnMock call) throws Throwable {
      if (call.getMethod().getDeclaringClass() == Object.class) {
        return RETURNS_DEFAULTS.answer(call);
      }
      throw new UnscriptedCall(port, call.getMethod().getName());
    }
  }

  private static final String TASK = "ABC-1";
  private static final String PROJECT = "proj";
  private static final String REQUEST = "http://mr/1";
  private static final int SETTLING_TICKS = 6;

  private final Path root;
  private final FakeClock clock = new FakeClock();
  private final long startedAt;
  private final Duration tick;
  private final Deque<String> pipelines = new ArrayDeque<>(List.of("running"));
  private final List<String> trace = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private final List<String> presses = new ArrayList<>();
  private final CodeHost codeHost =
      mock(CodeHost.class, withSettings().stubOnly().defaultAnswer(new Unscripted("code host")));
  private final ReviewReader reader =
      mock(
          ReviewReader.class,
          withSettings().stubOnly().defaultAnswer(new Unscripted("review reader")));
  private final SessionProbe probe =
      mock(
          SessionProbe.class,
          withSettings().stubOnly().defaultAnswer(new Unscripted("session probe")));
  private final CommandService commands =
      mock(
          CommandService.class,
          withSettings().stubOnly().defaultAnswer(new Unscripted("commands")));
  private final WorktreeChanges worktrees =
      mock(WorktreeChanges.class, withSettings().stubOnly().defaultAnswer(new Unscripted("git")));
  private final RoundRelay relay =
      mock(
          RoundRelay.class,
          withSettings().stubOnly().defaultAnswer(new Unscripted("session relay")));
  private boolean readable = true;
  private Session.Window.State window = Session.Window.State.RUNNING;
  private long quietSince;
  private boolean windowsUnreadable;
  private String tip = "c0ffee";
  private @Nullable Read late;
  private Services services;

  public FakeFaultWorld(long seed) {
    clock.advance(Duration.ofMinutes(Math.floorMod(seed, 1_440)));
    startedAt = clock.millis();
    tick = Duration.ofSeconds(120 + new SplittableRandom(seed).nextInt(120));
    try {
      root = Files.createTempDirectory("jagt-faults");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    scripted();
    StateService state = stateService().withRoot(root).build();
    state.putTask(
        TASK,
        TaskState.builder(PROJECT, root.resolve("wt").toString(), TaskStatus.CI_POLLING)
            .mrUrl(REQUEST)
            .autoReview(true)
            .lastActiveTimestamp(startedAt)
            .build());
    services = built(state);
  }

  public static List<Case> cases() {
    try (InputStream in =
        Objects.requireNonNull(FakeFaultWorld.class.getResourceAsStream("/fault-schedules.txt"))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(line -> line.split("\\|"))
          .map(cells ->
              new Case(
                  cells[0].strip(),
                  Long.parseLong(cells[1].strip()),
                  Stream.of(cells[2].split(",")).map(Event::named).toList(),
                  cells[3].strip()))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void scripted() {
    doAnswer(call -> new Answer<>(Optional.empty(), TokenUsage.NONE))
        .when(codeHost)
        .requestFingerprint(anyString(), anyString());
    doAnswer(call -> new Answer<>(Optional.of(List.of()), TokenUsage.NONE))
        .when(codeHost)
        .unresolvedNotes(anyString(), anyString());
    doAnswer(call -> tip).when(worktrees).pushedTips(any());
    doAnswer(call -> review()).when(reader).read(anyString(), anyString());
    doAnswer(call -> session(call.getArgument(1), call.getArgument(2)))
        .when(probe)
        .read(anyString(), any(), anyLong(), anyLong());
    doAnswer(call -> pressed(call.getArgument(1))).when(commands).execute(anyString(), any());
    doAnswer(
            call -> {
              trace.add("relayed " + call.getArgument(4));
              return new SweepResult(SweepResult.Kind.RELAYED, "relayed");
            })
        .when(relay)
        .relay(anyString(), any(), anyString(), any(), anyString(), anyString());
  }

  private Services built(StateService state) {
    FlowReports flow = flowReports().build(state, clock);
    ConfigService config;
    try {
      config =
          configService()
              .withRoot(root)
              .withYaml(
                  "orchestrator:\n"
                      + "  roles:\n"
                      + "    master: { mode: act, mine: [] }\n"
                      + "  projects:\n"
                      + "    proj: { path: "
                      + root
                      + ", baseBranch: origin/main }\n")
              .build(new FakeAgentRuntime(root.resolve("session.log")));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    TaskStops stops = taskStops().withRoot(root).build(state, clock);
    AgentStatusReports reports = agentStatusReports().build(state, flow, worktrees, config);
    InfraWaitProperties waits = InfraWaitProperties.defaults();
    RedCauses causes = new RedCauses(codeHost, new InfraRetries(state, stops, waits, clock), waits);
    return new Services(
        state,
        reports,
        flow,
        new ReviewRound(reader, codeHost),
        new RoundOutcome(new RoundRecord(state, stops, clock, causes), reports, relay),
        watchdogService().withRoot(root).build(state, probe, mock(SilentWake.class), clock),
        masterDeployJob().build(config, commands, worktrees, stops, state, clock));
  }

  public void run(List<Event> schedule) {
    schedule.forEach(this::play);
  }

  /** Each fault followed by a tick, then green checks and ticks enough for every step to land. */
  public void runSettling(List<Event> faults) {
    faults.forEach(fault -> {
      play(fault);
      play(Event.TICK);
    });
    play(Event.GREEN);
    Stream.generate(() -> Event.TICK).limit(SETTLING_TICKS).forEach(this::play);
  }

  private void play(Event event) {
    long now = clock.millis();
    switch (event) {
      case TICK -> ticked();
      case QUESTION -> {
        said("REVIEW_PENDING", "a job failed on a runner; retry it?", "question");
        quiet(now);
      }
      case MASTER_RETRY -> {
        TaskState read = task();
        services
            .flow()
            .reportWhileStanding(
                TASK,
                read,
                FlowRules.relayed(),
                AgentReport.PLAIN,
                "retry the failed job",
                opened -> services.state().reducer().relayed(opened, "answer", "retry", "", now));
        working();
      }
      case PUSH -> {
        tip = tip + "+";
        said("CI_POLLING", "pushed; waiting on the pipeline", null);
        working();
      }
      case SILENT -> quiet(now);
      case DIES -> {
        window = Session.Window.State.MISSING;
        quietSince = now;
      }
      case WINDOWS_UNREADABLE -> windowsUnreadable = true;
      case GREEN -> checks("success");
      case RED -> checks("failed");
      case SLOW -> checks("running");
      case FLAKY -> checks("failed", "success");
      case UNREAD -> readable = false;
      case LATE -> late = read();
      default -> services = built(stateService().withRoot(root).build());
    }
    trace.add(stamp(event.spelled()) + " -> " + standing());
  }

  private void said(String status, String message, @Nullable String outcome) {
    try {
      services
          .reports()
          .report(
              new AgentStatusMessage(
                  status,
                  message,
                  outcome,
                  new AgentStatusMessage.Request(REQUEST, Map.of()),
                  null,
                  null,
                  null,
                  Map.of()),
              TASK);
    } catch (Refusal refused) {
      trace.add(stamp("session") + " told: " + refused.getMessage());
    }
  }

  private void working() {
    window = Session.Window.State.RUNNING;
    quietSince = 0;
  }

  private void quiet(long now) {
    window = Session.Window.State.RUNNING;
    quietSince = now;
  }

  private void checks(String... reads) {
    readable = true;
    pipelines.clear();
    pipelines.addAll(List.of(reads));
  }

  private void ticked() {
    clock.advance(tick);
    job("auto-review", this::swept);
    job(services.watchdog().id(), services.watchdog()::run);
    Job deploy = services.deploy();
    job(deploy.id(), () -> services.deploy().runOn(services.state().tasks()));
  }

  private void job(String id, Runnable run) {
    try {
      run.run();
    } catch (SessionHost.WindowsUnreadable unknown) {
      trace.add(stamp(id) + " read no windows");
    } catch (RuntimeException e) {
      trace.add(stamp(id) + " failed: " + e);
      failures.add(id + " threw " + e + ", which no fault of the schedule threw");
    }
  }

  private void swept() {
    Read read = Optional.ofNullable(late).orElseGet(this::read);
    late = null;
    if (read.facts().facts().isEmpty()) {
      trace.add(stamp("auto-review") + " unread: " + read.facts().failure());
      return;
    }
    SweepResult result =
        services
            .outcome()
            .settle(TASK, PROJECT, REQUEST, read.facts().facts().get(), read.tip(), read.at());
    trace.add(stamp("auto-review") + " " + result.kind());
  }

  private Read read() {
    Generation at = Generation.of(Generation.Kind.HEAD, task());
    return new Read(services.round().read(TASK, PROJECT, REQUEST), tip, at);
  }

  private Answer<ReviewFacts> review() {
    if (!readable) {
      return Answer.failed(TokenUsage.NONE, "the host answered 502");
    }
    String status = pipelines.size() > 1 ? pipelines.removeFirst() : pipelines.getFirst();
    return new Answer<>(
        Optional.of(
            new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline(status, "", ""),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
        TokenUsage.NONE);
  }

  private SessionSign.Read session(TaskState task, long staleMs) {
    if (windowsUnreadable) {
      windowsUnreadable = false;
      throw new SessionHost.WindowsUnreadable("tmux answered nothing");
    }
    long now = clock.millis();
    long seen = quietSince == 0 ? now : quietSince;
    return new SessionSign.Read(
        window, seen, seen, task.standing().lastActiveTimestamp(), staleMs, null);
  }

  private String pressed(TaskAction action) {
    presses.add(action.id() + " on the arrival at " + task().standing().statusSince());
    trace.add(stamp("master-deploy") + " pressed " + action.id());
    return "pressed";
  }

  private TaskState task() {
    return services.state().task(TASK).orElseThrow();
  }

  private String stamp(String what) {
    return "+" + (clock.millis() - startedAt) / 1_000 + "s " + what;
  }

  private String standing() {
    TaskState task = task();
    TaskStop stop = task.work().stop();
    return task.standing().status()
        + " "
        + task.standing().report()
        + (stop == null ? "" : " stopped " + stop.trigger());
  }

  public String outcome() {
    if (presses.stream().anyMatch(press -> press.startsWith(TaskAction.DEPLOY.id() + " "))) {
      return "deploys";
    }
    TaskState task = task();
    TaskStop stop = task.work().stop();
    return stop != null ? "stops " + stop.trigger() : "stalls " + task.standing().status();
  }

  /** A verb pressed twice on one arrival of the task at its status was told twice. */
  public List<String> pressedTwice() {
    return presses.stream()
        .filter(press -> presses.indexOf(press) != presses.lastIndexOf(press))
        .distinct()
        .toList();
  }

  public List<String> failures() {
    return List.copyOf(failures);
  }

  public List<String> trace() {
    return List.copyOf(trace);
  }

  public String told(String schedule) {
    return TASK + " under " + schedule + ":\n  " + String.join("\n  ", trace);
  }

  @Override
  public void close() {
    // A fake whose answers reach back to it is never collected until it is cleared.
    Stream.of(codeHost, reader, probe, commands, worktrees, relay)
        .forEach(Mockito.framework()::clearInlineMock);
    try (Stream<Path> files = Files.walk(root)) {
      files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
