package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.CheckerPhase;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The after-deploy check where the role is enabled: one QA run per deploy, opened once the deploy's pipelines are
 * green. {@link DeployCheckJob} asks the session nothing then, so a deploy is never checked twice.
 */
@Service
@RequiredArgsConstructor
public class CheckerCheckJob implements TaskJob {

    private final RoleSettings settings;
    private final CheckerPrompts prompts;
    private final CheckerReads reads;
    private final CheckerRecords records;
    private final CheckerOutcomes outcomes;

    @Override
    public String id() {
        return settings.role().id() + "-check";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "check each deployed change against its ticket on the environments roles.qa.environments names";
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
        RoleConfig qa = settings.load();
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
        String step = CheckerPhase.of(task, settings.role().id()) == CheckerPhase.READING ? QaVerdict.AGAIN : QaVerdict.ACCEPT;
        Answer<QaVerdict> answer = reads.check(taskId, step, digest, prompts.accept(taskId, worktree, sha, qa, step));
        QaVerdict verdict = answer.facts().orElse(QaVerdict.failed(sha, answer.failure()));
        records.put(taskId, digest, sha, outcomes.apply(taskId, task, verdict));
    }

    /** The deployment where a check may open: the role is on and every predicate its trigger names holds. */
    private Optional<Deployment> openFor(TaskState task, RoleConfig config) {
        List<String> trigger = settings.role().trigger().trigger();
        return Optional.ofNullable(task.deployment()).filter(deployment -> config.enabledOrDefault()
                && deployment.askedDigest() != null
                && trigger.stream().allMatch(predicate -> holds(predicate, deployment, config)));
    }

    private static boolean holds(String predicate, Deployment deployment, RoleConfig config) {
        return switch (predicate) {
            case "deployed" -> deployment.live();
            case "pipelines-green" -> deployment.askedDigest() != null;
            case "environment-named" -> !config.environmentsOrEmpty().isEmpty();
            default -> false;
        };
    }
}
