package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Auto-review poller: after `ship`, watches each CI_POLLING task's MR on its own within the configured
 * time window, escalating the interval via {@link AutoReviewCadence}, and reacting through the SHARED
 * {@link ReviewSweepService} — so an unattended poll behaves exactly like a human typing `review`
 * (approval advances state, comments are only relayed as drafts for the human to close out). The backend
 * still talks to NO external system: the sweep delegates the code-host read to a headless Claude.
 *
 * <p>Decision ({@link #decide}) is pure and unit-tested; {@link #scan()} just applies it — one poll at a
 * time per task (in-flight guard), on a bounded executor since a sweep is slow (minutes), and one
 * "window elapsed" ping per task.
 */
@Service
public class AutoReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoReviewScheduler.class);

    enum Action { SKIP, POLL, WINDOW_ELAPSED }

    private final StateService stateService;
    private final ConfigService configService;
    private final ReviewSweepService reviewSweep;
    private final UserNotifier userNotifier;
    private final Executor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> windowElapsedNotified = ConcurrentHashMap.newKeySet();

    // @Autowired disambiguates: this class also has a package-private test constructor (injectable
    // Executor), and with two constructors Spring otherwise demands a no-arg default and fails to start.
    @Autowired
    public AutoReviewScheduler(StateService stateService, ConfigService configService,
                               ReviewSweepService reviewSweep, UserNotifier userNotifier) {
        // Single thread: a sweep spawns a headless Claude (minutes); serialising them bounds the cost and
        // keeps the 60s tick from ever piling up overlapping polls.
        this(stateService, configService, reviewSweep, userNotifier,
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "auto-review");
                    t.setDaemon(true);
                    return t;
                }));
    }

    AutoReviewScheduler(StateService stateService, ConfigService configService, ReviewSweepService reviewSweep,
                        UserNotifier userNotifier, Executor executor) {
        this.stateService = stateService;
        this.configService = configService;
        this.reviewSweep = reviewSweep;
        this.userNotifier = userNotifier;
        this.executor = executor;
    }

    @Scheduled(fixedRate = 60_000)
    public void scan() {
        var cfg = configService.load().autoReview();
        if (!cfg.enabledOrDefault()) {
            return;
        }
        AutoReviewCadence cadence = AutoReviewCadence.from(cfg);
        long now = System.currentTimeMillis();
        stateService.tasks().forEach((taskId, task) -> {
            // A task that left CI_POLLING (re-shipped, deployed, done) re-arms its window-elapsed ping.
            if (task.status() != TaskStatus.CI_POLLING) {
                windowElapsedNotified.remove(taskId);
                return;
            }
            switch (decide(task, cadence, now)) {
                case WINDOW_ELAPSED -> {
                    if (windowElapsedNotified.add(taskId)) {
                        userNotifier.notify("jagt · " + taskId, "auto-review window elapsed — `review` manually");
                    }
                }
                case POLL -> poll(taskId);
                case SKIP -> { }
            }
        });
    }

    /** Pure poll decision for a task the caller has already confirmed is CI_POLLING under an enabled config. */
    static Action decide(TaskState task, AutoReviewCadence cadence, long now) {
        if (task.status() != TaskStatus.CI_POLLING || !task.autoReviewEnabled(true)) {
            return Action.SKIP;
        }
        if (task.mrUrl() == null || task.mrUrl().isBlank() || task.mrCreatedAt() == 0) {
            return Action.SKIP;
        }
        Duration interval = cadence.pollInterval(Duration.ofMillis(now - task.mrCreatedAt()));
        if (interval == null) {
            return Action.WINDOW_ELAPSED;
        }
        return now - task.lastPolledAt() >= interval.toMillis() ? Action.POLL : Action.SKIP;
    }

    private void poll(String taskId) {
        if (!inFlight.add(taskId)) {   // a previous (slow) sweep is still running for this task
            return;
        }
        executor.execute(() -> {
            try {
                reviewSweep.sweep(taskId);
            } catch (RuntimeException e) {
                log.warn("Auto-review sweep failed for {}: {}", taskId, e.toString());
            } finally {
                stateService.updateTask(taskId, t -> t.withLastPolledAt(System.currentTimeMillis()));
                inFlight.remove(taskId);
            }
        });
    }
}
