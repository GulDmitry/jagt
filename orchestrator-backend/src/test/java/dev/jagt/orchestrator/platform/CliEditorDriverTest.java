package dev.jagt.orchestrator.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
}
