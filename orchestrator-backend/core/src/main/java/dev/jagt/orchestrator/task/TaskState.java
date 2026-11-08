package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
    @JsonUnwrapped Gate gate,
    @JsonUnwrapped Work work) {

  /**
   * The log cannot be unbounded, so the oldest entries drop; the first stays, for it says who
   * opened the task.
   */
  public static final int MAX_HISTORY = 50;

  public TaskState {
    repos = repos == null ? List.of() : List.copyOf(repos);
  }

  /** Where the task stands, and every move that brought it there. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Standing(
      TaskStatus status,
      long lastActiveTimestamp,
      @Nullable String message,
      AgentReport report,
      // Append-only, oldest first: every status this task actually moved TO, with when.
      List<StatusChange> history) {

    public Standing {
      history = List.copyOf(history);
    }
  }

  /** What the task was opened for. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Ticket(
      @Nullable String alias,
      @Nullable String title,
      @Nullable String ticketUrl,
      // Null = the project's configured baseBranch, so a config change still reaches the task.
      @Nullable String baseBranch) {}

  /** The review round on the request: when it opened, how it is polled, what it was told. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Round(
      // mrCreatedAt is when jagt stamped the round, not the host's own creation time. Zero = unset.
      long mrCreatedAt,
      // When the HOST says the request was opened; 0 until a read said so (a model read cannot).
      @JsonSetter(nulls = Nulls.AS_EMPTY) long requestOpenedAt,
      long lastPolledAt,
      @Nullable Boolean autoReview,
      // Whether the host says this round is approved. Null = no read has said yet.
      @Nullable Boolean approved,
      // Null until an auto-review ping was sent.
      @Nullable ToldOnce told,
      @Nullable RoundTrail trail) {}

  /** What the host's checks last said, on which commit, and the re-runs they earned. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Checks(
      // The host's own wording for the checks, unparsed. Null = never read.
      @Nullable String pipelineStatus,
      // The reads running that could not list them, newest last; any = the word above is an older
      // round's.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ChecksUnread> checksUnread,
      // The pushed commit the stored checks and approval were read on; null = a read that named
      // none.
      @Nullable String checksTip,
      // The newest pushed commit a read saw. Null = none yet, and the stored reads stand as they
      // are.
      @Nullable String headTip,
      // The re-runs of a pipeline that died on infrastructure: the ones sent, and at most one still
      // armed.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<InfraRetry> infraRetries) {

    public Checks {
      checksUnread = checksUnread == null ? List.of() : List.copyOf(checksUnread);
      infraRetries = infraRetries == null ? List.of() : List.copyOf(infraRetries);
    }
  }

  /**
   * What jagt holds back and meters on the task: the plan gate, the probes, the refusals, the
   * spend.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Gate(
      PlanGate planGate,
      // What the last plan submitted declared, each with its last result once VERIFYING ran it.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Probe> probes,
      // Switch name to the session's "yes — why" or "no — why"; empty until it judged one.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> switches,
      // Null until first recorded.
      @Nullable GitTrust gitTrust,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<Guard, Integer> refusals,
      // Null until the first metered call.
      @Nullable TokenUsage usage,
      // What this task's own agent sessions have burned, and how far their log has been read.
      @Nullable AgentSpend agentSpend) {

    public Gate {
      planGate = planGate == null ? PlanGate.OPEN : planGate;
      probes = probes == null ? List.of() : List.copyOf(probes);
      switches = switches == null ? Map.of() : Map.copyOf(switches);
      refusals = refusals == null ? Map.of() : Map.copyOf(refusals);
    }
  }

  /**
   * The work in flight: the session, whose turn it is, what stopped it, what it deployed, who
   * checked it.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Work(
      // Absent in a file written before the session was recorded, which reads as no sign yet.
      Session session,
      // Absent in a file written before the turn existed, which reads as no turn open.
      @Nullable Turn turn,
      // Every stretch the human held the turn, oldest first; the last may still stand.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<HumanWait> waits,
      // Set by jagt alone, cleared by a human's lift alone.
      @Nullable TaskStop stop,
      // The project key a session asked to have a repository of, until one is added or it reports
      // anything else.
      @Nullable String repoRequest,
      // Absent in a file written before the deployment was recorded.
      @Nullable Deployment deployment,
      // What each checker left on the task, by role id; empty until one read or judged.
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, RoleRecord> roles,
      // What the Master left on the task; absent until it left, answered or sealed one.
      @Nullable Master master) {

    public Work {
      session = session == null ? Session.defaults() : session;
      waits = waits == null ? List.of() : List.copyOf(waits);
      roles = roles == null ? Map.of() : Map.copyOf(roles);
    }
  }

  /**
   * Reads BOTH shapes: the current one with {@code repos}, and older files with those fields at the
   * top level.
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static TaskState fromJson(StoredTask stored) {
    return stored.read();
  }

  @JsonIgnore
  public TaskStatus status() {
    return standing.status();
  }

  @JsonIgnore
  public long lastActiveTimestamp() {
    return standing.lastActiveTimestamp();
  }

  @JsonIgnore
  public @Nullable String message() {
    return standing.message();
  }

  @JsonIgnore
  public AgentReport report() {
    return standing.report();
  }

  @JsonIgnore
  public List<StatusChange> history() {
    return standing.history();
  }

  @JsonIgnore
  public @Nullable String alias() {
    return ticket.alias();
  }

  @JsonIgnore
  public @Nullable String title() {
    return ticket.title();
  }

  @JsonIgnore
  public @Nullable String ticketUrl() {
    return ticket.ticketUrl();
  }

  @JsonIgnore
  public @Nullable String baseBranch() {
    return ticket.baseBranch();
  }

  @JsonIgnore
  public long mrCreatedAt() {
    return round.mrCreatedAt();
  }

  @JsonIgnore
  public long requestOpenedAt() {
    return round.requestOpenedAt();
  }

  @JsonIgnore
  public long lastPolledAt() {
    return round.lastPolledAt();
  }

  @JsonIgnore
  public @Nullable Boolean autoReview() {
    return round.autoReview();
  }

  @JsonIgnore
  public @Nullable Boolean approved() {
    return round.approved();
  }

  @JsonIgnore
  public @Nullable ToldOnce told() {
    return round.told();
  }

  @JsonIgnore
  public @Nullable RoundTrail trail() {
    return round.trail();
  }

  @JsonIgnore
  public @Nullable String pipelineStatus() {
    return checks.pipelineStatus();
  }

  @JsonIgnore
  public List<ChecksUnread> checksUnread() {
    return checks.checksUnread();
  }

  @JsonIgnore
  public @Nullable String checksTip() {
    return checks.checksTip();
  }

  @JsonIgnore
  public @Nullable String headTip() {
    return checks.headTip();
  }

  @JsonIgnore
  public List<InfraRetry> infraRetries() {
    return checks.infraRetries();
  }

  @JsonIgnore
  public PlanGate planGate() {
    return gate.planGate();
  }

  @JsonIgnore
  public List<Probe> probes() {
    return gate.probes();
  }

  @JsonIgnore
  public Map<String, String> switches() {
    return gate.switches();
  }

  @JsonIgnore
  public @Nullable GitTrust gitTrust() {
    return gate.gitTrust();
  }

  @JsonIgnore
  public Map<Guard, Integer> refusals() {
    return gate.refusals();
  }

  @JsonIgnore
  public @Nullable TokenUsage usage() {
    return gate.usage();
  }

  @JsonIgnore
  public @Nullable AgentSpend agentSpend() {
    return gate.agentSpend();
  }

  @JsonIgnore
  public Session session() {
    return work.session();
  }

  @JsonIgnore
  public @Nullable Turn turn() {
    return work.turn();
  }

  @JsonIgnore
  public List<HumanWait> waits() {
    return work.waits();
  }

  @JsonIgnore
  public @Nullable TaskStop stop() {
    return work.stop();
  }

  @JsonIgnore
  public @Nullable String repoRequest() {
    return work.repoRequest();
  }

  @JsonIgnore
  public @Nullable Deployment deployment() {
    return work.deployment();
  }

  @JsonIgnore
  public Map<String, RoleRecord> roles() {
    return work.roles();
  }

  @JsonIgnore
  public @Nullable Master master() {
    return work.master();
  }

  public TaskStateBuilder toBuilder() {
    return new TaskStateBuilder()
        .repos(repos)
        .status(status())
        .lastActiveTimestamp(lastActiveTimestamp())
        .message(message())
        .report(report())
        .history(history())
        .alias(alias())
        .title(title())
        .ticketUrl(ticketUrl())
        .baseBranch(baseBranch())
        .mrCreatedAt(mrCreatedAt())
        .requestOpenedAt(requestOpenedAt())
        .lastPolledAt(lastPolledAt())
        .autoReview(autoReview())
        .approved(approved())
        .told(told())
        .trail(trail())
        .pipelineStatus(pipelineStatus())
        .checksUnread(checksUnread())
        .checksTip(checksTip())
        .headTip(headTip())
        .infraRetries(infraRetries())
        .planGate(planGate())
        .probes(probes())
        .switches(switches())
        .gitTrust(gitTrust())
        .refusals(refusals())
        .usage(usage())
        .agentSpend(agentSpend())
        .session(session())
        .turn(turn())
        .waits(waits())
        .stop(stop())
        .repoRequest(repoRequest())
        .deployment(deployment())
        .roles(roles())
        .master(master());
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

  /**
   * {@code event} records a step even when the status is unchanged: true for something DONE to the
   * task, false for a task repeating itself, whose keep-alives would otherwise drown the real
   * transitions.
   */
  public TaskState withStatus(
      TaskStatus status, @Nullable String message, boolean event, long now) {
    List<StatusChange> known = seededHistory(now);
    boolean record = event || status != this.status();
    return toBuilder()
        .status(status)
        .lastActiveTimestamp(now)
        .message(message)
        .session(
            session()
                .withQuietSince(0)
                .withHalt(
                    session().halt() != null && session().halt().kind() == Session.Halt.Kind.IDLE
                        ? session().halt()
                        : null))
        .history(record ? appended(known, new StatusChange(status, now, null)) : known)
        .build();
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
        .map(
            project ->
                linked.withReviewRound(
                    project, Objects.requireNonNull(urlByProject.get(project)), now))
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

  /**
   * A task written before history existed is seeded with its current status at the last activity
   * stamp, or {@link #statusSince()} falls back to a stamp every keep-alive bumps.
   */
  private List<StatusChange> seededHistory(long now) {
    if (!history().isEmpty()) {
      return history();
    }
    long since = lastActiveTimestamp() > 0 ? lastActiveTimestamp() : now;
    return List.of(new StatusChange(status(), since, null));
  }

  /** The host's own answer; a read that does not know it (0) must not erase one that did. */
  public TaskState withRequestOpenedAt(long requestOpenedAt) {
    return requestOpenedAt <= 0 ? this : toBuilder().requestOpenedAt(requestOpenedAt).build();
  }

  /**
   * What a round actually READ off the host on {@code tip}, which also ends any older round's
   * failure to read it.
   */
  public TaskState withChecksRead(String hostStatus, @Nullable String tip) {
    return toBuilder()
        .pipelineStatus(hostStatus)
        .checksUnread(List.of())
        .checksTip(blankToNull(tip))
        .build();
  }

  /** The newest commit a read saw pushed. */
  public TaskState withHead(@Nullable String tip) {
    String head = blankToNull(tip);
    if (head == null || head.equals(headTip())) {
      return this;
    }
    return toBuilder().headTip(head).build();
  }

  /**
   * The checks as the host last said them of the request's current head; null where they were read
   * on another or on none.
   */
  public @Nullable String checksRead() {
    return readsStale() ? null : pipelineStatus();
  }

  /**
   * The approval as the host last said it of the request's current head; null where it was read on
   * another or on none.
   */
  public @Nullable Boolean approvedRead() {
    return readsStale() ? null : approved();
  }

  private boolean readsStale() {
    return readsStaleOn(checksTip());
  }

  /**
   * Whether checks read on {@code tip} are not of the head the task has seen; none seen, none
   * stale.
   */
  public boolean readsStaleOn(@Nullable String tip) {
    return headTip() != null && !headTip().equals(blankToNull(tip));
  }

  private static @Nullable String blankToNull(@Nullable String tip) {
    return tip == null || tip.isBlank() ? null : tip;
  }

  /** A round that could not read them: the last verdict stands, marked as not this round's. */
  public TaskState withChecksUnread(ChecksUnread read) {
    List<ChecksUnread> running = new ArrayList<>(checksUnread());
    running.add(read);
    return toBuilder()
        .checksUnread(
            running.subList(Math.max(0, running.size() - ChecksUnread.CEILING), running.size()))
        .build();
  }

  @JsonIgnore
  public boolean pipelineUnread() {
    return !checksUnread().isEmpty();
  }

  /**
   * Something was done to the task that leaves its status where it is: one step in its history,
   * nothing else touched.
   */
  public TaskState withMoveRecorded(long now) {
    return toBuilder()
        .history(appended(seededHistory(now), new StatusChange(status(), now, null)))
        .build();
  }

  public TaskState withInfraRetries(List<InfraRetry> retries) {
    return toBuilder().infraRetries(retries).build();
  }

  /**
   * No checks read, none unread: a round's answers describe a dead state once the round is over.
   */
  public TaskState withChecksReset() {
    return toBuilder().pipelineStatus(null).checksUnread(List.of()).checksTip(null).build();
  }

  public TaskState withApproved(@Nullable Boolean approved) {
    return toBuilder().approved(approved).build();
  }

  /** Does NOT touch lastActiveTimestamp: a refused call moved nothing. */
  public TaskState refused(Guard guard) {
    Map<Guard, Integer> counted = new EnumMap<>(Guard.class);
    counted.putAll(refusals());
    counted.merge(guard, 1, Integer::sum);
    return toBuilder().refusals(counted).build();
  }

  /**
   * Stamps who caused the step just taken — separate from taking it, because the transition is
   * built where the work happens and the asker is known only at the entry point.
   */
  public TaskState withLastChangeOrigin(ActionOrigin origin) {
    return withLastChange(step -> step.by(origin));
  }

  /** The reason the asker gave for the step just taken, stamped as its origin is. */
  public TaskState withLastChangeWhy(String why) {
    return withLastChange(step -> step.because(why));
  }

  /** The tree a ship was pressed on, stamped on the step just taken where that step is the ship. */
  public TaskState withLastShipTree(String tree) {
    return withLastChange(step -> step.status() == TaskStatus.SHIPPING ? step.on(tree) : step);
  }

  private TaskState withLastChange(UnaryOperator<StatusChange> stamp) {
    if (history().isEmpty()) {
      return this;
    }
    List<StatusChange> stamped = new ArrayList<>(history());
    stamped.set(stamped.size() - 1, stamp.apply(stamped.getLast()));
    return toBuilder().history(List.copyOf(stamped)).build();
  }

  private static List<StatusChange> appended(List<StatusChange> history, StatusChange change) {
    List<StatusChange> grown = new ArrayList<>(history);
    grown.add(change);
    if (grown.size() > MAX_HISTORY) {
      grown.subList(1, grown.size() - MAX_HISTORY + 1).clear();
    }
    return List.copyOf(grown);
  }

  /**
   * Whether the oldest steps have been dropped, which makes any total over the log a FLOOR, not the
   * figure.
   */
  @JsonIgnore
  public boolean historyAtCap() {
    return history().size() >= MAX_HISTORY;
  }

  /** How many times the task went out for review: every entry into CI_POLLING is one round. */
  @JsonIgnore
  public int rounds() {
    return (int) history().stream().filter(step -> step.status() == TaskStatus.CI_POLLING).count();
  }

  /**
   * Since when the task has been in its CURRENT status. NOT {@code lastActiveTimestamp}, which a
   * keep-alive bumps.
   */
  public long statusSince() {
    return history().isEmpty() ? lastActiveTimestamp() : history().get(history().size() - 1).at();
  }

  public TaskState touched(long now) {
    return toBuilder().lastActiveTimestamp(now).history(seededHistory(now)).build();
  }

  /** The status, line and deployment {@code read} had, written back as one move. */
  public TaskState restoredFrom(TaskState read, long now) {
    return withStatus(read.status(), read.message(), now).toBuilder()
        .deployment(read.deployment())
        .planGate(read.planGate())
        .report(read.report())
        .build();
  }

  public TaskState withReport(AgentReport report) {
    return toBuilder().report(report).build();
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
    return toBuilder().lastPolledAt(lastPolledAt).build();
  }

  /** A null project key means the primary repo. */
  private List<TaskRepo> mapRepo(
      @Nullable String project, java.util.function.UnaryOperator<TaskRepo> change) {
    List<TaskRepo> updated = new ArrayList<>(repos.size());
    for (TaskRepo repo : repos) {
      boolean matches = project == null ? repo.equals(primary()) : project.equals(repo.project());
      updated.add(matches ? change.apply(repo) : repo);
    }
    return List.copyOf(updated);
  }

  /** The task's own base branch, or the project default when it has none (null or blank). */
  public String baseBranchOr(String projectDefault) {
    return baseBranch() == null || baseBranch().isBlank() ? projectDefault : baseBranch();
  }

  /** An unset (null) per-task flag follows the config default. */
  public boolean autoReviewEnabled(boolean configDefault) {
    return autoReview() == null ? configDefault : autoReview();
  }

  public TokenUsage usageOrNone() {
    return usage() == null ? TokenUsage.NONE : usage();
  }

  public AgentSpend agentSpendOrNone() {
    return agentSpend() == null ? AgentSpend.NONE : agentSpend();
  }

  /** Every token this task has cost, jagt's own reads and its agent's session alike. */
  @JsonIgnore
  public TokenUsage totalUsage() {
    return usageOrNone().plus(agentSpendOrNone().usageOrNone());
  }

  /** Does NOT touch lastActiveTimestamp: reading a session's own log is not that session moving. */
  public TaskState withAgentSpend(AgentSpend spend) {
    return toBuilder().agentSpend(spend).build();
  }

  /** Does NOT touch lastActiveTimestamp: what git holds is not agent activity. */
  public TaskState withGitTrust(GitTrust trust) {
    return toBuilder().gitTrust(trust).build();
  }

  @JsonIgnore
  public boolean gitStateChanged() {
    return gitTrust() != null && gitTrust().changed() != null;
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
  public TaskState withTrail(RoundTrail trail) {
    return toBuilder().trail(trail).build();
  }

  public RoundTrail trailOrNone() {
    return trail() == null ? RoundTrail.none() : trail();
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
  public TaskState withStop(TaskStop stop) {
    return toBuilder().stop(stop).build();
  }

  /** The stop cleared and the trail counting anew from {@code now}. */
  public TaskState lifted(long now) {
    return toBuilder().stop(null).trail(RoundTrail.liftedAt(now)).build();
  }

  @JsonIgnore
  public boolean stopped() {
    return stop() != null;
  }

  @JsonIgnore
  public boolean paused() {
    return stop() != null && stop().trigger().ends() == TaskStop.Trigger.Ends.PAUSE;
  }

  /** The conflict this task stands in now, as its deployment records it. */
  @JsonIgnore
  public Optional<Conflict> currentConflict() {
    return status() == TaskStatus.DEPLOY_CONFLICT
        ? Optional.ofNullable(deployment())
            .flatMap(Deployment::conflictedTarget)
            .map(target -> target.progress().conflict())
        : Optional.empty();
  }

  /** Does NOT touch lastActiveTimestamp: jagt recorded them, the agent did nothing. */
  public TaskState withProbes(List<Probe> probes) {
    return toBuilder().probes(probes).build();
  }

  /**
   * Does NOT touch lastActiveTimestamp: it records what the session asked, and null is it asking
   * nothing.
   */
  public TaskState withRepoRequest(@Nullable String project) {
    return toBuilder().repoRequest(project).build();
  }

  @JsonIgnore
  public boolean asksForRepository() {
    return repoRequest() != null;
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

  /** A repository whose change is live on a deploy branch until a revert takes it out. */
  public boolean landedIn(String project) {
    return deployment() != null
        && deployment().targets().stream()
            .anyMatch(target -> target.project().equals(project) && target.landed());
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
    return toBuilder().baseBranch(baseBranch).build();
  }

  /**
   * What the session judged, merged over what it judged before: the last word on a switch stands.
   * Does NOT touch lastActiveTimestamp: the gate moves by a human's word or by the session
   * declaring no plan.
   */
  public TaskState judged(Map<String, String> judged) {
    if (judged.isEmpty()) {
      return this;
    }
    Map<String, String> merged = new TreeMap<>(switches());
    merged.putAll(judged);
    return toBuilder().switches(merged).build();
  }

  /**
   * The role passed the change now live: its record names the tips it checked, so a later deploy
   * shows none.
   */
  public boolean passedBy(String role) {
    return status() == TaskStatus.DEPLOYED && passedOnTips(role);
  }

  /** The role passed the tips the deployment stands at now. */
  public boolean passedOnTips(String role) {
    return deployment() != null && roleOrNone(role).passedOn(deployment().tips());
  }

  /** A round of the role went out on the deploy now live and the session has not yet settled it. */
  public boolean roundOpen(String role) {
    return deployment() != null
        && deployment().askedDigest() != null
        && deployment().askedDigest().equals(roleOrNone(role).roundDigest());
  }

  public RoleRecord roleOrNone(String role) {
    return roles().getOrDefault(role, RoleRecord.defaults());
  }

  @JsonIgnore
  public Master masterOrNone() {
    return master() == null ? Master.defaults() : master();
  }

  public TaskState withMaster(Master master) {
    return toBuilder().master(master.isEmpty() ? null : master).build();
  }

  /** The digest of the question standing on the task, which the Master's unanswered mark names. */
  public String askedDigest() {
    return Turn.digestOf(message() == null ? "" : message());
  }

  public TaskState withRole(String role, RoleRecord record) {
    Map<String, RoleRecord> kept = new TreeMap<>(roles());
    if (record.isEmpty()) {
      kept.remove(role);
    } else {
      kept.put(role, record);
    }
    return toBuilder().roles(kept).build();
  }

  public TaskState withMark(String step, String value) {
    return withTurn(turnOrNone().withMark(step, value));
  }

  public TaskState withoutMark(String step) {
    return withTurn(turnOrNone().withoutMark(step));
  }

  public TaskState withTurn(Turn turn) {
    return toBuilder().turn(turn).build();
  }

  @JsonIgnore
  public Turn turnOrNone() {
    return turn() == null ? Turn.none() : turn();
  }

  public TaskState withDeployment(Deployment deployment) {
    return toBuilder().deployment(deployment).build();
  }

  @JsonIgnore
  public ToldOnce toldOrNone() {
    return told() == null ? ToldOnce.none() : told();
  }

  public TaskState withWaits(List<HumanWait> waits) {
    return toBuilder().waits(waits).build();
  }

  public TaskState withTold(ToldOnce told) {
    return toBuilder().told(told.isEmpty() ? null : told).build();
  }

  public TaskState withSession(Session session) {
    return toBuilder().session(session).build();
  }

  @JsonIgnore
  public @Nullable ModelTier tier() {
    return session().tier();
  }

  public TaskState withTier(@Nullable ModelTier tier) {
    return withSession(session().withTier(tier));
  }

  public TaskState withPlanGate(PlanGate planGate) {
    return toBuilder().planGate(planGate).build();
  }

  /** Does NOT touch lastActiveTimestamp: metering is not agent activity. */
  public TaskState withUsageAdded(TokenUsage added) {
    return toBuilder().usage(usageOrNone().plus(added)).build();
  }

  public static TaskStateBuilder builder(String project, String worktreePath, TaskStatus status) {
    return builder(List.of(TaskRepo.of(project, worktreePath)), status);
  }

  public static TaskStateBuilder builder(List<TaskRepo> repos, TaskStatus status) {
    return new TaskStateBuilder()
        .repos(repos == null || repos.isEmpty() ? List.of(TaskRepo.of(null, null)) : repos)
        .status(status);
  }

  /** Missing fields stay unset; the single-repo setters write the FIRST repo. */
  @SuppressWarnings("NullAway.Init")
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
    private PlanGate planGate = PlanGate.OPEN;
    private List<Probe> probes = List.of();
    private Map<String, String> switches = Map.of();
    private @Nullable GitTrust gitTrust;
    private Map<Guard, Integer> refusals = Map.of();
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

    public TaskStateBuilder repos(List<TaskRepo> repos) {
      this.repos = repos;
      return this;
    }

    public TaskStateBuilder status(TaskStatus status) {
      this.status = status;
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

    public TaskStateBuilder refusals(Map<Guard, Integer> refusals) {
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
          new Checks(pipelineStatus, checksUnread, checksTip, headTip, infraRetries),
          new Gate(planGate, probes, switches, gitTrust, refusals, usage, agentSpend),
          new Work(session, turn, waits, stop, repoRequest, deployment, roles, master));
    }
  }
}
