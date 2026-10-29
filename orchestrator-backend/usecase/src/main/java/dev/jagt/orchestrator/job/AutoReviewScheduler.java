package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReviewSweepService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls the request of every task that HAS one through the SHARED {@link ReviewSweepService}, so an unattended
 * poll can do no more than a human asking for one. What the task is doing meanwhile decides nothing — see
 * {@link AutoReviewCadence#polls}.
 */
@Service
@RequiredArgsConstructor
public class AutoReviewScheduler implements Job {
    @Override
    public String id() {
        return "autoreview";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "poll the review request of every task out for review, and relay the round to its agent";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    enum Action { SKIP, POLL, WINDOW_ELAPSED }

    private final StateService stateService;
    private final ConfigService configService;
    private final AutoReviewPolls polls;
    private final Notifications notifications;
    private final Clock clock;
    private final Set<String> windowElapsedNotified = ConcurrentHashMap.newKeySet();

    @Override
    public void run() {
        AutoReviewCadence cadence = AutoReviewCadence.from(configService.load().autoReview());
        if (!cadence.enabled()) {
            return;
        }
        long now = clock.millis();
        var tasks = stateService.tasks();
        // A task RETIRED while out for review never leaves that status, so its marker would never be dropped.
        windowElapsedNotified.removeIf(marker -> !tasks.containsKey(marker.substring(0, marker.lastIndexOf('@'))));
        polls.rearmUnreadable(marker -> !tasks.containsKey(marker.substring(0, marker.lastIndexOf('@'))));
        tasks.forEach((taskId, task) -> {
            // A task the poller has no business with any more re-arms its window-elapsed ping.
            if (!cadence.polls(task)) {
                windowElapsedNotified.removeIf(marker -> marker.startsWith(taskId + "@"));
                polls.rearmUnreadable(marker -> marker.startsWith(taskId + "@"));
                return;
            }
            switch (decide(task, cadence, now)) {
                case WINDOW_ELAPSED -> {
                    // Keyed by the WINDOW: another round starts a new one without ever leaving CI_POLLING.
                    if (windowElapsedNotified.add(taskId + "@" + task.mrCreatedAt())) {
                        notifications.send(Notification.fromAgent(taskId, "auto-review",
                                Objects.requireNonNull(AutoReviewWatch.windowElapsed(cadence.windowHours()).note())));
                    }
                }
                case POLL -> poll(taskId, task.alias(), task.mrCreatedAt());
                case SKIP -> { }
            }
        });
    }

    /** Pure poll decision, taken from the WATCH every human surface shows. */
    static Action decide(TaskState task, AutoReviewCadence cadence, long now) {
        AutoReviewWatch watch = cadence.watch(task, now);
        return switch (watch.state()) {
            case WINDOW_ELAPSED -> Action.WINDOW_ELAPSED;
            case WATCHING -> watch.nextPollAt() <= now ? Action.POLL : Action.SKIP;
            case NONE, OFF_FOR_TASK, NO_ROUND, LOOP_STOPPED -> Action.SKIP;
        };
    }

    private void poll(String taskId, @Nullable String alias, long round) {
        polls.start(taskId, alias, round, () -> stateService.updateTask(taskId, t -> t.withLastPolledAt(clock.millis())));
    }
}
