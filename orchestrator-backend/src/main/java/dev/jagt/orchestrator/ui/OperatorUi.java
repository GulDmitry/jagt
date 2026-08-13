package dev.jagt.orchestrator.ui;

/**
 * How the human drives jagt. A strategy like every other OS- or vendor-specific piece in this codebase
 * ({@code UserNotifier}, {@code TerminalDriver}, {@code AgentRuntime}, {@code CodeHost}): selected by config,
 * never by an {@code if} somewhere in the flow.
 *
 * <p>Two implementations ship — the web board (default) and the console TUI — and BOTH read the same
 * {@code TaskView} projection and execute through the same {@code CommandService}. That is the whole point of
 * the seam: adding a surface must not add a second answer to "what can I do with this task" or "what does
 * ship do". Selected with {@code orchestrator.ui}: {@code web} (default), {@code tui}, or {@code both}.
 */
public interface OperatorUi {

    /**
     * Takes over as the human's control surface. A surface that OWNS the terminal (the TUI) blocks here until
     * the human exits and is then responsible for stopping the backend; a surface that only needs the HTTP
     * server (the web board) returns immediately and lets the server thread keep the process alive.
     */
    void start();

    /** For the startup log, so it is obvious which surface is live. */
    String name();

    /** Whether {@link #start()} blocks for the session's lifetime — the runner starts such a surface LAST. */
    default boolean blocking() {
        return false;
    }
}
