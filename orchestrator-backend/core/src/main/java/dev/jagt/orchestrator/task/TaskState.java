package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.task.deploy.Checks;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.plan.Holds;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.review.Round;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.session.Work;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.ticket.Ticket;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.HumanWait;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Standing;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.ModelTier;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * One task, as {@code state.json} holds it.
 *
 * <p>A task works in one or MORE repositories but always in ONE session: {@code repos.get(0)} is
 * where that session runs, and the single-repo accessors answer for it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskState(
    List<TaskRepo> repos,
    @JsonUnwrapped Standing standing,
    @JsonUnwrapped Ticket ticket,
    @JsonUnwrapped Round round,
    @JsonUnwrapped Checks checks,
    @JsonUnwrapped Holds gate,
    @JsonUnwrapped Work work) {

  public TaskState {
    repos = repos == null ? List.of() : List.copyOf(repos);
  }

  /**
   * Reads BOTH shapes: the current one with {@code repos}, and older files with those fields at the
   * top level.
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static TaskState fromJson(StoredTask stored) {
    return stored.read();
  }

  public TaskStateBuilder toBuilder() {
    return new TaskStateBuilder(repos, standing.status())
        .lastActiveTimestamp(standing.lastActiveTimestamp())
        .message(standing.message())
        .report(standing.report())
        .history(standing.history())
        .alias(ticket.alias())
        .title(ticket.title())
        .ticketUrl(ticket.ticketUrl())
        .baseBranch(ticket.baseBranch())
        .mrCreatedAt(round.mrCreatedAt())
        .requestOpenedAt(round.requestOpenedAt())
        .lastPolledAt(round.lastPolledAt())
        .autoReview(round.autoReview())
        .approved(round.approved())
        .told(round.told())
        .trail(round.trail())
        .pipelineStatus(checks.pipelineStatus())
        .checksUnread(checks.checksUnread())
        .checksTip(checks.checksTip())
        .headTip(checks.headTip())
        .infraRetries(checks.infraRetries())
        .pipelineUrl(checks.pipelineUrl())
        .planGate(gate.planGate())
        .probes(gate.probes())
        .switches(gate.switches())
        .gitTrust(gate.gitTrust())
        .refusals(gate.refusals())
        .usage(gate.usage())
        .agentSpend(gate.agentSpend())
        .session(work.session())
        .turn(work.turn())
        .waits(work.waits())
        .stop(work.stop())
        .repoRequest(work.repoRequest())
        .deployment(work.deployment())
        .roles(work.roles())
        .master(work.master());
  }

  @JsonIgnore
  public TaskRepo primary() {
    return repos.isEmpty() ? TaskRepo.of(null, null) : repos.get(0);
  }

  /** Holds no repository yet: it plans in a scratch folder. */
  @JsonIgnore
  public boolean unplaced() {
    return projects().isEmpty();
  }

  @JsonIgnore
  public @Nullable String project() {
    return primary().project();
  }

  @JsonIgnore
  public @Nullable String worktreePath() {
    return primary().worktreePath();
  }

  @JsonIgnore
  public @Nullable String remoteUrl() {
    return primary().remoteUrl();
  }

  /**
   * The primary repo's request, else the first repo's that has one; a multi-repo task has one per
   * repo.
   */
  @JsonIgnore
  public @Nullable String mrUrl() {
    return primary().mrUrl() != null
        ? primary().mrUrl()
        : repos.stream().map(TaskRepo::mrUrl).filter(Objects::nonNull).findFirst().orElse(null);
  }

  @JsonIgnore
  public List<String> projects() {
    return repos.stream().map(TaskRepo::project).filter(p -> p != null && !p.isBlank()).toList();
  }

  @JsonIgnore
  public java.util.Optional<TaskRepo> repo(String project) {
    return repos.stream()
        .filter(r -> r.project() != null && r.project().equals(project))
        .findFirst();
  }

  /** Empty when that project is not one of the task's, or holds no request yet. */
  @JsonIgnore
  public Optional<String> reviewRequestOf(String project) {
    return repos.stream()
        .filter(repo -> Objects.requireNonNull(repo.project()).equals(project))
        .map(TaskRepo::mrUrl)
        .filter(url -> url != null && !url.isBlank())
        .findFirst();
  }

  /** Every repository's request URL, in order of the URLs. */
  @JsonIgnore
  public List<String> requests() {
    return repos.stream()
        .map(TaskRepo::mrUrl)
        .filter(url -> url != null && !url.isBlank())
        .sorted()
        .toList();
  }

  /** True when ANY of the repos has a review request open. */
  @JsonIgnore
  public boolean hasReviewRequest() {
    return repos.stream().anyMatch(TaskRepo::hasReviewRequest);
  }

  /**
   * True when every repository has a request and git found each one inside its target: nothing is
   * left to ship.
   */
  @JsonIgnore
  public boolean requestsMerged() {
    return repos.stream().allMatch(repo -> repo.hasReviewRequest() && repo.wasMerged());
  }

  /** A status move — the ONE place history grows. A new line says nothing about the report. */
  public TaskState withStatus(TaskStatus status, @Nullable String message, long now) {
    return withStatus(status, message, false, now);
  }

  public TaskState withStatus(
      TaskStatus status, @Nullable String message, boolean event, long now) {
    Session session = work.session();
    Session calmed =
        session
            .withQuietSince(0)
            .withHalt(
                session.halt() != null && session.halt().kind() == Session.Halt.Kind.IDLE
                    ? session.halt()
                    : null);
    return withStanding(standing.moved(status, message, event, now)).withSession(calmed);
  }

  /**
   * A NEW review round on one repository's request. The status that follows is not decided here.
   */
  public TaskState withReviewRound(@Nullable String project, String reviewRequestUrl, long now) {
    return relinked(project, reviewRequestUrl, now)
        .lastActiveTimestamp(now)
        // The polling window is per ROUND, not per request; lastPolledAt=0 means "poll at the next
        // tick".
        .mrCreatedAt(now)
        .lastPolledAt(0)
        .build();
  }

  /** The single-repo form: the round landed on the repo the agent works in. */
  public TaskState withReviewRound(String reviewRequestUrl, long now) {
    return withReviewRound(primary().project(), reviewRequestUrl, now);
  }

  /**
   * One ship, however many repositories it landed in: every URL goes on its own repo, and the round
   * is recorded ONCE, on the link of the first repo it landed in, the session repo leading; a repo
   * it missed stays unlinked.
   */
  public TaskState withReviewRound(Map<String, String> urlByProject, long now) {
    TaskState linked = withMrUrls(urlByProject, now);
    return repos.stream()
        .map(TaskRepo::project)
        .filter(Objects::nonNull)
        .filter(urlByProject::containsKey)
        .findFirst()
        .map(project ->
            linked.withReviewRound(project, Objects.requireNonNull(urlByProject.get(project)), now))
        .orElse(linked);
  }

  /** Links each repository to its own request WITHOUT recording a round. */
  public TaskState withMrUrls(Map<String, String> urlByProject, long now) {
    TaskState linked = this;
    for (Map.Entry<String, String> request : urlByProject.entrySet()) {
      linked = linked.withMrUrl(request.getKey(), request.getValue(), now);
    }
    return linked;
  }

  public TaskState withRequestOpenedAt(long requestOpenedAt) {
    return withRound(round.withRequestOpenedAt(requestOpenedAt));
  }

  /**
   * What a round actually READ off the host on {@code tip}, which also ends any older round's
   * failure to read it.
   */
  public TaskState withChecksRead(String hostStatus, @Nullable String tip) {
    return withChecks(checks.withRead(hostStatus, tip));
  }

  /** The pipeline address a read listed; a read that listed none must not erase one that did. */
  public TaskState withPipelineUrl(@Nullable String url) {
    return withChecks(checks.withPipelineUrl(url));
  }

  /** The newest commit a read saw pushed. */
  public TaskState withHead(@Nullable String tip) {
    return withChecks(checks.withHead(tip));
  }

  /**
   * The approval as the host last said it of the request's current head; null where it was read on
   * another or on none.
   */
  public @Nullable Boolean approvedRead() {
    return readsStale() ? null : round.approved();
  }

  private boolean readsStale() {
    return checks.readsStaleOn(checks.checksTip());
  }

  /** A round that could not read them: the last verdict stands, marked as not this round's. */
  public TaskState withChecksUnread(ChecksUnread read) {
    return withChecks(checks.withUnread(read));
  }

  public TaskState withMoveRecorded(long now) {
    return withStanding(standing.moveRecorded(now));
  }

  public TaskState withInfraRetries(List<InfraRetry> retries) {
    return withChecks(checks.withInfraRetries(retries));
  }

  /**
   * No checks read, none unread: a round's answers describe a dead state once the round is over.
   */
  public TaskState withChecksReset() {
    return withChecks(checks.reset());
  }

  private TaskState withStanding(Standing changed) {
    return changed.equals(standing)
        ? this
        : new TaskState(repos, changed, ticket, round, checks, gate, work);
  }

  private TaskState withTicket(Ticket changed) {
    return changed.equals(ticket)
        ? this
        : new TaskState(repos, standing, changed, round, checks, gate, work);
  }

  private TaskState withRound(Round changed) {
    return changed.equals(round)
        ? this
        : new TaskState(repos, standing, ticket, changed, checks, gate, work);
  }

  private TaskState withChecks(Checks changed) {
    return changed.equals(checks)
        ? this
        : new TaskState(repos, standing, ticket, round, changed, gate, work);
  }

  private TaskState withGate(Holds changed) {
    return changed.equals(gate)
        ? this
        : new TaskState(repos, standing, ticket, round, checks, changed, work);
  }

  private TaskState withWork(Work changed) {
    return changed.equals(work)
        ? this
        : new TaskState(repos, standing, ticket, round, checks, gate, changed);
  }

  public TaskState withApproved(@Nullable Boolean approved) {
    return withRound(round.withApproved(approved));
  }

  /** Does NOT touch lastActiveTimestamp: a refused call moved nothing. */
  public TaskState refused(RefusalKind kind) {
    return withGate(gate.refused(kind));
  }

  public TaskState withLastChangeOrigin(ActionOrigin origin) {
    return withStanding(standing.withLastChangeOrigin(origin));
  }

  public TaskState withLastChangeRole(String role) {
    return withStanding(standing.withLastChangeRole(role));
  }

  public TaskState withLastChangeWhy(String why) {
    return withStanding(standing.withLastChangeWhy(why));
  }

  public TaskState withLastShipTree(String tree) {
    return withStanding(standing.withLastShipTree(tree));
  }

  public TaskState touched(long now) {
    return withStanding(standing.touched(now));
  }

  /** The status, line and deployment {@code read} had, written back as one move. */
  public TaskState restoredFrom(TaskState read, long now) {
    return withStatus(read.standing.status(), read.standing.message(), now).toBuilder()
        .deployment(read.work.deployment())
        .planGate(read.gate.planGate())
        .report(read.standing.report())
        .build();
  }

  public TaskState withReport(AgentReport report) {
    return withStanding(standing.withReport(report));
  }

  public TaskState withMrUrl(@Nullable String project, String mrUrl, long now) {
    return relinked(project, mrUrl, now).build();
  }

  public TaskState withMrUrl(String mrUrl, long now) {
    return withMrUrl(primary().project(), mrUrl, now);
  }

  /**
   * Points one repository at a request, dropping every answer a read gave about the old one and
   * stamping the new one as opened NOW — a request reaches here only as it is being opened. A
   * {@code resume} adopting somebody else's request is linked through the builder instead, and
   * keeps its unknown age.
   */
  private TaskStateBuilder relinked(@Nullable String project, String mrUrl, long now) {
    List<TaskRepo> repos = mapRepo(project, repo -> repo.withMrUrl(mrUrl));
    if (repos.equals(this.repos)) {
      return toBuilder().repos(repos);
    }
    return toBuilder().repos(repos).requestOpenedAt(now);
  }

  /** {@code project} holding no request, its read answers gone with it. */
  public TaskState withoutRequest(String project) {
    return toBuilder().repos(mapRepo(project, repo -> repo.withMrUrl(null))).build();
  }

  public TaskState withMerged(String project, boolean merged) {
    return toBuilder().repos(mapRepo(project, repo -> repo.withMerged(merged))).build();
  }

  public TaskState withMergedByHost(String project) {
    return toBuilder().repos(mapRepo(project, TaskRepo::withMergedByHost)).build();
  }

  public TaskState withRemoteUrl(String project, String remoteUrl) {
    return toBuilder().repos(mapRepo(project, repo -> repo.withRemoteUrl(remoteUrl))).build();
  }

  public TaskState withLastPolledAt(long lastPolledAt) {
    return withRound(round.withLastPolledAt(lastPolledAt));
  }

  /** A null project key means the primary repo. */
  private List<TaskRepo> mapRepo(@Nullable String project, UnaryOperator<TaskRepo> change) {
    List<TaskRepo> updated = new ArrayList<>(repos.size());
    for (TaskRepo repo : repos) {
      boolean matches = project == null ? repo.equals(primary()) : project.equals(repo.project());
      updated.add(matches ? change.apply(repo) : repo);
    }
    return List.copyOf(updated);
  }

  /** Does NOT touch lastActiveTimestamp: reading a session's own log is not that session moving. */
  public TaskState withAgentSpend(AgentSpend spend) {
    return withGate(gate.withAgentSpend(spend));
  }

  /** Does NOT touch lastActiveTimestamp: what git holds is not agent activity. */
  public TaskState withGitTrust(GitTrust trust) {
    return withGate(gate.withGitTrust(trust));
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
  public TaskState withTrail(RoundTrail trail) {
    return withRound(round.withTrail(trail));
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
  public TaskState withStop(TaskStop stop) {
    return withWork(work.withStop(stop));
  }

  /** The stop cleared and the trail counting anew from {@code now}. */
  public TaskState lifted(long now) {
    return withRound(round.withTrail(RoundTrail.liftedAt(now))).withWork(work.lifted());
  }

  /** The conflict this task stands in now, as its deployment records it. */
  @JsonIgnore
  public Optional<Conflict> currentConflict() {
    return standing.status() == TaskStatus.DEPLOY_CONFLICT
        ? Optional.ofNullable(work.deployment())
            .flatMap(Deployment::conflictedTarget)
            .map(target -> target.progress().conflict())
        : Optional.empty();
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded them, the agent did nothing. */
  public TaskState withProbes(List<Probe> probes) {
    return withGate(gate.withProbes(probes));
  }

  /**
   * Does NOT touch lastActiveTimestamp: it records what the session asked, and null is it asking
   * nothing.
   */
  public TaskState withRepoRequest(@Nullable String project) {
    return withWork(work.withRepoRequest(project));
  }

  /** Adds the repository the session asked for, which ends the request. */
  public TaskState withRepo(TaskRepo repo) {
    List<TaskRepo> grown = new ArrayList<>(repos);
    grown.add(repo);
    return toBuilder().repos(grown).repoRequest(null).build();
  }

  public TaskState placedIn(List<TaskRepo> placed, @Nullable String baseBranch) {
    return toBuilder().repos(placed).repoRequest(null).baseBranch(baseBranch).build();
  }

  /** The primary repository stays, so the last one does. */
  public boolean canLose(String project) {
    return !project.equals(project());
  }

  public TaskState withoutRepo(String project) {
    if (!canLose(project)) {
      return this;
    }
    return toBuilder()
        .repos(repos.stream().filter(repo -> !project.equals(repo.project())).toList())
        .build();
  }

  public TaskState withBaseBranch(String baseBranch) {
    return withTicket(ticket.withBaseBranch(baseBranch));
  }

  public TaskState judged(Map<String, String> judged) {
    return withGate(gate.judged(judged));
  }

  /**
   * The role passed the change now live: its record names the tips it checked, so a later deploy
   * shows none.
   */
  public boolean passedBy(String role) {
    return standing.status() == TaskStatus.DEPLOYED && work.passedOnTips(role);
  }

  public TaskState withMaster(Master master) {
    return withWork(work.withMaster(master));
  }

  public TaskState withRole(String role, RoleRecord record) {
    return withWork(work.withRole(role, record));
  }

  public TaskState withMark(String step, String value) {
    return withTurn(work.turnOrNone().withMark(step, value));
  }

  public TaskState withoutMark(String step) {
    return withTurn(work.turnOrNone().withoutMark(step));
  }

  public TaskState withTurn(Turn turn) {
    return withWork(work.withTurn(turn));
  }

  public TaskState withDeployment(Deployment deployment) {
    return withWork(work.withDeployment(deployment));
  }

  public TaskState withWaits(List<HumanWait> waits) {
    return withWork(work.withWaits(waits));
  }

  public TaskState withTold(ToldOnce told) {
    return withRound(round.withTold(told));
  }

  public TaskState withSession(Session session) {
    return withWork(work.withSession(session));
  }

  public TaskState withTier(@Nullable ModelTier tier) {
    return withSession(work.session().withTier(tier));
  }

  public TaskState withPlanGate(PlanGate planGate) {
    return withGate(gate.withPlanGate(planGate));
  }

  /** Does NOT touch lastActiveTimestamp: metering is not agent activity. */
  public TaskState withUsageAdded(TokenUsage added) {
    return withGate(gate.withUsageAdded(added));
  }

  public static TaskStateBuilder builder(
      @Nullable String project, @Nullable String worktreePath, TaskStatus status) {
    return builder(List.of(TaskRepo.of(project, worktreePath)), status);
  }

  public static TaskStateBuilder builder(List<TaskRepo> repos, TaskStatus status) {
    return new TaskStateBuilder(
        repos == null || repos.isEmpty() ? List.of(TaskRepo.of(null, null)) : repos, status);
  }

  /** Missing fields stay unset; the single-repo setters write the FIRST repo. */
  public static class TaskStateBuilder {
    private List<TaskRepo> repos;
    private TaskStatus status;
    private long lastActiveTimestamp;
    private @Nullable String message;
    private AgentReport report = AgentReport.PLAIN;
    private @Nullable List<StatusChange> history;
    private @Nullable String alias;
    private @Nullable String title;
    private @Nullable String ticketUrl;
    private @Nullable String baseBranch;
    private long mrCreatedAt;
    private long requestOpenedAt;
    private long lastPolledAt;
    private @Nullable Boolean autoReview;
    private @Nullable Boolean approved;
    private @Nullable ToldOnce told;
    private @Nullable RoundTrail trail;
    private @Nullable String pipelineStatus;
    private List<ChecksUnread> checksUnread = List.of();
    private @Nullable String checksTip;
    private @Nullable String headTip;
    private List<InfraRetry> infraRetries = List.of();
    private @Nullable String pipelineUrl;
    private PlanGate planGate = PlanGate.OPEN;
    private List<Probe> probes = List.of();
    private Map<String, String> switches = Map.of();
    private @Nullable GitTrust gitTrust;
    private Map<RefusalKind, Integer> refusals = Map.of();
    private @Nullable TokenUsage usage;
    private @Nullable AgentSpend agentSpend;
    private Session session = Session.defaults();
    private @Nullable Turn turn;
    private List<HumanWait> waits = List.of();
    private @Nullable TaskStop stop;
    private @Nullable String repoRequest;
    private @Nullable Deployment deployment;
    private Map<String, RoleRecord> roles = Map.of();
    private @Nullable Master master;

    private TaskStateBuilder(List<TaskRepo> repos, TaskStatus status) {
      this.repos = repos;
      this.status = status;
    }

    public TaskStateBuilder repos(List<TaskRepo> repos) {
      this.repos = repos;
      return this;
    }

    public TaskStateBuilder lastActiveTimestamp(long lastActiveTimestamp) {
      this.lastActiveTimestamp = lastActiveTimestamp;
      return this;
    }

    public TaskStateBuilder message(@Nullable String message) {
      this.message = message;
      return this;
    }

    public TaskStateBuilder report(AgentReport report) {
      this.report = report;
      return this;
    }

    public TaskStateBuilder history(@Nullable List<StatusChange> history) {
      this.history = history;
      return this;
    }

    public TaskStateBuilder alias(@Nullable String alias) {
      this.alias = alias;
      return this;
    }

    public TaskStateBuilder title(@Nullable String title) {
      this.title = title;
      return this;
    }

    public TaskStateBuilder ticketUrl(@Nullable String ticketUrl) {
      this.ticketUrl = ticketUrl;
      return this;
    }

    public TaskStateBuilder baseBranch(@Nullable String baseBranch) {
      this.baseBranch = baseBranch;
      return this;
    }

    public TaskStateBuilder mrCreatedAt(long mrCreatedAt) {
      this.mrCreatedAt = mrCreatedAt;
      return this;
    }

    public TaskStateBuilder requestOpenedAt(long requestOpenedAt) {
      this.requestOpenedAt = requestOpenedAt;
      return this;
    }

    public TaskStateBuilder lastPolledAt(long lastPolledAt) {
      this.lastPolledAt = lastPolledAt;
      return this;
    }

    public TaskStateBuilder autoReview(@Nullable Boolean autoReview) {
      this.autoReview = autoReview;
      return this;
    }

    public TaskStateBuilder approved(@Nullable Boolean approved) {
      this.approved = approved;
      return this;
    }

    public TaskStateBuilder told(@Nullable ToldOnce told) {
      this.told = told;
      return this;
    }

    public TaskStateBuilder trail(@Nullable RoundTrail trail) {
      this.trail = trail;
      return this;
    }

    public TaskStateBuilder pipelineUrl(@Nullable String pipelineUrl) {
      this.pipelineUrl = pipelineUrl;
      return this;
    }

    public TaskStateBuilder pipelineStatus(@Nullable String pipelineStatus) {
      this.pipelineStatus = pipelineStatus;
      return this;
    }

    public TaskStateBuilder checksUnread(List<ChecksUnread> checksUnread) {
      this.checksUnread = checksUnread;
      return this;
    }

    public TaskStateBuilder checksTip(@Nullable String checksTip) {
      this.checksTip = checksTip;
      return this;
    }

    public TaskStateBuilder headTip(@Nullable String headTip) {
      this.headTip = headTip;
      return this;
    }

    public TaskStateBuilder infraRetries(List<InfraRetry> infraRetries) {
      this.infraRetries = infraRetries;
      return this;
    }

    public TaskStateBuilder planGate(PlanGate planGate) {
      this.planGate = planGate;
      return this;
    }

    public TaskStateBuilder probes(List<Probe> probes) {
      this.probes = probes;
      return this;
    }

    public TaskStateBuilder switches(Map<String, String> switches) {
      this.switches = switches;
      return this;
    }

    public TaskStateBuilder gitTrust(@Nullable GitTrust gitTrust) {
      this.gitTrust = gitTrust;
      return this;
    }

    public TaskStateBuilder refusals(Map<RefusalKind, Integer> refusals) {
      this.refusals = refusals;
      return this;
    }

    public TaskStateBuilder usage(@Nullable TokenUsage usage) {
      this.usage = usage;
      return this;
    }

    public TaskStateBuilder agentSpend(@Nullable AgentSpend agentSpend) {
      this.agentSpend = agentSpend;
      return this;
    }

    public TaskStateBuilder session(Session session) {
      this.session = session;
      return this;
    }

    public TaskStateBuilder turn(@Nullable Turn turn) {
      this.turn = turn;
      return this;
    }

    public TaskStateBuilder waits(List<HumanWait> waits) {
      this.waits = waits;
      return this;
    }

    public TaskStateBuilder stop(@Nullable TaskStop stop) {
      this.stop = stop;
      return this;
    }

    public TaskStateBuilder repoRequest(@Nullable String repoRequest) {
      this.repoRequest = repoRequest;
      return this;
    }

    public TaskStateBuilder deployment(@Nullable Deployment deployment) {
      this.deployment = deployment;
      return this;
    }

    public TaskStateBuilder roles(Map<String, RoleRecord> roles) {
      this.roles = roles;
      return this;
    }

    public TaskStateBuilder master(@Nullable Master master) {
      this.master = master;
      return this;
    }

    public TaskStateBuilder mrUrl(@Nullable String mrUrl) {
      return firstRepo(repo -> repo.withMrUrl(mrUrl));
    }

    private TaskStateBuilder firstRepo(UnaryOperator<TaskRepo> change) {
      List<TaskRepo> updated = new ArrayList<>(repos);
      updated.set(0, change.apply(updated.get(0)));
      this.repos = List.copyOf(updated);
      return this;
    }

    public TaskState build() {
      List<StatusChange> opened =
          history == null ? List.of(new StatusChange(status, lastActiveTimestamp, null)) : history;
      return new TaskState(
          repos,
          new Standing(status, lastActiveTimestamp, message, report, opened),
          new Ticket(alias, title, ticketUrl, baseBranch),
          new Round(mrCreatedAt, requestOpenedAt, lastPolledAt, autoReview, approved, told, trail),
          new Checks(pipelineStatus, checksUnread, checksTip, headTip, infraRetries, pipelineUrl),
          new Holds(planGate, probes, switches, gitTrust, refusals, usage, agentSpend),
          new Work(session, turn, waits, stop, repoRequest, deployment, roles, master));
    }
  }
}
