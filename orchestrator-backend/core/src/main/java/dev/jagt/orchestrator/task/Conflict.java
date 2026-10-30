package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The conflict a deploy target stands in, from the press that met it. {@code progress} is null until its session is
 * handed it, {@code movedAt} is when its worktree last changed, {@code told} the human's one banner, {@code hold} the
 * stop that kept its resolution from being deployed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Conflict(Deployment.Cause cause, @JsonAlias("conflictSince") long since, @Nullable Progress progress,
                       long movedAt, @Nullable Told told, @Nullable Hold hold) {

    /** {@code digest} changes with any edit in the worktree, staged or not. */
    public record Progress(int unmerged, int staged, String digest) {
    }

    public record Told(long at, String reason) {
    }

    public record Hold(long stopAt) {
    }

    public static Conflict of(Deployment.Cause cause, long now) {
        return new Conflict(cause, now, null, now, null, null);
    }

    public boolean handed() {
        return progress != null;
    }

    public Conflict seen(Progress now, long at) {
        return now.equals(progress) ? this : new Conflict(cause, since, now, at, told, hold);
    }

    public boolean due(Duration after, long now) {
        return told == null && now - movedAt >= after.toMillis();
    }

    public String reason(String worktree) {
        Progress seen = progress == null ? new Progress(0, 0, "") : progress;
        return seen.unmerged() + " paths unmerged, " + seen.staged() + " staged in " + worktree + ", unchanged since "
                + Instant.ofEpochMilli(movedAt).truncatedTo(ChronoUnit.MINUTES);
    }

    public Conflict toldAt(long now, String reason) {
        return new Conflict(cause, since, progress, movedAt, new Told(now, reason), hold);
    }

    /** Told, and nothing has moved in the worktree since. */
    public boolean stalled() {
        return told != null && movedAt <= told.at();
    }

    public Conflict heldBy(long stopAt) {
        return new Conflict(cause, since, progress, movedAt, told, new Hold(stopAt));
    }

    public boolean isHeldBy(long stopAt) {
        return hold != null && hold.stopAt() == stopAt;
    }
}
