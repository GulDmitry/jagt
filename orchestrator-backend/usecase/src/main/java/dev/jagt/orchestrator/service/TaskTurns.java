package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.UnaryOperator;

/** A task's turn, written into the task so a restart keeps it; whoever it passes to is {@link Turns#next}'s answer. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskTurns {

    /** A step the Master offered the human instead of pressing it. */
    public static final String LEFT = "left";
    /** The question the Master read and gave no usable answer to. */
    public static final String UNANSWERED = "unanswered";
    /** The arrival at which the Master pressed deploy. */
    public static final String PRESSED_DEPLOY = "press:deploy";

    private final StateService stateService;
    private final Clock clock;

    public Turn of(String taskId) {
        return stateService.task(stateService.canonicalTaskId(taskId)).map(TaskState::turnOrNone).orElse(Turn.none());
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
            return restored[0] ? task.withTurn(before) : task;
        });
        return restored[0];
    }

    /** The write that opens the session's turn, for a caller that makes it part of a larger one. */
    public UnaryOperator<TaskState> opening(String step, String digest) {
        return task -> task.withTurn(task.turnOrNone().relayed(step, digest, clock.millis()));
    }

    /** {@code checked} is the status the door admitted the step at, not the one the task holds by now. */
    public void announced(String taskId, String step, @Nullable TaskStatus checked) {
        String id = stateService.canonicalTaskId(taskId);
        log.atInfo().setMessage("turn opened").addKeyValue("task", id).addKeyValue("step", step)
                .addKeyValue("status", checked == null ? "unknown" : checked)
                .addKeyValue("turn", of(id).id()).log();
    }

    /** A reply refused stops its task first, and a stopped task's turn is the human's whatever the Master's profile. */
    public void refused(String taskId) {
        stateService.updateTask(stateService.canonicalTaskId(taskId), task -> closed(task, Turns.Profile.OFF));
    }

    /** The turn the task's last report answered, passed to whoever holds it now. */
    public static TaskState closed(TaskState task, Turns.Profile profile) {
        Turn.Holder holder = Turns.next(task.status(), task.report(), task.stopped(), profile);
        return task.withTurn(task.turnOrNone().closed(holder, task.lastActiveTimestamp()));
    }

    public void mark(String taskId, String step, String value) {
        update(taskId, turn -> turn.withMark(step, value));
    }

    public void unmark(String taskId, String step) {
        update(taskId, turn -> turn.withoutMark(step));
    }

    /** The question standing on the task, as {@link #UNANSWERED} records it. */
    public static String asked(TaskState task) {
        return Turn.digestOf(task.message() == null ? "" : task.message());
    }

    /** The Master left what it would hold to the human: a step it offered, a question, a deploy that did not land. */
    public static boolean leftToTheHuman(TaskState task) {
        Turn turn = task.turnOrNone();
        return turn.mark(LEFT) != null || task.report().asks() && turn.marked(UNANSWERED, asked(task))
                || FlowRules.deployedByTheMaster(task.status())
                && turn.marked(PRESSED_DEPLOY, String.valueOf(task.statusSince()));
    }

    private void update(String taskId, UnaryOperator<Turn> change) {
        stateService.updateTask(stateService.canonicalTaskId(taskId),
                task -> task.withTurn(change.apply(task.turnOrNone())));
    }
}
