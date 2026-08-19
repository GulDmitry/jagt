package dev.jagt.orchestrator.surface.ui;

/**
 * How the human drives jagt, selected by configuration rather than by an {@code if} somewhere in the flow.
 * Every surface reads the same task projection and executes through the same gate: adding one must not add a
 * second answer to "what can I do with this task" or "what does ship do".
 */
public interface OperatorUi {

    /**
     * A surface that OWNS the terminal blocks here until the human exits and is then responsible for stopping
     * the backend; one that only needs the HTTP server returns immediately and lets the server thread keep the
     * process alive.
     */
    void start();

    String name();

    /** Whether {@link #start()} blocks for the session's lifetime. */
    default boolean blocking() {
        return false;
    }
}
