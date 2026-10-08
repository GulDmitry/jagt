package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Each repository of a task paired with where it lands, as the deploy, its undo and the conflict registry read it. */
@Component
@RequiredArgsConstructor
class DeployTargets {

    private final ConfigService configService;

    /**
     * Every repository the task works in. All resolved before anything is pushed, so a project misconfigured at the
     * end of the list cannot be discovered half way through.
     */
    List<Target> all(TaskState task) {
        List<Target> targets = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            targets.add(new Target(repo.project(), configService.project(repo.project())));
        }
        return targets;
    }

    /**
     * The repositories an undo has something to take out, and ONLY those: a repository that never landed must not
     * stand between a human and the merge that is live, whatever became of its configuration since.
     */
    List<Target> landed(TaskState task) {
        List<Target> landed = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            if (repo.deployCommit() != null && !repo.deployCommit().isBlank()) {
                landed.add(new Target(repo.project(), configService.project(repo.project())));
            }
        }
        return landed;
    }

    /** The deploy branch must NEVER be the base branch tasks are cut from. */
    static void requireDeployable(Target target) {
        ProjectConfig project = target.config();
        if (project.deployBranch() == null || project.deployBranch().isBlank()) {
            throw new IllegalArgumentException("Project '" + target.project()
                    + "' has no deployBranch in jagt.yml — set it to enable deploy");
        }
        if (project.deploysIntoTheBaseBranch()) {
            throw new IllegalArgumentException("REFUSED: deployBranch equals the base branch '"
                    + project.baseBranchName()
                    + "'. jagt must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
    }

    static String mergeCommit(TaskState task, Target target) {
        String commit = task.repo(target.project()).map(TaskRepo::deployCommit).orElse(null);
        return commit == null || commit.isBlank() ? null : commit;
    }

    static String names(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    /** A cause without a message must not end the report in the word "null". */
    static String because(RuntimeException cause) {
        return cause.getMessage() == null ? "" : " " + cause.getMessage();
    }

    record Target(String project, ProjectConfig config) {

        Path path() {
            return Path.of(config.path());
        }

        String deployBranch() {
            return config.deployBranch();
        }
    }
}
