package dev.jagt.orchestrator.adapter.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoNotifierTest {

  @Test
  void saysAtStartupWhichKeyEnablesBanners() {
    assertThat(new NoNotifier().notes())
        .singleElement()
        .satisfies(
            note -> assertThat(note.text()).contains("set `notifier` in jagt.yml to `desktop`"));
  }

  @Test
  void raisesNoProblemSoTheStartProceeds() {
    assertThat(new NoNotifier().problems()).isEmpty();
  }
}
