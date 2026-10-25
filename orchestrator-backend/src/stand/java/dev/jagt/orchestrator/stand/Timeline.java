package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.stand.invariant.TimelineFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class Timeline {

    private final Path file;
    private final ContextRelays relays;
    private final List<String> lines = new ArrayList<>();
    private final Map<String, String> lastSeen = new HashMap<>();
    private String lastStatus = "none";
    private String lastOwner = "none";
    private String lastReport = "";
    private int relaysSeen;
    private int masterReadsSeen;
    private final Set<String> stepsRelayed = new HashSet<>();
    private String windows = "";
    private Instant lastChange = Instant.now();
    private @Nullable Stop stop;

    public record Stop(String trigger, String evidence) {
    }

    public Timeline(Path file, ContextRelays relays) {
        this.file = file;
        this.relays = relays;
    }

    public void record(JsonNode state, JsonNode board) {
        Map<String, String> owners = new HashMap<>();
        board.path("tasks").forEach(task -> owners.put(task.path("id").asString(), task.path("owner").asString()));
        state.path("tasks").properties().forEach(task -> record(task.getKey(), task.getValue(), owners));
    }

    public void masterReads(int total) {
        if (total > masterReadsSeen) {
            note("master read");
        }
        masterReadsSeen = total;
    }

    public void windows(String names) {
        if (!names.equals(windows)) {
            windows = names;
            note("tmux windows " + names);
        }
    }

    public boolean stalledFor(Duration quiet) {
        return Instant.now().isAfter(lastChange.plus(quiet));
    }

    public void note(String event) {
        append(Instant.now().truncatedTo(ChronoUnit.SECONDS) + " stand " + event);
    }

    public String lastStatus() {
        return lastStatus;
    }

    public String lastReport() {
        return lastReport;
    }

    public String lastOwner() {
        return lastOwner;
    }

    public boolean relayed(String step) {
        return stepsRelayed.contains(step);
    }

    public int relaysSeen() {
        return relaysSeen;
    }

    public List<String> tail(int count) {
        return List.copyOf(lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }

    public Optional<Stop> stop() {
        return Optional.ofNullable(stop);
    }

    private void stopped(String id, JsonNode found) {
        Stop seen = new Stop(found.path("trigger").asString(), found.path("evidence").asString());
        if (!seen.equals(stop)) {
            stop = seen;
            note("task " + id + " stopped, human needed: " + seen.trigger() + ": " + seen.evidence());
        }
    }

    private void record(String id, JsonNode task, Map<String, String> owners) {
        JsonNode found = task.path("stop");
        if (found.isObject()) {
            stopped(id, found);
        }
        lastStatus = task.path("status").asString();
        lastReport = task.path("report").asString("");
        lastOwner = owners.getOrDefault(id, "?");
        relays.changed(id, task).ifPresent(steps -> relayed(id, steps));
        String entry = id + " " + lastStatus + " owner=" + lastOwner + " "
                + task.path("message").asString("").replaceAll("\\s+", " ").strip();
        if (!entry.equals(lastSeen.put(id, entry))) {
            append(Instant.now().truncatedTo(ChronoUnit.SECONDS) + " " + entry);
        }
    }

    private void relayed(String id, List<String> steps) {
        if (steps.isEmpty()) {
            note("context of " + id + " changed, no brief step recognised");
            return;
        }
        relaysSeen++;
        stepsRelayed.addAll(steps);
        note(TimelineFile.relayLine(id, steps));
    }

    private void append(String line) {
        lastChange = Instant.now();
        lines.add(line);
        try {
            Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new StandFailure("cannot write " + file, e);
        }
    }
}
