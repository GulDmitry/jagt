package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.UsageLimits;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a task has consumed, in tokens and at list price. Two sources kept apart — jagt's own metered reads and what
 * each agent session burned, the Master's on a line of its own — and two bottom lines: the tasks still open,
 * against every call since the backend started.
 */
@Component
@RequiredArgsConstructor
public class UsageStatsRenderer {

    /** Must fit the longest {@link AssistantCallKind} label, or a number shifts out of its column. */
    static final int LABEL_W = 18;
    private static final String ROW = "%-" + LABEL_W + "s %6s %9s %9s %9s %9s %8s %8s%n";

    private static final String LIMIT_ROW = "%-" + LABEL_W + "s %6s  %s%n";
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

    private final UsageTracker usageTracker;
    private final UsageLimits limits;
    private final FinishedTasks finished;

    public String render(Map<String, TaskState> tasks) {
        StringBuilder out = new StringBuilder(limits(limits.last()))
                .append("token spend — jagt's own reads, then what the agent sessions burned\n\n");
        out.append(String.format(ROW, "TASK", "CALLS", "IN", "CACHED", "OUT", "TOTAL", "USD", "COLD"));

        TokenUsage tasksTotal = TokenUsage.NONE;
        var billed = tasks.entrySet().stream()
                .filter(e -> !e.getValue().usageOrNone().isNone())
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, TaskState> e) -> e.getValue().usageOrNone().total()).reversed())
                .toList();
        for (var entry : billed) {
            TokenUsage usage = entry.getValue().usageOrNone();
            tasksTotal = tasksTotal.plus(usage);
            out.append(row(entry.getKey(), usage));
        }
        if (billed.isEmpty()) {
            out.append("(nothing spent on the current tasks)\n");
        }
        TokenUsage session = usageTracker.session();
        out.append("\n").append(row("current tasks", tasksTotal)).append(row("this session", session));

        var agents = tasks.entrySet().stream()
                .filter(e -> !e.getValue().agentSpendOrNone().usageOrNone().isNone())
                .sorted(Comparator.comparingLong((Map.Entry<String, TaskState> e) ->
                        e.getValue().agentSpendOrNone().usageOrNone().total()).reversed())
                .toList();
        if (!agents.isEmpty()) {
            out.append('\n').append(String.format(ROW, "AGENT SESSION", "TURNS", "IN", "CACHED", "OUT", "TOTAL",
                    "USD", "COLD"));
            TokenUsage agentTotal = TokenUsage.NONE;
            for (var entry : agents) {
                TokenUsage usage = entry.getValue().agentSpendOrNone().usageOrNone();
                agentTotal = agentTotal.plus(usage);
                out.append(row(entry.getKey(), usage));
            }
            out.append(row("agents", agentTotal));
        }

        var byKind = usageTracker.sessionByKind();
        if (!byKind.isEmpty()) {
            out.append('\n').append(String.format(ROW, "BY CALL", "CALLS", "IN", "CACHED", "OUT", "TOTAL", "USD", "COLD"));
            byKind.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<AssistantCallKind, TokenUsage> e) ->
                            e.getValue().total()).reversed())
                    .forEach(e -> out.append(row(e.getKey().label(), e.getValue())));
        }

        if (session.isNone()) {
            out.append("\nno calls since this backend started — the per-task numbers above were"
                    + " restored from state.json.\n");
        } else {
            out.append("\naverage per call: ")
                    .append(TokenFormat.compact(session.total() / session.calls()))
                    .append(" tokens — most of that is fixed per-process overhead, so making FEWER calls"
                            + " beats shortening a prompt.\n");
        }
        return out.append(costPerDeployed(finished.all())).toString();
    }

    /** Over the tasks that reached DEPLOYED; one whose session spend was never read is left out, not counted as 0. */
    static String costPerDeployed(List<FinishedTask> finished) {
        List<FinishedTask> deployed = finished.stream().filter(task -> task.reached(TaskStatus.DEPLOYED)).toList();
        double[] costs = deployed.stream().map(FinishedTask::costUsd).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sorted().toArray();
        if (costs.length == 0) {
            return "median cost per deployed task: unknown (" + deployed.size() + " deployed, none with a known spend)\n";
        }
        double median = (costs[(costs.length - 1) / 2] + costs[costs.length / 2]) / 2;
        return String.format(Locale.ROOT, "median cost per deployed task: %.2f USD (%d of %d deployed with a known spend)%n",
                median, costs.length, deployed.size());
    }

    static String limits(Optional<UsageLimits.Reading> reading) {
        if (reading.isEmpty()) {
            return "";
        }
        String head = "subscription limits, read " + AT.format(reading.get().at().atZone(ZoneOffset.UTC));
        return switch (reading.get().answer()) {
            case SubscriptionUsage.NoSubscription none ->
                    head + ": none, the agent CLI is signed in without a subscription\n\n";
            case SubscriptionUsage.Unreadable failed -> head + ": could not read, " + failed.cause() + "\n\n";
            case SubscriptionUsage.Read(SubscriptionUsage.Snapshot snapshot) -> {
                StringBuilder out = new StringBuilder(head).append("\n\n")
                        .append(String.format(LIMIT_ROW, "WINDOW", "USED", "RESETS"));
                snapshot.windows().forEach(window -> out.append(String.format(LIMIT_ROW, window.name(),
                        String.format(Locale.ROOT, "%.0f%%", window.percentUsed()),
                        window.resetsAt().map(at -> AT.format(at.atZone(ZoneOffset.UTC))).orElse("-"))));
                snapshot.paid().ifPresent(paid -> out.append(String.format(LIMIT_ROW, "paid usage", "",
                        UsageLimits.spent(paid))));
                yield out.append('\n').toString();
            }
        };
    }

    private static String row(String label, TokenUsage usage) {
        return String.format(ROW, label, usage.calls(),
                TokenFormat.compact(usage.inputTokens()),
                TokenFormat.compact(usage.cachedInputTokens()),
                TokenFormat.compact(usage.outputTokens()),
                TokenFormat.compact(usage.total()),
                String.format(Locale.ROOT, "%.2f", usage.costUsd()),
                cold(usage));
    }

    private static String cold(TokenUsage usage) {
        return usage.measuredStarts() == 0 ? "?" : usage.coldStarts() + "/" + usage.measuredStarts();
    }
}
