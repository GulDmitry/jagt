package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.nio.file.attribute.PosixFilePermission;

import static org.assertj.core.api.Assertions.assertThat;

class WorktreeHooksTest {

    @Test
    void leavesASessionUngatedRatherThanTakingAwayTheProjectsOwnHooks(@TempDir Path dir) {
        assertThat(WorktreeHooks.gitEnv(dir.resolve("a worktree from before this existed"))).isEmpty();
    }

    @Test
    void carriesAHookNameFromEveryRepositoryOfTheTaskIntoTheOneDirectoryASessionUses(@TempDir Path dir)
            throws Exception {
        Path first = dir.resolve("first/hooks");
        Path second = dir.resolve("second/hooks");
        Path worktree = dir.resolve("wt");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.createDirectories(worktree);
        Files.writeString(first.resolve("pre-commit"), "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(first.resolve("pre-commit"), Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        Files.writeString(second.resolve("commit-msg"), "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(second.resolve("commit-msg"), Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));

        WorktreeHooks.install(worktree, List.of(first, second), "ABC-42");

        assertThat(worktree.resolve(".jagt/hooks"))
                .isDirectoryContaining("glob:**/pre-commit")
                .isDirectoryContaining("glob:**/commit-msg");
    }

    @Test
    void refusesAPushToAnyBranchButTheTaskBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", worktree.toString(), "-b", "ABC-42"));
        Files.writeString(worktree.resolve("f.txt"), "work");
        runner.run(worktree, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qam", "work"));
        WorktreeHooks.install(worktree, List.of(repo.resolve(".git").resolve("hooks")), "ABC-42");

        Process push = new ProcessBuilder("/bin/sh", "-c",
                WorktreeHooks.gitEnv(worktree) + "git push origin HEAD:refs/heads/main")
                .directory(worktree.toFile()).redirectErrorStream(true).start();
        String output = new String(push.getInputStream().readAllBytes());
        assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(push.exitValue()).isNotZero();
        assertThat(output).contains("refs/heads/main is not this task's branch");
    }

    @Test
    void pushesTheTaskBranchItself(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", worktree.toString(), "-b", "ABC-42"));
        WorktreeHooks.install(worktree, List.of(repo.resolve(".git").resolve("hooks")), "ABC-42");

        Process push = new ProcessBuilder("/bin/sh", "-c",
                WorktreeHooks.gitEnv(worktree) + "git push origin ABC-42")
                .directory(worktree.toFile()).redirectErrorStream(true).start();
        String output = new String(push.getInputStream().readAllBytes());
        assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(push.exitValue()).describedAs(output).isZero();
    }

    @Test
    void refusesDeletingTheBranchTheReviewRequestIsBuiltOn(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", worktree.toString(), "-b", "ABC-42"));
        runner.run(worktree, timeout, List.of("git", "push", "-q", "origin", "ABC-42"));
        WorktreeHooks.install(worktree, List.of(repo.resolve(".git").resolve("hooks")), "ABC-42");

        Process push = new ProcessBuilder("/bin/sh", "-c",
                WorktreeHooks.gitEnv(worktree) + "git push origin :ABC-42")
                .directory(worktree.toFile()).redirectErrorStream(true).start();
        String output = new String(push.getInputStream().readAllBytes());
        assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(push.exitValue()).isNotZero();
        assertThat(output).contains("refuses deleting refs/heads/ABC-42");
    }

    @Test
    void runsTheProjectsOwnPushHookOnAnAllowedPush(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", worktree.toString(), "-b", "ABC-42"));
        Path projectHooks = repo.resolve(".git").resolve("hooks");
        Files.writeString(projectHooks.resolve("pre-push"),
                "#!/bin/sh\ncat > " + dir.resolve("seen-by-the-project.txt") + "\n");
        Files.setPosixFilePermissions(projectHooks.resolve("pre-push"), Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        WorktreeHooks.install(worktree, List.of(projectHooks), "ABC-42");

        Process push = new ProcessBuilder("/bin/sh", "-c",
                WorktreeHooks.gitEnv(worktree) + "git push origin ABC-42")
                .directory(worktree.toFile()).redirectErrorStream(true).start();
        assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(dir.resolve("seen-by-the-project.txt")).content().contains("refs/heads/ABC-42");
    }

    @Test
    void delegatesToTheHooksOfTheRepositoryBeingPushedNotOfTheOneItWasWrittenFor(@TempDir Path dir)
            throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path sessionRepo = dir.resolve("session");
        Path siblingRepo = dir.resolve("sibling");
        Path sessionWorktree = dir.resolve("session-wt");
        Path siblingWorktree = dir.resolve("sibling-wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), sessionRepo.toString()));
        Files.writeString(sessionRepo.resolve("f.txt"), "base");
        runner.run(sessionRepo, timeout, List.of("git", "add", "."));
        runner.run(sessionRepo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(sessionRepo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), siblingRepo.toString()));
        runner.run(sessionRepo, timeout,
                List.of("git", "worktree", "add", "-q", sessionWorktree.toString(), "-b", "ABC-42"));
        runner.run(siblingRepo, timeout,
                List.of("git", "worktree", "add", "-q", siblingWorktree.toString(), "-b", "ABC-42"));
        Files.writeString(siblingRepo.resolve(".git").resolve("hooks").resolve("pre-push"),
                "#!/bin/sh\ncat > " + dir.resolve("seen-by-the-sibling.txt") + "\n");
        Files.setPosixFilePermissions(siblingRepo.resolve(".git").resolve("hooks").resolve("pre-push"),
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        WorktreeHooks.install(sessionWorktree, List.of(sessionRepo.resolve(".git").resolve("hooks")), "ABC-42");

        Process push = new ProcessBuilder("/bin/sh", "-c",
                WorktreeHooks.gitEnv(sessionWorktree) + "git push origin ABC-42")
                .directory(siblingWorktree.toFile()).redirectErrorStream(true).start();
        assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(dir.resolve("seen-by-the-sibling.txt")).content().contains("refs/heads/ABC-42");
    }

    @Test
    void keepsTheProjectsOtherHooksRunningInTheSession(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        Files.createDirectories(repo);
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", worktree.toString(), "-b", "ABC-42"));
        Path projectHooks = repo.resolve(".git").resolve("hooks");
        Files.writeString(projectHooks.resolve("pre-commit"),
                "#!/bin/sh\necho ran > " + dir.resolve("linted.txt") + "\n");
        Files.setPosixFilePermissions(projectHooks.resolve("pre-commit"), Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        WorktreeHooks.install(worktree, List.of(projectHooks), "ABC-42");

        Files.writeString(worktree.resolve("f.txt"), "changed");
        Process commit = new ProcessBuilder("/bin/sh", "-c", WorktreeHooks.gitEnv(worktree)
                + "git -c user.email=t@t -c user.name=t commit -qam work")
                .directory(worktree.toFile()).redirectErrorStream(true).start();
        assertThat(commit.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(dir.resolve("linted.txt")).exists();
    }
}
