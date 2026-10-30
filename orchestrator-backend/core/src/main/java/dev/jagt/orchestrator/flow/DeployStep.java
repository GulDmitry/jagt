package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;

import java.util.List;

/** What a deploy did, as data; {@link TaskReducer} is the one place that records it. */
public sealed interface DeployStep {

    Deployment applyTo(Deployment deployment, long now);

    record Opened(List<Deployment.Target> targets) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return Deployment.defaults().withPressedAt(now).withTargets(targets);
        }
    }

    record Merged(String project, String sha) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withTarget(project, target -> target.withMerge(sha).withConflict(null));
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
                    : deployment.withTarget(project, target -> target.withConflict(cause)).withAt(index);
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

    record Asked(String digest) implements DeployStep {
        @Override
        public Deployment applyTo(Deployment deployment, long now) {
            return deployment.withAskedDigest(digest);
        }
    }
}
