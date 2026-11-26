package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.StopTriage;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The one place a task's status, stop, plan gate, report, turn, deployment, session, git trust,
 * repository request, review requests, checks, approval and infrastructure re-runs are written: the
 * chart's step, every effect applied in order.
 */
public final class TaskReducer {

  private static final long TIMELESS = 0;

  private final TaskChart chart;

  public TaskReducer(TaskChart chart) {
    this.chart = chart;
  }

  /** {@code move} applied to {@code task}; where the chart has no edge for it the status stays. */
  TaskState reduce(
      TaskState task, TaskMove move, @Nullable String message, boolean event, long now) {
    List<TaskEffect> effects = effectsOf(task, move);
    return effects.isEmpty()
        ? stayed(task, message, event, now)
        : applied(task, effects, message, event, now);
  }

  /** A line or a step written on the status the task already has, no move taken. */
  TaskState stayed(TaskState task, @Nullable String message, boolean event, long now) {
    return task.withStatus(task.standing().status(), message, event, now);
  }

  /** {@code task} stopped by {@code stop}; where a stop already stands, it stands alone. */
  public TaskState stop(TaskState task, TaskStop stop, long now) {
    return step(task, new TaskMove.StopSet(stop), now);
  }

  /** {@code task} with {@code read} on its stop, where that stop is the one keyed {@code key}. */
  public TaskState triaged(TaskState task, String key, StopTriage read) {
    TaskStop stop = task.work().stop();
    return stop != null && stop.triage() == null && stop.key().equals(key)
        ? task.withStop(stop.withTriage(read))
        : task;
  }

  private static @Nullable ModelTier lowered(TaskState task) {
    return task.work().tier() == null ? null : task.work().tier().lower();
  }

  private static @Nullable ModelTier raised(TaskState task) {
    return task.work().tier() == null ? null : task.work().tier().raise();
  }

  /** {@code task} with its stop lifted and its session due on the next model tier. */
  public TaskState escalated(TaskState task, long now) {
    return step(task, new TaskMove.Escalated(), now);
  }

  /** {@code task} with its stop lifted by a human. */
  public TaskState lift(TaskState task, long now) {
    return step(task, new TaskMove.Lifted(), now);
  }

  /**
   * {@code task} with the stop that ends on {@code ends} cleared, where it was counted on {@code
   * request} or none.
   */
  public TaskState ended(TaskState task, TaskStop.Trigger.Ends ends, String request, long now) {
    TaskStop standing = task.work().stop();
    return standing == null || !standing.countedOnOrNone(request)
        ? task
        : step(task, new TaskMove.StopEnded(ends), now);
  }

  /**
   * {@code task} just linked to its requests, with a stop counted on a request it no longer carries
   * cleared.
   */
  public TaskState relinked(TaskState task, long now) {
    TaskStop standing = task.work().stop();
    return standing == null || !standing.voidedBy(task.requests())
        ? task
        : step(task, new TaskMove.RequestReplaced(), now);
  }

  /** {@code task} with {@code kind} told for {@code key}. */
  public TaskState told(TaskState task, ToldOnce.Kind kind, String key) {
    return task.withTold(task.round().toldOrNone().with(kind, key));
  }

  /** {@code task} with {@code point} told for {@code key}. */
  public TaskState told(TaskState task, Point point, String key) {
    return task.withTold(task.round().toldOrNone().withPoint(point.id(), key));
  }

  /** {@code task} with each of {@code kinds} told no more. */
  public TaskState untold(TaskState task, ToldOnce.Kind... kinds) {
    return task.withTold(task.round().toldOrNone().without(kinds));
  }

  /**
   * {@code task} asked to open in plan mode; where it is not plan mode, or the code is written, the
   * gate stays.
   */
  public TaskState planAsked(TaskState task, boolean planMode) {
    return step(task, new TaskMove.PlanAsked(planMode));
  }

  /** {@code task} with the plan the human approved, its gate open. */
  public TaskState planApproved(TaskState task) {
    return step(task, new TaskMove.PlanApproved()).withPlanApproved(true);
  }

  /** {@code task} whose session declared it needs no plan; a gate the human asked for stays. */
  public TaskState noPlanDeclared(TaskState task) {
    return step(task, new TaskMove.NoPlanDeclared(task.ticket().openedOutside()));
  }

  /** Why a session may not declare {@code task} needs no plan, empty where it may. */
  public Optional<String> refusedNoPlan(TaskState task) {
    PlanGate plan = task.gate().planGate();
    boolean taken =
        plan != PlanGate.CLOSED
            || !chart
                .effects(
                    Phases.at(TaskStatus.IN_PROGRESS, plan),
                    new TaskMove.NoPlanDeclared(task.ticket().openedOutside()))
                .isEmpty();
    return taken
        ? Optional.empty()
        : Optional.of(
            "no_plan refused: an outside system opened this task, so its plan gate is jagt's"
                + " — report PLAN_PENDING with the plan, or a question if the ticket is unsound");
  }

  /**
   * Why {@code by} may not approve the plan of {@code task}, empty where it may; {@code sound} is
   * read only where the answer depends on it.
   */
  public Optional<String> refusedApproval(
      TaskState task, @Nullable ActionOrigin by, BooleanSupplier sound) {
    PlanGate plan = task.gate().planGate();
    boolean outside = task.ticket().openedOutside();
    boolean human = ChartFacts.human(by);
    if (!plan.closed() || human || !outside) {
      return Optional.empty();
    }
    return chart.takesApproval(plan, new TaskMove.ApprovalAsked(true, false, sound.getAsBoolean()))
        ? Optional.empty()
        : Optional.of(
            "the plan waits for the human: an outside system opened this task and plan.md is not"
                + " headed `verdict: sound`");
  }

  /**
   * {@code task} born under autoplan: its gate shut until a plan is approved or declared needless.
   */
  public TaskState autoplanned(TaskState task) {
    return step(task, new TaskMove.Autoplanned());
  }

  /**
   * {@code task} with the report a session made; a status that holds its line keeps a report that
   * is not a question.
   */
  TaskState said(TaskState task, AgentReport means) {
    return step(task, new TaskMove.Said(means));
  }

  /** {@code task} with a question jagt itself puts, on the status it already has. */
  TaskState asked(TaskState task, AgentReport means, String message, long now) {
    return stayed(said(task, means), message, false, now);
  }

  /**
   * {@code task} whose session answered {@code means} on {@code reported}: a round of {@code role}
   * it declines holds DEPLOYED.
   */
  TaskState checkerDeclined(
      TaskState task, String role, AgentReport means, TaskStatus reported, long now) {
    return step(task, new TaskMove.Checked.RoundDeclined(role, means, reported), now);
  }

  /**
   * {@code task} whose {@code role} read of {@code step} on {@code digest} ended: the turn it
   * opened closes where it still stands.
   */
  public TaskState checkerReadEnded(
      TaskState task, String role, String step, String digest, long now) {
    return step(
        task,
        new TaskMove.Checked.ReadEnded(role, task.work().turnOrNone().holds(step, digest)),
        now);
  }

  /** {@code task} whose Master offered the human {@code step} instead of pressing it. */
  public TaskState arbiterLeft(TaskState task, String step) {
    return noted(task, new MasterNote.Left(step));
  }

  /** {@code task} whose Master no longer leaves a step to the human. */
  public TaskState arbiterTook(TaskState task) {
    return noted(task, new MasterNote.Took());
  }

  /** {@code task} whose Master gave the question {@code digest} no usable answer. */
  public TaskState arbiterUnanswered(TaskState task, String digest) {
    return noted(task, new MasterNote.Unanswered(digest));
  }

  /** {@code task} whose Master pressed deploy on the arrival {@code arrival}. */
  public TaskState arbiterPressedDeploy(TaskState task, String arrival) {
    return noted(task, new MasterNote.PressedDeploy(arrival));
  }

  /** {@code task} whose Master was asked about the stop {@code key}. */
  public TaskState arbiterAskedStop(TaskState task, String key) {
    return noted(task, new MasterNote.AskedStop(key));
  }

  /**
   * {@code task} whose Master has lifted {@code count} stops since the human's last lift or deploy.
   */
  public TaskState arbiterLiftedStops(TaskState task, int count) {
    return noted(task, new MasterNote.LiftedStops(count));
  }

  /**
   * {@code task} with the fingerprint of the verdict jagt wrote for its Master, answering {@code
   * round}.
   */
  public TaskState arbiterSealed(TaskState task, String seal, long round) {
    return noted(task, new MasterNote.Sealed(seal, round));
  }

  /** {@code task} whose Master found the verdict file it sealed gone or not the one jagt wrote. */
  public TaskState arbiterVerdictGone(TaskState task) {
    return arbiterSealed(task, "", 0);
  }

  private TaskState noted(TaskState task, MasterNote note) {
    return step(task, resolved(task, new TaskMove.MasterNoted(note, MasterPhase.STANDS_IN)));
  }

  /** {@code task} that {@code role} passed at the deploy standing on {@code sha}. */
  public TaskState checkerPassed(TaskState task, String role, String sha) {
    return step(task, resolved(task, new TaskMove.Checked.Passed(role, sha, false)));
  }

  /**
   * {@code task} that {@code role} sent a round on, the deploy's asked {@code digest} and the
   * {@code rows} it carried.
   */
  public TaskState checkerRoundSent(TaskState task, String role, String digest, String rows) {
    return step(task, resolved(task, new TaskMove.Checked.RoundSent(role, digest, rows, false)));
  }

  /** {@code task} with the report a verb's stamp means. */
  TaskState stamped(TaskState task, AgentReport means) {
    return step(task, new TaskMove.Stamped(means));
  }

  /**
   * {@code task} whose session was handed the turn {@code step} opens; a turn already open is
   * replaced.
   */
  public TaskState relayed(TaskState task, String step, String digest, String answered, long now) {
    return step(task, new TaskMove.Relayed(step, digest, answered), now);
  }

  /** {@code task} whose turn a report closed, the next holder's starting at {@code now}. */
  public TaskState turnClosed(TaskState task, long now) {
    return step(task, new TaskMove.TurnClosed(), now);
  }

  /** {@code task} with the turn {@code before} put back where a relay failed to send. */
  public TaskState turnRestored(TaskState task, Turn before) {
    return step(task, new TaskMove.TurnRestored(before));
  }

  /**
   * {@code task} with {@code step} recorded on its deployment; a step the phase has no edge for is
   * dropped.
   */
  public TaskState deployStepped(TaskState task, DeployStep step, long now) {
    return step(task, new TaskMove.DeployStepped(step), now);
  }

  /**
   * {@code task} whose stalled conflict is put to the Master, once, as its session's question
   * {@code reason}; where no conflict stands, nothing is asked.
   */
  public TaskState conflictReferred(TaskState task, String reason, long now) {
    boolean unasked =
        task.currentConflict().filter(conflict -> !conflict.askedTheArbiter()).isPresent();
    TaskState referred = deployStepped(task, new DeployStep.Referred(), now);
    boolean nowAsked = referred.currentConflict().filter(Conflict::askedTheArbiter).isPresent();
    return unasked && nowAsked ? asked(referred, AgentReport.QUESTION, reason, now) : referred;
  }

  /**
   * {@code task} with the sign its session made recorded; a sign the phase has no edge for is
   * dropped.
   */
  public TaskState signed(TaskState task, SessionSign sign, long now) {
    return step(task, new TaskMove.Signed(sign), now);
  }

  /** {@code task} whose session starts over: nothing the old one signed speaks for the new one. */
  public TaskState sessionStartedOver(TaskState task) {
    return step(task, new TaskMove.SessionStartedOver());
  }

  /**
   * {@code task} trusting {@code trust} as the git state it obeys; a change found stands no longer.
   */
  public TaskState gitTrusted(TaskState task, GitTrust trust) {
    return step(task, new TaskMove.GitTrusted(trust));
  }

  /**
   * {@code task} with a git change found, kept until a human trusts it; where nothing was trusted
   * yet, nothing is kept.
   */
  public TaskState gitChanged(TaskState task, String changed) {
    return step(task, new TaskMove.GitChanged(changed));
  }

  /**
   * {@code task} whose trusted git state takes in the entries of a repository added to it; where
   * nothing was trusted, it stays so.
   */
  public TaskState gitRepoJoined(TaskState task, GitTrust added) {
    return step(task, new TaskMove.GitRepoJoined(added));
  }

  /** {@code task} with the repository its session needs, or none where the report names none. */
  public TaskState repoAsked(TaskState task, @Nullable String project) {
    return step(
        task, project == null ? new TaskMove.RepoUnasked() : new TaskMove.RepoAsked(project));
  }

  /** {@code task} with {@code repo} added, which ends the request for one. */
  public TaskState repoAttached(TaskState task, TaskRepo repo) {
    return step(task, new TaskMove.RepoAttached(repo));
  }

  /** {@code task} holding {@code repos} in place of the scratch folder it planned in. */
  public TaskState placed(TaskState task, List<TaskRepo> repos, @Nullable String baseBranch) {
    return step(task, new TaskMove.Placed(repos, baseBranch));
  }

  /** {@code task} without {@code project}, its request ended with it; the last repository stays. */
  public TaskState repoLeft(TaskState task, String project, long now) {
    return step(
        task,
        resolved(
            task, new TaskMove.RepoLeft(project, RequestPhase.NONE, TaskMove.ConflictDrop.NONE)),
        now);
  }

  /** {@code task} without {@code project}, and without the trusted git entries only it held. */
  public TaskState repoLeft(TaskState task, String project, Set<String> trustedOnlyByIt, long now) {
    TaskState left = repoLeft(task, project, now);
    GitTrust trust = left.gate().gitTrust();
    return trust == null || left.repos().size() == task.repos().size()
        ? left
        : left.withGitTrust(trust.excluding(trustedOnlyByIt));
  }

  /**
   * {@code task} with the requests a report linked, a new round where it is one; the links are
   * stamped {@code now}.
   */
  public TaskState requestLinked(
      TaskState task, Map<String, String> byProject, String url, boolean newRound, long now) {
    return step(
        task,
        resolved(task, new TaskMove.RequestLinked(byProject, url, newRound, false, false)),
        now);
  }

  /**
   * {@code task} with what git found of {@code project}'s request: merged into its target or not.
   */
  public TaskState mergedByGit(TaskState task, String project, boolean merged) {
    return step(task, resolved(task, new TaskMove.MergedByGit(project, merged, false)));
  }

  /**
   * {@code task} whose host said {@code project}'s request is merged, a word git's later reads do
   * not take back.
   */
  public TaskState mergedByHost(TaskState task, String project) {
    return step(task, resolved(task, new TaskMove.MergedByHost(project, false)));
  }

  /** {@code task} that has seen {@code tip} pushed, where it is not the head it already had. */
  public TaskState headSeen(TaskState task, String tip) {
    return step(task, resolved(task, new TaskMove.HeadSeen(tip, false, false, InfraPhase.NONE)));
  }

  /**
   * {@code task} with the checks a read listed on {@code tip}, which ends any older round's failure
   * to read them.
   */
  public TaskState checksRead(TaskState task, String hostStatus, String tip) {
    return step(task, resolved(task, new TaskMove.ChecksRead(hostStatus, tip, false)));
  }

  /**
   * {@code task} with a round that could not read the checks: the last verdict stands, marked as
   * not this round's.
   */
  public TaskState checksUnread(TaskState task, ChecksUnread read) {
    return step(task, new TaskMove.ChecksUnread(read));
  }

  /** {@code task} with the approval the host last said. */
  public TaskState approvalRead(TaskState task, boolean approved) {
    return step(task, new TaskMove.ApprovalRead(approved));
  }

  /**
   * {@code task} with {@code retry} armed in place of any still waiting, the ones sent since the
   * last lift kept.
   */
  public TaskState infraArmed(TaskState task, InfraRetry retry) {
    return step(task, new TaskMove.InfraArmed(retry));
  }

  /** {@code task} without the armed re-run, and without the sent ones where {@code all}. */
  public TaskState infraCleared(TaskState task, boolean all) {
    return step(task, resolved(task, new TaskMove.InfraCleared(all, InfraPhase.NONE)));
  }

  /** {@code task} whose armed re-run went out at {@code now}, counted with those sent. */
  public TaskState infraSent(TaskState task, InfraRetry retry, long now) {
    return step(task, new TaskMove.InfraSent(retry), now);
  }

  /**
   * {@code move} with the flags only the record can say filled in: where a head or a read stands
   * against it.
   */
  TaskMove resolved(TaskState task, TaskMove move) {
    return switch (move) {
      case TaskMove.HeadSeen seen ->
          new TaskMove.HeadSeen(
              seen.tip(),
              !seen.tip().isBlank() && !seen.tip().equals(task.checks().headTip()),
              task.withHead(seen.tip()).checks().readsStaleOn(task.checks().checksTip()),
              unarmed(task));
      case TaskMove.RequestLinked links -> {
        TaskState after =
            linked(
                task,
                new TaskEffect.LinksRequests(links.byProject(), links.url(), links.newRound()),
                0);
        yield new TaskMove.RequestLinked(
            links.byProject(),
            links.url(),
            links.newRound(),
            !after.repos().equals(task.repos()),
            RequestPhase.of(after) == RequestPhase.OPEN);
      }
      case TaskMove.MergedByGit merges ->
          new TaskMove.MergedByGit(
              merges.project(),
              merges.merged(),
              RequestPhase.of(task.withMerged(merges.project(), merges.merged()))
                  == RequestPhase.MERGED);
      case TaskMove.MergedByHost merges ->
          new TaskMove.MergedByHost(
              merges.project(),
              RequestPhase.of(task.withMergedByHost(merges.project())) == RequestPhase.MERGED);
      case TaskMove.RepoLeft left ->
          new TaskMove.RepoLeft(
              left.project(),
              RequestPhase.of(task.withoutRepo(left.project())),
              dropOf(task, left.project()));
      case TaskMove.MasterNoted noted ->
          new TaskMove.MasterNoted(
              noted.note(),
              MasterPhase.of(task.withMaster(noted.note().applyTo(task.work().arbiterOrNone()))));
      case TaskMove.Checked.Passed passed ->
          new TaskMove.Checked.Passed(
              passed.role(),
              passed.sha(),
              CheckerPhase.of(
                      task.withRole(
                          passed.role(),
                          task.work().roleOrNone(passed.role()).withPassed(passed.sha())),
                      passed.role())
                  == CheckerPhase.PASSED);
      case TaskMove.Checked.RoundSent sent ->
          new TaskMove.Checked.RoundSent(
              sent.role(),
              sent.digest(),
              sent.rows(),
              CheckerPhase.of(
                      task.withRole(
                          sent.role(),
                          task.work()
                              .roleOrNone(sent.role())
                              .withRound(sent.digest(), sent.rows())),
                      sent.role())
                  == CheckerPhase.ROUND_OPEN);
      case TaskMove.InfraCleared cleared -> new TaskMove.InfraCleared(cleared.all(), unarmed(task));
      case TaskMove.ChecksRead read ->
          new TaskMove.ChecksRead(
              read.hostStatus(), read.tip(), !task.checks().readsStaleOn(read.tip()));
      default -> move;
    };
  }

  /**
   * A report ends what the Master left unanswered: a question reported afresh is a new one,
   * whatever its words.
   */
  private static TaskState reported(TaskState task, AgentReport report) {
    return task.withReport(report).withMaster(task.work().arbiterOrNone().withUnanswered(""));
  }

  private static TaskMove.ConflictDrop dropOf(TaskState task, String project) {
    Deployment deployment = task.work().deployment();
    if (task.standing().status() != TaskStatus.DEPLOY_CONFLICT
        || !task.canLose(project)
        || deployment == null) {
      return TaskMove.ConflictDrop.NONE;
    }
    return deployment
        .conflictedTarget()
        .filter(target -> target.project().equals(project))
        .map(target -> dropOf(deployment, target))
        .orElse(TaskMove.ConflictDrop.NONE);
  }

  private static TaskMove.ConflictDrop dropOf(Deployment deployment, Deployment.Target conflicted) {
    return switch (Objects.requireNonNull(conflicted.progress().conflict()).cause()) {
      case RED_PIPELINE -> TaskMove.ConflictDrop.LANDS;
      case REVERT -> TaskMove.ConflictDrop.NONE;
      case MERGE ->
          !deployment.live()
              ? TaskMove.ConflictDrop.RETURNS
              : deployment.indexOf(conflicted.project()) < deployment.targets().size() - 1
                  ? TaskMove.ConflictDrop.HOLDS
                  : TaskMove.ConflictDrop.LANDS;
    };
  }

  private static InfraPhase unarmed(TaskState task) {
    return InfraPhase.of(task.withInfraRetries(sent(task)));
  }

  private List<TaskEffect> effectsOf(TaskState task, TaskMove move) {
    return chart.effects(Phases.of(task), move);
  }

  private TaskState step(TaskState task, TaskMove move, long now) {
    return applied(task, effectsOf(task, move), null, false, now);
  }

  private TaskState step(TaskState task, TaskMove move) {
    List<TaskEffect> effects = effectsOf(task, move);
    effects.stream()
        .filter(TaskEffect::readsTime)
        .findFirst()
        .ifPresent(effect -> {
          throw new IllegalStateException(
              "a move sent without a time yields " + effect.getClass().getSimpleName());
        });
    return applied(task, effects, null, false, TIMELESS);
  }

  private static TaskState applied(
      TaskState task, List<TaskEffect> effects, @Nullable String message, boolean event, long now) {
    TaskState moved = task;
    for (TaskEffect effect : effects) {
      moved = apply(moved, effect, message, event, now);
    }
    return moved;
  }

  private static List<InfraRetry> sent(TaskState task) {
    return task.checks().infraRetries().stream().filter(InfraRetry::sent).toList();
  }

  private static List<InfraRetry> joined(List<InfraRetry> kept, InfraRetry retry) {
    List<InfraRetry> all = new ArrayList<>(kept);
    all.add(retry);
    return all;
  }

  private static TaskState linked(TaskState task, TaskEffect.LinksRequests links, long now) {
    if (links.byProject().isEmpty()) {
      return links.newRound()
          ? task.withReviewRound(links.url(), now)
          : task.withMrUrl(links.url(), now);
    }
    return links.newRound()
        ? task.withReviewRound(links.byProject(), now)
        : task.withMrUrls(links.byProject(), now);
  }

  private static TaskState declined(
      TaskState task, String role, @Nullable String message, boolean event, long now) {
    TaskState landed = task.withStatus(TaskStatus.DEPLOYED, message, event, now);
    RoleRecord record = landed.work().roleOrNone(role);
    return landed.withRole(role, record.withDeclined(record.roundDigest()));
  }

  private static TaskState apply(
      TaskState task, TaskEffect effect, @Nullable String message, boolean event, long now) {
    return switch (effect) {
      case TaskEffect.Lands lands -> task.withStatus(lands.to(), message, event, now);
      case TaskEffect.Stops stops -> task.withStop(stops.stop().countedOn(task.requests()));
      case TaskEffect.ClearsStop _ -> task.lifted(now);
      case TaskEffect.Lifts _ -> task.lifted(now).withTier(lowered(task));
      case TaskEffect.Escalates _ -> task.lifted(now).withTier(raised(task));
      case TaskEffect.GatesPlan gates -> task.withPlanGate(gates.gate());
      case TaskEffect.Reports reports -> reported(task, reports.report());
      case TaskEffect.OpensTurn opens ->
          task.withTurn(
              task.work()
                  .turnOrNone()
                  .relayed(opens.step(), opens.digest(), opens.answered(), now));
      case TaskEffect.ClosesTurn _ -> task.withTurn(task.work().turnOrNone().closed(now));
      case TaskEffect.RestoresTurn restores -> task.withTurn(restores.before());
      case TaskEffect.Deploys deploys ->
          task.withDeployment(
              deploys
                  .step()
                  .applyTo(
                      task.work().deployment() == null
                          ? Deployment.defaults()
                          : task.work().deployment(),
                      now));
      case TaskEffect.RestartsSession _ ->
          task.withSession(Session.startedOver(task.work().session()));
      case TaskEffect.TrustsGit trusts -> task.withGitTrust(trusts.trust());
      case TaskEffect.ParksGit parks ->
          task.withGitTrust(
              Objects.requireNonNull(task.gate().gitTrust()).withChanged(parks.changed()));
      case TaskEffect.JoinsGit joins ->
          task.withGitTrust(
              Objects.requireNonNull(task.gate().gitTrust()).including(joins.added()));
      case TaskEffect.AsksRepo asks -> task.withRepoRequest(asks.project());
      case TaskEffect.DropsRepoRequest _ -> task.withRepoRequest(null);
      case TaskEffect.AttachesRepo attaches -> task.withRepo(attaches.repo());
      case TaskEffect.Places places -> task.placedIn(places.repos(), places.baseBranch());
      case TaskEffect.LeavesRepo leaves -> task.withoutRepo(leaves.project());
      case TaskEffect.LinksRequests links -> linked(task, links, now);
      case TaskEffect.MergesRequest merges -> task.withMerged(merges.project(), merges.merged());
      case TaskEffect.MergesRequestByHost merges -> task.withMergedByHost(merges.project());
      case TaskEffect.ResetsChecks _ -> task.withChecksReset();
      case TaskEffect.ResetsApproval _ -> task.withApproved(null);
      case TaskEffect.ResetsInfra _ -> task.withInfraRetries(List.of());
      case TaskEffect.NotesMaster notes ->
          task.withMaster(notes.note().applyTo(task.work().arbiterOrNone()));
      case TaskEffect.OpensCheckerRead opens ->
          task.withRole(
              opens.role(), task.work().roleOrNone(opens.role()).withReading(opens.step()));
      case TaskEffect.ClosesCheckerRead closes ->
          task.withRole(closes.role(), task.work().roleOrNone(closes.role()).withReading(""));
      case TaskEffect.PassesChecker passes ->
          task.withRole(
              passes.role(), task.work().roleOrNone(passes.role()).withPassed(passes.sha()));
      case TaskEffect.SendsCheckerRound sends ->
          task.withRole(
              sends.role(),
              task.work().roleOrNone(sends.role()).withRound(sends.digest(), sends.rows()));
      case TaskEffect.DeclinesRound declines ->
          declined(task, declines.role(), message, event, now);
      case TaskEffect.SeesHead sees -> task.withHead(sees.tip());
      case TaskEffect.ReadsChecks reads -> task.withChecksRead(reads.hostStatus(), reads.tip());
      case TaskEffect.FailsToReadChecks fails -> task.withChecksUnread(fails.read());
      case TaskEffect.ReadsApproval reads -> task.withApproved(reads.approved());
      case TaskEffect.ArmsInfra arms ->
          task.withInfraRetries(
              joined(
                  InfraRetry.sentSince(task.checks().infraRetries(), task.round().trailOrNone()),
                  arms.retry()));
      case TaskEffect.ClearsInfra clears ->
          task.withInfraRetries(clears.all() ? List.of() : sent(task));
      case TaskEffect.SendsInfra sends ->
          task.withInfraRetries(joined(sent(task), sends.retry().sentAt(now)));
      case TaskEffect.DisarmsInfra _ -> task.withInfraRetries(sent(task));
      case TaskEffect.Signs signs ->
          task.withSession(signs.sign().applyTo(task.work().session(), now));
    };
  }
}
