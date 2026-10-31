package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRulesOracleProperties {

    private record Rule(Set<TaskStatus> from, BiPredicate<TaskStatus, Facts> when) {

        boolean allows(TaskStatus status, Facts facts) {
            return from.contains(status) && when.test(status, facts);
        }
    }

    private static final Map<TaskAction, Rule> RULES = new EnumMap<>(TaskAction.class);

    private static Set<TaskStatus> leavesBy(TaskAction action) {
        Set<TaskStatus> leaves = EnumSet.noneOf(TaskStatus.class);
        for (TaskStatus from : TaskStatus.values()) {
            for (Outcome.Kind outcome : Outcome.Kind.values()) {
                TaskChart.land(from, new TaskMove.Ran(action, outcome)).ifPresent(to -> leaves.add(from));
            }
        }
        return leaves;
    }

    private static void rule(TaskAction action, Set<TaskStatus> from, BiPredicate<TaskStatus, Facts> when) {
        RULES.put(action, new Rule(from, when));
    }

    static {
        rule(TaskAction.SHIP, leavesBy(TaskAction.SHIP),
                (status, facts) -> !facts.gitStateChanged() && !facts.requestMerged() && switch (status) {
                    case IN_PROGRESS, VERIFYING, REVIEW_PENDING -> true;
                    case SHIPPING -> !facts.agentLive().getAsBoolean();
                    default -> facts.hasReviewRequest();
                });
        rule(TaskAction.APPROVE_PLAN, leavesBy(TaskAction.APPROVE_PLAN), (status, facts) -> true);
        rule(TaskAction.ADD_REPO, EnumSet.allOf(TaskStatus.class),
                (status, facts) -> status != TaskStatus.DONE && facts.repoRequested());
        rule(TaskAction.SWEEP, EnumSet.allOf(TaskStatus.class), (status, facts) -> facts.hasReviewRequest());
        rule(TaskAction.DEPLOY, leavesBy(TaskAction.DEPLOY),
                (status, facts) -> !facts.gitStateChanged() && !facts.revertConflict()
                        && (status == TaskStatus.DEPLOY_CONFLICT || facts.hasReviewRequest()));
        rule(TaskAction.REVERT, EnumSet.allOf(TaskStatus.class),
                (status, facts) -> !facts.gitStateChanged() && (status == TaskStatus.DEPLOYED
                        || status == TaskStatus.DEPLOY_CONFLICT || facts.liveDeploy()));
        rule(TaskAction.TRUST, EnumSet.allOf(TaskStatus.class), (status, facts) -> facts.gitStateChanged());
        rule(TaskAction.LIFT, EnumSet.allOf(TaskStatus.class), (status, facts) -> facts.stopped());
        EnumSet.of(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF, TaskAction.RESPAWN, TaskAction.DONE)
                .forEach(action -> rule(action, EnumSet.allOf(TaskStatus.class), (status, facts) -> true));
    }

    @Property(tries = 3000)
    void theChartOffersExactlyWhatTheRulesTableOffered(@ForAll TaskStatus status, @ForAll TaskAction action,
                                                       @ForAll boolean reviewRequest, @ForAll boolean liveDeploy,
                                                       @ForAll boolean agentLive, @ForAll boolean gitChanged,
                                                       @ForAll boolean stopped, @ForAll boolean repoRequested,
                                                       @ForAll boolean merged, @ForAll boolean revertConflict) {
        AtomicInteger oldProbes = new AtomicInteger();
        AtomicInteger newProbes = new AtomicInteger();
        Facts oldFacts = new Facts(reviewRequest, liveDeploy, () -> {
            oldProbes.incrementAndGet();
            return agentLive;
        }, gitChanged, stopped, repoRequested, merged, revertConflict);
        Facts newFacts = new Facts(reviewRequest, liveDeploy, () -> {
            newProbes.incrementAndGet();
            return agentLive;
        }, gitChanged, stopped, repoRequested, merged, revertConflict);

        boolean expected = RULES.containsKey(action) && RULES.get(action).allows(status, oldFacts);

        assertThat(FlowRules.allows(status, action, newFacts)).isEqualTo(expected);
        assertThat(newProbes.get()).isEqualTo(oldProbes.get());
        assertThat(FlowRules.mentions(action)).isEqualTo(RULES.containsKey(action));
    }

    @Property(tries = 1000)
    void theChartListsTheActionsTheRulesTableListedInTheSameOrder(@ForAll TaskStatus status,
                                                                  @ForAll boolean reviewRequest,
                                                                  @ForAll boolean liveDeploy,
                                                                  @ForAll boolean agentLive,
                                                                  @ForAll boolean gitChanged, @ForAll boolean stopped,
                                                                  @ForAll boolean repoRequested,
                                                                  @ForAll boolean merged,
                                                                  @ForAll boolean revertConflict) {
        Facts facts = new Facts(reviewRequest, liveDeploy, () -> agentLive, gitChanged, stopped, repoRequested,
                merged, revertConflict);

        List<TaskAction> expected = RULES.entrySet().stream()
                .filter(entry -> entry.getValue().allows(status, facts)).map(Map.Entry::getKey)
                .sorted(Comparator.comparing(TaskAction::group)).toList();

        assertThat(FlowRules.allowed(status, facts)).isEqualTo(expected);
    }
}
