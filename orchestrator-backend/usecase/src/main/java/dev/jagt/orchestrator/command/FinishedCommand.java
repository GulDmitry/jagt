package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.Guard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class FinishedCommand implements GlobalCommand {

    private static final Map<Owner, String> HOLDER = Map.of(Owner.YOU, "you", Owner.AGENT, "the agents",
            Owner.CI, "the code host");

    private final FinishedTasks finished;

    @Override
    public String id() {
        return "finished";
    }

    @Override
    public int rank() {
        return 170;
    }

    @Override
    public String hint() {
        return "what has been closed, how long each took, how many rounds it cost and who held it";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        return render(finished.all());
    }

    static String render(List<FinishedTask> records) {
        if (records.isEmpty()) {
            return "nothing finished yet — a task is recorded here when `done` retires it.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("finished (newest first):");
        for (FinishedTask task : records) {
            lines.add("  %-14s %-9s %-8s %s".formatted(task.id(), took(task),
                    task.rounds() + (task.rounds() == 1 ? " round" : " rounds"),
                    task.title() == null ? "" : task.title()));
        }
        String clock = clock(records);
        if (!clock.isEmpty()) {
            lines.add(clock);
        }
        Map<Guard, Integer> refused = new EnumMap<>(Guard.class);
        records.forEach(task -> task.refusals().forEach((guard, count) -> refused.merge(guard, count, Integer::sum)));
        if (!refused.isEmpty()) {
            lines.add("refused: " + refused.entrySet().stream()
                    .map(entry -> entry.getKey().label() + " " + entry.getValue()).collect(Collectors.joining(", ")));
        }
        return String.join("\n", lines);
    }

    private static String clock(List<FinishedTask> records) {
        long[] took = records.stream().mapToLong(FinishedTask::tookMillis).filter(millis -> millis > 0)
                .sorted().toArray();
        if (took.length == 0) {
            return "";
        }
        long median = (took[(took.length - 1) / 2] + took[took.length / 2]) / 2;
        Map<Owner, Long> held = new EnumMap<>(Owner.class);
        records.forEach(task -> HeldTime.byOwner(task.history(), task.finishedAt()).forEach(
                (owner, millis) -> held.merge(owner, millis, Long::sum)));
        held.remove(Owner.NOBODY);
        long total = held.values().stream().mapToLong(Long::longValue).sum();
        String split = Stream.of(Owner.YOU, Owner.AGENT, Owner.CI)
                .filter(owner -> held.getOrDefault(owner, 0L) > 0)
                .map(owner -> {
                    long millis = held.getOrDefault(owner, 0L);
                    return "%s %s (%d%%)".formatted(HOLDER.get(owner), DurationFormat.compact(millis),
                            Math.round(100.0 * millis / total));
                })
                .collect(Collectors.joining(", "));
        return "median " + DurationFormat.compact(median) + (split.isEmpty() ? "" : "; held by " + split);
    }

    private static String took(FinishedTask task) {
        return task.tookMillis() <= 0 ? "-" : DurationFormat.compact(task.tookMillis());
    }
}
