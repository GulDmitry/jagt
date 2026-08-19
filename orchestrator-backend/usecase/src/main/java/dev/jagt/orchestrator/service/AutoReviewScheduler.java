package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Polls each CI_POLLING task's request through the SHARED {@link ReviewSweepService}, so an unattended poll can
 * do no more than a human asking for one.
 */
@Service
@Slf4j
public class AutoReviewScheduler implements Job {
    @Override
    public String id() {
        return "autoreview";
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
    private final ReviewSweepService reviewSweep;
    private final Notifications notifications;
    private final Executor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> windowElapsedNotified = ConcurrentHashMap.newKeySet();

    // @Autowired disambiguates: this class also has a package-private test constructor (injectable
    // Executor), and with two constructors Spring otherwise demands a no-arg default and fails to start.
    @Autowired
    public AutoReviewScheduler(StateService stateService, ConfigService configService,
                               ReviewSweepService reviewSweep, Notifications notifications) {
        // Single thread: a sweep runs for minutes, so serialising bounds the cost and keeps the tick from
        // piling up overlapping polls.
        this(stateService, configService, reviewSweep, notifications,
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "auto-review");
                    t.setDaemon(true);
                    return t;
                }));
    }

    AutoReviewScheduler(StateService stateService, ConfigService configService, ReviewSweepService reviewSweep,
                        Notifications notifications, Executor executor) {
        this.stateService = stateService;
        this.configService = configService;
        this.reviewSweep = reviewSweep;
        this.notifications = notifications;
        this.executor = executor;
    }

    @Override
    public void run() {
        AutoReviewCadence cadence = AutoReviewCadence.from(configService.load().autoReview());
        if (!cadence.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        var tasks = stateService.tasks();
        // A task RETIRED while still CI_POLLING never leaves that status, so the branch below would keep its
        // marker for the life of the process.
        windowElapsedNotified.removeIf(marker -> !tasks.containsKey(marker.substring(0, marker.lastIndexOf('@'))));
        tasks.forEach((taskId, task) -> {
            // A task that left CI_POLLING (deployed, done) re-arms its window-elapsed ping.
            if (task.status() != TaskStatus.CI_POLLING) {
                windowElapsedNotified.removeIf(marker -> marker.startsWith(taskId + "@"));
                return;
            }
            switch (decide(task, cadence, now)) {
                case WINDOW_ELAPSED -> {
                    // Keyed by the WINDOW, not the task: shipping another round starts a new window without
                    // ever leaving CI_POLLING, and that round deserves its own reminder.
                    if (windowElapsedNotified.add(taskId + "@" + task.mrCreatedAt())) {
                        notifications.send(Notification.fromAgent(taskId, "auto-review",
                                "window elapsed — `sweep` manually"));
                    }
                }
                case POLL -> poll(taskId, task.alias());
                case SKIP -> { }
            }
        });
    }

    /**
     * Pure poll decision, taken from the WATCH every human surface shows — so a card promising a poll in four
     * minutes and a scheduler that would not make one cannot happen.
     */
    static Action decide(TaskState task, AutoReviewCadence cadence, long now) {
        AutoReviewWatch watch = cadence.watch(task, now);
        return switch (watch.state()) {
            case WINDOW_ELAPSED -> Action.WINDOW_ELAPSED;
            case WATCHING -> watch.nextPollAt() <= now ? Action.POLL : Action.SKIP;
            case NONE, OFF_FOR_TASK, NO_ROUND -> Action.SKIP;
        };
    }

    private void poll(String taskId, String alias) {
        // Stops the 60s tick from QUEUING polls behind a sweep that runs for minutes. It does NOT make a
        // task's sweeps mutually exclusive — a human typing `sweep` at the same time is a different
        // trigger entirely; that exclusion lives in ReviewSweepService, where every trigger passes through.
        if (!inFlight.add(taskId)) {
            return;
        }
        log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", alias)
                .log("auto-review {}: poll due, reading the review request", TaskLabel.of(taskId, alias));
        try {
            executor.execute(() -> pollNow(taskId));
        } catch (RejectedExecutionException e) {
            // Submission itself failed (the executor is shut down with the backend). Caught NARROWLY: an
            // exception thrown by the sweep must not land here, or it would be logged as a scheduling failure
            // and — with a same-thread executor — leave lastPolledAt unadvanced, re-running the sweep forever.
            inFlight.remove(taskId);
            log.warn("Could not schedule an auto-review poll for {}: {}", taskId, e.toString());
        }
    }

    private void pollNow(String taskId) {
        try {
            OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> reviewSweep.sweep(taskId));
        } catch (RuntimeException e) {
            log.warn("Auto-review sweep failed for {}: {}", taskId, e.toString());
        } finally {
            // Release the marker LAST and unconditionally: stamping the poll writes state.json, which throws
            // when the disk is full or the file turns unwritable. Losing the release there would exclude this
            // task from auto-review for the rest of the JVM's life, long after the disk recovered.
            try {
                stateService.updateTask(taskId, t -> t.withLastPolledAt(System.currentTimeMillis()));
            } finally {
                inFlight.remove(taskId);
            }
        }
    }
}
