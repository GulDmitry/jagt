package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where each task's clock has gone, added up from the status history. A retired task takes its history with it, so
 * this is a picture of the OPEN work, never a throughput figure over time.
 */
@Component
public class CycleTimeRenderer {

    static final int TASK_W = 18;
    private static final String ROW = "%-" + TASK_W + "s %7s %7s %9s %7s %7s %7s%n";

    private record Clock(String taskId, long age, long onYou, long onAgent, long onCi, int byYou,
                         int rounds, boolean partial) {

        long held() {
            return onYou + onAgent + onCi;
        }

        /** A log that has dropped its oldest steps can only give a floor, and a number must say when it is one. */
        String floor(String figure) {
            return partial ? figure + "+" : figure;
        }
    }

    String render(Map<String, TaskState> tasks, long now) {
        List<Clock> clocks = tasks.entrySet().stream()
                .filter(e -> !e.getValue().history().isEmpty())
                .map(e -> clockOf(e.getKey(), e.getValue(), now))
                .sorted(Comparator.comparingLong(Clock::onYou).reversed())
                .toList();

        StringBuilder out = new StringBuilder("cycle time — where each task's clock has gone, from its"
                + " status history\n\n");
        if (clocks.isEmpty()) {
            return out.append("(no task has a status history yet)\n").toString();
        }
        out.append(String.format(ROW, "TASK", "AGE", "ON YOU", "ON AGENT", "ON CI", "BY YOU", "ROUNDS"));
        clocks.forEach(clock -> out.append(row(clock, clock.taskId())));

        Clock all = summed(clocks);
        out.append('\n').append(row(all, "all tasks"));

        return out.append('\n').append(slowestStep(all))
                .append(String.format(Locale.ROOT, "a round is one trip out for review — the ship that pushed"
                                + " it, and the verdict that came back; %.1f per task.%n",
                        all.rounds() / (double) clocks.size()))
                .append("by you counts the moves a human made on the task; fewer is a task the agents and the Master"
                        + " carried.\n")
                .append(all.partial() ? "a + marks a task whose oldest steps have aged out of its history,"
                        + " so its age and rounds are floors.\n" : "")
                .toString();
    }

    private static String row(Clock clock, String label) {
        return String.format(ROW, label,
                clock.floor(DurationFormat.compact(clock.age())), DurationFormat.compact(clock.onYou()),
                DurationFormat.compact(clock.onAgent()), DurationFormat.compact(clock.onCi()),
                clock.floor(String.valueOf(clock.byYou())), clock.floor(String.valueOf(clock.rounds())));
    }

    private static String slowestStep(Clock all) {
        if (all.held() == 0) {
            return "";
        }
        long slowest = Math.max(all.onYou(), Math.max(all.onAgent(), all.onCi()));
        String whose = slowest == all.onYou() ? "you have"
                : slowest == all.onAgent() ? "the agents have" : "the code host has";
        return String.format(Locale.ROOT, "%s been the slowest step: %s of the %s anyone has held these"
                        + " tasks (%d%%).%n", whose, DurationFormat.compact(slowest),
                DurationFormat.compact(all.held()), Math.round(100.0 * slowest / all.held()));
    }

    private static Clock summed(List<Clock> clocks) {
        return clocks.stream().reduce(new Clock("all tasks", 0, 0, 0, 0, 0, 0, false),
                (total, clock) -> new Clock(total.taskId(), total.age() + clock.age(),
                        total.onYou() + clock.onYou(), total.onAgent() + clock.onAgent(),
                        total.onCi() + clock.onCi(), total.byYou() + clock.byYou(),
                        total.rounds() + clock.rounds(),
                        total.partial() || clock.partial()));
    }

    private static int byYou(List<StatusChange> history) {
        return (int) history.stream()
                .filter(step -> step.origin() == ActionOrigin.BOARD || step.origin() == ActionOrigin.PALETTE)
                .count();
    }

    private static Clock clockOf(String taskId, TaskState task, long now) {
        Map<Owner, Long> byOwner = HeldTime.byOwner(task.history(), now);
        return new Clock(taskId, Math.max(0, now - task.history().getFirst().at()),
                byOwner.getOrDefault(Owner.YOU, 0L), byOwner.getOrDefault(Owner.AGENT, 0L),
                byOwner.getOrDefault(Owner.CI, 0L), byYou(task.history()), task.rounds(), task.historyAtCap());
    }
}
