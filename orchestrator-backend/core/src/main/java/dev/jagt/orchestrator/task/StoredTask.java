package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** A task as a file holds it, the current keys and every older one, until {@link #read()} makes the one value. */
@JsonIgnoreProperties(ignoreUnknown = true)
record StoredTask(
        @JsonUnwrapped Placement placement,
        @JsonUnwrapped ReadStanding standing,
        @JsonUnwrapped TaskState.Ticket ticket,
        @JsonUnwrapped TaskState.Round round,
        @JsonUnwrapped ReadChecks checks,
        @JsonUnwrapped TaskState.Gate gate,
        @JsonUnwrapped ReadWork work,
        @JsonUnwrapped Unmigrated unmigrated
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Placement(@Nullable List<LegacyDeployment.Repo> repos, @Nullable String project,
                     @Nullable String worktreePath, @Nullable String remoteUrl, @Nullable String mrUrl,
                     @Nullable String deployCommit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadStanding(TaskStatus status, long lastActiveTimestamp, @Nullable String message,
                        // Absent in a file written while the meaning was a marker in the message.
                        @Nullable AgentReport report, @Nullable List<StatusChange> history) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadChecks(@Nullable String pipelineStatus,
                      // A file written before the reads were kept holds only whether the newest one failed.
                      @Nullable List<ChecksUnread> checksUnread, @Nullable String checksTip,
                      @Nullable String headTip, @Nullable List<InfraRetry> infraRetries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadWork(@Nullable Session session, @Nullable Turn turn, @Nullable List<HumanWait> waits,
                    @Nullable TaskStop stop, @Nullable String repoRequest, @Nullable Deployment deployment,
                    @Nullable Map<String, RoleRecord> roles, @Nullable Master master) {
    }

    /** What only a file written before the current shape holds, each read once and rewritten where it now lives. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Unmigrated(
            // Rewritten as the session's quiet.
            @Nullable Long silentSince,
            @Nullable Boolean pipelineUnread,
            // Rewritten as the trail and the stop.
            @Nullable Loop loop,
            // Rewritten on the deployment.
            LegacyDeployment.@Nullable Watch conflict,
            // Rewritten as roles["qa"].
            @Nullable RoleRecord qa
    ) {
    }

    record Loop(List<RoundTrail.Round> rounds, long since, @Nullable TaskStop stop) {

        static @Nullable RoundTrail trail(@Nullable RoundTrail trail, @Nullable Loop loop) {
            return trail != null || loop == null ? trail : new RoundTrail(loop.rounds(), loop.since());
        }

        static @Nullable TaskStop stop(@Nullable TaskStop stop, @Nullable Loop loop) {
            return stop != null || loop == null ? stop : loop.stop();
        }
    }

    TaskState read() {
        List<LegacyDeployment.Repo> stored = LegacyDeployment.stored(placement.repos(), new LegacyDeployment.Repo(
                placement.project(), placement.worktreePath(), placement.remoteUrl(), placement.mrUrl(),
                placement.deployCommit(), null, null, null));
        TaskState read = new TaskState(stored.stream().map(LegacyDeployment.Repo::repo).toList(), migratedStanding(), ticket,
                migratedRound(), migratedChecks(), gate, migratedWork());
        Deployment migrated = LegacyDeployment.of(read, stored, unmigrated.conflict());
        return migrated == work.deployment() ? read : read.toBuilder().deployment(migrated).build();
    }

    private TaskState.Standing migratedStanding() {
        AgentReport stored = standing.report();
        List<StatusChange> history = standing.history();
        return new TaskState.Standing(standing.status(), standing.lastActiveTimestamp(),
                stored == null ? ReportMarkerMigration.lineOf(standing.message()) : standing.message(),
                stored == null ? ReportMarkerMigration.reportOf(standing.message()) : stored,
                history == null ? List.of() : history);
    }

    private TaskState.Round migratedRound() {
        return new TaskState.Round(round.mrCreatedAt(), round.requestOpenedAt(), round.lastPolledAt(),
                round.autoReview(), round.approved(), LegacyTold.of(round.told(), work.turn()),
                Loop.trail(round.trail(), unmigrated.loop()));
    }

    private TaskState.Checks migratedChecks() {
        List<InfraRetry> infraRetries = checks.infraRetries();
        return new TaskState.Checks(checks.pipelineStatus(),
                unreadOf(checks.checksUnread(), unmigrated.pipelineUnread()), checks.checksTip(), checks.headTip(),
                infraRetries == null ? List.of() : infraRetries);
    }

    private TaskState.Work migratedWork() {
        List<HumanWait> waits = work.waits();
        return new TaskState.Work(Session.migrated(work.session(), unmigrated.silentSince()),
                LegacyTold.withoutMarks(LegacyMaster.withoutMarks(LegacyQa.withoutMarks(work.turn()))),
                waits == null ? List.of() : waits,
                Loop.stop(work.stop(), unmigrated.loop()), work.repoRequest(), work.deployment(),
                LegacyRoles.of(work.roles(), LegacyQa.of(unmigrated.qa(), work.turn(), standing.status())),
                LegacyMaster.of(work.master(), work.turn()));
    }

    /** Flagged without a stamp, so it never counts toward a stop. */
    private static List<ChecksUnread> unreadOf(@Nullable List<ChecksUnread> checksUnread, @Nullable Boolean flagged) {
        if (checksUnread != null) {
            return checksUnread;
        }
        return Boolean.TRUE.equals(flagged) ? List.of(new ChecksUnread(0, "")) : List.of();
    }
}
