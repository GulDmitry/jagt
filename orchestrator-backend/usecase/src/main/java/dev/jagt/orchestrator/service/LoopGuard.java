package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/** The trail of rounds relayed into a task, and the stop it trips when they look like a loop. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoopGuard {

    public enum Relay { RELAY, UNCHANGED, STOPPED }

    private final StateService stateService;
    private final ConfigService configService;
    private final Notifications notifications;
    private final Clock clock;

    /** A round identical to the last one relayed is not news, whatever was relayed in between. */
    public Relay round(String taskId, ReviewFacts facts, String brief) {
        String digest = digest(brief);
        AtomicReference<Relay> relay = new AtomicReference<>(Relay.RELAY);
        trip(taskId, (task, breaker) -> {
            LoopWatch watch = task.loopOrNone();
            if (watch.relayed(digest)) {
                relay.set(Relay.UNCHANGED);
                return watch;
            }
            LoopWatch next = watch.withRound(new LoopWatch.Round(clock.millis(), facts.threads().size(),
                    failing(facts), digest));
            return breaker.afterRound(next, task.history(), clock.millis()).map(next::stopped).orElse(next);
        }).ifPresent(stopped -> relay.set(Relay.STOPPED));
        return relay.get();
    }

    public boolean mayShip(String taskId) {
        return trip(taskId, (task, breaker) -> breaker.beforeShip(task.loopOrNone(), task.history(), clock.millis())
                .map(task.loopOrNone()::stopped).orElse(task.loopOrNone())).isEmpty();
    }

    public boolean stopped(String taskId) {
        return stateService.task(taskId).filter(TaskState::loopStopped).isPresent();
    }

    public void lift(String taskId) {
        stateService.updateTask(taskId, task -> task.withLoop(LoopWatch.liftedAt(clock.millis())));
    }

    /** The stop standing once {@code next} ran, announced the once it was new. */
    private Optional<LoopWatch.Stop> trip(String taskId, BiFunction<TaskState, LoopBreaker, LoopWatch> next) {
        LoopBreaker breaker = configService.load().loopBreakerOrDefault();
        AtomicReference<LoopWatch.@Nullable Stop> standing = new AtomicReference<>();
        AtomicReference<LoopWatch.@Nullable Stop> tripped = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            if (task.loopStopped()) {
                standing.set(task.loopOrNone().stop());
                return task;
            }
            LoopWatch watch = next.apply(task, breaker);
            tripped.set(watch.stop());
            standing.set(watch.stop());
            return watch.equals(task.loopOrNone()) ? task : task.withLoop(watch);
        });
        Optional.ofNullable(tripped.get()).ifPresent(stop -> announce(taskId, stop));
        return Optional.ofNullable(standing.get());
    }

    private void announce(String taskId, LoopWatch.Stop stop) {
        String alias = stateService.task(taskId).map(TaskState::alias).orElse(taskId);
        log.atWarn().setMessage("task loop stopped").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", stop.trigger().name().toLowerCase(Locale.ROOT))
                .addKeyValue("said", OneLine.clipped(stop.evidence()))
                .addKeyValue("effect", "auto-review and the master leave the task")
                .addKeyValue("fix", "lift " + alias)
                .log();
        notifications.send(new Notification(Notification.Topic.INSTALL, taskId, "jagt stopped a loop",
                stop.evidence() + " — likely a jagt defect: read the log, then lift or close"));
    }

    private static String failing(ReviewFacts facts) {
        return Pipeline.of(facts.pipelineStatus()) == Pipeline.RED
                ? facts.pipelineFailure().lines().findFirst().orElse("") : "";
    }

    private static String digest(String brief) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(brief.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
