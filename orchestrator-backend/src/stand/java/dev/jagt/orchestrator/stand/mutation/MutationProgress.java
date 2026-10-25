package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandOutcome;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MutationProgress {

    private static final Set<String> DEFECT_STOPS = Set.of("STALLED", "SHIPS", "CHECKS");

    private MutationProgress() {
    }

    public static Optional<String> lacking(Mutation.Kind kind, StandOutcome outcome, List<RunRecord.Step> steps,
                                           int misses, boolean awaitsTheReviewer) {
        boolean attack = kind == Mutation.Kind.ATTACK;
        if (!attack && (misses > 0 || outcome.stopTrigger().equals("MISS"))) {
            return Optional.of("a " + kind.name().toLowerCase(java.util.Locale.ROOT) + " mutation hit a miss: "
                    + outcome.detail());
        }
        if (steps.stream().noneMatch(step -> step.origin().equals(ActionOrigin.MCP.name()))) {
            return Optional.of("the session never reported");
        }
        if (DEFECT_STOPS.contains(outcome.stopTrigger())) {
            return Optional.of("jagt stopped the task on its own defect: " + outcome.detail());
        }
        if (outcome.unfired() > 0 && !outcome.asked()) {
            return Optional.of("the mutation's event never fired and the session never asked");
        }
        boolean deployed = outcome.status().equals(TaskStatus.DEPLOYED.name());
        boolean reviewed = outcome.withTheCodeHost() && awaitsTheReviewer
                && steps.stream().anyMatch(step -> step.status() == TaskStatus.CI_POLLING);
        return deployed || outcome.asked() || attack && misses > 0 || reviewed ? Optional.empty()
                : Optional.of("the run reached neither a deploy, a question, a recorded refusal nor a waiting reviewer");
    }
}
