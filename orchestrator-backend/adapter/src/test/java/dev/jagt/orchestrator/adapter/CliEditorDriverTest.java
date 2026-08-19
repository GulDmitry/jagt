package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.EditorDriver.WorktreeLocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CliEditorDriverTest {

    private static final String XML = """
            <application><component name="RecentProjectsManager"><option name="additionalInfo"><map>
                <entry key="$USER_HOME$/www/repos/ABC-2391-demo">
                  <value><RecentProjectMetaInfo><option name="frameTitle" value="ABC-2391" /></RecentProjectMetaInfo></value>
                </entry>
                <entry key="$USER_HOME$/www/repos/demo-back">
                  <value><RecentProjectMetaInfo /></value>
                </entry>
            </map></option></component></application>""";

    /**
     * The launcher is looked up HERE, where the process is spawned — the configuration keeps the bare name the
     * human wrote, and only the launcher is resolved: the arguments after it are theirs.
     */
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

        assertThat(driver.problems())
                .anySatisfy(problem -> assertThat(problem).contains("orchestrator.editor-command"))
                .anySatisfy(problem -> assertThat(problem).contains("orchestrator.editor-diff-command"));
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
    void removesTheDoneWorktreeEntryButKeepsTheOthers() {
        String pruned = CliEditorDriver.pruneRecentProjects(XML, "/Users/me",
                Path.of("/Users/me/www/repos/ABC-2391-demo"));

        assertThat(pruned).doesNotContain("ABC-2391-demo").contains("demo-back");
    }

    @Test
    void prunesAnEntryTheIdeStoredAsAnAbsolutePathRatherThanUnderItsHomeMacro() {
        String absForm = XML.replace("$USER_HOME$/www/repos/ABC-2391-demo", "/Users/me/www/repos/ABC-2391-demo");

        String pruned = CliEditorDriver.pruneRecentProjects(absForm, "/Users/me",
                Path.of("/Users/me/www/repos/ABC-2391-demo"));

        assertThat(pruned).doesNotContain("ABC-2391-demo").contains("demo-back");
    }

    @Test
    void leavesTheFileUntouchedWhenTheWorktreeIsNotListed() {
        String pruned = CliEditorDriver.pruneRecentProjects(XML, "/Users/me",
                Path.of("/Users/me/www/repos/NOT-THERE-demo"));

        assertThat(pruned).isEqualTo(XML);
    }

    private static final String GC_XML = """
            <application><component name="RecentProjectsManager"><option name="additionalInfo"><map>
                <entry key="$USER_HOME$/www/repos/ABC-2575-demo"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/repos/ABC-2575-deploy"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/repos/ABC-2676-demo"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/repos/demo-back"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/other/some-old-project"><value><RecentProjectMetaInfo /></value></entry>
            </map></option></component></application>""";

    /** Only ABC-2676-demo and demo-back are still on disk; every other entry names a directory that is gone. */
    @Test
    void garbageCollectsDeadTaskAndDeployWorktreesButKeepsLiveAndForeignEntries() {
        Predicate<Path> dirExists = p -> p.endsWith("ABC-2676-demo") || p.endsWith("demo-back");

        List<String> dead = CliEditorDriver.deadWorktreeKeys(GC_XML, "/Users/me",
                List.of(new WorktreeLocation(Path.of("/Users/me/www/repos"), "demo")), dirExists);
        String pruned = CliEditorDriver.removeEntries(GC_XML, dead);

        assertThat(pruned)
                .doesNotContain("ABC-2575-demo").doesNotContain("ABC-2575-deploy")
                .contains("ABC-2676-demo")
                .contains("demo-back")
                .contains("some-old-project");
    }

    @Test
    void keepsADeadEntryThatIsNotUnderAnyConfiguredWorktreeLocation() {
        List<String> dead = CliEditorDriver.deadWorktreeKeys(GC_XML, "/Users/me", List.of(), p -> false);

        assertThat(dead).isEmpty();
    }

    /**
     * The leak the Linux port predicted and found: a prune that only looked in the macOS location left a dead
     * entry in the IDE's recent-projects list for every {@code done} task on Linux, forever.
     */
    @Test
    void looksForJetBrainsConfigWhereEachPlatformKeepsIt() {
        var dirs = CliEditorDriver.jetBrainsConfigDirs("/home/dev").stream().map(Path::toString).toList();

        assertThat(dirs).containsExactly("/home/dev/Library/Application Support/JetBrains",
                "/home/dev/.config/JetBrains");
    }
}
