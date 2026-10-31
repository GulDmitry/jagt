package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ProjectRemoval;
import dev.jagt.orchestrator.service.ProjectSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectCommandTest {

    private final ProjectSetup setup = mock(ProjectSetup.class);
    private final ProjectRemoval removal = mock(ProjectRemoval.class);
    private final ProjectCommand command = new ProjectCommand(setup, removal);

    @Test
    void handsTypedAloneOverToTheAddAProjectForm() {
        assertThat(command.part()).isEqualTo("project");
        assertThat(command.usage()).containsExactly("project", "project <path>", "project remove <key>");
    }

    @Test
    void addsTheRepositoryWithWhatItsReadingSuggestsWhenAPathIsTyped() {
        when(setup.read("/work/billing")).thenReturn(new ProjectSetup.Reading(
                List.of(new ProjectSetup.Line(true, "a git repository")), "billing", "origin/main", "develop",
                List.of("develop"), List.of("make", "test")));
        when(setup.add(new ProjectSetup.Entry("billing", "/work/billing", "origin/main", "develop",
                List.of("make", "test"), null))).thenReturn("Added billing to jagt.yml");

        assertThat(command.run(" /work/billing ")).isEqualTo("Added billing to jagt.yml");
    }

    @Test
    void refusesAPathThatIsNotReadyWithTheFirstThingWrongAndAddsNothing() {
        when(setup.read("/work/notes")).thenReturn(new ProjectSetup.Reading(
                List.of(new ProjectSetup.Line(false, "not a git repository")), "notes", "", "", List.of(), List.of()));

        assertThatThrownBy(() -> command.run("/work/notes")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a git repository");
        verify(setup, never()).add(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesWhereNoPathWasTypedAndPointsAtTheForm() {
        assertThatThrownBy(() -> command.run("  ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project <path>");
    }

    @Test
    void removesTheProjectNamedAfterRemove() {
        when(removal.remove("billing")).thenReturn("Removed billing from jagt.yml");

        assertThat(command.run("remove billing")).isEqualTo("Removed billing from jagt.yml");
    }
}
