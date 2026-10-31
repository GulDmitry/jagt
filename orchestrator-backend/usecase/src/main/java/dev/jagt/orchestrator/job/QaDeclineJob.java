package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Reads the session's no-change reply to a QA round back to QA: it accepts, or disputes. Once a round and a first
 * dispute are behind, a reply is read once more; a second dispute goes to the human, and nothing is read after it.
 */
@Service
@RequiredArgsConstructor
public class QaDeclineJob implements TaskJob {

    private final ConfigService config;
    private final QaPrompts prompts;
    private final QaReader reader;
    private final QaRecords records;
    private final QaOutcomes outcomes;

    @Override
    public String id() {
        return "qa-decline";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "read the session's answer to a QA round that changed nothing back to QA";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::checkedWhereItLanded).withStops(StopKind.NONE)
                .withReports(report -> report == AgentReport.NO_CHANGES);
    }

    @Override
    public void run(String taskId, TaskState task) {
        QaConfig qa = config.load().qa();
        Optional<QaRecords.Record> asked = records.latest(taskId).filter(record -> answered(task, record));
        if (asked.isEmpty() || !qa.enabledOrDefault() || task.worktreePath() == null || task.turnOrNone().open()) {
            return;
        }
        String sha = asked.get().sha();
        String rows = Optional.ofNullable(task.turnOrNone().mark(QaVerdict.ROUND_ROWS)).orElse("");
        Answer<QaDecline> answer = reader.reconsider(prompts.decline(taskId, task.worktreePath(), sha, qa, rows,
                String.valueOf(task.message())));
        QaDecline decline = answer.facts().orElse(QaDecline.failed(sha, answer.failure()));
        QaEnding ending = outcomes.declined(taskId, decline, asked.get().ending() == QaEnding.DISPUTED);
        records.put(taskId, asked.get().digest(), sha, ending);
    }

    /** A round or a first dispute of this deploy stands, and the session replied after it was put. */
    private static boolean answered(TaskState task, QaRecords.Record record) {
        Deployment deployment = task.deployment();
        return deployment != null && record.digest().equals(deployment.askedDigest()) && task.qaRoundOpen()
                && (record.ending() == QaEnding.NOT_MET || record.ending() == QaEnding.DISPUTED)
                && task.session().reportedAt() > record.at();
    }
}
