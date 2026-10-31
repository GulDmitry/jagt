package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectFolders;
import dev.jagt.orchestrator.service.ProjectRemoval;
import dev.jagt.orchestrator.service.ProjectSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectsControllerTest {

    private final ProjectSetup setup = mock(ProjectSetup.class);
    private final ProjectFolders folders = mock(ProjectFolders.class);
    private final ProjectRemoval removal = mock(ProjectRemoval.class);
    private final ProjectsController projects = new ProjectsController(setup, folders, removal);

    @Test
    void servesWhatTheSetupReadsAtThePathTheHumanTyped() {
        ProjectSetup.Reading reading = new ProjectSetup.Reading(
                List.of(new ProjectSetup.Line(true, "a git repository")), "demo", "main", "main", List.of(), List.of());
        when(setup.read("/work/demo", null)).thenReturn(reading);

        assertThat(projects.check("/work/demo", null)).isSameAs(reading);
    }

    @Test
    void readsAPathWithoutCountingTheProjectBeingEditedAsAnotherOnePointingThere() {
        ProjectSetup.Reading reading = new ProjectSetup.Reading(
                List.of(new ProjectSetup.Line(true, "no project points here yet")), "demo", "main", "", List.of(),
                List.of());
        when(setup.read("/work/demo", "demo")).thenReturn(reading);

        assertThat(projects.check("/work/demo", "demo")).isSameAs(reading);
    }

    @Test
    void servesTheEntryTheEditFormStartsFrom() {
        ProjectSetup.Entry entry = new ProjectSetup.Entry("demo", "/work/demo", "main", "dev", List.of("make"), "one");
        when(setup.entry("demo")).thenReturn(entry);

        assertThat(projects.entry("demo")).isSameAs(entry);
    }

    @Test
    void answersAnAddedProjectWithWhatTheSetupSaid() {
        ProjectSetup.Entry entry = new ProjectSetup.Entry("demo", "/work/demo", "main", null, null, null);
        when(setup.add(entry)).thenReturn("demo added");

        assertThat(projects.add(entry).message()).isEqualTo("demo added");
    }

    @Test
    void servesTheFoldersUnderWhatTheHumanTyped() {
        List<ProjectFolders.Folder> found = List.of(new ProjectFolders.Folder("billing", true));
        when(folders.under("/work/bi")).thenReturn(found);

        assertThat(projects.dirs("/work/bi")).isEqualTo(found);
    }

    @Test
    void changesTheProjectTheUrlNamesWhateverKeyTheBodyCarries() {
        when(setup.edit(new ProjectSetup.Entry("demo", "/work/demo", "main", null, null, "one line")))
                .thenReturn("Changed demo in jagt.yml");

        assertThat(projects.edit("demo", new ProjectSetup.Entry("other", "/work/demo", "main", null, null, "one line"))
                .message()).isEqualTo("Changed demo in jagt.yml");
    }

    @Test
    void removesTheProjectTheUrlNames() {
        when(removal.remove("demo")).thenReturn("Removed demo from jagt.yml");

        assertThat(projects.remove("demo").message()).isEqualTo("Removed demo from jagt.yml");
    }
}
