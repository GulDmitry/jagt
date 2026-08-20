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
 * <p>Java rather than configuration on purpose: every status and every action here is compiler-checked.
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
                        TaskStatus.CI_POLLING, TaskStatus.CI_FAILED, TaskStatus.REVIEWED, TaskStatus.DEPLOYED,
                        TaskStatus.REVERTED)
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

    /** Every action legal for a task, what moves it on before what only looks at it — so no surface sorts. */
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

    /** Whether the table says anything at all about this action — a verb it does not mention can never run. */
    public static boolean mentions(TaskAction action) {
        return RULES.containsKey(action);
    }

    /** Every status any action can lead to. */
    public static Set<TaskStatus> targets() {
        return RULES.values().stream().flatMap(rule -> rule.next().values().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }



    /** Whether a task may be moved to {@code status} by its own agent rather than by jagt. */
    public static boolean reportable(TaskStatus status) {
        return AGENT_REPORTABLE.contains(status);
    }

    /** The same question with the status the task is IN. */
    public static boolean reportable(TaskStatus from, TaskStatus to) {
        return refusedReport(from, to).isEmpty();
    }

    /**
     * Why a task in {@code from} may not report {@code to}, or empty when it may — one owner for the answer AND
     * the reason, because the reason is what an agent has to act on.
     *
     * <p>Two reports are not source-agnostic. Saying CI_POLLING is saying "a request is open and waiting", which
     * for a task the review has already passed drags it backwards and re-arms the unattended poll on work that
     * is done. And a task at REVERTED is one whose deploy was taken back out — that record is not an agent's to
     * erase by starting up and announcing itself, which is exactly what a `respawn` used to do (through
     * IN_PROGRESS and straight on to CI_POLLING, laundering the guard above). Everything else an agent may say
     * about itself holds wherever it got to.
     */
    public static Optional<String> refusedReport(TaskStatus from, TaskStatus to) {
        if (!reportable(to)) {
            return Optional.of(to + " is jagt's to set, not a task's to report");
        }
        if (to == TaskStatus.CI_POLLING && !BEFORE_THE_VERDICT.contains(from)) {
            return Optional.of(to + " cannot be reported by a task that is already " + from
                    + " — that would take it backwards and start polling finished work");
        }
        return Optional.empty();
    }

    /**
     * The status a report actually lands on. A task at REVERTED KEEPS it: what came back out of a shared branch
     * is a human's to move on from (`ship` for another round, `done` to close it), and a restarted agent
     * announcing itself used to erase that record — then reach CI_POLLING through the IN_PROGRESS it had just
     * claimed, laundering the guard above and re-arming the unattended poll on reverted work. The report is
     * ACCEPTED rather than refused, because the agent's own protocol is to keep saying what it is doing and to
     * ask questions without moving its task: a status it cannot report is a session whose every call errors.
     */
    public static TaskStatus reported(TaskStatus from, TaskStatus to) {
        return STANDS_UNTIL_MOVED_BY_A_HUMAN.contains(from) ? from : to;
    }

    private static final Set<TaskStatus> STANDS_UNTIL_MOVED_BY_A_HUMAN = EnumSet.of(TaskStatus.REVERTED);

    /** Statuses a task can still be waiting on its checks from. */
    private static final Set<TaskStatus> BEFORE_THE_VERDICT = EnumSet.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS,
            TaskStatus.SHIPPING, TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING, TaskStatus.CI_FAILED);

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
