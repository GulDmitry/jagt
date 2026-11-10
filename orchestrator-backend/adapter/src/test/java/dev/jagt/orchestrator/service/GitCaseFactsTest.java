package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.capability.deploy.GitCaseFacts;
import dev.jagt.orchestrator.port.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCaseFactsTest {

  @Test
  void listsOnlyTheMergesNamingTheTaskNewestFirst(@TempDir Path dir) throws Exception {
    Processes runner = new ProcessRunner();
    Duration t = Duration.ofSeconds(30);
    Path origin = dir.resolve("o.git");
    Path repo = dir.resolve("repo");
    runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
    runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
    Files.writeString(repo.resolve("f.txt"), "base");
    runner.run(repo, t, List.of("git", "add", "."));
    runner.run(
        repo,
        t,
        List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
    runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
    runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "dev"));
    for (String merged : List.of("ABC-1 first", "ABC-2 other", "ABC-1 second")) {
      runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "topic", "main"));
      Files.writeString(repo.resolve(merged.replace(' ', '-') + ".txt"), merged);
      runner.run(repo, t, List.of("git", "add", "."));
      runner.run(
          repo,
          t,
          List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", merged));
      runner.run(repo, t, List.of("git", "checkout", "-q", "dev"));
      runner.run(
          repo,
          t,
          List.of(
              "git",
              "-c",
              "user.email=t@t",
              "-c",
              "user.name=t",
              "merge",
              "--no-ff",
              "-qm",
              "Merge " + merged,
              "topic"));
      runner.run(repo, t, List.of("git", "branch", "-D", "topic"));
    }
    runner.run(repo, t, List.of("git", "push", "-q", "origin", "dev"));
    GitCaseFacts facts =
        new GitCaseFacts(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> Optional.empty(),
                stopped -> {}),
            null);

    List<GitCaseFacts.Candidate> found = facts.mergeCandidates(repo, "dev", "ABC-1");

    assertThat(found).extracting(GitCaseFacts.Candidate::subject)
        .containsExactly("Merge ABC-1 second", "Merge ABC-1 first");
  }
}
