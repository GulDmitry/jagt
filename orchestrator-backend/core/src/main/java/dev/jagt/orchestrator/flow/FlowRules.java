package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * THE life of a task, in one file: which statuses allow which action, and what each outcome of that action leads
 * to. Nothing else may answer either question, so a card cannot advertise a move the gate then refuses. Java
 * rather than configuration, so every status and action here is compiler-checked.
 */
public final class FlowRules {

    /** An action a task's own status has nothing to say about: looking at it, or restarting its session. */
    private static final Set<TaskAction> ALWAYS = EnumSet.of(TaskAction.FOCUS, TaskAction.IDE, TaskAction.DIFF,
            TaskAction.RESPAWN, TaskAction.DONE);

    private record Rule(Set<TaskStatus> from, BiPredicate<TaskStatus, Facts> when,
                        Map<Outcome.Kind, TaskStatus> next, Map<Outcome.Kind, Set<TaskStatus>> keeps) {

        boolean allows(TaskStatus status, Facts facts) {
            return from.contains(status) && when.test(status, facts);
        }
    }

    private static final Map<TaskAction, Rule> RULES = new EnumMap<>(TaskAction.class);

    static {
        // `ship` IS the human's approval, so a task an agent has not reported on passes too; a SHIPPING task
        // whose agent has DIED passes as recovery, while a live one means the push is already in flight.
        rule(TaskAction.SHIP)
                .from(TaskStatus.IN_PROGRESS, TaskStatus.VERIFYING, TaskStatus.REVIEW_PENDING,
                        TaskStatus.SHIPPING, TaskStatus.CI_POLLING, TaskStatus.CI_FAILED, TaskStatus.REVIEWED,
                        TaskStatus.DEPLOYED, TaskStatus.REVERTED)
                .when((status, facts) -> switch (status) {
                    case IN_PROGRESS, VERIFYING, REVIEW_PENDING -> true;
                    case SHIPPING -> !facts.agentLive().getAsBoolean();
                    default -> facts.hasReviewRequest();
                })
                .on(Outcome.Kind.OK, TaskStatus.CI_POLLING)
                .on(Outcome.Kind.RELAYED, TaskStatus.SHIPPING)
                .add();

        // Reading a review round decides nothing by itself: what it found is REPORTED.
        rule(TaskAction.SWEEP)
                .fromAny()
                .when((status, facts) -> facts.hasReviewRequest())
                .add();

        // What a reviewer SAID is not a gate: deploy merges the task BRANCH, and git's only precondition is
        // commits on it. Excluded are the statuses where it could only race or refuse.
        rule(TaskAction.DEPLOY)
                .from(TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING, TaskStatus.CI_FAILED,
                        TaskStatus.REVIEWED, TaskStatus.APPROVED, TaskStatus.DEPLOYED,
                        TaskStatus.DEPLOY_CONFLICT)
                // A stalled deploy is finished by deploying again, whatever the request says.
                .when((status, facts) -> status == TaskStatus.DEPLOY_CONFLICT || facts.hasReviewRequest())
                .on(Outcome.Kind.OK, TaskStatus.DEPLOYED)
                .on(Outcome.Kind.CONFLICT, TaskStatus.DEPLOY_CONFLICT)
                .add();

        // A deploy that stopped part way, or one the task has moved on from, may have left a repository live.
        rule(TaskAction.REVERT)
                .fromAny()
                .when((status, facts) -> status == TaskStatus.DEPLOYED || status == TaskStatus.DEPLOY_CONFLICT
                        || facts.liveDeploy())
                .on(Outcome.Kind.OK, TaskStatus.REVERTED)
                // Only some of it came out, so what is left is still live.
                .on(Outcome.Kind.PARTIAL, TaskStatus.DEPLOYED)
                // Its half-merge still waits in the deploy worktree, which nothing resumes from DEPLOYED.
                .keeps(Outcome.Kind.PARTIAL, TaskStatus.DEPLOY_CONFLICT)
                .add();

        for (TaskAction action : ALWAYS) {
            rule(action).fromAny().add();
        }
    }

    /**
     * Statuses an AGENT may put its own task into. Everything else is jagt's to set — a task cannot talk itself
     * onto a shared branch, out of one, closed, or past its own review.
     */
    private static final Set<TaskStatus> AGENT_REPORTABLE = EnumSet.of(TaskStatus.PLAN_PENDING,
            TaskStatus.IN_PROGRESS,
            TaskStatus.SHIPPING, TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED, TaskStatus.CI_POLLING);

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

    /** The status this outcome leads a task in {@code from} to, or empty to leave the task where it is. */
    public static Optional<TaskStatus> next(TaskStatus from, TaskAction action, Outcome.Kind outcome) {
        Rule rule = RULES.get(action);
        if (rule == null || rule.keeps().getOrDefault(outcome, Set.of()).contains(from)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rule.next().get(outcome));
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
     * Why a task in {@code from} may not report {@code to}, or empty when it may. Two reports are not
     * source-agnostic: CI_POLLING says "a request is open and waiting", which for a task the review has already
     * passed drags it backwards and re-arms the unattended poll; REVERTED is a record no agent may erase.
     */
    public static Optional<String> refusedReport(TaskStatus from, TaskStatus to) {
        if (!reportable(to)) {
            return Optional.of(to + " is jagt's to set, not a task's to report");
        }
        if (to == TaskStatus.PLAN_PENDING && !BEFORE_THE_CODE.contains(from)) {
            return Optional.of(to + " cannot be reported by a task that is already " + from
                    + " — a plan is what comes before the code, not after it");
        }
        if (to == TaskStatus.CI_POLLING && !BEFORE_THE_VERDICT.contains(from)) {
            return Optional.of(to + " cannot be reported by a task that is already " + from
                    + " — that would take it backwards and start polling finished work");
        }
        return Optional.empty();
    }

    /**
     * The status a report actually lands on. A task at REVERTED KEEPS it: what came back out of a shared branch is
     * a human's to move on from; one at DEPLOY_CONFLICT keeps it until the deploy finishes. The report is ACCEPTED
     * rather than refused — an agent's protocol is to keep saying what it is doing, and a status it cannot report
     * is a session whose every call errors.
     */
    public static TaskStatus reported(TaskStatus from, TaskStatus to) {
        return reported(from, to, false);
    }

    /**
     * The same, told whether this project still owes a verification run. A hand-back waits at VERIFYING until
     * jagt has run the project's own command, so a human never opens a red tree.
     */
    public static TaskStatus reported(TaskStatus from, TaskStatus to, boolean verificationOwed) {
        if (HELD_AGAINST_A_REPORT.contains(from)) {
            return from;
        }
        return verificationOwed && to == TaskStatus.REVIEW_PENDING ? TaskStatus.VERIFYING : to;
    }

    /** What a read of the review round can conclude, which only the host's round read may land. */
    public static Set<TaskStatus> reads() {
        return EnumSet.copyOf(READ_FROM_THE_HOST);
    }

    /**
     * Where a round read lands. Red stops a task only where the round waits on the host, a session on it keeps
     * going; a verdict never undoes a deploy, since that would ask shipped work for an approval.
     */
    public static TaskStatus readLands(TaskStatus from, TaskStatus read) {
        if (!READ_FROM_THE_HOST.contains(read)) {
            throw new IllegalArgumentException(read + " is not what a read of the review round concludes");
        }
        if (read == TaskStatus.CI_FAILED) {
            return WAITING_ON_THE_HOST.contains(from) ? TaskStatus.CI_FAILED : from;
        }
        return HELD_AGAINST_A_REPORT.contains(from) || PAST_THE_REVIEW.contains(from) ? from : read;
    }

    /**
     * What a read of the review round leads to: a red run stops it, a clean round is approved or reviewed, and
     * anything else leaves the task where its session has it.
     */
    public static Optional<TaskStatus> readReview(boolean unresolved, boolean approved, Pipeline checks) {
        if (checks == Pipeline.RED) {
            return Optional.of(TaskStatus.CI_FAILED);
        }
        if (unresolved) {
            return Optional.empty();
        }
        if (approved) {
            return Optional.of(TaskStatus.APPROVED);
        }
        return checks == Pipeline.GREEN ? Optional.of(TaskStatus.REVIEWED) : Optional.empty();
    }

    /** A verified hand-back reaches the human; a failed one goes back to its session. */
    public static TaskStatus verified(boolean passed) {
        return passed ? TaskStatus.REVIEW_PENDING : TaskStatus.IN_PROGRESS;
    }

    /** A session handed findings, an answer or a go is at work again. */
    public static TaskStatus relayed() {
        return TaskStatus.IN_PROGRESS;
    }

    /** A task resumed onto a request already open waits on that request's checks. */
    public static TaskStatus resumedOnARequest() {
        return TaskStatus.CI_POLLING;
    }

    /** A session writing the round it hands back next. */
    public static boolean atWork(TaskStatus status) {
        return status == relayed();
    }

    /** A closed task: its worktree is gone, so nothing can be relayed into it. */
    public static boolean closed(TaskStatus status) {
        return status == TaskStatus.DONE;
    }

    /** Where the agent is expected to act, so its silence means death; NEW for one that died before reporting. */
    public static boolean watched(TaskStatus status) {
        return WATCHED.contains(status);
    }

    /** A status a session hands control back with: news to the human, and where its drafted replies wait. */
    public static boolean handsBack(TaskStatus status) {
        return HANDED_BACK.contains(status);
    }

    /** A hand-back the Master reads before the human, verification included. */
    public static boolean readByTheMasterNext(TaskStatus status) {
        return status == TaskStatus.REVIEW_PENDING || status == TaskStatus.VERIFYING;
    }

    /** A request green with every thread closed, which in act the Master deploys as the human would. */
    public static boolean deployedByTheMaster(TaskStatus status) {
        return WAITING_FOR_A_DEPLOY.contains(status);
    }

    /** A hand-back held until jagt has run the project's own verification command. */
    public static boolean awaitingVerification(TaskStatus status) {
        return status == TaskStatus.VERIFYING;
    }

    /** Live on the deploy branch, so its session checks the change where it landed. */
    public static boolean checkedWhereItLanded(TaskStatus status) {
        return status == TaskStatus.DEPLOYED;
    }

    /** A plan waiting to be read, rather than a round of code. */
    public static boolean holdsAPlan(TaskStatus status) {
        return status == TaskStatus.PLAN_PENDING;
    }

    /** A deploy waiting in the deploy worktree, where its conflict is resolved. */
    public static boolean conflictedInTheDeployWorktree(TaskStatus status) {
        return status == TaskStatus.DEPLOY_CONFLICT;
    }

    /** A round read that found the request approved. */
    public static boolean approved(TaskStatus read) {
        return read == TaskStatus.APPROVED;
    }

    /** A round read that found nothing unresolved and the checks green, with no approval yet. */
    public static boolean reviewed(TaskStatus read) {
        return read == TaskStatus.REVIEWED;
    }

    /** Whether the work has left the worktree, which is the only point a tracker's word can end the task. */
    public static boolean handedOver(TaskStatus status) {
        return HANDED_OVER.contains(status);
    }

    /** Statuses no action leads to and no task reports: a redirect in {@link #reported} is the only way in. */
    public static Set<TaskStatus> redirects() {
        return EnumSet.of(TaskStatus.VERIFYING);
    }

    private static final Set<TaskStatus> WATCHED = EnumSet.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS,
            TaskStatus.SHIPPING);

    private static final Set<TaskStatus> HANDED_BACK = EnumSet.of(TaskStatus.REVIEW_PENDING, TaskStatus.CI_FAILED);

    private static final Set<TaskStatus> WAITING_FOR_A_DEPLOY = EnumSet.of(TaskStatus.REVIEWED, TaskStatus.APPROVED);

    private static final Set<TaskStatus> HELD_AGAINST_A_REPORT = EnumSet.of(TaskStatus.REVERTED,
            TaskStatus.DEPLOY_CONFLICT);

    private static final Set<TaskStatus> READ_FROM_THE_HOST = EnumSet.of(TaskStatus.REVIEWED, TaskStatus.APPROVED,
            TaskStatus.CI_FAILED);

    private static final Set<TaskStatus> WAITING_ON_THE_HOST = EnumSet.of(TaskStatus.CI_POLLING,
            TaskStatus.REVIEWED, TaskStatus.APPROVED);

    /** Statuses a round is BEHIND: the code went to the shared branch without waiting for what it says. */
    private static final Set<TaskStatus> PAST_THE_REVIEW = EnumSet.of(TaskStatus.DEPLOY_CONFLICT,
            TaskStatus.DEPLOYED, TaskStatus.DONE);

    /** Statuses the work has been handed over from, so a tracker reaching its own last stage may close it. */
    private static final Set<TaskStatus> HANDED_OVER = EnumSet.of(TaskStatus.REVIEWED, TaskStatus.APPROVED,
            TaskStatus.DEPLOYED);

    /** Statuses a task has written nothing from yet, so a plan is still the next thing it hands over. */
    private static final Set<TaskStatus> BEFORE_THE_CODE = EnumSet.of(TaskStatus.NEW, TaskStatus.PLAN_PENDING,
            TaskStatus.IN_PROGRESS);

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
        private final Map<Outcome.Kind, Set<TaskStatus>> keeps = new EnumMap<>(Outcome.Kind.class);

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

        private Builder keeps(Outcome.Kind outcome, TaskStatus status) {
            keeps.put(outcome, EnumSet.of(status));
            return this;
        }

        private void add() {
            RULES.put(action, new Rule(from, when, Map.copyOf(next), Map.copyOf(keeps)));
        }
    }
}
