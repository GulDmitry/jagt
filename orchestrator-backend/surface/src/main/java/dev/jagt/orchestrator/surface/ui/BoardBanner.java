package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.service.log.BoardUrl;
import java.io.PrintStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@RequiredArgsConstructor
public class BoardBanner implements ApplicationRunner {

  private final BoardUrl boardUrl;
  private final PrintStream out;

  @Override
  public void run(ApplicationArguments args) {
    out.println("jagt board → " + boardUrl.root() + "   (Ctrl-C stops)");
  }
}
