package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * One task, as {@code state.json} holds it.
 *
 * <p>A task works in one or MORE repositories but always in ONE session: {@code repos.get(0)} is where that
 * session runs, and the single-repo accessors answer for it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        // Whether the NEWEST round failed to read them, so the word above is an older round's.
        boolean pipelineUnread,
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
        @Nullable LoopWatch loop,
        @Nullable ConflictWatch conflict,
        // The project key a session asked to have a repository of, until one is added or it reports anything else.
        @Nullable String repoRequest
) {

    /** The whole file is rewritten on every update, so the log cannot be unbounded; the OLDEST entries drop. */
    private static final int MAX_HISTORY = 50;

    public TaskState {
        repos = repos == null ? List.of() : List.copyOf(repos);
        history = history == null ? List.of() : List.copyOf(history);
        refusals = refusals == null ? Map.of() : Map.copyOf(refusals);
        probes = probes == null ? List.of() : List.copyOf(probes);
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
            @JsonProperty("pipelineUnread") @Nullable Boolean pipelineUnread,
            @JsonProperty("approved") @Nullable Boolean approved,
            @JsonProperty("usage") @Nullable TokenUsage usage,
            @JsonProperty("agentSpend") @Nullable AgentSpend agentSpend,
            @JsonProperty("history") @Nullable List<StatusChange> history,
            @JsonProperty("refusals") @Nullable Map<Guard, Integer> refusals,
            @JsonProperty("gitTrust") @Nullable GitTrust gitTrust,
            @JsonProperty("probes") @Nullable List<Probe> probes,
            // Absent in a file written before the gate existed, which reads as nothing to wait for.
            @JsonProperty("planGate") @Nullable PlanGate planGate,
            @JsonProperty("loop") @Nullable LoopWatch loop,
            @JsonProperty("conflict") @Nullable ConflictWatch conflict,
            @JsonProperty("repoRequest") @Nullable String repoRequest) {
        List<TaskRepo> resolved = repos != null && !repos.isEmpty()
                ? repos
                : List.of(new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit));
        return new TaskState(resolved, status, lastActiveTimestamp,
                report == null ? ReportMarkerMigration.lineOf(message) : message,
                report == null ? ReportMarkerMigration.reportOf(message) : report, alias, title, ticketUrl,
                baseBranch, mrCreatedAt, requestOpenedAt == null ? 0 : requestOpenedAt, lastPolledAt,
                silentSince == null ? 0 : silentSince, silentBecause, autoReview, pipelineStatus,
                pipelineUnread != null && pipelineUnread, approved, usage, agentSpend, history == null ? List.of() : history,
                refusals == null ? Map.of() : refusals, gitTrust,
                probes == null ? List.of() : probes, planGate == null ? PlanGate.OPEN : planGate, loop, conflict,
                repoRequest);
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

    /** True when ANY of the repos has a review request open. */
    @JsonIgnore
    public boolean hasReviewRequest() {
        return repos.stream().anyMatch(TaskRepo::hasReviewRequest);
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
                .history(record ? appended(known, new StatusChange(status, now, null)) : known)
                .build();
    }

    /** A NEW review round on one repository's request. The status that follows is not decided here. */
    public TaskState withReviewRound(@Nullable String project, String reviewRequestUrl, long now) {
        return relinked(project, reviewRequestUrl, now).lastActiveTimestamp(now)
                // A new round has new checks and no verdict yet; the last one's answers describe a dead state.
                .pipelineStatus(null).pipelineUnread(false).approved(null)
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
        return toBuilder().pipelineStatus(hostStatus).pipelineUnread(false).build();
    }

    /** A round that could not read them: the last verdict stands, marked as not this round's. */
    public TaskState withChecksUnread() {
        return toBuilder().pipelineUnread(true).build();
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
        return grown.size() <= MAX_HISTORY
                ? List.copyOf(grown)
                : List.copyOf(grown.subList(grown.size() - MAX_HISTORY, grown.size()));
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
        return toBuilder().repos(mapRepo(project, repo -> repo.withDeployCommit(deployCommit))).build();
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
    private Builder relinked(@Nullable String project, String mrUrl, long now) {
        List<TaskRepo> repos = mapRepo(project, repo -> repo.withMrUrl(mrUrl));
        if (repos.equals(this.repos)) {
            return toBuilder().repos(repos);
        }
        return toBuilder().repos(repos).requestOpenedAt(now)
                .pipelineStatus(null).pipelineUnread(false).approved(null);
    }

    public TaskState withMrUrl(String mrUrl, long now) {
        return withMrUrl(primary().project(), mrUrl, now);
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
    public TaskState withLoop(LoopWatch loop) {
        return toBuilder().loop(loop).build();
    }

    public LoopWatch loopOrNone() {
        return loop == null ? LoopWatch.none() : loop;
    }

    @JsonIgnore
    public boolean loopStopped() {
        return loop != null && loop.stop() != null;
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
    public TaskState withPlanGate(PlanGate planGate) {
        return toBuilder().planGate(planGate).build();
    }

    /** Does NOT touch lastActiveTimestamp: metering is not agent activity. */
    public TaskState withUsageAdded(TokenUsage added) {
        return toBuilder().usage(usageOrNone().plus(added)).build();
    }

    public static Builder builder(String project, String worktreePath, TaskStatus status) {
        return new Builder(List.of(TaskRepo.of(project, worktreePath)), status);
    }

    public static Builder builder(List<TaskRepo> repos, TaskStatus status) {
        return new Builder(repos, status);
    }

    private Builder toBuilder() {
        return new Builder(repos, status)
                .lastActiveTimestamp(lastActiveTimestamp).message(message).report(report).alias(alias)
                .title(title).ticketUrl(ticketUrl).baseBranch(baseBranch)
                .mrCreatedAt(mrCreatedAt).requestOpenedAt(requestOpenedAt)
                .lastPolledAt(lastPolledAt).silentSince(silentSince).silentBecause(silentBecause)
                .autoReview(autoReview).pipelineStatus(pipelineStatus).pipelineUnread(pipelineUnread)
                .approved(approved)
                .usage(usage).agentSpend(agentSpend).history(history).refusals(refusals).gitTrust(gitTrust).probes(probes)
                .planGate(planGate).loop(loop).conflict(conflict).repoRequest(repoRequest);
    }

    /** Missing fields stay unset; the single-repo setters write the FIRST repo. */
    public static final class Builder {
        private List<TaskRepo> repos;
        private TaskStatus status;
        private long lastActiveTimestamp;
        private @Nullable String message;
        private AgentReport report = AgentReport.PLAIN;
        private @Nullable String alias;
        private @Nullable String title;
        private @Nullable String ticketUrl;
        private @Nullable String baseBranch;
        private long mrCreatedAt;
        private long requestOpenedAt;
        private long lastPolledAt;
        private long silentSince;
        private @Nullable String silentBecause;
        private @Nullable Boolean autoReview;
        private @Nullable String pipelineStatus;
        private boolean pipelineUnread;
        private @Nullable Boolean approved;
        private @Nullable TokenUsage usage;
        private @Nullable AgentSpend agentSpend;
        /** Null means "a brand-new task". */
        private @Nullable List<StatusChange> history;
        private Map<Guard, Integer> refusals = Map.of();
        private @Nullable GitTrust gitTrust;
        private List<Probe> probes = List.of();
        private PlanGate planGate = PlanGate.OPEN;
        private @Nullable LoopWatch loop;
        private @Nullable ConflictWatch conflict;
        private @Nullable String repoRequest;

        private Builder(List<TaskRepo> repos, TaskStatus status) {
            this.repos = repos == null || repos.isEmpty() ? List.of(TaskRepo.of(null, null)) : List.copyOf(repos);
            this.status = status;
        }

        public Builder repos(List<TaskRepo> repos) {
            this.repos = List.copyOf(repos);
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastActiveTimestamp(long lastActiveTimestamp) {
            this.lastActiveTimestamp = lastActiveTimestamp;
            return this;
        }

        public Builder message(@Nullable String message) {
            this.message = message;
            return this;
        }

        public Builder report(AgentReport report) {
            this.report = report;
            return this;
        }

        public Builder alias(@Nullable String alias) {
            this.alias = alias;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder mrUrl(@Nullable String mrUrl) {
            return firstRepo(repo -> repo.withMrUrl(mrUrl));
        }

        public Builder deployCommit(@Nullable String deployCommit) {
            return firstRepo(repo -> repo.withDeployCommit(deployCommit));
        }

        private Builder firstRepo(java.util.function.UnaryOperator<TaskRepo> change) {
            List<TaskRepo> updated = new ArrayList<>(repos);
            updated.set(0, change.apply(updated.get(0)));
            this.repos = List.copyOf(updated);
            return this;
        }

        public Builder ticketUrl(@Nullable String ticketUrl) {
            this.ticketUrl = ticketUrl;
            return this;
        }

        public Builder baseBranch(@Nullable String baseBranch) {
            this.baseBranch = baseBranch;
            return this;
        }

        public Builder mrCreatedAt(long mrCreatedAt) {
            this.mrCreatedAt = mrCreatedAt;
            return this;
        }

        public Builder requestOpenedAt(long requestOpenedAt) {
            this.requestOpenedAt = requestOpenedAt;
            return this;
        }

        public Builder lastPolledAt(long lastPolledAt) {
            this.lastPolledAt = lastPolledAt;
            return this;
        }

        public Builder silentBecause(@Nullable String silentBecause) {
            this.silentBecause = silentBecause;
            return this;
        }

        public Builder silentSince(long silentSince) {
            this.silentSince = silentSince;
            return this;
        }

        public Builder autoReview(@Nullable Boolean autoReview) {
            this.autoReview = autoReview;
            return this;
        }

        public Builder pipelineStatus(@Nullable String pipelineStatus) {
            this.pipelineStatus = pipelineStatus;
            return this;
        }

        public Builder pipelineUnread(boolean pipelineUnread) {
            this.pipelineUnread = pipelineUnread;
            return this;
        }

        public Builder approved(@Nullable Boolean approved) {
            this.approved = approved;
            return this;
        }

        public Builder usage(@Nullable TokenUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder agentSpend(@Nullable AgentSpend agentSpend) {
            this.agentSpend = agentSpend;
            return this;
        }

        public Builder history(@Nullable List<StatusChange> history) {
            this.history = history;
            return this;
        }

        public Builder refusals(Map<Guard, Integer> refusals) {
            this.refusals = refusals;
            return this;
        }

        public Builder gitTrust(@Nullable GitTrust gitTrust) {
            this.gitTrust = gitTrust;
            return this;
        }

        public Builder probes(List<Probe> probes) {
            this.probes = probes;
            return this;
        }

        public Builder planGate(PlanGate planGate) {
            this.planGate = planGate;
            return this;
        }

        public Builder loop(@Nullable LoopWatch loop) {
            this.loop = loop;
            return this;
        }

        public Builder conflict(@Nullable ConflictWatch conflict) {
            this.conflict = conflict;
            return this;
        }

        public Builder repoRequest(@Nullable String repoRequest) {
            this.repoRequest = repoRequest;
            return this;
        }

        /** A task built from scratch starts its history AT its initial status; a rebuilt one carries its own. */
        public TaskState build() {
            List<StatusChange> log = history != null ? history : List.of(new StatusChange(status,
                    lastActiveTimestamp, null));
            return new TaskState(repos, status, lastActiveTimestamp, message, report, alias, title, ticketUrl,
                    baseBranch, mrCreatedAt, requestOpenedAt, lastPolledAt, silentSince, silentBecause,
                    autoReview, pipelineStatus, pipelineUnread, approved, usage, agentSpend, log, refusals, gitTrust,
                    probes, planGate, loop, conflict, repoRequest);
        }
    }
}
