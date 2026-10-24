package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Where the Master stands in for the human, a session that stopped to ask is answered by it, not left waiting. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterAnswerJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final MasterPanel panel;
    private final MasterVerdicts verdicts;
    /** A question no answer came back for is not asked again, nor after a restart; a new one is. */
    private static final String UNANSWERED = "unanswered";

    @Override
    public String id() {
        return "master-answer";
    }

    @Override
    public Serves serves() {
        return Serves.MASTER;
    }

    @Override
    public String describe() {
        return "answer the question a session stopped on, where the Master stands in for the human";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(2);
    }

    @Override
    public void run() {
        ConfigService.ConfigFile.MasterConfig config = configService.load().master();
        if (!config.may(MasterRight.ANSWER)) {
            return;
        }
        Turns.Profile profile = config.profile();
        stateService.tasks().entrySet().stream()
                .filter(entry -> answers(entry.getValue(), profile))
                .findFirst()
                .ifPresent(entry -> answer(entry.getKey(), entry.getValue(), config));
    }

    private static boolean answers(TaskState task, Turns.Profile profile) {
        return Move.asking(task.status(), task.report()) && !task.loopStopped()
                && Turns.next(task.status(), task.report(), profile) == Turn.Holder.MASTER
                && !task.turnOrNone().marked(UNANSWERED, asked(task));
    }

    private static String asked(TaskState task) {
        return Turn.digestOf(Objects.requireNonNull(task.message()));
    }

    private void unanswered(String taskId, TaskState task) {
        stateService.updateTask(taskId, now -> now.withTurn(now.turnOrNone().withMark(UNANSWERED, asked(task))));
    }

    /** A report or a relay while the Master read moved the turn on: the question it read is no longer standing. */
    private boolean standing(String taskId, TaskState task) {
        return stateService.task(taskId).map(TaskState::turnOrNone).filter(task.turnOrNone()::sameTurnAs).isPresent();
    }

    /** What no prose answer settles: a right only the human grants, a repository jagt attaches, a plan. */
    private static Optional<String> humans(TaskState task) {
        if (task.report() == AgentReport.PERMISSION) {
            return Optional.of("a permission only the human grants");
        }
        if (task.asksForARepository()) {
            return Optional.of("a repository jagt attaches");
        }
        return FlowRules.holdsAPlan(task.status()) ? Optional.of("the plan review answers it") : Optional.empty();
    }

    private void answer(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        Optional<String> humans = humans(task);
        if (humans.isPresent()) {
            log.atInfo().setMessage("master answer withheld").addKeyValue("task", taskId)
                    .addKeyValue("cause", humans.get())
                    .addKeyValue("effect", "no answer is written here")
                    .log();
            unanswered(taskId, task);
            return;
        }
        int given = verdicts.answersOverThisTree(task);
        boolean stalled = verdicts.stalled(task);
        if (given >= MasterDecisions.RUNAWAY || stalled) {
            log.atWarn().setMessage("master answer runaway").addKeyValue("task", taskId)
                    .addKeyValue("answers", given)
                    .addKeyValue("cause", stalled ? "findings repeat earlier rounds"
                            : "answers over a tree nothing changed in")
                    .addKeyValue("effect", "the question waits for the human")
                    .log();
            unanswered(taskId, task);
            return;
        }
        // Answers nothing acted on change the approach: a session with fresh tools, a change in the worktrees.
        boolean stuck = given >= MasterDecisions.ANSWERS_PER_TREE;
        log.atInfo().setMessage("master answers").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .log();
        Optional<MasterPanel.Decision> decision = panel.answer(taskId, task, Objects.requireNonNull(task.message()), config, stuck);
        if (decision.isPresent() && !standing(taskId, task)) {
            log.atInfo().setMessage("master answer dropped").addKeyValue("task", taskId)
                    .addKeyValue("cause", "the turn moved on while the Master read")
                    .addKeyValue("effect", "nothing relayed, the status left as it is")
                    .log();
            return;
        }
        if (decision.isEmpty() || !verdicts.answered(taskId, task, Objects.requireNonNull(task.message()), decision.get(), stuck)) {
            log.atWarn().setMessage("master answer unusable").addKeyValue("task", taskId)
                    .addKeyValue("effect", "the question waits for the human")
                    .log();
            unanswered(taskId, task);
        }
    }
}
