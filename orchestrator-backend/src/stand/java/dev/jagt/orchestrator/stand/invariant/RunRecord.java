package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record RunRecord(Map<String, List<Step>> histories, List<Miss> misses, Timeline timeline,
                        Map<String, List<RefUpdate>> origin) {

    public static final String BASE = "main";
    public static final String DEPLOY = "dev";

    public record Step(TaskStatus status, Instant at, String origin) {
    }

    public record Miss(Instant at, String task, String step, String status, String line) {
    }

    public record Sample(Instant at, String task, TaskStatus status, String line) {
    }

    public record Relay(Instant at, String task, List<String> steps, @Nullable TaskStatus status, String line) {
    }

    public record RefUpdate(String old, String now, Instant at, String line) {

        public boolean creates() {
            return old.chars().allMatch(c -> c == '0');
        }
    }

    public record Timeline(List<Sample> samples, List<Relay> relays, Set<String> standWrites) {
    }

    public static RunRecord read(Path root) {
        Timeline sampled = TimelineFile.read(root.resolve("timeline.log"));
        Timeline timeline = new Timeline(sampled.samples(), BackendLog.relays(root.resolve("jagt-backend.log")),
                sampled.standWrites());
        return new RunRecord(StateFiles.histories(root.resolve("state.json")), StateFiles.misses(root.resolve("misses.jsonl")),
                timeline, Map.of(BASE, Reflog.read(root.resolve("origin.git"), BASE),
                        DEPLOY, Reflog.read(root.resolve("origin.git"), DEPLOY)));
    }

    public List<Step> history(String task) {
        return histories.getOrDefault(task, List.of());
    }

    public TaskStatus statusAt(String task, Instant at) {
        TaskStatus status = TaskStatus.NEW;
        for (Step step : history(task)) {
            if (step.at().isAfter(at)) {
                break;
            }
            status = step.status();
        }
        return status;
    }
}
