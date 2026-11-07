package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * One press of deploy: its repositories in landing order, {@code at}, the index of the first one
 * not yet pushed, and {@code askedDigest}, the facts the session was asked to check the change on
 * once the pipelines were green.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Deployment(
    long pressedAt, List<Target> targets, int at, @Nullable String askedDigest) {

  public Deployment {
    targets = targets == null ? List.of() : List.copyOf(targets);
  }

  public static Deployment defaults() {
    return new Deployment(0, List.of(), 0, null);
  }

  public Deployment withPressedAt(long pressedAt) {
    return new Deployment(pressedAt, targets, at, askedDigest);
  }

  public Deployment withTargets(List<Target> targets) {
    return new Deployment(pressedAt, targets, at, askedDigest);
  }

  public Deployment withAt(int at) {
    return new Deployment(pressedAt, targets, at, askedDigest);
  }

  public Deployment withAskedDigest(String askedDigest) {
    return new Deployment(pressedAt, targets, at, askedDigest);
  }

  /**
   * {@code change} applied to the target of {@code project}; none such leaves the deployment as it
   * is.
   */
  public Deployment withTarget(String project, UnaryOperator<Target> change) {
    List<Target> changed = new ArrayList<>(targets);
    for (int i = 0; i < changed.size(); i++) {
      if (changed.get(i).project().equals(project)) {
        changed.set(i, change.apply(changed.get(i)));
      }
    }
    return withTargets(changed);
  }

  /** The target the press stopped at for a conflict; none while no target is flagged conflicted. */
  public Optional<Target> conflictedTarget() {
    return at < targets.size() && targets.get(at).conflicted()
        ? Optional.of(targets.get(at))
        : Optional.empty();
  }

  /**
   * A conflict held by pipelines alone: some target is conflicted and none for a merge or a revert.
   */
  public boolean heldByRedPipeline() {
    List<Conflict> held =
        targets.stream().map(t -> t.progress().conflict()).filter(Objects::nonNull).toList();
    return !held.isEmpty()
        && held.stream().allMatch(conflict -> conflict.cause() == Cause.RED_PIPELINE);
  }

  /**
   * {@code change} applied to the conflict of the target the press stopped at; none leaves the
   * deployment as it is.
   */
  public Deployment withConflict(UnaryOperator<Conflict> change) {
    Optional<Target> stopped = conflictedTarget();
    return stopped.isEmpty()
        ? this
        : withTarget(
            stopped.get().project(),
            target ->
                target.withConflict(
                    change.apply(Objects.requireNonNull(target.progress().conflict()))));
  }

  /**
   * What a conflicted target waits on: a merge that conflicts, the pipeline of the commit it
   * pushed, or a revert that conflicts.
   */
  public enum Cause {
    MERGE,
    RED_PIPELINE,
    REVERT
  }

  /** -1 where {@code project} is none of the targets. */
  public int indexOf(String project) {
    for (int i = 0; i < targets.size(); i++) {
      if (targets.get(i).project().equals(project)) {
        return i;
      }
    }
    return -1;
  }

  /** The commit each landed target stands at; what a QA verdict names as the sha it checked. */
  public String tips() {
    return targets.stream()
        .filter(Target::landed)
        .map(target -> target.project() + "@" + target.tip())
        .collect(java.util.stream.Collectors.joining(","));
  }

  /** A recorded merge is one no revert has taken out yet. */
  public boolean live() {
    return targets.stream().anyMatch(Target::landed);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Target(
      String project,
      String deployBranch,
      @JsonUnwrapped Landed landing,
      @Nullable String revertCommit,
      @JsonUnwrapped Progress progress) {

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Landed(@Nullable String merge, List<String> fixes, long pushedAt) {

      public Landed {
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
      }

      public static Landed defaults() {
        return new Landed(null, List.of(), 0);
      }
    }

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Progress(
        @Nullable Boolean blocked,
        @Nullable Boolean bare,
        @Nullable Integer unread,
        @Nullable String retried,
        @JsonProperty("conflicted") @Nullable Conflict conflict) {

      public static Progress defaults() {
        return new Progress(null, null, null, null, null);
      }
    }

    /** The branch is the one configured when the press was made. */
    public static Target of(String project, String deployBranch) {
      return new Target(project, deployBranch, Landed.defaults(), null, Progress.defaults());
    }

    public Target withMerge(String merge) {
      return new Target(project, deployBranch, landing.withMerge(merge), revertCommit, progress);
    }

    /** A new merge: what a revert takes out, with no fix on top of it yet. */
    public Target merged(String merge) {
      return new Target(
          project,
          deployBranch,
          new Landed(merge, List.of(), landing.pushedAt()),
          null,
          Progress.defaults().withConflict(progress.conflict()));
    }

    /**
     * A fix committed on the deploy branch after the merge; a target that recorded no merge takes
     * it as the merge.
     */
    public Target withFix(String fix) {
      if (!landed()) {
        return merged(fix);
      }
      List<String> all = new ArrayList<>(landing.fixes());
      all.add(fix);
      return new Target(
          project,
          deployBranch,
          landing.withFixes(all),
          null,
          Progress.defaults().withConflict(progress.conflict()));
    }

    public Target withPushedAt(long pushedAt) {
      return new Target(
          project, deployBranch, landing.withPushedAt(pushedAt), revertCommit, progress);
    }

    public boolean conflicted() {
      return progress.conflict() != null;
    }

    public Target withConflict(@Nullable Conflict conflict) {
      return new Target(
          project, deployBranch, landing, revertCommit, progress.withConflict(conflict));
    }

    public Target withBlocked() {
      return new Target(
          project, deployBranch, landing, revertCommit, progress.withBlocked(Boolean.TRUE));
    }

    /**
     * Settled with no pipeline listed on its tip, so one the host lists later is still read; false
     * once one is.
     */
    public Target withBare(boolean bare) {
      return new Target(
          project,
          deployBranch,
          landing,
          revertCommit,
          progress.withBare(bare ? Boolean.TRUE : null));
    }

    /** Taken out of the deploy branch by the revert commit {@code sha}. */
    public Target withReverted(String sha) {
      return new Target(
          project,
          deployBranch,
          new Landed(null, List.of(), landing.pushedAt()),
          sha,
          Progress.defaults().withConflict(progress.conflict()));
    }

    /** Carries what an earlier press of the task left live in this repository into a new press. */
    public Target carrying(Target earlier) {
      return earlier.landed()
          ? new Target(
              project,
              deployBranch,
              new Landed(earlier.landing.merge(), earlier.landing.fixes(), 0),
              null,
              Progress.defaults())
          : this;
    }

    @JsonIgnore
    public boolean isBlocked() {
      return Boolean.TRUE.equals(progress.blocked());
    }

    @JsonIgnore
    public boolean isBare() {
      return Boolean.TRUE.equals(progress.bare());
    }

    /**
     * One more read running that could not list the pipeline on its tip where {@code unreadNow},
     * else none.
     */
    public Target withUnread(boolean unreadNow) {
      return new Target(
          project,
          deployBranch,
          landing,
          revertCommit,
          progress.withUnread(unreadNow ? unreadReads() + 1 : null));
    }

    @JsonIgnore
    public int unreadReads() {
      return progress.unread() == null ? 0 : progress.unread();
    }

    /**
     * The pipeline of {@code tip} re-run once, kept so a restart does not run it again; a new push
     * owes its own.
     */
    public Target withRetried(String tip) {
      return new Target(project, deployBranch, landing, revertCommit, progress.withRetried(tip));
    }

    @JsonIgnore
    public boolean retriedTip() {
      return tip() != null && tip().equals(progress.retried());
    }

    public boolean landed() {
      return landing.merge() != null && !landing.merge().isBlank();
    }

    /** The commit last pushed to the deploy branch for this task, which its pipeline runs on. */
    public @Nullable String tip() {
      return landing.fixes().isEmpty() ? landing.merge() : landing.fixes().getLast();
    }
  }
}
