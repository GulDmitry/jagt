package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * One task, as {@code state.json} holds it.
 *
 * <p>A task works in one or MORE repositories but always in ONE session: {@code repos.get(0)} is where that
 * session runs, and the single-repo accessors answer for it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
public record TaskState(
        List<TaskRepo> repos,
        TaskStatus status,
        long lastActiveTimestamp,
        @Nullable String message,
        AgentReport report,
        @Nullable String alias,
        @Nullable String title,
        @Nullable String ticketUrl,
        // Null = the project's configured baseBranch, so a config change still reaches the task.
        @Nullable String baseBranch,
        // mrCreatedAt is when jagt stamped the round, not the host's own creation time. Zero = unset.
        long mrCreatedAt,
        // When the HOST says the request was opened; 0 until a read said so (a model read cannot).
        long requestOpenedAt,
        long lastPolledAt,
        // When the watchdog last saw a sign of life from an agent it found silent; 0 = not silent.
        long silentSince,
        // Why it is silent, in the words a human reads. Null while it is not.
        @Nullable String silentBecause,
        @Nullable Boolean autoReview,
        // The host's own wording for the checks, unparsed. Null = never read.
        @Nullable String pipelineStatus,
        // The reads running that could not list them, newest last; any = the word above is an older round's.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ChecksUnread> checksUnread,
        // Whether the host says this round is approved. Null = no read has said yet.
        @Nullable Boolean approved,
        // Null until the first metered call.
        @Nullable TokenUsage usage,
        // What this task's own agent sessions have burned, and how far their log has been read.
        @Nullable AgentSpend agentSpend,
        // Append-only, oldest first: every status this task actually moved TO, with when.
        List<StatusChange> history,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<Guard, Integer> refusals,
        // Null until first recorded.
        @Nullable GitTrust gitTrust,
        // What the last plan submitted declared, each with its last result once VERIFYING ran it.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Probe> probes,
        PlanGate planGate,
        @Nullable RoundTrail trail,
        // Set by jagt alone, cleared by a human's lift alone.
        @Nullable TaskStop stop,
        @Nullable ConflictWatch conflict,
        // The project key a session asked to have a repository of, until one is added or it reports anything else.
        @Nullable String repoRequest,
        // Absent in a file written before the turn existed, which reads as no turn open.
        @Nullable Turn turn,
        // Switch name to the session's "yes — why" or "no — why"; empty until it judged one.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> switches,
        // The re-runs of a pipeline that died on infrastructure: the ones sent, and at most one still armed.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<InfraRetry> infraRetries
) {

    /** The log cannot be unbounded, so the oldest entries drop; the first stays, for it says who opened the task. */
    public static final int MAX_HISTORY = 50;

    public TaskState {
        repos = repos == null ? List.of() : List.copyOf(repos);
        history = history == null ? List.of(new StatusChange(status, lastActiveTimestamp, null)) : List.copyOf(history);
        refusals = refusals == null ? Map.of() : Map.copyOf(refusals);
        probes = probes == null ? List.of() : List.copyOf(probes);
        checksUnread = checksUnread == null ? List.of() : List.copyOf(checksUnread);
        switches = switches == null ? Map.of() : Map.copyOf(switches);
        infraRetries = infraRetries == null ? List.of() : List.copyOf(infraRetries);
    }

    /** Reads BOTH shapes: the current one with {@code repos}, and older files with those fields at the top level. */
    @JsonCreator
    static TaskState fromJson(
            @JsonProperty("repos") @Nullable List<TaskRepo> repos,
            @JsonProperty("project") @Nullable String project,
            @JsonProperty("worktreePath") @Nullable String worktreePath,
            @JsonProperty("remoteUrl") @Nullable String remoteUrl,
            @JsonProperty("mrUrl") @Nullable String mrUrl,
            @JsonProperty("deployCommit") @Nullable String deployCommit,
            @JsonProperty("status") TaskStatus status,
            @JsonProperty("lastActiveTimestamp") long lastActiveTimestamp,
            @JsonProperty("message") @Nullable String message,
            // Absent in a file written while the meaning was a marker in the message.
            @JsonProperty("report") @Nullable AgentReport report,
            @JsonProperty("alias") @Nullable String alias,
            @JsonProperty("title") @Nullable String title,
            @JsonProperty("ticketUrl") @Nullable String ticketUrl,
            @JsonProperty("baseBranch") @Nullable String baseBranch,
            @JsonProperty("mrCreatedAt") long mrCreatedAt,
            @JsonProperty("requestOpenedAt") @Nullable Long requestOpenedAt,
            @JsonProperty("lastPolledAt") long lastPolledAt,
            // Boxed: a file written before this field existed omits it, and Jackson refuses an absent value
            // onto a primitive.
            @JsonProperty("silentSince") @Nullable Long silentSince,
            @JsonProperty("silentBecause") @Nullable String silentBecause,
            @JsonProperty("autoReview") @Nullable Boolean autoReview,
            @JsonProperty("pipelineStatus") @Nullable String pipelineStatus,
            // A file written before the reads were kept holds only whether the newest one failed.
            @JsonProperty("pipelineUnread") @Nullable Boolean pipelineUnread,
            @JsonProperty("checksUnread") @Nullable List<ChecksUnread> checksUnread,
            @JsonProperty("approved") @Nullable Boolean approved,
            @JsonProperty("usage") @Nullable TokenUsage usage,
            @JsonProperty("agentSpend") @Nullable AgentSpend agentSpend,
            @JsonProperty("history") @Nullable List<StatusChange> history,
            @JsonProperty("refusals") @Nullable Map<Guard, Integer> refusals,
            @JsonProperty("gitTrust") @Nullable GitTrust gitTrust,
            @JsonProperty("probes") @Nullable List<Probe> probes,
            // Absent in a file written before the gate existed, which reads as nothing to wait for.
            @JsonProperty("planGate") @Nullable PlanGate planGate,
            @JsonProperty("trail") @Nullable RoundTrail trail,
            @JsonProperty("stop") @Nullable TaskStop stop,
            // Written before the stop left the trail: read once, rewritten as the two above.
            @JsonProperty("loop") @Nullable Loop loop,
            @JsonProperty("conflict") @Nullable ConflictWatch conflict,
            @JsonProperty("repoRequest") @Nullable String repoRequest,
            @JsonProperty("turn") @Nullable Turn turn,
            @JsonProperty("switches") @Nullable Map<String, String> switches,
            @JsonProperty("infraRetries") @Nullable List<InfraRetry> infraRetries) {
        List<TaskRepo> resolved = repos != null && !repos.isEmpty()
                ? repos
                : List.of(new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit));
        return new TaskState(resolved, status, lastActiveTimestamp,
                report == null ? ReportMarkerMigration.lineOf(message) : message,
                report == null ? ReportMarkerMigration.reportOf(message) : report, alias, title, ticketUrl,
                baseBranch, mrCreatedAt, requestOpenedAt == null ? 0 : requestOpenedAt, lastPolledAt,
                silentSince == null ? 0 : silentSince, silentBecause, autoReview, pipelineStatus,
                unreadOf(checksUnread, pipelineUnread), approved, usage, agentSpend, history == null ? List.of() : history,
                refusals == null ? Map.of() : refusals, gitTrust,
                probes == null ? List.of() : probes, planGate == null ? PlanGate.OPEN : planGate,
                Loop.trail(trail, loop), Loop.stop(stop, loop), conflict, repoRequest, turn, switches == null ? Map.of() : switches,
                infraRetries == null ? List.of() : infraRetries);
    }

    record Loop(List<RoundTrail.Round> rounds, long since, @Nullable TaskStop stop) {

        static @Nullable RoundTrail trail(@Nullable RoundTrail trail, @Nullable Loop loop) {
            return trail != null || loop == null ? trail : new RoundTrail(loop.rounds(), loop.since());
        }

        static @Nullable TaskStop stop(@Nullable TaskStop stop, @Nullable Loop loop) {
            return stop != null || loop == null ? stop : loop.stop();
        }
    }

    /** Flagged without a stamp, so it never counts toward a stop. */
    private static List<ChecksUnread> unreadOf(@Nullable List<ChecksUnread> checksUnread, @Nullable Boolean flagged) {
        if (checksUnread != null) {
            return checksUnread;
        }
        return Boolean.TRUE.equals(flagged) ? List.of(new ChecksUnread(0, "")) : List.of();
    }

    @JsonIgnore
    public TaskRepo primary() {
        return repos.isEmpty() ? TaskRepo.of(null, null) : repos.get(0);
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

    /** The primary repo's request only; a multi-repo task has one per repo. */
    @JsonIgnore
    public @Nullable String mrUrl() {
        return primary().mrUrl();
    }

    @JsonIgnore
    public @Nullable String deployCommit() {
        return primary().deployCommit();
    }

    @JsonIgnore
    public List<String> projects() {
        return repos.stream().map(TaskRepo::project).filter(p -> p != null && !p.isBlank()).toList();
    }

    @JsonIgnore
    public java.util.Optional<TaskRepo> repo(String project) {
        return repos.stream().filter(r -> r.project() != null && r.project().equals(project)).findFirst();
    }

    /** Empty when that project is not one of the task's, or holds no request yet. */
    @JsonIgnore
    public Optional<String> reviewRequestOf(String project) {
        return repos.stream().filter(repo -> Objects.requireNonNull(repo.project()).equals(project))
                .map(TaskRepo::mrUrl).filter(url -> url != null && !url.isBlank()).findFirst();
    }

    /** Every repository's request URL, in order of the URLs. */
    @JsonIgnore
    public List<String> requests() {
        return requestsOf(repos);
    }

    private static List<String> requestsOf(List<TaskRepo> repos) {
        return repos.stream().map(TaskRepo::mrUrl).filter(url -> url != null && !url.isBlank()).sorted().toList();
    }

    /** True when ANY of the repos has a review request open. */
    @JsonIgnore
    public boolean hasReviewRequest() {
        return repos.stream().anyMatch(TaskRepo::hasReviewRequest);
    }

    /** True when every repository has a request and git found each one inside its target: nothing is left to ship. */
    @JsonIgnore
    public boolean requestsMerged() {
        return repos.stream().allMatch(repo -> repo.hasReviewRequest() && repo.wasMerged());
    }

    /** A recorded merge is one no revert has taken out yet. */
    @JsonIgnore
    public boolean hasLiveDeploy() {
        return repos.stream().anyMatch(repo -> repo.deployCommit() != null && !repo.deployCommit().isBlank());
    }

    /** A status move — the ONE place history grows. A new line means nothing until {@link #withReport} says so. */
    public TaskState withStatus(TaskStatus status, @Nullable String message, long now) {
        return withStatus(status, message, false, now);
    }

    /**
     * {@code event} records a step even when the status is unchanged: true for something DONE to the task, false for
     * a task repeating itself, whose keep-alives would otherwise drown the real transitions.
     */
    public TaskState withStatus(TaskStatus status, @Nullable String message, boolean event, long now) {
        List<StatusChange> known = seededHistory(now);
        boolean record = event || status != this.status;
        return toBuilder().status(status).lastActiveTimestamp(now).message(message).report(AgentReport.PLAIN)
                .silentSince(0)
                .silentBecause(null)
                .conflict(status == TaskStatus.DEPLOY_CONFLICT ? conflict : null)
                .history(record ? appended(known, new StatusChange(status, now, null)) : known)
                .build();
    }

    /** A NEW review round on one repository's request. The status that follows is not decided here. */
    public TaskState withReviewRound(@Nullable String project, String reviewRequestUrl, long now) {
        return relinked(project, reviewRequestUrl, now).lastActiveTimestamp(now)
                // A new round has new checks and no verdict yet; the last one's answers describe a dead state.
                .pipelineStatus(null).checksUnread(List.of()).infraRetries(List.of()).approved(null)
                // The polling window is per ROUND, not per request; lastPolledAt=0 means "poll at the next tick".
                .mrCreatedAt(now).lastPolledAt(0)
                .build();
    }

    /** The single-repo form: the round landed on the repo the agent works in. */
    public TaskState withReviewRound(String reviewRequestUrl, long now) {
        return withReviewRound(primary().project(), reviewRequestUrl, now);
    }

    /**
     * One ship, however many repositories it landed in: every URL goes on its own repo, and the round is recorded
     * ONCE, carrying the session repo's link.
     */
    public TaskState withReviewRound(Map<String, String> urlByProject, long now) {
        if (urlByProject.isEmpty()) {
            return this;
        }
        String primaryUrl = urlByProject.getOrDefault(primary().project(),
                urlByProject.values().iterator().next());
        return withMrUrls(urlByProject, now).withReviewRound(primary().project(), primaryUrl, now);
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
     * A task written before history existed is seeded with its current status at the last activity stamp, or
     * {@link #statusSince()} falls back to a stamp every keep-alive bumps.
     */
    private List<StatusChange> seededHistory(long now) {
        if (!history.isEmpty()) {
            return history;
        }
        long since = lastActiveTimestamp > 0 ? lastActiveTimestamp : now;
        return List.of(new StatusChange(status, since, null));
    }

    /**
     * What the watchdog found. Its own wither because silence is not activity: stamping it must not bump
     * {@code lastActiveTimestamp}. Any report clears it, since an agent that speaks is alive.
     */
    public TaskState withSilentSince(long silentSince, @Nullable String because) {
        return toBuilder().silentSince(silentSince).silentBecause(silentSince > 0 ? because : null).build();
    }

    @JsonIgnore
    public boolean agentIsSilent() {
        return silentSince > 0;
    }

    /** The host's own answer; a read that does not know it (0) must not erase one that did. */
    public TaskState withRequestOpenedAt(long requestOpenedAt) {
        return requestOpenedAt <= 0 ? this : toBuilder().requestOpenedAt(requestOpenedAt).build();
    }

    /** What a round actually READ off the host, which also ends any older round's failure to read it. */
    public TaskState withChecksRead(String hostStatus) {
        return toBuilder().pipelineStatus(hostStatus).checksUnread(List.of()).build();
    }

    /** A round that could not read them: the last verdict stands, marked as not this round's. */
    public TaskState withChecksUnread(ChecksUnread read) {
        List<ChecksUnread> running = new ArrayList<>(checksUnread);
        running.add(read);
        return toBuilder().checksUnread(running.subList(Math.max(0, running.size() - ChecksUnread.CEILING),
                running.size())).build();
    }

    @JsonIgnore
    public boolean pipelineUnread() {
        return !checksUnread.isEmpty();
    }

    /** Something was done to the task that leaves its status where it is: one step in its history, nothing else touched. */
    public TaskState withMoveRecorded(long now) {
        return toBuilder().history(appended(seededHistory(now), new StatusChange(status, now, null))).build();
    }

    public TaskState withInfraRetries(List<InfraRetry> retries) {
        return toBuilder().infraRetries(retries).build();
    }

    public TaskState withApproved(Boolean approved) {
        return toBuilder().approved(approved).build();
    }

    /** Does NOT touch lastActiveTimestamp: a refused call moved nothing. */
    public TaskState refused(Guard guard) {
        Map<Guard, Integer> counted = new EnumMap<>(Guard.class);
        counted.putAll(refusals);
        counted.merge(guard, 1, Integer::sum);
        return toBuilder().refusals(counted).build();
    }

    /**
     * Stamps who caused the step just taken — separate from taking it, because the transition is built where the
     * work happens and the asker is known only at the entry point.
     */
    public TaskState withLastChangeOrigin(ActionOrigin origin) {
        return withLastChange(step -> step.by(origin));
    }

    /** The reason the asker gave for the step just taken, stamped as its origin is. */
    public TaskState withLastChangeWhy(String why) {
        return withLastChange(step -> step.because(why));
    }

    private TaskState withLastChange(UnaryOperator<StatusChange> stamp) {
        if (history.isEmpty()) {
            return this;
        }
        List<StatusChange> stamped = new ArrayList<>(history);
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

    /** Whether the oldest steps have been dropped, which makes any total over the log a FLOOR, not the figure. */
    @JsonIgnore
    public boolean historyAtCap() {
        return history.size() >= MAX_HISTORY;
    }

    /** How many times the task went out for review: every entry into CI_POLLING is one round. */
    @JsonIgnore
    public int rounds() {
        return (int) history.stream().filter(step -> step.status() == TaskStatus.CI_POLLING).count();
    }

    /**
     * Since when the task has been in its CURRENT status. NOT {@code lastActiveTimestamp}, which a keep-alive
     * bumps.
     */
    public long statusSince() {
        return history.isEmpty() ? lastActiveTimestamp : history.get(history.size() - 1).at();
    }

    public TaskState touched(long now) {
        return withStatus(status, message, now).withReport(report);
    }

    public TaskState withReport(AgentReport report) {
        return toBuilder().report(report).build();
    }

    /** The merge commit a deploy created in THAT repo; the status move is a separate step. */
    public TaskState withDeployCommit(@Nullable String project, @Nullable String deployCommit) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withMerge(deployCommit))).build();
    }

    public TaskState withDeployFix(@Nullable String project, String fix) {
        return toBuilder().repos(mapRepo(project, repo -> repo.withDeployFix(fix))).build();
    }

    public TaskState withDeployCommit(String deployCommit) {
        return withDeployCommit(primary().project(), deployCommit);
    }

    public TaskState withMrUrl(@Nullable String project, String mrUrl, long now) {
        return relinked(project, mrUrl, now).build();
    }

    /**
     * Points one repository at a request, dropping every answer a read gave about the old one and stamping the new
     * one as opened NOW — a request reaches here only as it is being opened. A {@code resume} adopting somebody
     * else's request is linked through the builder instead, and keeps its unknown age.
     */
    private TaskStateBuilder relinked(@Nullable String project, String mrUrl, long now) {
        List<TaskRepo> repos = mapRepo(project, repo -> repo.withMrUrl(mrUrl));
        if (repos.equals(this.repos)) {
            return toBuilder().repos(repos);
        }
        TaskState base = stop != null && stop.voidedBy(requestsOf(repos)) ? lifted(now) : this;
        return base.toBuilder().repos(repos).requestOpenedAt(now)
                .pipelineStatus(null).checksUnread(List.of()).infraRetries(List.of()).approved(null);
    }

    public TaskState withMrUrl(String mrUrl, long now) {
        return withMrUrl(primary().project(), mrUrl, now);
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
    private List<TaskRepo> mapRepo(@Nullable String project, java.util.function.UnaryOperator<TaskRepo> change) {
        List<TaskRepo> updated = new ArrayList<>(repos.size());
        for (TaskRepo repo : repos) {
            boolean matches = project == null
                    ? repo.equals(primary())
                    : project.equals(repo.project());
            updated.add(matches ? change.apply(repo) : repo);
        }
        return List.copyOf(updated);
    }

    /** The task's own base branch, or the project default when it has none (null or blank). */
    public String baseBranchOr(String projectDefault) {
        return baseBranch == null || baseBranch.isBlank() ? projectDefault : baseBranch;
    }

    /** An unset (null) per-task flag follows the config default. */
    public boolean autoReviewEnabled(boolean configDefault) {
        return autoReview == null ? configDefault : autoReview;
    }

    public TokenUsage usageOrNone() {
        return usage == null ? TokenUsage.NONE : usage;
    }

    public AgentSpend agentSpendOrNone() {
        return agentSpend == null ? AgentSpend.NONE : agentSpend;
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
        return gitTrust != null && gitTrust.changed() != null;
    }

    /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
    public TaskState withTrail(RoundTrail trail) {
        return toBuilder().trail(trail).build();
    }

    public RoundTrail trailOrNone() {
        return trail == null ? RoundTrail.none() : trail;
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
        return stop != null;
    }

    /** Does NOT touch lastActiveTimestamp: jagt recorded it, the agent did nothing. */
    public TaskState withConflict(ConflictWatch conflict) {
        return toBuilder().conflict(conflict).build();
    }

    /** The watch of the conflict this task stands in now; one kept from an earlier press is not it. */
    @JsonIgnore
    public Optional<ConflictWatch> currentConflict() {
        return status == TaskStatus.DEPLOY_CONFLICT && conflict != null && conflict.handedFor(statusSince())
                ? Optional.of(conflict) : Optional.empty();
    }

    /** Does NOT touch lastActiveTimestamp: jagt recorded them, the agent did nothing. */
    public TaskState withProbes(List<Probe> probes) {
        return toBuilder().probes(probes).build();
    }

    /** Does NOT touch lastActiveTimestamp: it records what the session asked, and null is it asking nothing. */
    public TaskState withRepoRequest(@Nullable String project) {
        return toBuilder().repoRequest(project).build();
    }

    @JsonIgnore
    public boolean asksForARepository() {
        return repoRequest != null;
    }

    /** Adds the repository the session asked for, which ends the request. */
    public TaskState withRepo(TaskRepo repo) {
        List<TaskRepo> grown = new ArrayList<>(repos);
        grown.add(repo);
        return toBuilder().repos(grown).repoRequest(null).build();
    }

    public TaskState withBaseBranch(String baseBranch) {
        return toBuilder().baseBranch(baseBranch).build();
    }

    /** Does NOT touch lastActiveTimestamp: the gate moves by a human's word or by the session declaring no plan. */
    /** What the session judged, merged over what it judged before: the last word on a switch stands. */
    public TaskState judged(Map<String, String> judged) {
        if (judged.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new TreeMap<>(switches);
        merged.putAll(judged);
        return toBuilder().switches(merged).build();
    }

    public TaskState withTurn(Turn turn) {
        return toBuilder().turn(turn).build();
    }

    @JsonIgnore
    public Turn turnOrNone() {
        return turn == null ? Turn.none() : turn;
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
                .repos(repos == null || repos.isEmpty() ? List.of(TaskRepo.of(null, null)) : repos).status(status);
    }

    /** Missing fields stay unset; the single-repo setters write the FIRST repo. */
    @SuppressWarnings("NullAway.Init")
    public static class TaskStateBuilder {
        private AgentReport report = AgentReport.PLAIN;
        private List<ChecksUnread> checksUnread = List.of();
        private List<InfraRetry> infraRetries = List.of();
        private Map<Guard, Integer> refusals = Map.of();
        private List<Probe> probes = List.of();
        private PlanGate planGate = PlanGate.OPEN;
        private Map<String, String> switches = Map.of();

        public TaskStateBuilder mrUrl(@Nullable String mrUrl) {
            return firstRepo(repo -> repo.withMrUrl(mrUrl));
        }

        public TaskStateBuilder deployCommit(@Nullable String deployCommit) {
            return firstRepo(repo -> repo.withMerge(deployCommit));
        }

        private TaskStateBuilder firstRepo(UnaryOperator<TaskRepo> change) {
            List<TaskRepo> updated = new ArrayList<>(repos);
            updated.set(0, change.apply(updated.get(0)));
            this.repos = List.copyOf(updated);
            return this;
        }
    }
}
