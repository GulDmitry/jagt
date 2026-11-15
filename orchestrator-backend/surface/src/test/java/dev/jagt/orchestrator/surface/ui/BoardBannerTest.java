package dev.jagt.orchestrator.surface.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.log.BoardUrl;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class BoardBannerTest {

  @Test
  void tellsTheHumanWhereTheBoardListens() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    new BoardBanner(new BoardUrl("8290", ""), new PrintStream(out, true, StandardCharsets.UTF_8))
        .run(new DefaultApplicationArguments());

    assertThat(out.toString(StandardCharsets.UTF_8))
        .contains("jagt board → http://localhost:8290   (Ctrl-C stops)");
  }
}
