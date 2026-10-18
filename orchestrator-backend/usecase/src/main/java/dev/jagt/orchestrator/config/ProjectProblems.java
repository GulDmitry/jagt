package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.ProjectConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** What makes a project entry one jagt refuses to start with, as far as the filesystem answers. */
public final class ProjectProblems {

    private ProjectProblems() {
    }

    public static List<Problem> of(String key, ProjectConfig project) {
        List<Problem> problems = new ArrayList<>();
        String where = "projects." + key;
        if (isBlank(project.path())) {
            problems.add(new Problem("orchestrator." + where + ".path", where
                    + ".path is empty — set it to the base repository."));
        } else {
            problems.addAll(repositoryProblems(where, Path.of(project.path())));
        }
        if (isBlank(project.baseBranch())) {
            problems.add(new Problem("orchestrator." + where + ".baseBranch", where + ".baseBranch is empty — set"
                    + " the branch tasks are cut from, e.g. `origin/main`."));
        }
        if (project.deploysIntoTheBaseBranch()) {
            problems.add(new Problem("orchestrator." + where + ".deployBranch", where + ".deployBranch equals the"
                    + " base branch '" + project.baseBranchName() + "' — jagt never writes to the base branch."
                    + " Point it elsewhere or remove it."));
        }
        return problems;
    }

    private static List<Problem> repositoryProblems(String where, Path path) {
        String key = "orchestrator." + where + ".path";
        if (!Files.isDirectory(path)) {
            return List.of(new Problem(key, where + ".path " + path + " is not a directory."));
        }
        if (!Files.exists(path.resolve(".git"))) {
            return List.of(new Problem(key, where + ".path " + path + " is not a git repository."));
        }
        Path worktreeParent = path.toAbsolutePath().normalize().getParent();
        if (worktreeParent != null && !Files.isWritable(worktreeParent)) {
            return List.of(new Problem(key, where + ".path " + path + ": jagt cuts every worktree next to the"
                    + " repository and cannot write in " + worktreeParent + "."));
        }
        return List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
