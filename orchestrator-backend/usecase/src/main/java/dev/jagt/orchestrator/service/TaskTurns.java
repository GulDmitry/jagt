package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** A task's turn, written into the task so a restart keeps it; whoever it passes to is {@link Turns#next}'s answer. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskTurns {

    /** The tree, as {@code WorktreeChanges#state} reads it, on which the Master pressed ship. */
    public static final String PRESSED_SHIP = "press:ship";

    private final StateService stateService;
    private final Clock clock;

    public Turn of(String taskId) {
        return stateService.task(stateService.canonicalTaskId(taskId)).map(TaskState::turnOrNone).orElse(Turn.none());
    }

    public Optional<Standing> standing(String taskId) {
        return stateService.task(stateService.canonicalTaskId(taskId)).map(Standing::of);
    }

    public void opened(String taskId, String step, String digest, @Nullable TaskStatus checked) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), opening(step, digest));
        announced(taskId, step, checked);
    }

    /** Puts {@code before} back where the turn is still the {@code opened} one; a turn moved on since stays. */
    public boolean restored(String taskId, Turn opened, Turn before) {
        boolean[] restored = new boolean[1];
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> {
            restored[0] = task.turnOrNone().sameTurnAs(opened);
            return restored[0] ? TaskReducer.turnRestored(task, before) : task;
        });
        return restored[0];
    }

    /** The write that opens the session's turn, for a caller that makes it part of a larger one. */
    public UnaryOperator<TaskState> opening(String step, String digest) {
        return task -> TaskReducer.relayed(task, step, digest, answeredBy(task), clock.millis());
    }

    private static String answeredBy(TaskState task) {
        return Turn.digestOf(task.history().reversed().stream().filter(move -> move.origin() == ActionOrigin.MCP).findFirst()
                .map(move -> move.status() + "\n" + (move.why() == null ? "" : move.why())).orElse(""));
    }

    /** {@code checked} is the status the door admitted the step at, not the one the task holds by now. */
    public void announced(String taskId, String step, @Nullable TaskStatus checked) {
        String id = stateService.canonicalTaskId(taskId);
        log.atInfo().setMessage("turn opened").addKeyValue("task", id).addKeyValue("step", step)
                .addKeyValue("status", checked == null ? "unknown" : checked)
                .addKeyValue("turn", of(id).id()).log();
    }

    /** The {@code role}'s read of {@code step} ended: its turn closes where it still stands, and a turn opened since stays. */
    public void closed(String taskId, String role, String step, String digest) {
        stateService.updateTask(stateService.canonicalTaskId(taskId),
                task -> TaskReducer.checkerReadEnded(task, role, step, digest, task.lastActiveTimestamp()));
    }

    /** A reply refused stops its task first, and a stopped task's turn is the human's whatever the Master's profile. */
    public void refused(String taskId) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> closed(task));
    }

    /** The turn the task's last report answered, passed to whoever holds it now. */
    public static TaskState closed(TaskState task) {
        return TaskReducer.turnClosed(task, task.lastActiveTimestamp());
    }

    public void left(String taskId, String step) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> TaskReducer.masterLeft(task, step));
    }

    public void took(String taskId) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), TaskReducer::masterTook);
    }

    public void unanswered(String taskId) {
        stateService.updateTask(stateService.canonicalTaskId(taskId),
                task -> TaskReducer.masterUnanswered(task, task.askedDigest()));
    }

    public void askedStop(String taskId, String key) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> TaskReducer.masterAskedStop(task, key));
    }

    public void pressedDeploy(String taskId, String arrival) {
        stateService.updateTask(stateService.canonicalTaskId(taskId),
                task -> TaskReducer.masterPressedDeploy(task, arrival));
    }

    public void mark(String taskId, String step, String value) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> task.withMark(step, value));
    }

    /** The tree a ship was pressed on, kept on the ship's own step as a digest. */
    public void shippedOn(String taskId, String tree) {
        String digest = HexFormat.of().formatHex(sha256(tree));
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> task.withLastShipTree(digest));
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JVM", e);
        }
    }

    public void unmark(String taskId, String step) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> task.withoutMark(step));
    }

    /** The question standing on the task, as {@link #UNANSWERED} records it. */
    public static String asked(TaskState task) {
        return task.askedDigest();
    }
}
