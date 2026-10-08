package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRepoTest {

    @Test
    void forgetsTheCredentialARemoteWasStoredWithBeforeItWasStripped() {
        String stored = """
                {"project":"proj","worktreePath":"/w/ABC-42","remoteUrl":"https://u:t0ken@example.com/g/proj.git"}""";

        TaskRepo repo = new JsonMapper().readValue(stored, TaskRepo.class);

        assertThat(repo.remoteUrl()).isEqualTo("https://example.com/g/proj.git");
    }
}
