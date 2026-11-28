package dev.jagt.orchestrator.adapter.values;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class RenamedKeysAliasTest {

  private final RenamedKeysAlias alias = new RenamedKeysAlias();

  @ParameterizedTest
  @CsvSource({
    "orchestrator.sessionHost, orchestrator.multiplexer",
    "orchestrator.session-host, orchestrator.multiplexer",
    "orchestrator.tmuxCommand, orchestrator.tmux.command",
    "orchestrator.tmux-socket, orchestrator.tmux.socket",
    "orchestrator.kittyCommand, orchestrator.kitty.command",
    "orchestrator.kitty-font-size, orchestrator.kitty.font-size"
  })
  void readsAnOldKeyAsItsNewOne(String written, String read) {
    MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("file", Map.of(written, "x")));

    alias.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(read)).isEqualTo("x");
  }

  @Test
  void keepsTheNewKeyWhereTheOldOneIsWrittenToo() {
    MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "file",
                Map.of("orchestrator.sessionHost", "tmux", "orchestrator.multiplexer", "herdr")));

    alias.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("orchestrator.multiplexer")).isEqualTo("herdr");
  }

  @Test
  void runsBeforeTheFileValuesAreExpanded() {
    assertThat(alias.getOrder()).isLessThan(new ConfiguredValues().getOrder());
  }

  @Test
  void addsNothingWhereNoOldKeyIsWritten() {
    MockEnvironment environment = new MockEnvironment();

    alias.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getPropertySources().contains("renamedKeysAlias")).isFalse();
  }
}
