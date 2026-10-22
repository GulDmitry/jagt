package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One deploy conflict handed to its session, keyed by the moment its task entered it: a later press is a new
 * conflict. {@code movedAt} is when its worktree last changed; {@code told} the human's one banner, null before.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConflictWatch(long conflictSince, String worktree, Progress progress, long movedAt, @Nullable Told told) {

    /** {@code digest} changes with any edit in the worktree, staged or not. */
    public record Progress(int unmerged, int staged, String digest) {
    }

    public record Told(long at, String reason) {
    }

    public static ConflictWatch handed(long conflictSince, String worktree, Progress progress, long now) {
        return new ConflictWatch(conflictSince, worktree, progress, now, null);
    }

    public boolean handedFor(long conflictSince) {
        return this.conflictSince == conflictSince;
    }

    public ConflictWatch seen(Progress now, long at) {
        return progress.equals(now) ? this : new ConflictWatch(conflictSince, worktree, now, at, told);
    }

    public boolean due(Duration after, long now) {
        return told == null && now - movedAt >= after.toMillis();
    }

    public ConflictWatch toldAt(long now) {
        return new ConflictWatch(conflictSince, worktree, progress, movedAt, new Told(now, progress.unmerged()
                + " paths unmerged, " + progress.staged() + " staged in " + worktree + ", unchanged since "
                + Instant.ofEpochMilli(movedAt).truncatedTo(ChronoUnit.MINUTES)));
    }

    /** Told, and nothing has moved in the worktree since. */
    public boolean stalled() {
        return told != null && movedAt <= told.at();
    }
}
