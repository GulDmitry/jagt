package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

/** The trail of rounds relayed into a task, and the stop it trips when they look like a loop. */
@Service
@RequiredArgsConstructor
public class LoopGuard {

    public enum Relay { RELAY, UNCHANGED, STOPPED }

    private final StateService stateService;
    private final ConfigService configService;
    private final TaskStops stops;
    private final Clock clock;

    /** A round identical to the last one relayed is not news, whatever was relayed in between. */
    public Relay round(String taskId, ReviewFacts facts, String brief) {
        String digest = Turn.digestOf(brief);
        AtomicReference<Relay> relay = new AtomicReference<>(Relay.RELAY);
        LoopBreaker breaker = breaker();
        stops.trip(taskId, task -> {
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
        LoopBreaker breaker = breaker();
        return stops.trip(taskId, task -> breaker.beforeShip(task.loopOrNone(), task.history(), clock.millis())
                .map(task.loopOrNone()::stopped).orElse(task.loopOrNone())).isEmpty();
    }

    public boolean stopped(String taskId) {
        return stateService.task(taskId).filter(TaskState::loopStopped).isPresent();
    }

    public void lift(String taskId) {
        stateService.updateTask(taskId, task -> task.withLoop(LoopWatch.liftedAt(clock.millis())));
    }

    private LoopBreaker breaker() {
        return configService.load().loopBreakerOrDefault();
    }

    private static String failing(ReviewFacts facts) {
        return Pipeline.of(facts.pipelineStatus()) == Pipeline.RED
                ? facts.pipelineFailure().lines().findFirst().orElse("") : "";
    }
}
