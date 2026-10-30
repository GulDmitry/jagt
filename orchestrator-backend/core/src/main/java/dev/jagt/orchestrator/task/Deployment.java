package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** One press of deploy: its repositories in landing order, and {@code at}, the index of the first one not yet pushed. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Deployment(long pressedAt, List<Target> targets, int at) {

    public Deployment {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public static Deployment defaults() {
        return new Deployment(0, List.of(), 0);
    }

    public Deployment withPressedAt(long pressedAt) {
        return new Deployment(pressedAt, targets, at);
    }

    public Deployment withTargets(List<Target> targets) {
        return new Deployment(pressedAt, targets, at);
    }

    public Deployment withAt(int at) {
        return new Deployment(pressedAt, targets, at);
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
                         long pushedAt, boolean conflicted) {

        public Target {
            fixes = fixes == null ? List.of() : List.copyOf(fixes);
        }

        /** The branch is the one configured when the press was made. */
        public static Target of(String project, String deployBranch) {
            return new Target(project, deployBranch, null, List.of(), 0, false);
        }

        public Target withMerge(String merge) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflicted);
        }

        public Target withFix(String fix) {
            List<String> all = new ArrayList<>(fixes);
            all.add(fix);
            return new Target(project, deployBranch, merge, all, pushedAt, conflicted);
        }

        public Target withPushedAt(long pushedAt) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflicted);
        }

        public Target withConflicted(boolean conflicted) {
            return new Target(project, deployBranch, merge, fixes, pushedAt, conflicted);
        }
    }
}
