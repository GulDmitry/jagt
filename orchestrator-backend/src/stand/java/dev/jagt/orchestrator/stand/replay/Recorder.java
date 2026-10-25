package dev.jagt.orchestrator.stand.replay;

import dev.jagt.orchestrator.adapter.replay.RecordingLine;
import dev.jagt.orchestrator.adapter.replay.Recording;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.stand.StandShell;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.task.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A run's model moves, read from what jagt already wrote: turns, session calls and read answers from the backend
 * log; verdicts (a restamp of one read once) and the session's files for jagt from the worktree snapshots, each placed before the first report
 * after it was seen, less one poll. The code the session pushed comes from its branch in origin.git, placed before
 * its first hand-back and its first report naming a request.
 */
public final class Recorder {

    public static final String FILE = "recording.jsonl";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String STATUS = "update_agent_status";
    private static final String ASKED = "- asked: ";
    private static final String DECIDED = " — decided: ";

    private record Timed(Instant at, RecordingLine line) {
    }

    private Recorder() {
    }

    public static Path write(Path root, Duration poll) {
        Path file = root.resolve(FILE);
        Recording.write(file, record(root, poll));
        return file;
    }

    public static List<RecordingLine> record(Path root, Duration poll) {
        List<Timed> timed = new ArrayList<>();
        for (BackendEvents.Event event : BackendEvents.read(root)) {
            moveOf(event).ifPresent(line -> timed.add(new Timed(Instant.parse(event.field("@timestamp")), line)));
        }
        List<JsonNode> snapshots = snapshots(root.resolve(WorktreeSnapshots.FILE));
        snapshots.stream().filter(snapshot -> snapshot.has("content")).filter(snapshot -> !decisions(snapshot))
                .forEach(snapshot -> wrote(timed, snapshot, poll));
        List<RecordingLine> lines = new ArrayList<>(timed.stream().map(Timed::line).toList());
        lines.stream().filter(RecordingLine.Move.class::isInstance).map(line -> ((RecordingLine.Move) line).task()).distinct()
                .toList().forEach(task -> pushed(root.resolve("origin.git"), task, lines));
        Map<String, JsonNode> read = new LinkedHashMap<>();
        snapshots.stream().filter(snapshot -> snapshot.has("review"))
                .filter(snapshot -> !snapshot.path("review").path("reads").equals(
                        read.put(snapshot.path("task").asString(), snapshot.path("review").path("reads"))))
                .forEach(snapshot -> judged(snapshot, lines));
        answered(snapshots, lines);
        return lines;
    }

    private static boolean decisions(JsonNode snapshot) {
        return snapshot.path("file").asString("").equals(Artifact.DECISIONS.fileName());
    }

    private static void answered(List<JsonNode> snapshots, List<RecordingLine> lines) {
        Map<String, Integer> seen = new HashMap<>();
        snapshots.stream().filter(Recorder::decisions).forEach(snapshot -> {
            List<String> written = snapshot.path("content").asString().lines().toList();
            written.subList(seen.getOrDefault(snapshot.path("task").asString(), 0), written.size()).stream()
                    .filter(line -> line.startsWith(ASKED)).forEach(line -> {
                        String[] parts = line.substring(ASKED.length()).split(DECIDED, 2);
                        lines.add(new RecordingLine.Answered(parts[0], parts.length > 1 ? parts[1] : ""));
                    });
            seen.put(snapshot.path("task").asString(), written.size());
        });
    }

    private static Optional<RecordingLine> moveOf(BackendEvents.Event event) {
        if (event.is("turn opened")) {
            return Optional.of(new RecordingLine.Opened(event.field("task"), event.number("turn"), event.field("step")));
        }
        if (event.is("mcp tool called")) {
            return Optional.of(new RecordingLine.Called(event.field("task"), event.number("turn"), event.field("tool"),
                    JSON.readTree(event.field("arguments"))));
        }
        if (event.is("assistant read answered")) {
            return Optional.of(new RecordingLine.Read(event.field("kind"), event.field("ref"),
                    JSON.readTree(event.field("answer"))));
        }
        return Optional.empty();
    }

    private static void wrote(List<Timed> timed, JsonNode snapshot, Duration poll) {
        String task = snapshot.path("task").asString();
        Instant written = Instant.parse(snapshot.path("at").asString()).minus(poll);
        for (int i = 0; i < timed.size(); i++) {
            if (timed.get(i).line() instanceof RecordingLine.Called called && reports(called, task)
                    && timed.get(i).at().isAfter(written)) {
                timed.add(i, new Timed(written, new RecordingLine.Wrote(task, called.turn(),
                        Map.of(snapshot.path("file").asString(), snapshot.path("content").asString()))));
                return;
            }
        }
    }

    private static void pushed(Path origin, String task, List<RecordingLine> lines) {
        if (!Files.isDirectory(origin) || !StandShell.gitSucceeds(origin, "rev-parse", "--verify", "refs/heads/" + task)) {
            return;
        }
        List<String> paths = StandShell.git(origin, "diff", "--name-only", RunRecord.BASE + "..." + task).lines()
                .filter(path -> !path.isBlank()).toList();
        Map<String, String> files = new LinkedHashMap<>();
        paths.forEach(path -> files.put(path, StandShell.git(origin, "show", task + ":" + path)));
        String message = StandShell.git(origin, "log", "-1", "--format=%s", task).strip();
        before(lines, task, arguments -> arguments.path("status").asString("").equals("REVIEW_PENDING"),
                turn -> new RecordingLine.Wrote(task, turn, files));
        before(lines, task, arguments -> !arguments.path("reviewRequestUrl").asString("").isBlank(),
                turn -> new RecordingLine.Pushed(task, turn, message, paths));
    }

    private static void before(List<RecordingLine> lines, String task, Predicate<JsonNode> said, LongFunction<RecordingLine> move) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) instanceof RecordingLine.Called called && reports(called, task) && said.test(called.arguments())) {
                lines.add(i, move.apply(called.turn()));
                return;
            }
        }
    }

    private static boolean reports(RecordingLine.Called called, String task) {
        return called.task().equals(task) && called.tool().equals(STATUS);
    }

    private static void judged(JsonNode snapshot, List<RecordingLine> lines) {
        JsonNode review = snapshot.path("review");
        review.path("reads").forEach(read -> lines.add(new RecordingLine.Judged(review.path("round").asLong(),
                read.path("role").asString(""), JSON.treeToValue(read.path("judgement"), Judgement.class))));
    }

    private static List<JsonNode> snapshots(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file).stream().filter(line -> !line.isBlank()).map(JSON::readTree).toList();
        } catch (IOException e) {
            throw new StandFailure("cannot read " + file, e);
        }
    }
}
