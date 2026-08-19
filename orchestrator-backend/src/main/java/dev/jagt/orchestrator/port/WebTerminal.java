package dev.jagt.orchestrator.port;

import java.util.OptionalInt;

/**
 * Serving one agent session to a browser. Empty means none is configured or none could be started — a surface
 * that asks must treat that as "no embedded terminal", never as a failure.
 */
public interface WebTerminal {

    /** The port a browser can attach to for this session, started if it is not already serving. */
    OptionalInt serve(String session);
}
