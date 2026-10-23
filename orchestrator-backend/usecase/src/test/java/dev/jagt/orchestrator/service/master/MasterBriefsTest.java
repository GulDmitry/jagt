package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MasterBriefsTest {

    @Test
    void readsTheBriefTheHumanCopiedBesideTheConfig(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge hard");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())),
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).contains("judge hard");
    }

    @Test
    void readsTheShippedBriefWhereNoneWasNamedOrCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "judge by default");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())),
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).contains("judge by default");
    }

    @Test
    void readsNoShippedBriefInPlaceOfOneTheHumanNamedAndMisspelled(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("mine.md.dist"), "judge by default");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())),
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        assertThat(briefs.master("ABC-1", new MasterConfig("act", "mine.md", null, null, null))).isEmpty();
    }

    @Test
    void readsNothingWhereNoBriefExistsAtAll(@TempDir Path root) {
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())),
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).isEmpty();
    }

    @Test
    void handsOverTheBriefTheSessionWorksTo(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## context\n\nnever commit unasked\n");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())),
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

        assertThat(briefs.author()).isEqualTo("never commit unasked");
    }
}
