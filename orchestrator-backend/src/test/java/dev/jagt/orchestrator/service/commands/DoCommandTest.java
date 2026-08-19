package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoCommandTest {

    private final ConfigService config = mock(ConfigService.class);
    private final DoCommand command = new DoCommand(mock(TaskLauncher.class), config);

    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "widgets", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));

        LaunchRequest args = command.parse(List.of("ABC-2099", "plan", "walk", "me", "through", "it"));

        assertThat(args.project()).isNull();
        assertThat(args.mode()).isEqualTo("plan");
        assertThat(args.notes()).isEqualTo("walk me through it");
    }

    @Test
    void readsTheBaseBranchAfterFromAndKeepsTheRestAsNotes() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()))));

        LaunchRequest args = command.parse(List.of("ABC-1", "from", "feature/parent", "demo",
                "keep the API stable"));

        assertThat(args.baseBranch()).isEqualTo("feature/parent");
        assertThat(args.project()).isEqualTo("demo");
        assertThat(args.notes()).isEqualTo("keep the API stable");
    }

    @Test
    void refusesFromWithoutABranchInsteadOfSwallowingTheNextWord() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        assertThatThrownBy(() -> command.parse(List.of("ABC-1", "from")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("`from` needs the branch");
    }

    @Test
    void readsSeveralProjectsFromOneTokenForWorkThatSpansThem() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "widgets", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));

        LaunchRequest args = command.parse(List.of("ABC-1", "demo,widgets", "keep them in step"));

        assertThat(args.project()).isEqualTo("demo,widgets");
        assertThat(args.notes()).isEqualTo("keep them in step");
    }

    @Test
    void keepsATokenAsNotesWhenOnlySomeOfItsCommaPartsAreProjects() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()))));

        LaunchRequest args = command.parse(List.of("ABC-1", "demo,whatever"));

        assertThat(args.project()).isNull();
        assertThat(args.notes()).isEqualTo("demo,whatever");
    }

    @Test
    void namesTheUsageWhenNoTicketWasTyped() {
        assertThatThrownBy(() -> command.run(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: do <ticket|url>");
    }
}
