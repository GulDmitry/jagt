package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChosenCheckerReaderTest {

  private final HeadlessClaudeCheckerReader claude = mock(HeadlessClaudeCheckerReader.class);
  private final OpenCodeCheckerReader opencode = mock(OpenCodeCheckerReader.class);

  @Test
  void readsOnOpenCodeForARoleNamedUnderReads() {
    CheckerReader.Run run =
        new CheckerReader.Run(
            "", "check ABC-42", Path.of("/wt"), "abc123", "", new CheckerReader.Reach(List.of(), List.of()), "{}", "qa");
    Answer<QaVerdict> local =
        new Answer<>(Optional.of(QaVerdict.failed("abc123", "local")), TokenUsage.NONE);
    when(opencode.read(run)).thenReturn(local);

    var chosen =
        new ChosenCheckerReader(
            claude,
            opencode,
            new OpenCodeProperties("", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("qa", "")));

    assertThat(chosen.read(run)).isSameAs(local);
  }

  @Test
  void readsOnClaudeForARoleNotNamedUnderReads() {
    CheckerReader.Run run =
        new CheckerReader.Run(
            "", "check ABC-42", Path.of("/wt"), "abc123", "", new CheckerReader.Reach(List.of(), List.of()), "{}", "qa");
    Answer<QaVerdict> cloud =
        new Answer<>(Optional.of(QaVerdict.failed("abc123", "cloud")), TokenUsage.NONE);
    when(claude.read(run)).thenReturn(cloud);

    var chosen = new ChosenCheckerReader(claude, opencode, OpenCodeProperties.defaults());

    assertThat(chosen.read(run)).isSameAs(cloud);
  }
}
