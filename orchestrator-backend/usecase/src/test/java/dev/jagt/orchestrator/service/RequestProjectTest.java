package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestProjectTest {

    private final ConfigService config = mock(ConfigService.class);
    private final GitWorktrees worktrees = mock(GitWorktrees.class);
    private final RequestProject projects = new RequestProject(config, worktrees);

    @Test
    void namesTheProjectWhoseOriginTheRequestUrlBelongsTo() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(new LinkedHashMap<>(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of()),
                "web", new ProjectConfig("/srv/web", "origin/main", "dev", List.of())))));
        when(worktrees.remoteUrl(Path.of("/srv/api"))).thenReturn("git@host.test:group/api.git");
        when(worktrees.remoteUrl(Path.of("/srv/web"))).thenReturn("git@host.test:group/web.git");

        assertThat(projects.of("https://host.test/group/web/-/merge_requests/4")).isEqualTo("web");
    }

    @Test
    void refusesARequestUrlNoConfiguredProjectOwns() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/srv/api", "origin/main", "dev", List.of()))));
        when(worktrees.remoteUrl(Path.of("/srv/api"))).thenReturn("git@host.test:group/api.git");

        assertThatThrownBy(() -> projects.of("https://host.test/group/web/-/merge_requests/4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no configured project matches request url: https://host.test/group/web/-/merge_requests/4");
    }
}
