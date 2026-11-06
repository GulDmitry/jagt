package dev.jagt.orchestrator.adapter.tmux;

import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TmuxArgv {

    private TmuxArgv() {
    }

    /** The binary, then {@code -L <socket>} when a socket is named; every tmux command line starts with it. */
    public static List<String> prefix(String binary, @Nullable String socket) {
        return socket == null || socket.isBlank() ? List.of(binary) : List.of(binary, "-L", socket.trim());
    }
}
