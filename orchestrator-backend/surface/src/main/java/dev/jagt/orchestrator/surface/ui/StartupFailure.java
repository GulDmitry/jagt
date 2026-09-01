package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.startup.Misconfigured;
import org.springframework.boot.web.server.PortInUseException;

/** Boot marks a startup failure as handled, so nothing else prints it. */
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
                return "jagt cannot start: port " + port + " is in use, most likely another jagt. Free it, or"
                        + " set `--server.port=<port>`.";
            }
        }
        return "jagt cannot start: " + failure + " — stack trace in logging.file.name.";
    }
}
