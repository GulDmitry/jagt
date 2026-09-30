package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
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
        return Duration.ofSeconds(20);
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
        return task.status() != TaskStatus.DONE && AgentReport.of(task.message()) == AgentReport.QUESTION;
    }

    private void answer(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        log.atInfo().setMessage("master answers").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .log();
        Optional<String> decision = panel.answer(taskId, task, task.message(), config);
        if (decision.isEmpty() || !verdicts.answered(taskId, decision.get())) {
            log.atWarn().setMessage("master answer unusable").addKeyValue("task", taskId)
                    .addKeyValue("effect", "the question waits for the human")
                    .log();
            unanswered.put(taskId, task.message());
        }
    }
}
