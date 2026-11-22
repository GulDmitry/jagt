package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.stand.invariant.Violation;
import dev.jagt.orchestrator.stand.replay.ReplayLog;
import dev.jagt.orchestrator.stand.replay.WorktreeSnapshots;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.opentest4j.TestAbortedException;
import tools.jackson.databind.JsonNode;

public final class StandRun implements AutoCloseable {

  private static final Duration PIPELINE = Duration.ofSeconds(1);
  private static final List<String> KICKED =
      List.of(
          "verify",
          "master-review",
          "master-answer",
          "master-repo",
          "autoreview",
          "master-deploy",
          "deploy-check",
          "acceptance-check",
          "deploy-conflict");
  private static final Duration STALL = Duration.ofMinutes(15);

  private final Path root;
  private final StandMode mode;
  private final StandProject project;
  private final StandHost host;
  private final @Nullable Path replayed;
  private final WorktreeSnapshots snapshots;
  private final Instant began = StandClock.now();
  private final Optional<Process> awake;
  private final StandTeardown teardown;
  private final Duration poll;
  private JagtEngine jagt;
  private StandMarker marker;
  private Instant polled = StandClock.now();
  private @Nullable Duration slept;
  private @Nullable StandOutcome last;
  private boolean resting;
  private boolean sweepsWaiting;
  private @Nullable RunEnd end;

  StandRun(
      StandSite site,
      StandMarker marker,
      StandMode mode,
      JagtEngine jagt,
      @Nullable Path replayed,
      StandTeardown teardown,
      Duration poll) {
    this.root = site.root();
    this.project = site.project();
    this.host = site.host();
    this.awake = site.awake();
    this.replayed = replayed;
    this.snapshots = new WorktreeSnapshots(root);
    this.marker = marker;
    this.mode = mode;
    this.jagt = jagt;
    this.teardown = teardown;
    this.poll = poll;
  }

  public static StandRun start(String scenario, StandMode mode) throws IOException {
    return StandLauncher.standard().start(scenario, mode, null);
  }

  /**
   * jagt as built, its sessions, reads and Master answered from {@code recording} instead of a
   * model.
   */
  public static StandRun replay(String scenario, StandMode mode, Path recording)
      throws IOException {
    return StandLauncher.standard().start(scenario, mode, recording);
  }

  void restartJagt() {
    restartJagt(Restart.JVM);
  }

  void restartJagt(Restart how) {
    jagt = jagt.restart(how);
    marker = marker.withJagt(jagt.pid(), jagt.port()).writeTo(root);
  }

  String windows() {
    return jagt.windows();
  }

  public Path root() {
    return root;
  }

  public StandStore store() {
    return host.store();
  }

  public StandProject project() {
    return project;
  }

  public String statusLabel(String task) {
    for (JsonNode card : jagt.board().tasks().path("tasks")) {
      if (card.path("id").asString().equals(task)) {
        return card.path("statusLabel").asString();
      }
    }
    return "";
  }

  public String acceptanceReport() throws IOException {
    for (var task : jagt.board().state().path("tasks").properties()) {
      String worktree = task.getValue().path("repos").path(0).path("worktreePath").asString("");
      if (!worktree.isEmpty()) {
        return Files.readString(Path.of(worktree).resolve("acceptance-report.md"));
      }
    }
    return "";
  }

  public RunRecord record() {
    jagt.harvest();
    return RunRecord.read(root);
  }

  public ReplayLog replayLog() {
    return ReplayLog.read(root);
  }

  public List<Violation> violations() {
    jagt.harvest();
    return StandBooks.at(root).violations();
  }

  public StandOutcome drive(Script script) {
    Timeline timeline =
        new Timeline(root.resolve("timeline.log"), new ContextRelays(StandBooks.at(root).brief()));
    final Pipelines pipelines = new Pipelines(host.store(), PIPELINE);
    final HumanStub human = new HumanStub(mode);
    end = new RunEnd(mode, script.expectsDeployConflict(), script.conflictStepComes());
    StandWorld world = new World(timeline, script.ref());
    List<WorldEvent> pending = new ArrayList<>(script.events());
    Instant start = StandClock.now();
    timeline.note(
        "open "
            + script.ref()
            + " answered "
            + jagt.board()
                .open(script.ref(), "proj", script.launchMode())
                .toString()
                .replaceAll("\\s+", " "));
    Instant deadline = start.plus(script.timeout());
    polled = StandClock.now();
    while (going(script, timeline, deadline, !pending.isEmpty())) {
      JsonNode board = tick(timeline);
      pipelines.passDue(timeline);
      pending.removeIf(event -> event.fire(world));
      human.take(board, jagt.board(), timeline);
      resting = human.nothingToPress(board);
    }
    Instant settled = StandClock.now().plus(script.settle());
    while (replayed == null
        && slept == null
        && timeline.lastStatus().equals(script.until())
        && StandClock.now().isBefore(settled)) {
      tick(timeline);
    }
    StandOutcome outcome = outcomeOf(script, timeline, start, pending.size());
    last = outcome;
    if (outcome.suspended()) {
      timeline.note("host suspended: " + outcome.detail());
      throw new TestAbortedException(
          "stand run " + marker.scenario() + " ended, " + outcome.describe());
    }
    return outcome;
  }

  private StandOutcome outcomeOf(Script script, Timeline timeline, Instant start, int unfired) {
    Optional<Timeline.Stop> stop = timeline.stop();
    StandOutcome.Ending ending =
        StandOutcome.Ending.of(
            timeline.lastStatus().equals(script.until()),
            slept != null,
            stop.isPresent(),
            held(timeline),
            Objects.requireNonNull(end).waitingOnTheReviewer(sweepsWaiting, timeline),
            timeline.stalledFor(STALL));
    String detail =
        switch (ending) {
          case SUSPENDED ->
              Objects.requireNonNull(slept).toSeconds()
                  + "s between two polls, the host was suspended";
          case STOPPED -> stop.get().trigger() + ": " + stop.get().evidence();
          default -> "";
        };
    return new StandOutcome(
        timeline.lastStatus(),
        timeline.lastOwner(),
        Duration.between(start, StandClock.now()),
        timeline.tail(20),
        root,
        ending,
        detail,
        new StandOutcome.Signals(
            stop.map(Timeline.Stop::trigger).orElse(""), timeline.lastReport(), unfired));
  }

  private boolean held(Timeline timeline) {
    return Objects.requireNonNull(end).held(timeline, resting);
  }

  private boolean going(Script script, Timeline timeline, Instant deadline, boolean eventsPending) {
    if (slept != null || timeline.stop().isPresent()) {
      return false;
    }
    if (replayed != null) {
      ReplayLog replay = ReplayLog.read(root);
      boolean done =
          replay.drained()
              && timeline.lastStatus().equals(script.until())
              && (!acceptanceRecorded() || statusLabel(script.ref()).equals("accepted"));
      return replay.divergences().isEmpty() && !done && StandClock.now().isBefore(deadline);
    }
    return !Objects.requireNonNull(end)
            .reached(timeline.lastStatus().equals(script.until()), eventsPending, timeline)
        && StandClock.now().isBefore(deadline)
        && !timeline.stalledFor(STALL)
        && !held(timeline)
        && !end.waitingOnTheReviewer(sweepsWaiting, timeline);
  }

  private boolean acceptanceRecorded() {
    try {
      return Files.readString(replayed).contains("\"kind\":\"ACCEPTANCE_CHECK\"");
    } catch (IOException e) {
      throw new StandFailure("cannot read the recording " + replayed, e);
    }
  }

  private JsonNode tick(Timeline timeline) {
    JagtProcess.pause(poll);
    Instant now = StandClock.now();
    if (HostSleep.between(polled, now)) {
      slept = Duration.between(polled, now);
    }
    polled = now;
    KICKED.forEach(jagt.board()::run);
    JsonNode board = jagt.board().tasks();
    JsonNode state = jagt.board().state();
    timeline.record(state, board);
    snapshots.take(state);
    timeline.windows(jagt.windows());
    BackendSignals signals = BackendSignals.read(root.resolve("jagt-backend.log"));
    timeline.masterReads(signals.masterReads());
    sweepsWaiting = signals.waitingOnTheReviewer();
    return board;
  }

  public void passed() {
    marker = marker.withOutcome(StandMarker.PASSED);
  }

  @Override
  public void close() throws IOException {
    try {
      shutDown();
    } finally {
      teardown.release();
    }
  }

  private void shutDown() throws IOException {
    Files.writeString(root.resolve("screens.log"), jagt.screens());
    jagt.close();
    host.close();
    awake.ifPresent(Process::destroy);
    StandShell.killTmux(root);
    teardown.keep(root, marker, last, replayed != null, began);
  }

  private final class World implements StandWorld {

    private final Timeline timeline;
    private final String task;

    private World(Timeline timeline, String task) {
      this.timeline = timeline;
      this.task = task;
    }

    @Override
    public StandStore store() {
      return host.store();
    }

    @Override
    public StandProject project() {
      return project;
    }

    @Override
    public Path scratch(String name) {
      return root.resolve(name);
    }

    @Override
    public void note(String event) {
      timeline.note(event);
    }

    @Override
    public int relaysSeen() {
      return timeline.relaysSeen();
    }

    @Override
    public void say(String line) {
      jagt.board().say(task, line);
    }

    @Override
    public void restart(Restart how) {
      restartJagt(how);
    }
  }
}
