package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.startup.Misconfigured;
import org.springframework.boot.web.server.PortInUseException;

/**
 * Boot reports a startup failure through logback and then marks it as handled, so the JVM prints no trace
 * either — with the console threshold off (see
 * {@link ConsoleLogging}) a jagt that failed to bind its port died in complete silence, leaving a shell
 * prompt and a still-serving OLD instance as the only evidence. That one case is worth naming outright,
 * because "another jagt is already running" is what it almost always means.
 */
public final class StartupFailure {

    private StartupFailure() {
    }

    public static String describe(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof Misconfigured misconfigured) {
                return misconfigured.getMessage();
            }
            if (cause instanceof PortInUseException portTaken) {
                int port = portTaken.getPort();
                return "jagt cannot start: port " + port + " is already in use — most likely a jagt that is"
                        + " still running. Stop that one, or start this one elsewhere with"
                        + " `--server.port=<port>`.";
            }
        }
        return "jagt cannot start: " + failure + " — the full stack trace went to the log file"
                + " (logging.file.name).";
    }
}
