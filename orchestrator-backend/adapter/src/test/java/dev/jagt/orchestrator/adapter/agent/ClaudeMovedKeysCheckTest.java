package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class ClaudeMovedKeysCheckTest {

  @ParameterizedTest
  @CsvSource({
    "orchestrator.agent.outputStyle, orchestrator.claude.outputStyle",
    "orchestrator.agent.output-style, orchestrator.claude.outputStyle",
    "orchestrator.agentDisabledPlugins[0], orchestrator.claude.disabledPlugins",
    "orchestrator.agent-disabled-plugins[0], orchestrator.claude.disabledPlugins"
  })
  void refusesAnOldKeyNamingTheNewOne(String written, String newKey) {
    ClaudeMovedKeysCheck check =
        new ClaudeMovedKeysCheck(new MockEnvironment().withProperty(written, "x"));

    List<Problem> problems = check.problems();

    assertThat(problems)
        .singleElement()
        .satisfies(problem -> assertThat(problem.text()).contains(newKey));
  }

  @Test
  void acceptsTheKeysAtTheirNewPlace() {
    ClaudeMovedKeysCheck check =
        new ClaudeMovedKeysCheck(
            new MockEnvironment()
                .withProperty("orchestrator.claude.outputStyle", "terse")
                .withProperty("orchestrator.claude.disabledPlugins[0]", "lsp@market"));

    assertThat(check.problems()).isEmpty();
  }
}
