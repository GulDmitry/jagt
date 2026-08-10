package dev.jagt.orchestrator.platform;

import dev.jagt.orchestrator.platform.EditorDriver.WorktreeLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class CliEditorDriverTest {

    private static final String XML = """
            <application><component name="RecentProjectsManager"><option name="additionalInfo"><map>
                <entry key="$USER_HOME$/www/sbrd/PAN-2391-sng">
                  <value><RecentProjectMetaInfo><option name="frameTitle" value="PAN-2391" /></RecentProjectMetaInfo></value>
                </entry>
                <entry key="$USER_HOME$/www/sbrd/sng-back">
                  <value><RecentProjectMetaInfo /></value>
                </entry>
            </map></option></component></application>""";

    @Test
    void removesTheDoneWorktreeEntryButKeepsTheOthers() {
        String pruned = CliEditorDriver.pruneRecentProjects(XML, "/Users/me",
                Path.of("/Users/me/www/sbrd/PAN-2391-sng"));

        assertThat(pruned).doesNotContain("PAN-2391-sng").contains("sng-back");
    }

    @Test
    void matchesTheAbsolutePathFormToo() {
        String absForm = XML.replace("$USER_HOME$/www/sbrd/PAN-2391-sng", "/Users/me/www/sbrd/PAN-2391-sng");

        String pruned = CliEditorDriver.pruneRecentProjects(absForm, "/Users/me",
                Path.of("/Users/me/www/sbrd/PAN-2391-sng"));

        assertThat(pruned).doesNotContain("PAN-2391-sng").contains("sng-back");
    }

    @Test
    void leavesTheFileUntouchedWhenTheWorktreeIsNotListed() {
        String pruned = CliEditorDriver.pruneRecentProjects(XML, "/Users/me",
                Path.of("/Users/me/www/sbrd/NOT-THERE-sng"));

        assertThat(pruned).isEqualTo(XML);
    }

    private static final String GC_XML = """
            <application><component name="RecentProjectsManager"><option name="additionalInfo"><map>
                <entry key="$USER_HOME$/www/sbrd/PAN-2575-sng"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/sbrd/PAN-2575-deploy"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/sbrd/PAN-2676-sng"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/sbrd/sng-back"><value><RecentProjectMetaInfo /></value></entry>
                <entry key="$USER_HOME$/www/other/some-old-project"><value><RecentProjectMetaInfo /></value></entry>
            </map></option></component></application>""";

    private static final List<WorktreeLocation> LOCATIONS =
            List.of(new WorktreeLocation(Path.of("/Users/me/www/sbrd"), "sng"));

    @Test
    void garbageCollectsDeadTaskAndDeployWorktreesButKeepsLiveAndForeignEntries() {
        // Everything is gone from disk EXCEPT the still-checked-out PAN-2676-sng worktree and the sng-back repo.
        Predicate<Path> dirExists = p -> p.endsWith("PAN-2676-sng") || p.endsWith("sng-back");

        List<String> dead = CliEditorDriver.deadWorktreeKeys(GC_XML, "/Users/me", LOCATIONS, dirExists);
        String pruned = CliEditorDriver.removeEntries(GC_XML, dead);

        assertThat(pruned)
                .doesNotContain("PAN-2575-sng").doesNotContain("PAN-2575-deploy")
                .contains("PAN-2676-sng")           // still checked out — live, kept
                .contains("sng-back")               // the base repo — kept
                .contains("some-old-project");      // dead but not a jagt worktree location — left alone
    }

    @Test
    void keepsADeadEntryThatIsNotUnderAnyConfiguredWorktreeLocation() {
        // No configured projects → nothing is a jagt worktree → GC removes nothing even though dirs are gone.
        List<String> dead = CliEditorDriver.deadWorktreeKeys(GC_XML, "/Users/me", List.of(), p -> false);

        assertThat(dead).isEmpty();
    }
}
