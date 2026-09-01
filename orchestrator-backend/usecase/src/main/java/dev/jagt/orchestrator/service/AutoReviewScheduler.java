package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskState;
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
 * Polls the request of every task that HAS one through the SHARED {@link ReviewSweepService}, so an unattended
 * poll can do no more than a human asking for one. What the task is doing meanwhile decides nothing — see
 * {@link AutoReviewCadence#polls}.
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

    // @Autowired disambiguates: with two constructors Spring otherwise demands a no-arg default.
    @Autowired
    public AutoReviewScheduler(StateService stateService, ConfigService configService,
                               ReviewSweepService reviewSweep, Notifications notifications) {
        // Single thread: a sweep runs for minutes, so serialising keeps the tick from piling up polls.
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
        // A task RETIRED while out for review never leaves that status, so its marker would never be dropped.
        windowElapsedNotified.removeIf(marker -> !tasks.containsKey(marker.substring(0, marker.lastIndexOf('@'))));
        tasks.forEach((taskId, task) -> {
            // A task the poller has no business with any more re-arms its window-elapsed ping.
            if (!cadence.polls(task)) {
                windowElapsedNotified.removeIf(marker -> marker.startsWith(taskId + "@"));
                return;
            }
            switch (decide(task, cadence, now)) {
                case WINDOW_ELAPSED -> {
                    // Keyed by the WINDOW: another round starts a new one without ever leaving CI_POLLING.
                    if (windowElapsedNotified.add(taskId + "@" + task.mrCreatedAt())) {
                        notifications.send(Notification.fromAgent(taskId, "auto-review",
                                AutoReviewWatch.windowElapsed(cadence.windowHours()).note()));
                    }
                }
                case POLL -> poll(taskId, task.alias());
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
            case NONE, OFF_FOR_TASK, NO_ROUND -> Action.SKIP;
        };
    }

    private void poll(String taskId, String alias) {
        // Stops the tick from QUEUING polls behind a sweep that runs for minutes. It does NOT make a task's
        // sweeps mutually exclusive; that exclusion lives where every trigger passes through.
        if (!inFlight.add(taskId)) {
            return;
        }
        log.atInfo().setMessage("auto-review poll due").addKeyValue("task", taskId)
                .addKeyValue("alias", alias)
                .log();
        try {
            executor.execute(() -> pollNow(taskId));
        } catch (RejectedExecutionException e) {
            // Submission itself failed. Caught NARROWLY: an exception from the sweep landing here would leave
            // lastPolledAt unadvanced under a same-thread executor, re-running the sweep forever.
            inFlight.remove(taskId);
            log.atWarn().setMessage("auto-review poll not scheduled")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }

    private void pollNow(String taskId) {
        try {
            OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> reviewSweep.sweep(taskId));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("auto-review sweep failed")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString())
                    .log();
        } finally {
            // Release the marker LAST and unconditionally: stamping the poll writes state.json and can throw,
            // and losing the release would exclude this task from auto-review for the JVM's life.
            try {
                stateService.updateTask(taskId, t -> t.withLastPolledAt(System.currentTimeMillis()));
            } finally {
                inFlight.remove(taskId);
            }
        }
    }
}
