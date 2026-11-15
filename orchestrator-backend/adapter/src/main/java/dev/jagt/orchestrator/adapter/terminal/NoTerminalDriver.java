package dev.jagt.orchestrator.adapter.terminal;

import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orchestrator.terminal", havingValue = "none")
public class NoTerminalDriver implements TerminalDriver {

  @Override
  public void openViewer(List<String> attachCommand, String dedicatedTitle, Path tabCwd) {}

  @Override
  public void bringToFront() {}

  @Override
  public Revealed reveal(String dedicatedTitle) {
    return Revealed.NO_TERMINAL;
  }

  @Override
  public void closeViewerWindow(String dedicatedTitle) {}
}
