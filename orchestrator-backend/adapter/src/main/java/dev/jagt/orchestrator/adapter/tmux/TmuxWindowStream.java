package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Views of a tmux window, each read through a control-mode client of its own. */
@Service
@RequiredArgsConstructor
public class TmuxWindowStream implements WindowStream {

  private final Processes processes;
  private final TmuxReads reads;
  private final ResizedWindows sizes;

  @Override
  public Optional<View> open(String session, String window, int columns, int rows, Sink sink) {
    Optional<String> id =
        reads.windows(session).stream()
            .filter(listed -> listed.name().equals(window))
            .map(TmuxReads.Window::id)
            .findFirst();
    if (id.isEmpty()) {
      return Optional.empty();
    }
    sizes.claim(id.get(), columns, rows);
    ControlView view = null;
    try {
      view =
          new ControlView(
              processes.start(null, reads.argv("-C", "attach", "-t", "=" + session)),
              id.get(),
              sizes,
              sink);
      view.begin();
      return Optional.of(view);
    } catch (RuntimeException failed) {
      if (view != null) {
        view.close();
      } else {
        sizes.release(id.get());
      }
      throw failed;
    }
  }
}
