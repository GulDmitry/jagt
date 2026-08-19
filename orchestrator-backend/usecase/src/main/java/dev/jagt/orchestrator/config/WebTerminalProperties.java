package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The terminal the board embeds: a local web server attached to the agents' tmux session.
 *
 * <p>Disabled by default — enabling it is what makes jagt need one more binary installed.
 *
 * @param enabled whether a server may be started at all
 * @param command the ttyd binary; a bare name is resolved on PATH and then in the known install dirs
 * @param port    port of the first server; a further tmux session takes the next one up
 * @param bind    address the server listens on, blank = every interface. Anything that reaches it can type
 *                into the agents' session, so loopback is the shipped value
 */
@ConfigurationProperties(prefix = "orchestrator.web-terminal")
@With
public record WebTerminalProperties(boolean enabled, String command, int port, String bind) {

    private static final String DEFAULT_COMMAND = "ttyd";
    private static final int DEFAULT_PORT = 8291;
    private static final String DEFAULT_BIND = "127.0.0.1";

    public WebTerminalProperties {
        command = command == null || command.isBlank() ? DEFAULT_COMMAND : command;
        port = port <= 0 ? DEFAULT_PORT : port;
        bind = bind == null ? DEFAULT_BIND : bind.strip();
    }

    public static WebTerminalProperties defaults() {
        return new WebTerminalProperties(false, null, 0, null);
    }
}
