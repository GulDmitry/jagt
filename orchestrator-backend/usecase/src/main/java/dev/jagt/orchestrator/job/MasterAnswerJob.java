package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterVerdicts;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Where the Master stands in for the human, a session that stopped to ask is answered by it, not left waiting. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterAnswerJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final MasterPanel panel;
    private final MasterVerdicts verdicts;
    /** A question no answer came back for is not asked again every tick; a new one is. */
    private final Map<String, String> unanswered = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "master-answer";
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
        stateService.tasks().entrySet().stream()
                .filter(entry -> asking(entry.getValue())
                        && !entry.getValue().message().equals(unanswered.get(entry.getKey())))
                .findFirst()
                .ifPresent(entry -> answer(entry.getKey(), entry.getValue(), config));
    }

    private static boolean asking(TaskState task) {
        return Move.asking(task.status(), AgentReport.of(task.message()));
    }

    private void answer(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        int given = verdicts.answersOverThisTree(task);
        if (given >= MasterDecisions.RUNAWAY) {
            log.atWarn().setMessage("master answer runaway").addKeyValue("task", taskId)
                    .addKeyValue("answers", given)
                    .addKeyValue("cause", "answers over a tree nothing changed in")
                    .addKeyValue("effect", "the question waits for the human")
                    .log();
            unanswered.put(taskId, task.message());
            return;
        }
        // Answers nothing acted on change the approach: a session with fresh tools, a change in the worktrees.
        boolean stuck = given >= MasterDecisions.ANSWERS_PER_TREE;
        log.atInfo().setMessage("master answers").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .log();
        Optional<String> decision = panel.answer(taskId, task, task.message(), config, stuck);
        if (decision.isEmpty() || !verdicts.answered(taskId, task, task.message(), decision.get(), stuck)) {
            log.atWarn().setMessage("master answer unusable").addKeyValue("task", taskId)
                    .addKeyValue("effect", "the question waits for the human")
                    .log();
            unanswered.put(taskId, task.message());
        }
    }
}
