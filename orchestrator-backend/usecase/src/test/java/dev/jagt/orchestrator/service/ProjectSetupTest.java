package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectSetupTest {

    @TempDir
    Path root;

    @Test
    void addsTheEntryUnderProjectsAndKeepsEveryOtherLineAsItWas() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                # mine
                orchestrator:
                  projects:
                    web:
                      path: /srv/web
                      baseBranch: origin/main
                  keepAwake: false   # stays
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class)), paths, git);

        setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", "dev", List.of("./gradlew", "test"),
                "the invoicing backend"));

        assertThat(Files.readString(configFile)).isEqualTo("""
                # mine
                orchestrator:
                  projects:
                    billing:
                      path: "%s"
                      baseBranch: "origin/main"
                      deployBranch: "dev"
                      verifyCommand: ["./gradlew","test"]
                      about: "the invoicing backend"
                    web:
                      path: /srv/web
                      baseBranch: origin/main
                  keepAwake: false   # stays
                """.formatted(repo));
    }

    @Test
    void refusesAPathThatIsNotAGitRepositoryAndWritesNothing() throws Exception {
        Path folder = Files.createDirectories(root.resolve("notes"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(128, "", "not a git repository"));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("notes", folder.toString(), "origin/main", null, null,
                null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
    }
}
