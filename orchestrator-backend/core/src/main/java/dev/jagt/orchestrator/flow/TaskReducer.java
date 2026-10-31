package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The one place a task's status, stop, plan gate, report, turn, deployment, session, git trust, repository request, review requests, checks, approval and infrastructure re-runs are written: the chart's step, every effect applied in order. */
public final class TaskReducer {

    private TaskReducer() {
    }

    /** {@code move} applied to {@code task}; where the chart has no edge for it the status stays. */
    static TaskState reduce(TaskState task, TaskMove move, @Nullable String message, boolean event, long now) {
        List<TaskEffect> effects = effectsOf(task, move);
        if (effects.isEmpty()) {
            return stayed(task, message, event, now);
        }
        TaskState moved = task;
        for (TaskEffect effect : effects) {
            moved = apply(moved, effect, message, event, now);
        }
        return moved;
    }

    /** A line or a step written on the status the task already has, no move taken. */
    static TaskState stayed(TaskState task, @Nullable String message, boolean event, long now) {
        return task.withStatus(task.status(), message, event, now);
    }

    /** {@code task} stopped by {@code stop}; where a stop already stands, it stands alone. */
    public static TaskState stop(TaskState task, TaskStop stop, long now) {
        return step(task, new TaskMove.StopSet(stop), now);
    }

    /** {@code task} with its stop lifted by a human. */
    public static TaskState lift(TaskState task, long now) {
        return step(task, new TaskMove.Lifted(), now);
    }

    /** {@code task} with the stop that ends on {@code ends} cleared, where it was counted on {@code request} or none. */
    public static TaskState ended(TaskState task, TaskStop.Trigger.Ends ends, String request, long now) {
        TaskStop standing = task.stop();
        return standing == null || !standing.countedOnOrNone(request) ? task
                : step(task, new TaskMove.StopEnded(ends), now);
    }

    /** {@code task} just linked to its requests, with a stop counted on a request it no longer carries cleared. */
    public static TaskState relinked(TaskState task, long now) {
        TaskStop standing = task.stop();
        return standing == null || !standing.voidedBy(task.requests()) ? task
                : step(task, new TaskMove.RequestReplaced(), now);
    }

    /** {@code task} asked to open in plan mode; where it is not plan mode, or the code is written, the gate stays. */
    public static TaskState planAsked(TaskState task, boolean planMode) {
        return step(task, new TaskMove.PlanAsked(planMode), 0);
    }

    /** {@code task} with the plan the human approved, its gate open. */
    public static TaskState planApproved(TaskState task) {
        return step(task, new TaskMove.PlanApproved(), 0);
    }

    /** {@code task} whose session declared it needs no plan; a gate the human asked for stays. */
    public static TaskState noPlanDeclared(TaskState task) {
        return step(task, new TaskMove.NoPlanDeclared(), 0);
    }

    /** {@code task} born under autoplan: its gate shut until a plan is approved or declared needless. */
    public static TaskState autoplanned(TaskState task) {
        return step(task, new TaskMove.Autoplanned(), 0);
    }

    /** {@code task} with the report a session made; a status that holds its line keeps a report that is not a question. */
    static TaskState said(TaskState task, AgentReport means) {
        return step(task, new TaskMove.Said(means), 0);
    }

    /** {@code task} with a question jagt itself puts, on the status it already has. */
    static TaskState asked(TaskState task, AgentReport means, String message, long now) {
        return stayed(said(task, means), message, false, now);
    }

    /** {@code task} whose session declined a QA round: the deployed change holds DEPLOYED. */
    static TaskState qaDeclined(TaskState task, long now) {
        return step(task, new TaskMove.QaDeclined(), now);
    }

    /** {@code task} with the report a verb's stamp means. */
    static TaskState stamped(TaskState task, AgentReport means) {
        return step(task, new TaskMove.Stamped(means), 0);
    }

    /** {@code task} whose session was handed the turn {@code step} opens; a turn already open is replaced. */
    public static TaskState relayed(TaskState task, String step, String digest, String answered, long now) {
        return step(task, new TaskMove.Relayed(step, digest, answered), now);
    }

    /** {@code task} whose turn a report closed, the next holder's starting at {@code now}. */
    public static TaskState turnClosed(TaskState task, long now) {
        return step(task, new TaskMove.TurnClosed(), now);
    }

    /** {@code task} with the turn {@code before} put back where a relay failed to send. */
    public static TaskState turnRestored(TaskState task, Turn before) {
        return step(task, new TaskMove.TurnRestored(before), 0);
    }

    /** {@code task} with {@code step} recorded on its deployment; a step the phase has no edge for is dropped. */
    public static TaskState deployStepped(TaskState task, DeployStep step, long now) {
        return step(task, new TaskMove.DeployStepped(step), now);
    }

    /** {@code task} with the sign its session made recorded; a sign the phase has no edge for is dropped. */
    public static TaskState signed(TaskState task, SessionSign sign, long now) {
        return step(task, new TaskMove.Signed(sign), now);
    }

    /** {@code task} whose session starts over: nothing the old one signed speaks for the new one. */
    public static TaskState sessionStartedOver(TaskState task) {
        return step(task, new TaskMove.SessionStartedOver(), 0);
    }

    /** {@code task} trusting {@code trust} as the git state it obeys; a change found stands no longer. */
    public static TaskState gitTrusted(TaskState task, GitTrust trust) {
        return step(task, new TaskMove.GitTrusted(trust), 0);
    }

    /** {@code task} with a git change found, kept until a human trusts it; where nothing was trusted yet, nothing is kept. */
    public static TaskState gitChanged(TaskState task, String changed) {
        return step(task, new TaskMove.GitChanged(changed), 0);
    }

    /** {@code task} whose trusted git state takes in the entries of a repository added to it; where nothing was trusted, it stays so. */
    public static TaskState gitRepoJoined(TaskState task, GitTrust added) {
        return step(task, new TaskMove.GitRepoJoined(added), 0);
    }

    /** {@code task} with the repository its session needs, or none where the report names none. */
    public static TaskState repoAsked(TaskState task, @Nullable String project) {
        return step(task, project == null ? new TaskMove.RepoUnasked() : new TaskMove.RepoAsked(project), 0);
    }

    /** {@code task} with {@code repo} added, which ends the request for one. */
    public static TaskState repoAttached(TaskState task, TaskRepo repo) {
        return step(task, new TaskMove.RepoAttached(repo), 0);
    }

    /** {@code task} with the requests a report linked, a new round where it is one; the links are stamped {@code now}. */
    public static TaskState requestLinked(TaskState task, Map<String, String> byProject, String url,
                                          boolean newRound, long now) {
        return step(task, resolved(task, new TaskMove.RequestLinked(byProject, url, newRound, false, false)), now);
    }

    /** {@code task} with what git found of {@code project}'s request: merged into its target or not. */
    public static TaskState mergedByGit(TaskState task, String project, boolean merged) {
        return step(task, resolved(task, new TaskMove.MergedByGit(project, merged, false)), 0);
    }

    /** {@code task} whose host said {@code project}'s request is merged, a word git's later reads do not take back. */
    public static TaskState mergedByHost(TaskState task, String project) {
        return step(task, resolved(task, new TaskMove.MergedByHost(project, false)), 0);
    }

    /** {@code task} that has seen {@code tip} pushed, where it is not the head it already had. */
    public static TaskState headSeen(TaskState task, String tip) {
        return step(task, resolved(task, new TaskMove.HeadSeen(tip, false, false, InfraPhase.NONE)), 0);
    }

    /** {@code task} with the checks a read listed on {@code tip}, which ends any older round's failure to read them. */
    public static TaskState checksRead(TaskState task, String hostStatus, String tip) {
        return step(task, resolved(task, new TaskMove.ChecksRead(hostStatus, tip, false)), 0);
    }

    /** {@code task} with a round that could not read the checks: the last verdict stands, marked as not this round's. */
    public static TaskState checksUnread(TaskState task, ChecksUnread read) {
        return step(task, new TaskMove.ChecksUnread(read), 0);
    }

    /** {@code task} with the approval the host last said. */
    public static TaskState approvalRead(TaskState task, boolean approved) {
        return step(task, new TaskMove.ApprovalRead(approved), 0);
    }

    /** {@code task} with {@code retry} armed in place of any still waiting, the ones sent since the last lift kept. */
    public static TaskState infraArmed(TaskState task, InfraRetry retry) {
        return step(task, new TaskMove.InfraArmed(retry), 0);
    }

    /** {@code task} without the armed re-run, and without the sent ones where {@code all}. */
    public static TaskState infraCleared(TaskState task, boolean all) {
        return step(task, resolved(task, new TaskMove.InfraCleared(all, InfraPhase.NONE)), 0);
    }

    /** {@code task} whose armed re-run went out at {@code now}, counted with those sent. */
    public static TaskState infraSent(TaskState task, InfraRetry retry, long now) {
        return step(task, new TaskMove.InfraSent(retry), now);
    }

    /** {@code move} with the flags only the record can say filled in: where a head or a read stands against it. */
    static TaskMove resolved(TaskState task, TaskMove move) {
        return switch (move) {
            case TaskMove.HeadSeen seen -> new TaskMove.HeadSeen(seen.tip(),
                    !seen.tip().isBlank() && !seen.tip().equals(task.headTip()),
                    task.withHead(seen.tip()).readsStaleOn(task.checksTip()), unarmed(task));
            case TaskMove.RequestLinked links -> {
                TaskState after = linked(task, new TaskEffect.LinksRequests(links.byProject(), links.url(),
                        links.newRound()), 0);
                yield new TaskMove.RequestLinked(links.byProject(), links.url(), links.newRound(),
                        !after.repos().equals(task.repos()), RequestPhase.of(after) == RequestPhase.OPEN);
            }
            case TaskMove.MergedByGit merges -> new TaskMove.MergedByGit(merges.project(), merges.merged(),
                    RequestPhase.of(task.withMerged(merges.project(), merges.merged())) == RequestPhase.MERGED);
            case TaskMove.MergedByHost merges -> new TaskMove.MergedByHost(merges.project(),
                    RequestPhase.of(task.withMergedByHost(merges.project())) == RequestPhase.MERGED);
            case TaskMove.InfraCleared cleared -> new TaskMove.InfraCleared(cleared.all(), unarmed(task));
            case TaskMove.ChecksRead read -> new TaskMove.ChecksRead(read.hostStatus(), read.tip(),
                    !task.readsStaleOn(read.tip()));
            default -> move;
        };
    }

    private static InfraPhase unarmed(TaskState task) {
        return InfraPhase.of(task.withInfraRetries(sent(task)));
    }

    private static List<TaskEffect> effectsOf(TaskState task, TaskMove move) {
        return TaskChart.effects(Phases.of(task), move);
    }

    private static TaskState step(TaskState task, TaskMove move, long now) {
        TaskState moved = task;
        for (TaskEffect effect : effectsOf(task, move)) {
            moved = apply(moved, effect, null, false, now);
        }
        return moved;
    }

    private static List<InfraRetry> sent(TaskState task) {
        return task.infraRetries().stream().filter(InfraRetry::sent).toList();
    }

    private static List<InfraRetry> joined(List<InfraRetry> kept, InfraRetry retry) {
        List<InfraRetry> all = new ArrayList<>(kept);
        all.add(retry);
        return all;
    }

    private static TaskState linked(TaskState task, TaskEffect.LinksRequests links, long now) {
        if (links.byProject().isEmpty()) {
            return links.newRound() ? task.withReviewRound(links.url(), now) : task.withMrUrl(links.url(), now);
        }
        return links.newRound() ? task.withReviewRound(links.byProject(), now)
                : task.withMrUrls(links.byProject(), now);
    }

    private static TaskState apply(TaskState task, TaskEffect effect, @Nullable String message, boolean event,
                                   long now) {
        return switch (effect) {
            case TaskEffect.Lands lands -> task.withStatus(lands.to(), message, event, now);
            case TaskEffect.Stops stops -> task.withStop(stops.stop().countedOn(task.requests()));
            case TaskEffect.ClearsStop cleared -> task.lifted(now);
            case TaskEffect.GatesPlan gates -> task.withPlanGate(gates.gate());
            case TaskEffect.Reports reports -> task.withReport(reports.report());
            case TaskEffect.OpensTurn opens -> task.withTurn(task.turnOrNone()
                    .relayed(opens.step(), opens.digest(), opens.answered(), now));
            case TaskEffect.ClosesTurn closes -> task.withTurn(task.turnOrNone().closed(now));
            case TaskEffect.RestoresTurn restores -> task.withTurn(restores.before());
            case TaskEffect.Deploys deploys -> task.withDeployment(deploys.step()
                    .applyTo(task.deployment() == null ? Deployment.defaults() : task.deployment(), now));
            case TaskEffect.RestartsSession restarts -> task.withSession(Session.startedOver());
            case TaskEffect.TrustsGit trusts -> task.withGitTrust(trusts.trust());
            case TaskEffect.ParksGit parks -> task.withGitTrust(Objects.requireNonNull(task.gitTrust()).withChanged(parks.changed()));
            case TaskEffect.JoinsGit joins -> task.withGitTrust(Objects.requireNonNull(task.gitTrust()).including(joins.added()));
            case TaskEffect.AsksRepo asks -> task.withRepoRequest(asks.project());
            case TaskEffect.DropsRepoRequest drops -> task.withRepoRequest(null);
            case TaskEffect.AttachesRepo attaches -> task.withRepo(attaches.repo());
            case TaskEffect.LinksRequests links -> linked(task, links, now);
            case TaskEffect.MergesRequest merges -> task.withMerged(merges.project(), merges.merged());
            case TaskEffect.MergesRequestByHost merges -> task.withMergedByHost(merges.project());
            case TaskEffect.ResetsChecks resets -> task.withChecksReset();
            case TaskEffect.ResetsApproval resets -> task.withApproved(null);
            case TaskEffect.ResetsInfra resets -> task.withInfraRetries(List.of());
            case TaskEffect.SeesHead sees -> task.withHead(sees.tip());
            case TaskEffect.ReadsChecks reads -> task.withChecksRead(reads.hostStatus(), reads.tip());
            case TaskEffect.FailsToReadChecks fails -> task.withChecksUnread(fails.read());
            case TaskEffect.ReadsApproval reads -> task.withApproved(reads.approved());
            case TaskEffect.ArmsInfra arms -> task.withInfraRetries(joined(
                    InfraRetry.sentSince(task.infraRetries(), task.trailOrNone()), arms.retry()));
            case TaskEffect.ClearsInfra clears -> task.withInfraRetries(clears.all() ? List.of() : sent(task));
            case TaskEffect.SendsInfra sends -> task.withInfraRetries(joined(sent(task), sends.retry().sentAt(now)));
            case TaskEffect.DisarmsInfra disarms -> task.withInfraRetries(sent(task));
            case TaskEffect.Signs signs -> task.withSession(signs.sign().applyTo(task.session(), now));
        };
    }
}
