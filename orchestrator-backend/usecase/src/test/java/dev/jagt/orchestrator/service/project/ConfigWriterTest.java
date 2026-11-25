package dev.jagt.orchestrator.service.project;

import static dev.jagt.orchestrator.service.TestConfigPathsBuilder.configPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigWriterTest {

  private final KeptNumbers kept = new KeptNumbers(new ReachLedger());

  @TempDir Path root;

  private Path configFile;
  private OrchestratorPaths paths;
  private ConfigService config;

  @BeforeEach
  void wire() throws Exception {
    configFile =
        Files.writeString(
            Files.createDirectories(root.resolve("conf")).resolve("jagt.yml"),
            "orchestrator:\n  projects: {}\n");
    paths = configPaths().withRoot(root.resolve("conf")).build();
    config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())),
            kept);
  }

  @AfterEach
  void unlock() {
    configFile.toFile().setWritable(true);
  }

  @Test
  void changesOneKeyAndKeepsTheCommentsAndTheOrderOfTheFile() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects: {}
          # who answers a review
          codeReview:
            postReviewReplies: true   # a draft otherwise
            reviewReplyAuthors: []
        """);

    String said =
        new ConfigWriter(config, paths)
            .set(List.of("orchestrator", "codeReview", "postReviewReplies"), false);

    assertThat(said).isEqualTo("Changed codeReview.postReviewReplies in jagt.yml");
    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:
              projects: {}
              # who answers a review
              codeReview:
                postReviewReplies: false  # a draft otherwise
                reviewReplyAuthors: []
            """);
  }

  @Test
  void restoresTheLastGoodFileAndSaysWhyWhereTheResultDoesNotRead() throws Exception {
    String before = "orchestrator:\n  projects: {}\n  codeReview:\n    postReviewReplies: true\n";
    Files.writeString(configFile, before);
    ConfigService unreadable = spy(config);
    doThrow(new IllegalStateException("bad yaml")).when(unreadable).load();
    ConfigWriter writer = new ConfigWriter(unreadable, paths);

    assertThatThrownBy(
            () -> writer.set(List.of("orchestrator", "codeReview", "postReviewReplies"), false))
        .isInstanceOfSatisfying(
            ConfigWriteFailed.class,
            failure -> {
              assertThat(failure.reason()).isEqualTo(ConfigWriteFailed.Reason.UNREADABLE_RESTORED);
              assertThat(failure.getMessage()).endsWith("so it was put back: bad yaml");
            });
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void refusesFileThatIsNotWritableAsTypedFailureAndWritesNothing() throws Exception {
    String before = "orchestrator:\n  projects: {}\n";
    Files.writeString(configFile, before);
    configFile.toFile().setWritable(false);
    ConfigWriter writer = new ConfigWriter(config, paths);

    assertThatThrownBy(() -> writer.set(List.of("orchestrator", "infraWait", "backoff"), List.of()))
        .isInstanceOfSatisfying(
            ConfigWriteFailed.class,
            failure -> {
              assertThat(failure.reason()).isEqualTo(ConfigWriteFailed.Reason.NOT_WRITABLE);
              assertThat(failure.file().getFileName()).hasToString("jagt.yml");
            });
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }
}
