package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KittyPropertiesTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void runsKittyWhereNoCommandIsConfigured(String command) {
    assertThat(new KittyProperties(command, "").command()).isEqualTo("kitty");
  }

  @Test
  void keepsTheConfiguredCommandWithoutItsPadding() {
    assertThat(new KittyProperties(" /opt/kitty ", "").command()).isEqualTo("/opt/kitty");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void leavesTheFontSizeToKittyWhereNoneIsConfigured(String fontSize) {
    assertThat(new KittyProperties("kitty", fontSize).fontSize()).isEmpty();
  }

  @Test
  void keepsTheConfiguredFontSizeWithoutItsPadding() {
    assertThat(new KittyProperties("kitty", " 13 ").fontSize()).isEqualTo("13");
  }
}
