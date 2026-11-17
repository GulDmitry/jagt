package dev.jagt.orchestrator.port.terminal;

import java.util.Optional;

/**
 * A live view of one window of a {@link SessionHost}: what it prints as it prints it, and a way to
 * type into it. Not a {@link TerminalDriver}, which shows a session on this machine's screen.
 *
 * <p>Each view is its own; any number may watch one window and every one may type. The window has
 * the size of the views while they last and its own again once the last is closed.
 */
public interface WindowStream {

  /** Where a view's output arrives. Called from the stream's own thread, never concurrently. */
  interface Sink {

    /** The screen as it is when the view opens, then each piece of output in order. */
    void output(byte[] bytes);

    /** The view can no longer follow the window: it was closed, or the host ended it. */
    void ended(String cause);
  }

  /** One open view; closing it more than once is harmless. */
  interface View extends AutoCloseable {

    /** Types {@code text} as a human would. */
    void type(String text);

    /** Gives the window the size of this view's screen. */
    void resize(int columns, int rows);

    @Override
    void close();
  }

  /** The view of the named window sized {@code columns} by {@code rows}; empty when it is gone. */
  Optional<View> open(String session, String window, int columns, int rows, Sink sink);
}
