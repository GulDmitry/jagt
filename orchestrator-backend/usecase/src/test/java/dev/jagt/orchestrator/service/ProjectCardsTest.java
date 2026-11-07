package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectCardsTest {

    private final ConfigService config = mock(ConfigService.class);
    private final GitWorktrees worktrees = mock(GitWorktrees.class);
    private final ProjectCards cards = new ProjectCards(config, worktrees);

    @Test
    void carriesTheEntryAndTheAddressOfItsRepository() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of("billing")))));
        when(worktrees.remoteUrl(Path.of("/srv/api"))).thenReturn("git@host.test:group/api.git");

        ProjectCards.Card card = cards.of("api");

        assertThat(card.remoteUrl()).isEqualTo("git@host.test:group/api.git");
        assertThat(card.branches().baseBranch()).isEqualTo("origin/main");
        assertThat(card.branches().deployBranch()).isEqualTo("dev");
        assertThat(card.repository().labels()).containsExactly("billing");
    }

    @Test
    void servesTheFlatKeysTheBoardReads() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of("billing")))));
        when(worktrees.remoteUrl(Path.of("/srv/api"))).thenReturn("host.test:group/api");

        JsonNode served = JsonMapper.builder().build().valueToTree(cards.of("api"));

        assertThat(served.get("key").asString()).isEqualTo("api");
        assertThat(served.get("path").asString()).isEqualTo("/srv/api");
        assertThat(served.get("baseBranch").asString()).isEqualTo("origin/main");
        assertThat(served.get("labels").get(0).asString()).isEqualTo("billing");
        assertThat(served.get("remoteUrl").asString()).isEqualTo("host.test:group/api");
    }

    @Test
    void leavesTheAddressBlankWhereTheRepositoryHasNoRemote() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "local", new ProjectConfig("/srv/local", "main", "dev", List.of()))));
        when(worktrees.remoteUrl(Path.of("/srv/local"))).thenThrow(new IllegalStateException("no such remote"));

        assertThat(cards.of("local").remoteUrl()).isEmpty();
    }

    @Test
    void refusesAProjectJagtYmlDoesNotHold() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

        assertThatThrownBy(() -> cards.of("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
