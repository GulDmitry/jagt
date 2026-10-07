package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.agent.StubAgentProperties;
import dev.jagt.orchestrator.adapter.agent.StubAgentRuntime;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorktreeInspectionTest {

    @Test
    void readsTheWorkAWorktreeHoldsUncommittedAndIgnoresJagtsOwnFiles(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Files.createDirectories(repo);
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        Files.writeString(repo.resolve("a file.java"), "class A {}");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        WorktreeInspection git = new WorktreeInspection(new GitCommands(runner, new LsofWorktreeProcesses(runner)), new StubAgentRuntime(StubAgentProperties.defaults()));

        Files.writeString(repo.resolve("task_context.md"), "the round brief");
        assertThat(git.hasUncommittedChanges(repo, repo)).isFalse();

        Files.writeString(repo.resolve("a file.java"), "class A { int x; }");
        assertThat(git.hasUncommittedChanges(repo, repo)).isTrue();
    }

    @Test
    void readsTheTreeAsChangedByAnEditToItsWorkButNotByJagtsOwnFiles(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Files.createDirectories(repo);
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        Files.writeString(repo.resolve("A.java"), "class A {}");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        WorktreeInspection git = new WorktreeInspection(new GitCommands(runner, new LsofWorktreeProcesses(runner)), new StubAgentRuntime(StubAgentProperties.defaults()));
        Files.writeString(repo.resolve("A.java"), "class A { int x; }");
        String answered = git.treeState(repo, repo);

        Files.writeString(repo.resolve("task_context.md"), "the next brief");
        assertThat(git.treeState(repo, repo)).isEqualTo(answered);

        Files.writeString(repo.resolve("A.java"), "class A { int y; }");
        assertThat(git.treeState(repo, repo)).isNotEqualTo(answered);
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "origin/main"})
    void readsTheChangesSinceTheBaseCommittedAndNotNamingNewFilesButNotJagtsOwn(String baseBranch,
                                                                              @TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("a.txt"), "base\n");
        Files.writeString(repo.resolve("b.txt"), "base\n");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        Files.writeString(repo.resolve("a.txt"), "committed\n");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qam", "ABC-42 Widen the column"));
        Files.writeString(repo.resolve("b.txt"), "uncommitted\n");
        Files.writeString(repo.resolve("new.txt"), "fresh\n");
        Files.writeString(repo.resolve("task_context.md"), "the round brief");
        WorktreeInspection git = new WorktreeInspection(new GitCommands(runner, new LsofWorktreeProcesses(runner)), new StubAgentRuntime(StubAgentProperties.defaults()));

        String changes = git.changesSince(repo, repo, baseBranch);

        assertThat(changes).contains("+committed", "+uncommitted")
                .endsWith("New files, not in the diff: new.txt\n");
    }

    @Test
    void countsTheLinesTheTaskAddedToTheAgentFileCommittedAndNot(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\n");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\nregistry: local\n");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qam", "ABC-42 Name the registry"));
        Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\nregistry: local\ninfra: its own repo\n");
        Files.writeString(repo.resolve("other.md"), "not the agent file\n");
        WorktreeInspection git = new WorktreeInspection(new GitCommands(runner, new LsofWorktreeProcesses(runner)), new StubAgentRuntime(StubAgentProperties.defaults()));

        int added = git.agentFileLinesAdded(repo, repo, "main");

        assertThat(added).isEqualTo(2);
    }

    @Test
    void readsABranchAsAheadOfItsTargetOnlyOnceItCarriesACommitTheTargetLacks(@TempDir Path dir)
            throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        WorktreeInspection git = new WorktreeInspection(new GitCommands(runner, new LsofWorktreeProcesses(runner)), new StubAgentRuntime(StubAgentProperties.defaults()));

        assertThat(git.aheadOfTarget(repo, repo, "main")).isFalse();

        Files.writeString(repo.resolve("f.txt"), "changed");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "ABC-42 Widen the column"));

        assertThat(git.aheadOfTarget(repo, repo, "main")).isTrue();
    }

    @Test
    void readsOneBranchNamePerLineIgnoringBlanks() {
        assertThat(WorktreeInspection.branchNames("ABC-40\nABC-41\n\n  main  \n")).containsExactly(
                "ABC-40", "ABC-41", "main");
        assertThat(WorktreeInspection.branchNames("")).isEmpty();
    }
}
