package dev.jagt.orchestrator.adapter.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoReleaseFeedTest {

  private final NoReleaseFeed feed = new NoReleaseFeed();

  @Test
  void namesNoReleaseWhenNoFeedIsChosen() {
    assertThat(feed.latest()).isEmpty();
  }

  @Test
  void namesNoHostWhenNoFeedIsChosen() {
    assertThat(feed.driver()).isEqualTo("none");
    assertThat(feed.host()).isEmpty();
  }

  @Test
  void downloadsNothingWhenNoFeedIsChosen() {
    assertThatThrownBy(() -> feed.jar("0.25.0", Path.of("a.jar"))).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> feed.checksums("0.25.0", Path.of("a.sha256")))
        .isInstanceOf(IOException.class);
  }
}
