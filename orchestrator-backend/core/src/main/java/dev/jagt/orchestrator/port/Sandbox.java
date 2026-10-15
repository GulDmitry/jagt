package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Where a session's shell may write. {@code excludedCommands} run outside it, for a CLI whose sandbox lets a command
 * reach only the web.
 */
public record Sandbox(boolean enabled, List<Path> writable, List<String> excludedCommands) {

    public Sandbox {
        writable = List.copyOf(writable);
        excludedCommands = List.copyOf(excludedCommands);
    }

    public static Sandbox off() {
        return new Sandbox(false, List.of(), List.of());
    }

    public static Sandbox on(List<Path> writable, List<String> excludedCommands) {
        return new Sandbox(true, writable, excludedCommands);
    }
}
