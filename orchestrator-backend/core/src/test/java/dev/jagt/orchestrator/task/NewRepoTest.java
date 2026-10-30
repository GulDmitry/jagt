package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewRepoTest {

    @Test
    void recordsARepositoryWithItsWorktreeAndRemoteAndNoRequestOrDeployYet() {
        NewRepo cut = new NewRepo("api", new ProjectConfig("/repos/api", "origin/main", "dev", List.of()),
                Path.of("/repos/api"), Path.of("/wt/api"), Path.of("/repos/api/.git"), "origin/main",
                "git@host:g/api.git", true);

        TaskRepo registered = cut.registered();

        assertThat(registered.project()).isEqualTo("api");
        assertThat(registered.worktreePath()).isEqualTo("/wt/api");
        assertThat(registered.remoteUrl()).isEqualTo("git@host:g/api.git");
        assertThat(registered.mrUrl()).isNull();
    }
}
