package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeHostCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final CodeHostCheck check = new CodeHostCheck(configService,
            List.of(new CommandPipeline(mock(Processes.class), new ReachLedger()), new NoCodeHost()));

    private static ProjectConfig project(String codeHost, List<String> pipelineCommand) {
        return new ProjectConfig("/repo/shop", "origin/main", "dev", List.of(), List.of(), null, null, codeHost, null,
                pipelineCommand, null, null, null, null);
    }

    @Test
    void refusesACodeHostNoDriverAnswersForNamingTheKey() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withProjects(Map.of("shop", project("bitbucket", null))));

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.projects.shop.codeHost");
    }

    @Test
    void refusesACommandHostWhoseProgramIsMissingNamingTheKey() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withProjects(Map.of("shop", project("command", List.of("no-such-ci-program", "{sha}")))));

        assertThat(check.problems()).extracting(Problem::key)
                .containsExactly("orchestrator.projects.shop.pipelineCommand");
    }

    @Test
    void asksNothingOfAProjectThatChoosesNone() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("none", null))));

        assertThat(check.problems()).isEmpty();
    }
}
