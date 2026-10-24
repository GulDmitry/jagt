package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class DeployBranchByDeploy implements Invariant {

    public static final String NAME = "deploy-branch-by-deploy";
    private static final Set<TaskStatus> LANDED = Set.of(TaskStatus.DEPLOYED, TaskStatus.REVERTED);
    private static final Duration BEFORE = Duration.ofSeconds(2);
    private static final Duration AFTER = Duration.ofMinutes(2);

    @Override
    public List<Violation> check(RunRecord run) {
        List<RunRecord.RefUpdate> updates = run.origin().getOrDefault(RunRecord.DEPLOY, List.of());
        if (updates.isEmpty()) {
            return List.of(new Violation(NAME, Instant.EPOCH, "origin.git holds no reflog of " + RunRecord.DEPLOY
                    + ", so nothing shows who wrote it"));
        }
        return updates.stream().skip(1)
                .filter(update -> !run.timeline().standWrites().contains(update.now()))
                .filter(update -> !landedBy(run, update.at()))
                .map(update -> new Violation(NAME, update.at(), update.line()))
                .toList();
    }

    private static boolean landedBy(RunRecord run, Instant at) {
        return run.histories().values().stream().flatMap(List::stream)
                .anyMatch(step -> LANDED.contains(step.status()) && !step.at().isBefore(at.minus(BEFORE))
                        && !step.at().isAfter(at.plus(AFTER)));
    }
}
