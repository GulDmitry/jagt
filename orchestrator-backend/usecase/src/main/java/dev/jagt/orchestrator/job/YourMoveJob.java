package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The human is tapped once each time a task's move becomes theirs, or becomes another one of theirs, and only once it
 * has stood for {@link #SETTLE}: a move a machine presses on within that time was never theirs.
 */
@Service
@RequiredArgsConstructor
public class YourMoveJob implements Job {

    private final StateService stateService;
    private final Notifications notifications;
    private final HandBack handBack;
    private final Clock clock;
    private final Map<String, Standing> seen = new ConcurrentHashMap<>();

    static final Duration SETTLE = Duration.ofSeconds(5);

    private record Standing(Optional<String> yours, Instant since, boolean told) {
    }

    @Override
    public String id() {
        return "your-move";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "tap you once when a task's move becomes yours";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(2);
    }

    @Override
    public void run() {
        Map<String, TaskState> tasks = stateService.tasks();
        seen.keySet().retainAll(tasks.keySet());
        tasks.forEach(this::watch);
    }

    private void watch(String taskId, TaskState task) {
        RoundState round = handBack.round(task);
        Move move = Move.forTask(task.status(), Facts.projected(task), round, false);
        Optional<String> yours = move.owner() == Owner.YOU ? Optional.of(move.hint()) : Optional.empty();
        Instant now = clock.instant();
        Standing before = seen.get(taskId);
        Standing standing = before != null && before.yours().equals(yours) ? before
                : new Standing(yours, now, before == null || yours.isEmpty());
        boolean settled = !standing.told() && !now.isBefore(standing.since().plus(SETTLE));
        seen.put(taskId, settled ? new Standing(yours, standing.since(), true) : standing);
        if (settled) {
            notifications.send(Notification.fromAgent(taskId, title(task.status(), round), banner(move.hint(), round)));
        }
    }

    private static String title(TaskStatus status, RoundState round) {
        return round.report() == AgentReport.QUESTION ? "needs input" : status.name().toLowerCase(Locale.ROOT);
    }

    private static String banner(String hint, RoundState round) {
        return round.draftedReplies() && round.report() != AgentReport.NO_CHANGES
                ? hint + " — drafted replies wait in review_replies.md"
                : hint;
    }
}
