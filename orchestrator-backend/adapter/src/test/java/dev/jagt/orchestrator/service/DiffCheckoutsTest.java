package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffCheckoutsTest {

    @Test
    void excludesGitIgnoredPlumbingFromTheIdeDiffSnapshot(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitCommands commands = new GitCommands(runner, new LsofWorktreeProcesses(runner));
        GitWorktrees git = new GitWorktrees(commands);
        DiffCheckouts diffs = new DiffCheckouts(commands);
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
        Files.writeString(wt.resolve("f.txt"), "task change");
        Files.writeString(wt.resolve("new.js"), "new source");
        Files.writeString(wt.resolve("mcp_client.js"), "plumbing");
        Files.writeString(repo.resolve(".git").resolve("info").resolve("exclude"), "mcp_client.js\n");

        Path clean = diffs.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-1", "proj");

        boolean plumbing = Files.exists(clean.resolve("mcp_client.js"));
        String changed = Files.readString(clean.resolve("f.txt"));
        String added = Files.readString(clean.resolve("new.js"));
        git.removeDiffWorktrees(repo, "ABC-1", "proj");

        assertThat(plumbing).isFalse();
        assertThat(changed).isEqualTo("task change");
        assertThat(added).isEqualTo("new source");
    }

    @Test
    void prunesTheAdminEntryOfADiffCheckoutSomethingElseAlreadyDeleted(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitCommands commands = new GitCommands(runner, new LsofWorktreeProcesses(runner));
        GitWorktrees git = new GitWorktrees(commands);
        DiffCheckouts diffs = new DiffCheckouts(commands);
        Path base = diffs.checkoutBaseForDiff(repo, "origin/main", "ABC-8", "proj");
        runner.run(dir, timeout, List.of("rm", "-rf", base.toString()));

        git.removeDiffWorktrees(repo, "ABC-8", "proj");

        assertThat(runner.run(repo, timeout, List.of("git", "worktree", "list")).stdout())
                .doesNotContain(base.getFileName().toString());
    }

    @Test
    void deletesBothThrowawayDiffCheckoutsWhenTheTaskIsRetired(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitCommands commands = new GitCommands(runner, new LsofWorktreeProcesses(runner));
        GitWorktrees git = new GitWorktrees(commands);
        DiffCheckouts diffs = new DiffCheckouts(commands);
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-9", "origin/main", BranchStrategy.FRESH);
        Path base = diffs.checkoutBaseForDiff(repo, "origin/main", "ABC-9", "proj");
        Path clean = diffs.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-9", "proj");

        git.removeDiffWorktrees(repo, "ABC-9", "proj");

        assertThat(base).doesNotExist();
        assertThat(clean).doesNotExist();
    }

    @Test
    void keepsTheDiffCheckoutsApartWhenOneTaskSpansTwoRepositories(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path apiOrigin = dir.resolve("api-origin.git");
        Path api = dir.resolve("api");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", apiOrigin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", apiOrigin.toString(), api.toString()));
        Files.writeString(api.resolve("f.txt"), "api base");
        runner.run(api, timeout, List.of("git", "add", "."));
        runner.run(api, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(api, timeout, List.of("git", "push", "-q", "origin", "main"));
        Path webOrigin = dir.resolve("web-origin.git");
        Path web = dir.resolve("web");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", webOrigin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", webOrigin.toString(), web.toString()));
        Files.writeString(web.resolve("f.txt"), "web base");
        runner.run(web, timeout, List.of("git", "add", "."));
        runner.run(web, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(web, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitCommands commands = new GitCommands(runner, new LsofWorktreeProcesses(runner));
        GitWorktrees git = new GitWorktrees(commands);
        DiffCheckouts diffs = new DiffCheckouts(commands);

        Path apiBase = diffs.checkoutBaseForDiff(api, "origin/main", "ABC-7", "api");
        Path webBase = diffs.checkoutBaseForDiff(web, "origin/main", "ABC-7", "web");

        String apiSaid = Files.readString(apiBase.resolve("f.txt"));
        String webSaid = Files.readString(webBase.resolve("f.txt"));
        git.removeDiffWorktrees(api, "ABC-7", "api");
        git.removeDiffWorktrees(web, "ABC-7", "web");
        assertThat(apiSaid).isEqualTo("api base");
        assertThat(webSaid).isEqualTo("web base");
    }
}
