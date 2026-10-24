package dev.jagt.orchestrator.adapter.replay;

import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stands in for every recorded session: answers the session's launch and each turn jagt opens with the moves the
 * recording made there. A turn the recording did not open, or one it opened that jagt does not, ends the replay.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@Slf4j
public class ReplaySessions {

    private static final long TICK_MILLIS = 500;

    private final Recording recording;
    private final TaskStore tasks;
    private final ReplayMoves moves;
    private final Map<String, TurnScript> scripts = new LinkedHashMap<>();
    private final Map<String, Long> answered = new ConcurrentHashMap<>();
    private final Set<Path> launched = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private boolean drained;

    public ReplaySessions(Recording recording, TaskStore tasks, ReplayMoves moves) {
        this.recording = recording;
        this.tasks = tasks;
        this.moves = moves;
        recording.lines().stream().map(ReplaySessions::taskOf).filter(task -> !task.isEmpty()).distinct()
                .forEach(task -> scripts.put(task, TurnScript.of(task, recording.lines())));
    }

    private static String taskOf(RecordingLine line) {
        return switch (line) {
            case RecordingLine.Opened opened -> opened.task();
            case RecordingLine.Move move -> move.task();
            default -> "";
        };
    }

    public void launched(Path worktree) {
        launched.add(worktree.toAbsolutePath().normalize());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ticker.scheduleWithFixedDelay(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        ticker.shutdownNow();
    }

    private void tick() {
        try {
            scripts.forEach((task, script) -> tasks.task(task).ifPresent(state -> answer(task, state, script)));
            if (!drained && !recording.diverged() && scripts.values().stream().allMatch(TurnScript::drained)) {
                drained = true;
                log.atInfo().setMessage("replay drained").log();
            }
        } catch (RuntimeException e) {
            recording.diverge("", "", "the replay failed: " + e);
        }
    }

    private void answer(String task, TaskState state, TurnScript script) {
        String worktree = state.worktreePath();
        if (recording.diverged() || worktree == null || !launched.contains(Path.of(worktree).toAbsolutePath().normalize())) {
            return;
        }
        Path at = Path.of(worktree);
        script.launched().forEach(move -> moves.make(at, move));
        Turn turn = state.turnOrNone();
        if (!turn.open() || answered.getOrDefault(task, 0L) >= turn.id()) {
            return;
        }
        answered.put(task, turn.id());
        TurnScript.Reply reply = script.opened(turn.step());
        if (reply.diverged()) {
            recording.diverge(task, turn.step(), reply.divergence());
            return;
        }
        reply.moves().forEach(move -> moves.make(at, move));
    }
}
