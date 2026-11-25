package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.capability.deploy.GitCaseFacts;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCaseFactsTest {

  @Test
  void listsOnlyTheMergesNamingTheTaskNewestFirst(@TempDir Path dir) throws Exception {
    Processes runner = new ProcessRunner();
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "dev");
    for (String merged : List.of("ABC-1 first", "ABC-2 other", "ABC-1 second")) {
      GitRepos.git(runner, repo, "checkout", "-q", "-b", "topic", "main");
      Files.writeString(repo.resolve(merged.replace(' ', '-') + ".txt"), merged);
      GitRepos.commit(runner, repo, merged);
      GitRepos.git(runner, repo, "checkout", "-q", "dev");
      GitRepos.git(runner, repo, "merge", "--no-ff", "-qm", "Merge " + merged, "topic");
      GitRepos.git(runner, repo, "branch", "-D", "topic");
    }
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    GitCaseFacts facts =
        new GitCaseFacts(GitRepos.commands(runner), mock(GitDeploy.class), mock(GitRevert.class));

    List<GitCaseFacts.Candidate> found = facts.mergeCandidates(repo, "dev", "ABC-1");

    assertThat(found)
        .extracting(GitCaseFacts.Candidate::subject)
        .containsExactly("Merge ABC-1 second", "Merge ABC-1 first");
  }
}
