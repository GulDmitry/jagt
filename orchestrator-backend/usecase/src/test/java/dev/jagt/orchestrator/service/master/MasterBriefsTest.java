package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterBriefsTest {

  @Test
  void readsTheBriefTheHumanCopiedBesideTheConfig(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge hard");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.master("ABC-1", ArbiterConfig.defaults())).contains("judge hard");
  }

  @Test
  void readsTheShippedBriefWhereNoneWasNamedOrCopied(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "judge by default");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.master("ABC-1", ArbiterConfig.defaults())).contains("judge by default");
  }

  @Test
  void readsNoBriefLeftBesideTheConfig(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("master-brief.md"), "judge hard");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.master("ABC-1", ArbiterConfig.defaults())).isEmpty();
  }

  @Test
  void readsNoShippedBriefInPlaceOfOneTheHumanNamedAndMisspelled(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("mine.md.dist"), "judge by default");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.master("ABC-1", new ArbiterConfig("act", "mine.md", null, null, null)))
        .isEmpty();
  }

  @Test
  void readsNothingWhereNoBriefExistsAtAll(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.master("ABC-1", ArbiterConfig.defaults())).isEmpty();
  }

  @Test
  void handsOverTheBriefTheSessionWorksTo(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md.dist"), "## context\n\nnever commit unasked\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));

    assertThat(briefs.author()).isEqualTo("never commit unasked");
  }
}
