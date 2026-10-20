package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectsControllerTest {

    private final ProjectSetup setup = mock(ProjectSetup.class);
    private final ProjectsController projects = new ProjectsController(setup);

    @Test
    void servesWhatTheSetupReadsAtThePathTheHumanTyped() {
        ProjectSetup.Reading reading = new ProjectSetup.Reading(
                List.of(new ProjectSetup.Line(true, "a git repository")), "demo", "main", "main", List.of());
        when(setup.read("/work/demo")).thenReturn(reading);

        assertThat(projects.check("/work/demo")).isSameAs(reading);
    }

    @Test
    void answersAnAddedProjectWithWhatTheSetupSaid() {
        ProjectSetup.Entry entry = new ProjectSetup.Entry("demo", "/work/demo", "main", null, null, null);
        when(setup.add(entry)).thenReturn("demo added");

        assertThat(projects.add(entry).message()).isEqualTo("demo added");
    }
}
