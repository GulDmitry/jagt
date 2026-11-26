package dev.jagt.orchestrator.flow.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.session.Wake;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.text.UtcTime;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * One task as a human surface sees it: ONE projection for every front-end. Serialized straight to
 * JSON, which is why the actions carry their wire ids and labels rather than an enum name.
 */
public record TaskView(
    @JsonUnwrapped Identity identity,
    @JsonUnwrapped Standing standing,
    @JsonUnwrapped Ask asking,
    @JsonUnwrapped Request request,
    @JsonUnwrapped Replies replies,
    @JsonUnwrapped Delivery delivery) {

  public record Identity(
      String id,
      @Nullable String alias,
      @Nullable String project,
      @Nullable String title,
      @Nullable String ticketUrl,
      @Nullable String tier) {}

  public record Standing(
      TaskStatus status,
      // The same status in words a human needs no glossary for; the enum name stays the wire value.
      String statusLabel,
      Phase phase,
      Owner owner,
      // How loudly it asks: what the header counts and what the own-move filter keeps.
      Attention attention,
      // Since when it has been in THIS status — not the activity stamp a keep-alive bumps.
      long statusSince,
      List<Step> history,
      // Why jagt stopped it; null while it is not stopped.
      @Nullable Stop stop) {}

  public record Ask(
      // WHICH act is wanted, short enough for a chip; null exactly when the tier above is NONE.
      @Nullable String ask,
      String hint,
      List<ActionView> actions,
      // Keyed by action id for every verb asked before it is sent, offered on this card or not.
      Map<String, String> confirmations,
      String detail,
      DashboardLine.Kind detailKind,
      // What the session waits on and when jagt next says so; null while it does not wait.
      @Nullable String waiting) {}

  public record Request(
      @Nullable String reviewRequestUrl,
      // When the code host says the review request was opened; 0 = no request, or no read has said
      // yet.
      long requestOpenedAt,
      // Whether the request is approved; null until a read has said. No status can answer this.
      @Nullable Boolean approved,
      // `pipeline` is the verdict anything decides on; `pipelineSaid` is the host's own wording,
      // for display.
      Pipeline pipeline,
      @Nullable String pipelineSaid,
      // Whether the newest round could not read them, so the verdict above is an older round's.
      boolean pipelineUnread) {}

  public record Replies(
      // Drafted review replies are waiting in the worktree; read on the record, so no page is sent
      // it.
      @JsonIgnore boolean draftedReplies,
      // When the drafted replies were last written; 0 while none stand.
      long repliesWrittenAt,
      AutoReviewWatch autoReview,
      // What the session judged on each switch that was on: name to "yes — why" or "no — why".
      Map<String, String> switches) {}

  public record Delivery(
      // A single-repo task has one entry, so nothing needs a second shape for the ordinary case.
      List<RepoView> repos,
      // Whether its code is on a shared branch RIGHT NOW; only a revert takes it back off.
      boolean deployed) {}

  public String id() {
    return identity.id();
  }

  public @Nullable String alias() {
    return identity.alias();
  }

  public @Nullable String project() {
    return identity.project();
  }

  public @Nullable String title() {
    return identity.title();
  }

  public @Nullable String ticketUrl() {
    return identity.ticketUrl();
  }

  public TaskStatus status() {
    return standing.status();
  }

  public String statusLabel() {
    return standing.statusLabel();
  }

  private static String statusLabel(TaskState task, RoundState round) {
    if (task.work().paused()) {
      return "‖ paused";
    }
    if (round.arbiterReading() || StopHold.of(task, round.arbiter()) == StopHold.ARBITER) {
      return "master review";
    }
    Optional<String> passer = round.checkers().keySet().stream().filter(task::passedBy).findFirst();
    if (passer.isPresent()) {
      return Checkers.passedLabel(passer.get());
    }
    return task.requestsMerged() && FlowRules.awaitsApproval(task.standing().status())
        ? "merged"
        : task.standing().status().label();
  }

  public Map<String, String> confirmations() {
    return asking.confirmations();
  }

  private static Map<String, String> confirmations(
      String id, TaskState task, Map<String, String> deployBranches) {
    List<String> targets =
        task.repos().stream()
            .map(repo -> {
              String branch = deployBranches.get(repo.project());
              return repo.project()
                  + " → "
                  + (branch == null || branch.isBlank() ? "no deployBranch in jagt.yml" : branch);
            })
            .toList();
    Map<String, String> confirmations = new LinkedHashMap<>();
    for (TaskAction action : TaskAction.values()) {
      action
          .confirmation(id, targets)
          .ifPresent(question -> confirmations.put(action.id(), question));
    }
    return confirmations;
  }

  public @Nullable String waiting() {
    return asking.waiting();
  }

  private static @Nullable String waiting(TaskState task) {
    if (task.standing().report() != AgentReport.WAIT) {
      return null;
    }
    String next =
        Wake.parse(task.work().turnOrNone().mark(Wake.MARK))
            .map(wake -> "\nnext try " + UtcTime.clock(wake.at()))
            .orElse("");
    return (task.standing().message() == null ? "waiting" : task.standing().message()) + next;
  }

  public Owner owner() {
    return standing.owner();
  }

  public Attention attention() {
    return standing.attention();
  }

  public List<Step> history() {
    return standing.history();
  }

  public @Nullable Stop stop() {
    return standing.stop();
  }

  public @Nullable String ask() {
    return asking.ask();
  }

  public List<ActionView> actions() {
    return asking.actions();
  }

  public String detail() {
    return asking.detail();
  }

  public @Nullable String reviewRequestUrl() {
    return request.reviewRequestUrl();
  }

  public long requestOpenedAt() {
    return request.requestOpenedAt();
  }

  public @Nullable Boolean approved() {
    return request.approved();
  }

  public Pipeline pipeline() {
    return request.pipeline();
  }

  public @Nullable String pipelineSaid() {
    return request.pipelineSaid();
  }

  public boolean draftedReplies() {
    return replies.draftedReplies();
  }

  public List<RepoView> repos() {
    return delivery.repos();
  }

  public boolean deployed() {
    return delivery.deployed();
  }

  /**
   * {@code reason} is the stop in a few words, {@code evidence} what it saw, {@code next} what a
   * human reads after, {@code triage} the read of the stop as one line, blank until made.
   */
  public record Stop(
      TaskStop.Trigger.Kind kind,
      String reason,
      String evidence,
      String next,
      long at,
      String triage) {

    static @Nullable Stop of(@Nullable TaskStop stop) {
      return stop == null
          ? null
          : new Stop(
              stop.trigger().kind(),
              stop.trigger().reason(),
              stop.evidence(),
              stop.trigger().next(),
              stop.at(),
              stop.triage() == null ? "" : stop.triage().line());
    }
  }

  /** One status the task passed through, in the words the board shows it by. */
  public record Step(
      TaskStatus status,
      String label,
      long at,
      @Nullable ActionOrigin origin,
      @Nullable String why,
      @Nullable String role) {}

  /**
   * {@code primary} = the move's own verb, and the move is the human's: where an agent holds it
   * nothing is filled. {@code again} = this verb has already run and what it did is still live, so
   * pressing it repeats it. Built in one place from a verb with every field set, so no field is
   * ever left to a default.
   */
  public record ActionView(
      String id,
      String label,
      String hint,
      String key,
      @JsonUnwrapped Emphasis emphasis,
      @JsonUnwrapped Placement placement) {

    public record Emphasis(boolean primary, boolean again, boolean readOnly) {}

    public record Placement(String group, boolean onRow, boolean apart) {}

    public boolean primary() {
      return emphasis.primary();
    }

    public boolean again() {
      return emphasis.again();
    }

    public String group() {
      return placement.group();
    }

    public boolean onRow() {
      return placement.onRow();
    }

    public boolean apart() {
      return placement.apart();
    }
  }

  public record RepoView(@Nullable String project, @Nullable String reviewRequestUrl) {}

  public static TaskView of(
      FlowRules rules,
      String id,
      TaskState task,
      RoundState round,
      @Nullable AutoReviewWatch autoReview,
      Map<String, String> deployBranches) {
    boolean draftedReplies = round.draftedReplies();
    Stop stop = Stop.of(task.work().stop());
    Facts projected = Facts.projected(task).withReach(round.reach());
    AutoReviewWatch watch = autoReview == null ? AutoReviewWatch.none() : autoReview;
    Move move =
        Move.forTask(
            rules,
            task.standing().status(),
            projected.withDelivery(
                projected.delivery().withShippedWithoutRequest(round.requestless())),
            round,
            SessionPhase.silent(task),
            watch);
    boolean deployed = Facts.deployLive(task);
    DashboardLine.Line line =
        DashboardLine.forTask(task, webLink(task.mrUrl()), round.reach().sessionHeld());
    List<ActionView> actions =
        move.actions().stream()
            .filter(action -> !action.typed())
            .map(action ->
                new ActionView(
                    action.id(),
                    action.label(),
                    action.hint(task),
                    action.key(),
                    new ActionView.Emphasis(
                        action == move.primary() && move.owner() == Owner.YOU,
                        deployed && action == TaskAction.DEPLOY,
                        action.readOnly()),
                    new ActionView.Placement(action.group().id(), action.onRow(), action.apart())))
            .toList();
    return new TaskView(
        new Identity(
            id,
            task.ticket().alias(),
            task.project(),
            task.ticket().title(),
            webLink(task.ticket().ticketUrl()),
            task.work().tier() != null && task.work().tier().raised()
                ? task.work().tier().current()
                : null),
        new Standing(
            task.standing().status(),
            statusLabel(task, round),
            move.phase(),
            move.owner(),
            move.attention(),
            task.standing().statusSince(),
            stepsOf(task),
            stop),
        new Ask(
            asked(move, stop),
            move.hint(),
            actions,
            confirmations(id, task, deployBranches),
            line.text(),
            line.kind(),
            waiting(task)),
        new Request(
            webLink(task.mrUrl()),
            task.hasReviewRequest() ? task.round().requestOpenedAt() : 0,
            task.hasReviewRequest() ? task.approvedRead() : null,
            checks(task),
            checksSaid(task),
            task.checks().unread()),
        new Replies(draftedReplies, round.repliesWrittenAt(), watch, task.gate().switches()),
        new Delivery(
            task.repos().stream()
                .map(repo -> new RepoView(repo.project(), webLink(repo.mrUrl())))
                .toList(),
            deployed));
  }

  private static Optional<InfraRetry> awaitingRetry(TaskState task) {
    return Pipeline.of(task.checks().read()) == Pipeline.RED
        ? InfraRetry.pending(task.checks().infraRetries())
        : Optional.empty();
  }

  /** A red whose retry is armed reads as running: its verdict is not in. */
  private static Pipeline checks(TaskState task) {
    return awaitingRetry(task).isPresent() ? Pipeline.RUNNING : Pipeline.of(task.checks().read());
  }

  private static @Nullable String checksSaid(TaskState task) {
    return awaitingRetry(task).map(InfraRetry::reading).orElse(task.checks().read());
  }

  private static List<Step> stepsOf(TaskState task) {
    List<Step> steps =
        task.standing().history().stream()
            .map(change ->
                new Step(
                    change.status(),
                    change.status().label(),
                    change.at(),
                    change.origin(),
                    change.why(),
                    change.role()))
            .collect(Collectors.toCollection(ArrayList::new));
    TaskStop stop = task.work().stop();
    if (task.work().paused() && stop != null) {
      steps.add(
          new Step(task.standing().status(), "‖ paused", stop.at(), null, stop.evidence(), null));
    }
    return steps;
  }

  private static @Nullable String asked(Move move, @Nullable Stop stop) {
    return stop != null
            && stop.kind() != TaskStop.Trigger.Kind.PAUSE
            && move.primary() == TaskAction.LIFT
        ? move.ask() + " — " + stop.reason()
        : move.ask();
  }

  /**
   * A link a page can put in an {@code href}, or nothing. Neither URL is jagt's own, so a {@code
   * javascript:} or {@code data:} URL would run on the page that renders it; anything but http(s)
   * is dropped, not escaped.
   */
  private static @Nullable String webLink(@Nullable String url) {
    if (url == null) {
      return null;
    }
    String trimmed = url.strip();
    boolean web =
        trimmed.regionMatches(true, 0, "http://", 0, 7)
            || trimmed.regionMatches(true, 0, "https://", 0, 8);
    return web ? trimmed : null;
  }
}
