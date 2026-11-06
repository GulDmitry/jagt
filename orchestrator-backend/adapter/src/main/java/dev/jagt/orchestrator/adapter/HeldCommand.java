package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class HeldCommand {

    private final Processes processes;
    private final List<String> command;
    private @Nullable Process held;

    public HeldCommand(Processes processes, List<String> command) {
        this.processes = processes;
        this.command = List.copyOf(command);
    }

    public synchronized void hold() {
        if (held != null && held.isAlive()) {
            return;
        }
        held = processes.runDetached(null, command);
    }

    public synchronized void release() {
        if (held == null) {
            return;
        }
        held.destroy();
        held = null;
    }
}
