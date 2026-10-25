package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.stand.invariant.Violation;
import dev.jagt.orchestrator.stand.replay.Recorder;
import dev.jagt.orchestrator.stand.replay.ReplayLog;
import dev.jagt.orchestrator.stand.replay.WorktreeSnapshots;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class StandRun implements AutoCloseable {

    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final Duration POLL = Duration.ofSeconds(5);
    private static final Duration REPLAY_POLL = Duration.ofSeconds(1);
    private static final Duration PIPELINE = Duration.ofSeconds(20);
    private static final Duration REPLAY_PIPELINE = Duration.ofSeconds(1);
    private static final List<String> REPLAY_KICKED = List.of("verify", "master-review", "master-answer", "master-repo",
            "autoreview", "master-deploy", "deploy-check", "deploy-conflict");
    private static final Duration STALL = Duration.ofMinutes(15);
    private static final Duration HELD = Duration.ofMinutes(10);

    private final Path root;
    private final StandMode mode;
    private final StandProject project;
    private final StandHost host;
    private final @Nullable Path replayed;
    private final WorktreeSnapshots snapshots;
    private final Instant began = Instant.now();
    private JagtProcess jagt;
    private StandMarker marker;

    private StandRun(Path root, StandMarker marker, StandMode mode, StandProject project, StandHost host, JagtProcess jagt,
                     @Nullable Path replayed) {
        this.root = root;
        this.replayed = replayed;
        this.snapshots = new WorktreeSnapshots(root);
        this.marker = marker;
        this.mode = mode;
        this.project = project;
        this.host = host;
        this.jagt = jagt;
    }

    public static StandRun start(String scenario, StandMode mode) throws IOException {
        return start(scenario, mode, null);
    }

    /** jagt as built, its sessions, reads and Master answered from {@code recording} instead of a model. */
    public static StandRun replay(String scenario, StandMode mode, Path recording) throws IOException {
        return start(scenario, mode, recording);
    }

    private static StandRun start(String scenario, StandMode mode, @Nullable Path replayed) throws IOException {
        Path root = newRoot();
        StandMarker marker = StandMarker.started(scenario + "/" + mode.id()).writeTo(root);
        Files.createDirectories(root.resolve("tmux"));
        StandProject project = StandProject.create(root);
        StandHost host = new StandHost(root.resolve("host")).start();
        try {
            StandConfig.write(root, project.repo(), host.mcpUrl(), root.getFileName().toString(), mode, replayed);
            JagtProcess jagt = JagtProcess.start(root);
            return new StandRun(root, marker.withJagt(jagt.pid(), jagt.port()).writeTo(root), mode, project, host, jagt,
                    replayed);
        } catch (RuntimeException e) {
            host.close();
            StandShell.killTmux(root);
            marker.withOutcome(StandMarker.FAILED).writeTo(root);
            throw new StandFailure("stand run " + scenario + " did not start; kept " + root, e);
        }
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

    public RunRecord record() {
        return RunRecord.read(root);
    }

    public ReplayLog replayLog() {
        return ReplayLog.read(root);
    }

    public List<Violation> violations() {
        return new StandBooks(root).violations();
    }

    public StandOutcome drive(Script script) {
        Timeline timeline = new Timeline(root.resolve("timeline.log"), new ContextRelays(new StandBooks(root).brief()));
        Pipelines pipelines = new Pipelines(host.store(), replayed == null ? PIPELINE : REPLAY_PIPELINE);
        HumanStub human = new HumanStub(mode);
        StandWorld world = new World(timeline, script.ref());
        List<WorldEvent> pending = new ArrayList<>(script.events());
        Instant start = Instant.now();
        timeline.note("open " + script.ref() + " answered "
                + jagt.board().open(script.ref(), "proj", script.launchMode()).toString().replaceAll("\\s+", " "));
        Instant deadline = start.plus(script.timeout());
        while (going(script, timeline, deadline)) {
            JsonNode board = tick(timeline);
            pipelines.passDue(timeline);
            pending.removeIf(event -> event.fire(world));
            human.take(board, jagt.board(), timeline);
        }
        Instant settled = Instant.now().plus(script.settle());
        while (replayed == null && timeline.lastStatus().equals(script.until()) && Instant.now().isBefore(settled)) {
            tick(timeline);
        }
        return new StandOutcome(timeline.lastStatus(), timeline.lastOwner(), Duration.between(start, Instant.now()),
                timeline.tail(20), root);
    }

    private boolean going(Script script, Timeline timeline, Instant deadline) {
        if (replayed != null) {
            ReplayLog replay = ReplayLog.read(root);
            boolean done = replay.drained() && timeline.lastStatus().equals(script.until());
            return replay.divergences().isEmpty() && !done && Instant.now().isBefore(deadline);
        }
        boolean held = timeline.lastOwner().equals("YOU") && timeline.stalledFor(HELD);
        return !timeline.lastStatus().equals(script.until()) && Instant.now().isBefore(deadline)
                && !timeline.stalledFor(STALL) && !held;
    }

    private JsonNode tick(Timeline timeline) {
        JagtProcess.pause(replayed == null ? POLL : REPLAY_POLL);
        if (replayed != null) {
            REPLAY_KICKED.forEach(jagt.board()::run);
        }
        JsonNode board = jagt.board().tasks();
        JsonNode state = jagt.board().state();
        timeline.record(state, board);
        snapshots.take(state);
        timeline.windows(StandShell.windows(root));
        return board;
    }

    public void passed() {
        marker = marker.withOutcome(StandMarker.PASSED);
    }

    @Override
    public void close() throws IOException {
        jagt.close();
        host.close();
        Files.writeString(root.resolve("screens.log"), StandShell.screens(root));
        StandShell.killTmux(root);
        printUsage();
        keepRecording();
        StandReport.append(marker.scenario(), marker.outcome().equals(StandMarker.PASSED));
        keepRunRecord();
        if (!marker.outcome().equals(StandMarker.PASSED)) {
            marker.withOutcome(StandMarker.FAILED).writeTo(root);
            System.out.println("stand run " + marker.scenario() + " failed; evidence: "
                    + StandEvidence.keep(root, marker.scenario(), StandEvidence.DIR).toAbsolutePath());
        }
        StandShell.deleteRoot(root);
        StandTraces.ofTheHuman(root.getParent()).remove(root.getFileName().toString()::equals);
    }

    private void keepRunRecord() {
        List<String> found;
        try {
            found = violations().stream().map(Violation::toString).toList();
        } catch (RuntimeException e) {
            found = List.of("invariants unreadable: " + e);
        }
        String[] named = marker.scenario().split("/", 2);
        StandRunLog.keep(StandRunLog.DIR, new StandRunLog.Entry(Instant.now(), named[0], named.length > 1 ? named[1] : "",
                replayed == null ? "live" : "replay", marker.outcome().equals(StandMarker.PASSED), found,
                Duration.between(began, Instant.now()).toSeconds(),
                StandSpend.read(root)), root.getFileName().toString());
    }

    private void keepRecording() {
        if (replayed != null) {
            return;
        }
        try {
            Path kept = Files.createDirectories(StandRecordings.DIR)
                    .resolve(marker.scenario().replace('/', '-') + "-" + root.getFileName() + ".jsonl");
            Files.copy(Recorder.write(root, POLL), kept, StandardCopyOption.REPLACE_EXISTING);
            StandRecordings.fold(StandRecordings.DIR);
            System.out.println("stand run " + marker.scenario() + " recorded: " + kept.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            System.out.println("stand run " + marker.scenario() + " not recorded: " + e);
        }
    }

    private void printUsage() {
        Path usage = root.resolve("usage-by-day.jsonl");
        try {
            String spent = Files.isRegularFile(usage) ? Files.readString(usage).strip() : "none recorded";
            System.out.println("stand run " + marker.scenario() + " usage: " + spent);
        } catch (IOException e) {
            System.out.println("stand run " + marker.scenario() + " usage unreadable: " + e.getMessage());
        }
    }

    private static Path newRoot() throws IOException {
        Path base = Path.of(System.getProperty("stand.base", System.getProperty("java.io.tmpdir")));
        String prefix = StandRoots.PREFIX + ProcessHandle.current().pid() + "-";
        Path root = base.resolve(prefix + RUNS.incrementAndGet());
        while (Files.exists(root)) {
            root = base.resolve(prefix + RUNS.incrementAndGet());
        }
        return Files.createDirectories(root).toRealPath();
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
        public void restart() {
            jagt.close();
            jagt = JagtProcess.start(root);
            marker = marker.withJagt(jagt.pid(), jagt.port()).writeTo(root);
        }
    }
}
