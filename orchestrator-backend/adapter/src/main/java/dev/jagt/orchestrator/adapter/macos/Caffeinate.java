package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.KeepAwake;
import dev.jagt.orchestrator.port.Processes;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code -w} ends the assertion with this process, however it dies. */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "macos", matchIfMissing = true)
public class Caffeinate implements KeepAwake {

    private final Processes processes;
    private final long pid;
    private @Nullable Process held;

    public Caffeinate(Processes processes) {
        this.processes = processes;
        this.pid = ProcessHandle.current().pid();
    }

    @Override
    public synchronized void hold() {
        if (held != null && held.isAlive()) {
            return;
        }
        held = processes.runDetached(null,
                List.of(Executables.resolve("caffeinate"), "-i", "-w", Long.toString(pid)));
    }

    @Override
    @PreDestroy
    public synchronized void release() {
        if (held == null) {
            return;
        }
        held.destroy();
        held = null;
    }
}
