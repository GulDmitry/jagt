package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public final class StandRun implements AutoCloseable {

    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final Duration POLL = Duration.ofSeconds(5);
    private static final Duration PIPELINE = Duration.ofSeconds(20);
    private static final Duration STALL = Duration.ofMinutes(15);

    private final Path root;
    private final StandProject project;
    private final StandHost host;
    private final JagtProcess jagt;
    private StandMarker marker;

    private StandRun(Path root, StandMarker marker, StandProject project, StandHost host, JagtProcess jagt) {
        this.root = root;
        this.marker = marker;
        this.project = project;
        this.host = host;
        this.jagt = jagt;
    }

    public static StandRun start(String scenario) throws IOException {
        Path root = newRoot();
        StandMarker marker = StandMarker.started(scenario).writeTo(root);
        Files.createDirectories(root.resolve("tmux"));
        StandProject project = StandProject.create(root);
        StandHost host = new StandHost(root.resolve("host")).start();
        try {
            StandConfig.write(root, project.repo(), host.mcpUrl(), root.getFileName().toString());
            JagtProcess jagt = JagtProcess.start(root);
            return new StandRun(root, marker.withJagt(jagt.pid(), jagt.port()).writeTo(root), project, host, jagt);
        } catch (RuntimeException e) {
            host.close();
            StandShell.killTmux(root);
            marker.withOutcome(StandMarker.FAILED).writeTo(root);
            throw new StandFailure("stand run " + scenario + " did not start; kept " + root, e);
        }
    }

    public StandStore store() {
        return host.store();
    }

    public StandProject project() {
        return project;
    }

    public StandOutcome drive(String ref, String until, Duration timeout, Duration settle) {
        Timeline timeline = new Timeline(root.resolve("timeline.log"));
        Pipelines pipelines = new Pipelines(host.store(), PIPELINE);
        Instant start = Instant.now();
        timeline.note("open " + ref + " answered " + jagt.board().open(ref, "proj").toString().replaceAll("\\s+", " "));
        Instant deadline = start.plus(timeout);
        while (!timeline.lastStatus().equals(until) && Instant.now().isBefore(deadline) && !timeline.stalledFor(STALL)) {
            tick(timeline);
            pipelines.passDue(timeline);
        }
        Instant settled = Instant.now().plus(settle);
        while (timeline.lastStatus().equals(until) && Instant.now().isBefore(settled)) {
            tick(timeline);
        }
        return new StandOutcome(timeline.lastStatus(), Duration.between(start, Instant.now()), timeline.tail(20), root);
    }

    private void tick(Timeline timeline) {
        JagtProcess.pause(POLL);
        timeline.record(jagt.board().state(), jagt.board().tasks());
        timeline.windows(StandShell.windows(root));
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
        if (marker.outcome().equals(StandMarker.PASSED)) {
            StandShell.deleteRoot(root);
            return;
        }
        marker.withOutcome(StandMarker.FAILED).writeTo(root);
        System.out.println("stand run " + marker.scenario() + " failed; kept " + root);
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
}
