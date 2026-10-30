package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * One press of deploy: its repositories in landing order, {@code at}, the index of the first one not yet pushed, and
 * {@code askedDigest}, the facts the session was asked to check the change on once the pipelines were green.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Deployment(long pressedAt, List<Target> targets, int at, @Nullable String askedDigest) {

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

    /** {@code change} applied to the target of {@code project}; none such leaves the deployment as it is. */
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
        return at < targets.size() && targets.get(at).conflicted() ? Optional.of(targets.get(at)) : Optional.empty();
    }

    /** What a conflicted target waits on: a merge that conflicts, or the pipeline of the commit it pushed. */
    public enum Cause { MERGE, RED_PIPELINE }

    /** -1 where {@code project} is none of the targets. */
    public int indexOf(String project) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).project().equals(project)) {
                return i;
            }
        }
        return -1;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Target(String project, String deployBranch, @Nullable String merge, List<String> fixes,
                         long pushedAt, @Nullable Cause conflict) {

        public Target {
            fixes = fixes == null ? List.of() : List.copyOf(fixes);
        }

        /** The branch is the one configured when the press was made. */
        public static Target of(String project, String deployBranch) {
            return new Target(project, deployBranch, null, List.of(), 0, null);
        }

        public Target withMerge(String merge) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflict);
        }

        public Target withFix(String fix) {
            List<String> all = new ArrayList<>(fixes);
            all.add(fix);
            return new Target(project, deployBranch, merge, all, pushedAt, conflict);
        }

        public Target withPushedAt(long pushedAt) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflict);
        }

        public boolean conflicted() {
            return conflict != null;
        }

        public Target withConflict(@Nullable Cause conflict) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflict);
        }
    }
}
