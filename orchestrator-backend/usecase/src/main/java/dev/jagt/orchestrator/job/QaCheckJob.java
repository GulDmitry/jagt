package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.QaPhase;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The after-deploy check where {@code qa.enabled} is on: one QA run per deploy, opened once the deploy's pipelines are
 * green. {@link DeployCheckJob} asks the session nothing then, so a deploy is never checked twice.
 */
@Service
@RequiredArgsConstructor
public class QaCheckJob implements TaskJob {

    private final ConfigService config;
    private final QaPrompts prompts;
    private final QaReads reads;
    private final QaRecords records;
    private final QaOutcomes outcomes;

    @Override
    public String id() {
        return "qa-check";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "check each deployed change against its ticket on the environments qa.environments names";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::checkedWhereItLanded).withStops(StopKind.NONE);
    }

    @Override
    public void run(String taskId, TaskState task) {
        QaConfig qa = config.load().qa();
        String worktree = task.worktreePath();
        Optional<Deployment> open = openFor(task, qa);
        if (open.isEmpty() || worktree == null) {
            return;
        }
        String digest = Objects.requireNonNull(open.get().askedDigest());
        if (records.settled(taskId, digest)) {
            return;
        }
        String sha = open.get().tips();
        String step = QaPhase.of(task) == QaPhase.READING ? QaVerdict.AGAIN : QaVerdict.ACCEPT;
        Answer<QaVerdict> answer = reads.check(taskId, step, digest, prompts.accept(taskId, worktree, sha, qa, step));
        QaVerdict verdict = answer.facts().orElse(QaVerdict.failed(sha, answer.failure()));
        records.put(taskId, digest, sha, outcomes.apply(taskId, task, verdict));
    }

    /** The deployment where a check may open: QA on, an environment named, the pipelines green and the change live. */
    private static Optional<Deployment> openFor(TaskState task, QaConfig qa) {
        return Optional.ofNullable(task.deployment()).filter(deployment -> qa.enabledOrDefault()
                && !qa.environmentsOrEmpty().isEmpty() && deployment.live() && deployment.askedDigest() != null);
    }
}
