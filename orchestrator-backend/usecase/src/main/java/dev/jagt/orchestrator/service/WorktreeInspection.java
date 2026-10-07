package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** What a worktree holds against its base, read without touching it; jagt's own files never count as work. */
@Service
@RequiredArgsConstructor
public class WorktreeInspection {

    private final GitCommands git;
    private final AgentRuntime agentRuntime;

    static List<String> branchNames(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        return stdout.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /**
     * Whether the agent's work is sitting in this worktree uncommitted. jagt's own generated files are excluded
     * exactly as they are from a commit, so a freshly provisioned worktree does not read as work.
     */
    public boolean hasUncommittedChanges(Path projectPath, Path worktree) {
        List<String> generated = WorktreeFiles.generated(agentRuntime);
        return git.locked(projectPath, () -> branchNames(git.run(worktree,
                                List.of("git", "status", "--porcelain"))
                        .expectSuccess("git status in " + worktree).stdout()).stream()
                .map(WorktreeInspection::changedPath)
                .anyMatch(path -> !generated.contains(path)));
    }

    /** One string that changes with any edit to the worktree's work, and with none of jagt's own files. */
    public String treeState(Path projectPath, Path worktree) {
        List<String> generated = WorktreeFiles.generated(agentRuntime);
        return git.locked(projectPath, () -> {
            String head = git.run(worktree, List.of("git", "rev-parse", "HEAD"))
                    .expectSuccess("git rev-parse in " + worktree).stdout().strip();
            String diff = git.run(worktree, List.of("git", "diff", "HEAD"))
                    .expectSuccess("git diff in " + worktree).stdout();
            String changed = branchNames(git.run(worktree,
                            List.of("git", "status", "--porcelain"))
                    .expectSuccess("git status in " + worktree).stdout()).stream()
                    .filter(line -> !generated.contains(changedPath(line)))
                    .collect(java.util.stream.Collectors.joining("\n"));
            return head + ":" + Integer.toHexString((diff + changed).hashCode());
        });
    }

    /**
     * Whether this worktree's branch carries commits its target does not hold. Read in the worktree against the
     * {@code origin/} ref the repository already has: a ship asks what is here, not what the host has since gained.
     */
    public boolean aheadOfTarget(Path projectPath, Path worktree, String targetBranch) {
        return git.locked(projectPath, () -> !"0".equals(git.run(worktree,
                        List.of("git", "rev-list", "--count", "origin/" + targetBranch + "..HEAD"))
                .expectSuccess("git rev-list count in " + worktree).stdout().trim()));
    }

    /** Everything the worktree changed since it left its base at origin, committed and not; new files by name. */
    public String changesSince(Path projectPath, Path worktree, String baseBranch) {
        List<String> generated = WorktreeFiles.generated(agentRuntime);
        return git.locked(projectPath, () -> {
            String diff = git.run(worktree,
                            List.of("git", "diff", "--no-color", "--no-ext-diff", mergeBase(worktree, baseBranch)))
                    .expectSuccess("git diff in " + worktree).stdout();
            List<String> added = branchNames(git.run(worktree,
                            List.of("git", "ls-files", "--others", "--exclude-standard"))
                    .expectSuccess("git ls-files in " + worktree).stdout()).stream()
                    .filter(path -> !generated.contains(path)).toList();
            return added.isEmpty() ? diff : diff + "New files, not in the diff: " + String.join(", ", added) + "\n";
        });
    }

    /** Lines the worktree added to the repository's agent files since it left its base, committed and not. */
    public int agentFileLinesAdded(Path projectPath, Path worktree, String baseBranch) {
        return git.locked(projectPath, () -> {
            List<String> command = new ArrayList<>(List.of("git", "diff", "--numstat",
                    mergeBase(worktree, baseBranch), "--"));
            command.addAll(agentRuntime.projectAgentFiles());
            return git.run(worktree, command)
                    .expectSuccess("git diff --numstat in " + worktree).stdout().lines()
                    .map(line -> line.split("\t")[0])
                    .filter(added -> added.matches("\\d+"))
                    .mapToInt(Integer::parseInt).sum();
        });
    }

    private String mergeBase(Path worktree, String baseBranch) {
        return git.run(worktree,
                        List.of("git", "merge-base", "origin/" + baseBranch.replaceFirst("^origin/", ""), "HEAD"))
                .expectSuccess("git merge-base in " + worktree).stdout().strip();
    }

    /**
     * The path out of one {@code git status --porcelain} line. Split at the FIRST space of the stripped line rather
     * than a fixed offset: the status field is one or two letters wide, and a path may hold spaces of its own.
     */
    private static String changedPath(String porcelainLine) {
        int afterStatus = porcelainLine.indexOf(' ');
        String path = afterStatus < 0 ? porcelainLine : porcelainLine.substring(afterStatus + 1).strip();
        int renamed = path.indexOf(" -> ");
        return renamed < 0 ? path : path.substring(renamed + 4);
    }
}
