package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"https://host/mr/1,true", "'  ',false", "NULL,false"})
    void holdsAReviewRequestOnlyWhereALinkWasWritten(String mrUrl, boolean holds) {
        assertThat(TaskRepo.of("proj", "/w").withMrUrl(mrUrl).hasReviewRequest()).isEqualTo(holds);
    }
}
