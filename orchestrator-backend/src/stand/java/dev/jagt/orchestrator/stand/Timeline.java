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
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class Timeline {

    private final Path file;
    private final ContextRelays relays;
    private final List<String> lines = new ArrayList<>();
    private final Map<String, String> lastSeen = new HashMap<>();
    private String lastStatus = "none";
    private String lastOwner = "none";
    private int relaysSeen;
    private String windows = "";
    private Instant lastChange = Instant.now();

    public Timeline(Path file, ContextRelays relays) {
        this.file = file;
        this.relays = relays;
    }

    public void record(JsonNode state, JsonNode board) {
        Map<String, String> owners = new HashMap<>();
        board.path("tasks").forEach(task -> owners.put(task.path("id").asString(), task.path("owner").asString()));
        state.path("tasks").properties().forEach(task -> record(task.getKey(), task.getValue(), owners));
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

    public String lastOwner() {
        return lastOwner;
    }

    public int relaysSeen() {
        return relaysSeen;
    }

    public List<String> tail(int count) {
        return List.copyOf(lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }

    private void record(String id, JsonNode task, Map<String, String> owners) {
        lastStatus = task.path("status").asString();
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
