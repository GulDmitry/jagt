package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterBriefsTest {

    private final PromptTemplates prompts = mock(PromptTemplates.class);

    @Test
    void readsTheBriefTheHumanCopiedBesideTheConfig(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge hard");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), prompts);

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).contains("judge hard");
    }

    @Test
    void readsTheShippedBriefWhereNoneWasNamedOrCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "judge by default");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), prompts);

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).contains("judge by default");
    }

    @Test
    void readsNoShippedBriefInPlaceOfOneTheHumanNamedAndMisspelled(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("mine.md.dist"), "judge by default");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), prompts);

        assertThat(briefs.master("ABC-1", new MasterConfig("act", "mine.md", null, null, null))).isEmpty();
    }

    @Test
    void readsNothingWhereNoBriefExistsAtAll(@TempDir Path root) {
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), prompts);

        assertThat(briefs.master("ABC-1", MasterConfig.defaults())).isEmpty();
    }

    @Test
    void handsOverTheBriefTheSessionWorksTo(@TempDir Path root) {
        when(prompts.subAgentContext()).thenReturn("never commit unasked");
        MasterBriefs briefs = new MasterBriefs(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), prompts);

        assertThat(briefs.author()).isEqualTo("never commit unasked");
    }
}
