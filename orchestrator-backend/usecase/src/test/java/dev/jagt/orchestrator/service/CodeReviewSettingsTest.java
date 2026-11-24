package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeReviewSettingsTest {

  @TempDir Path root;

  private final ConfigService configService = mock(ConfigService.class);
  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);

  @Test
  void spendCeilingIsTheAssistantsOwn() {
    when(paths.root()).thenReturn(root);
    when(configService.load()).thenReturn(ConfigFile.defaults());
    AssistantProperties assistant = AssistantProperties.empty().withMaxCostUsd(1.5);

    CodeReviewSettings.Settings read =
        new CodeReviewSettings(configService, paths, assistant).load();

    assertThat(read.maxCostUsd()).isEqualTo(1.5);
    assertThat(read.postOwn()).isTrue();
    assertThat(read.postOthers()).isFalse();
  }

  @Test
  void reviewFileOverridesTheDefaultsOfWhatItNames() throws IOException {
    Files.createDirectories(root.resolve("roles/code-reviewer"));
    Files.writeString(
        root.resolve("roles/code-reviewer/review.yml"),
        "post:\n  others: true\nmaxDiffLines: 100\n");
    when(paths.root()).thenReturn(root);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    CodeReviewSettings.Settings read =
        new CodeReviewSettings(configService, paths, AssistantProperties.empty()).load();

    assertThat(read.postOthers()).isTrue();
    assertThat(read.maxDiffLines()).isEqualTo(100);
    assertThat(read.postOwn()).isTrue();
  }

  @Test
  void mayPostOnTheTasksOwnRequestByDefault() {
    when(paths.root()).thenReturn(root);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    CodeReviewSettings settings =
        new CodeReviewSettings(configService, paths, AssistantProperties.empty());

    assertThat(settings.mayPostOnTasksOwnRequest()).isTrue();
  }

  @Test
  void mayNotPostOnTheTasksOwnRequestWhereTheReviewFileForbidsIt() throws IOException {
    Files.createDirectories(root.resolve("roles/code-reviewer"));
    Files.writeString(root.resolve("roles/code-reviewer/review.yml"), "post:\n  own: false\n");
    when(paths.root()).thenReturn(root);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    CodeReviewSettings settings =
        new CodeReviewSettings(configService, paths, AssistantProperties.empty());

    assertThat(settings.role()).isEqualTo("code-reviewer");
    assertThat(settings.mayPostOnTasksOwnRequest()).isFalse();
  }
}
