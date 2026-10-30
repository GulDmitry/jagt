package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** What a deploy did, as data; {@link TaskReducer} is the one place that records it. */
public sealed interface DeployStep {

    Deployment applyTo(Deployment deployment, long now);

    record Opened(List<Deployment.Target> targets) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return Deployment.defaults().withPressedAt(now).withTargets(targets.stream()
                    .map(target -> deployment.targets().stream()
                            .filter(earlier -> earlier.project().equals(target.project())).findFirst()
                            .map(target::carrying).orElse(target)).toList());
        }
    }

    record Merged(String project, String sha) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, target -> target.merged(sha).withConflict(null));
        }
    }

    record Fixed(String project, String sha) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, target -> target.withFix(sha).withConflict(null));
        }
    }

    record Conflicted(String project, Deployment.Cause cause) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            int index = deployment.indexOf(project);
            return index < 0 ? deployment
                    : deployment.withTarget(project, target -> target.withConflict(Conflict.of(cause, now))).withAt(index);
        }
    }

    record Pushed(String project, long at) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            int index = deployment.indexOf(project);
            return index < 0 ? deployment : deployment.withTarget(project, target -> target.withPushedAt(at))
                    .withAt(Math.max(deployment.at(), index + 1));
        }
    }

    record Blocked(String project) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, Deployment.Target::withBlocked);
        }
    }

    record Reverted(String project, String sha) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, target -> target.withReverted(sha));
        }
    }

    record Discarded(String project) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, target -> target.withConflict(null));
        }
    }

    record Asked(String digest) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withAskedDigest(digest);
        }
    }

    /** Which landed targets settled with no pipeline listed on their tip: {@code projects}, and no other. */
    record Bare(List<String> projects) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTargets(deployment.targets().stream()
                    .map(target -> target.withBare(projects.contains(target.project()))).toList());
        }
    }

    /** A read of the deploy's pipelines that could not list the one on {@code unread}'s tip, null where it listed every one. */
    record ReadOn(@Nullable String unread) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTargets(deployment.targets().stream()
                    .map(target -> target.withUnread(target.project().equals(unread))).toList());
        }
    }

    record Seen(Conflict.Progress progress) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withConflict(conflict -> conflict.seen(progress, now));
        }
    }

    record Told(String reason) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withConflict(conflict -> conflict.toldAt(now, reason));
        }
    }

    record Held(long stopAt) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withConflict(conflict -> conflict.heldBy(stopAt));
        }
    }

    record Cleared() implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.conflictedTarget().map(Deployment.Target::project)
                    .map(project -> deployment.withTarget(project, target -> target.withConflict(null)))
                    .orElse(deployment);
        }
    }
}
