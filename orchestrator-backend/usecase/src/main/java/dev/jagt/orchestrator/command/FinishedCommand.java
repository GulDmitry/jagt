package dev.jagt.orchestrator.command;

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

@Component
@RequiredArgsConstructor
public class FinishedCommand implements GlobalCommand {

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
        return "what has been closed, how long each took and how many rounds it cost";
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
        Map<Guard, Integer> refused = new EnumMap<>(Guard.class);
        records.forEach(task -> task.refusals().forEach((guard, count) -> refused.merge(guard, count, Integer::sum)));
        if (!refused.isEmpty()) {
            lines.add("refused: " + refused.entrySet().stream()
                    .map(entry -> entry.getKey().label() + " " + entry.getValue()).collect(Collectors.joining(", ")));
        }
        return String.join("\n", lines);
    }

    private static String took(FinishedTask task) {
        return task.tookMillis() <= 0 ? "-" : DurationFormat.compact(task.tookMillis());
    }
}
