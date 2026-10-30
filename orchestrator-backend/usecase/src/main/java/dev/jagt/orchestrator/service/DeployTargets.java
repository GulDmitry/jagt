package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Each repository of a task paired with where it lands, as the deploy, its undo and the conflict registry read it. */
@Component
@RequiredArgsConstructor
public class DeployTargets {

    private final ConfigService configService;
    private final GitDeploy gitDeploy;

    /**
     * Every repository the task works in. All resolved before anything is pushed, so a project misconfigured at the
     * end of the list cannot be discovered half way through.
     */
    public List<Target> all(TaskState task) {
        List<Target> targets = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            targets.add(new Target(Objects.requireNonNull(repo.project()), configService.project(Objects.requireNonNull(repo.project()))));
        }
        return targets;
    }

    /**
     * The repositories an undo has something to take out, and ONLY those: a repository that never landed must not
     * stand between a human and the merge that is live, whatever became of its configuration since.
     */
    public List<Target> landed(TaskState task) {
        List<Target> landed = new ArrayList<>();
        for (Deployment.Target recorded : Optional.ofNullable(task.deployment()).map(Deployment::targets).orElse(List.of())) {
            if (recorded.landed()) {
                landed.add(new Target(recorded.project(), configService.project(recorded.project())));
            }
        }
        return landed;
    }

    /** Read where the project is still configured: a recipe for the human must survive one that was removed. */
    public Optional<String> deployBranch(String project) {
        return Optional.ofNullable(configService.load().projects().get(project))
                .map(ProjectConfig::deployBranch)
                .filter(branch -> !branch.isBlank());
    }

    /**
     * Where a deploy handed back from a conflict waits: the target its record flags conflicted. A project gone from
     * the configuration holds nothing to wait in.
     */
    public Optional<Target> stopped(TaskState task) {
        if (!FlowRules.conflictedInTheDeployWorktree(task.status())) {
            return Optional.empty();
        }
        Map<String, ProjectConfig> projects = configService.load().projects();
        return Optional.ofNullable(task.deployment())
                .flatMap(Deployment::conflictedTarget).map(Deployment.Target::project)
                .filter(projects::containsKey)
                .map(project -> new Target(project, Objects.requireNonNull(projects.get(project))));
    }

    /** What the task's deployment waits on, as its record says; none while it stands in no conflict. */
    public static Optional<Deployment.Cause> conflictCause(TaskState task) {
        return Optional.ofNullable(task.deployment()).flatMap(Deployment::conflictedTarget)
                .map(target -> Objects.requireNonNull(target.conflict()).cause());
    }

    /** Whether the deploy waits for its pipeline, as its record says. */
    public static boolean handedBackForItsPipeline(TaskState task) {
        return conflictCause(task).filter(cause -> cause == Deployment.Cause.RED_PIPELINE).isPresent();
    }

    /** The deploy branch must NEVER be the base branch tasks are cut from. */
    public static void requireDeployable(Target target) {
        ProjectConfig project = target.config();
        if (project.deployBranch() == null || project.deployBranch().isBlank()) {
            throw Refusal.byState("Project '" + target.project()
                    + "' has no deployBranch in jagt.yml — set it to enable deploy");
        }
        if (project.deploysIntoTheBaseBranch()) {
            throw Refusal.byState("REFUSED: deployBranch equals the base branch '"
                    + project.baseBranchName()
                    + "'. jagt must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
    }

    public static String names(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    /** A cause without a message must not end the report in the word "null". */
    public static String because(RuntimeException cause) {
        return cause.getMessage() == null ? "" : " " + cause.getMessage();
    }

    public record Target(String project, ProjectConfig config) {

        public Path path() {
            return Path.of(config.path());
        }

        public String deployBranch() {
            return config.deployBranch();
        }
    }
}
