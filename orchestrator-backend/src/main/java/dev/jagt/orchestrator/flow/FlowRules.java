package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * THE life of a task, in one file: which statuses allow which action, and what each outcome of that action leads
 * to. Nothing else may answer either question — a projection asks it to decide what to offer and the engine asks
 * it before acting, so a card cannot advertise a move the gate then refuses.
 *
 * <p>It stays Java rather than configuration on purpose: every status and every action here is checked by the
 * compiler, and a table nobody can mistype is worth more than one nobody has to rebuild for.
 */
public final class FlowRules {

    /** An action a task's own status has nothing to say about: looking at it, or restarting its session. */
    private static final Set<TaskAction> ALWAYS = EnumSet.of(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF,
            TaskAction.RESPAWN, TaskAction.DONE);

    private record Rule(Set<TaskStatus> from, BiPredicate<TaskStatus, Facts> when,
                        Map<Outcome.Kind, TaskStatus> next) {

        boolean allows(TaskStatus status, Facts facts) {
            return from.contains(status) && when.test(status, facts);
        }
    }

    private static final Map<TaskAction, Rule> RULES = new EnumMap<>(TaskAction.class);

    static {
        // `ship` IS the human's approval, so a task an agent has not reported on passes too; a SHIPPING task
        // whose agent has DIED passes as recovery, while a live one means the push is already in flight. Once a
        // request exists, another round may be shipped onto it from wherever the task got to.
        rule(TaskAction.SHIP)
                .from(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, TaskStatus.SHIPPING,
                        TaskStatus.CI_POLLING, TaskStatus.CI_FAILED, TaskStatus.DEPLOYED, TaskStatus.REVERTED)
                .when((status, facts) -> switch (status) {
                    case IN_PROGRESS, REVIEW_PENDING -> true;
                    case SHIPPING -> !facts.agentLive().getAsBoolean();
                    default -> facts.hasReviewRequest();
                })
                .on(Outcome.Kind.OK, TaskStatus.CI_POLLING)
                .on(Outcome.Kind.RELAYED, TaskStatus.SHIPPING)
                .add();

        // Reading a review round decides nothing by itself: what it found is REPORTED, and that door has its own
        // rules below.
        rule(TaskAction.SWEEP)
                .fromAny()
                .when((status, facts) -> facts.hasReviewRequest())
                .add();

        // What a reviewer SAID is not a gate: deploy merges the task BRANCH, and git's only precondition is
        // commits on it. Excluded are the statuses where it could only race or refuse — nothing on the branch
        // yet, a push in flight, an agent committing into the branch this would merge, or a revert that leaves
        // the deploy branch already holding everything.
        rule(TaskAction.DEPLOY)
                .from(TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING, TaskStatus.CI_FAILED,
                        TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOYED,
                        TaskStatus.DEPLOY_CONFLICT)
                // A stalled deploy is finished by deploying again, whatever the request says.
                .when((status, facts) -> status == TaskStatus.DEPLOY_CONFLICT || facts.hasReviewRequest())
                .on(Outcome.Kind.OK, TaskStatus.DEPLOYED)
                .on(Outcome.Kind.CONFLICT, TaskStatus.DEPLOY_CONFLICT)
                .add();

        rule(TaskAction.REVERT)
                .from(TaskStatus.DEPLOYED)
                .on(Outcome.Kind.OK, TaskStatus.REVERTED)
                // Only some of it came out, so what is left is still live.
                .on(Outcome.Kind.PARTIAL, TaskStatus.DEPLOYED)
                .add();

        for (TaskAction action : ALWAYS) {
            rule(action).fromAny().add();
        }
    }

    /**
     * Statuses an AGENT may put its own task into. Everything else is jagt's to set — a task cannot talk itself
     * onto a shared branch, out of one, or closed.
     */
    private static final Set<TaskStatus> AGENT_REPORTABLE = EnumSet.of(TaskStatus.IN_PROGRESS,
            TaskStatus.SHIPPING, TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED, TaskStatus.CI_POLLING,
            TaskStatus.REVIEWED, TaskStatus.APPROVED);

    private FlowRules() {
    }

    public static boolean allows(TaskStatus status, TaskAction action, Facts facts) {
        Rule rule = RULES.get(action);
        return rule != null && rule.allows(status, facts);
    }

    /**
     * Every action legal for a task, what moves it on before what only looks at it — grouped rather than left to
     * the enum's own order, so both surfaces render the same card without either of them sorting.
     */
    public static List<TaskAction> allowed(TaskStatus status, Facts facts) {
        return RULES.entrySet().stream().filter(entry -> entry.getValue().allows(status, facts))
                .map(Map.Entry::getKey)
                .sorted(java.util.Comparator.comparing(TaskAction::group)).toList();
    }

    /** The status this outcome leads to, or empty to leave the task where it is. */
    public static Optional<TaskStatus> next(TaskAction action, Outcome.Kind outcome) {
        Rule rule = RULES.get(action);
        return rule == null ? Optional.empty() : Optional.ofNullable(rule.next().get(outcome));
    }

    /** Whether a task may be moved to {@code status} by its own agent rather than by jagt. */
    public static boolean reportable(TaskStatus status) {
        return AGENT_REPORTABLE.contains(status);
    }

    private static Builder rule(TaskAction action) {
        return new Builder(action);
    }

    private static final class Builder {

        private final TaskAction action;
        private Set<TaskStatus> from = EnumSet.allOf(TaskStatus.class);
        private BiPredicate<TaskStatus, Facts> when = (status, facts) -> true;
        private final Map<Outcome.Kind, TaskStatus> next = new EnumMap<>(Outcome.Kind.class);

        private Builder(TaskAction action) {
            this.action = action;
        }

        private Builder from(TaskStatus... statuses) {
            this.from = EnumSet.copyOf(List.of(statuses));
            return this;
        }

        private Builder fromAny() {
            return this;
        }

        private Builder when(BiPredicate<TaskStatus, Facts> when) {
            this.when = when;
            return this;
        }

        private Builder on(Outcome.Kind outcome, TaskStatus status) {
            next.put(outcome, status);
            return this;
        }

        private void add() {
            RULES.put(action, new Rule(from, when, Map.copyOf(next)));
        }
    }
}
