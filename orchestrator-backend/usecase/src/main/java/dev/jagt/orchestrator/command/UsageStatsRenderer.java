package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.TokenFormat;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * What a task has consumed, in TOKENS. Two sources kept apart — jagt's own metered reads and what each task's
 * agent sessions burned — and two bottom lines: the tasks still open, against every call since the backend started.
 */
@Component
@RequiredArgsConstructor
public class UsageStatsRenderer {

    /** Must fit the longest {@link AssistantCallKind} label, or a number shifts out of its column. */
    static final int LABEL_W = 18;
    private static final String ROW = "%-" + LABEL_W + "s %6s %9s %9s %9s %9s%n";

    private final UsageTracker usageTracker;

    public String render(Map<String, TaskState> tasks) {
        StringBuilder out = new StringBuilder("token spend — jagt's own reads, then what the agent"
                + " sessions burned\n\n");
        out.append(String.format(ROW, "TASK", "CALLS", "IN", "CACHED", "OUT", "TOTAL"));

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
            out.append('\n').append(String.format(ROW, "AGENT SESSION", "TURNS", "IN", "CACHED", "OUT", "TOTAL"));
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
            out.append('\n').append(String.format(ROW, "BY CALL", "CALLS", "IN", "CACHED", "OUT", "TOTAL"));
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
        return out.toString();
    }

    private static String row(String label, TokenUsage usage) {
        return String.format(ROW, label, usage.calls(),
                TokenFormat.compact(usage.inputTokens()),
                TokenFormat.compact(usage.cachedInputTokens()),
                TokenFormat.compact(usage.outputTokens()),
                TokenFormat.compact(usage.total()));
    }
}
