package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CliEditorDriverTest {

    @Test
    void findsTheLauncherOnPathAndPassesItsArgumentsThroughUntouched() {
        ProcessRunner runner = mock(ProcessRunner.class);
        CliEditorDriver driver = new CliEditorDriver(runner,
                OrchestratorProperties.defaults().withEditorDiffCommand(List.of("sh", "diff")));

        driver.openDiff(Path.of("/left"), Path.of("/right"));

        ArgumentCaptor<List<String>> spawned = ArgumentCaptor.captor();
        verify(runner).runDetached(isNull(), spawned.capture());
        assertThat(spawned.getValue().getFirst()).endsWith("/sh");
        assertThat(spawned.getValue()).containsSubsequence("diff", "/left", "/right");
    }

    @Test
    void refusesToStartNamingBothEditorKeysWhenNeitherLauncherIsInstalled() {
        CliEditorDriver driver = new CliEditorDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults().withEditorCommand(List.of("no-such-editor"))
                        .withEditorDiffCommand(List.of("no-such-difftool")));

        assertThat(driver.problems()).extracting(Problem::key)
                .containsExactly("orchestrator.editor-command", "orchestrator.editor-diff-command");
    }

    @Test
    void saysWhichKeyToSetWhenTheConfiguredEditorLauncherIsNowhereToBeFound() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        CliEditorDriver driver = new CliEditorDriver(processRunner, OrchestratorProperties.defaults()
                .withEditorCommand(List.of("no-such-editor")));

        assertThatThrownBy(() -> driver.open(Path.of("/wt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orchestrator.editor-command")
                .hasMessageContaining("no-such-editor");
        verifyNoInteractions(processRunner);
    }

    @Test
    void saysWhichKeyToSetWhenTheConfiguredEditorLauncherIsBlank() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        CliEditorDriver driver = new CliEditorDriver(processRunner, OrchestratorProperties.defaults()
                .withEditorCommand(List.of(" ")));

        assertThatThrownBy(() -> driver.open(Path.of("/wt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orchestrator.editor-command is empty");
        verifyNoInteractions(processRunner);
    }

    @Test
    void startsWithoutADiffCommand() {
        CliEditorDriver driver = new CliEditorDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults().withEditorCommand(List.of("sh")));

        assertThat(driver.problems()).isEmpty();
    }

    @Test
    void refusesADiffNamingTheKeyAndAnExampleWhenNoDiffCommandIsSet() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        CliEditorDriver driver = new CliEditorDriver(processRunner, OrchestratorProperties.defaults()
                .withEditorCommand(List.of("sh")));

        assertThatThrownBy(() -> driver.openDiff(Path.of("/left"), Path.of("/right")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orchestrator.editor-diff-command is not set")
                .hasMessageContaining("[meld]");
        verifyNoInteractions(processRunner);
    }
}
