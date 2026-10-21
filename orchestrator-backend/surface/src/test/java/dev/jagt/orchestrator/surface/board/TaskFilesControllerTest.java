package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.TaskArtifacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskFilesControllerTest {

    private final TaskArtifacts artifacts = mock(TaskArtifacts.class);
    private final TaskFilesController api = new TaskFilesController(artifacts);

    @Test
    void listsWhatTheWorktreeHoldsNow() {
        when(artifacts.present("ABC-42")).thenReturn(List.of("plan.md"));

        assertThat(api.files("ABC-42")).containsExactly("plan.md");
    }

    @Test
    void answersADocumentAsHtmlUnderItsName() {
        when(artifacts.read("ABC-42", "plan.md")).thenReturn("# Plan");

        var rendered = api.file("ABC-42", "plan.md");

        assertThat(rendered.name()).isEqualTo("plan.md");
        assertThat(rendered.html()).contains("<h1>Plan</h1>");
    }
}
