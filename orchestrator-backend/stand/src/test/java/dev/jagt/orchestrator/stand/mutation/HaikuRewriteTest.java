package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.stand.StandFailure;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HaikuRewriteTest {

  @TempDir Path directory;

  @Test
  void rewriteThroughUnrunnableCloudCommandIsFailure() {
    HaikuRewrite rewrite = new HaikuRewrite(directory.resolve("missing"));

    assertThatThrownBy(() -> rewrite.rewrite("ask", "ticket"))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("the ticket rewrite failed");
  }
}
