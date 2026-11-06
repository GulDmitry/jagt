package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.adapter.HeldCommand;
import dev.jagt.orchestrator.port.KeepAwake;
import dev.jagt.orchestrator.port.Processes;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** The inhibitor lives as long as the command it wraps, and {@code tail --pid} ends with this process. */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "linux")
public class SystemdInhibit implements KeepAwake {

    private final HeldCommand inhibitor;

    public SystemdInhibit(Processes processes) {
        this.inhibitor = new HeldCommand(processes, List.of(Executables.resolve("systemd-inhibit"),
                "--what=idle:sleep", "--who=jagt", "--why=unattended work", "--mode=block",
                Executables.resolve("tail"), "--pid=" + ProcessHandle.current().pid(), "-f", "/dev/null"));
    }

    @Override
    public void hold() {
        inhibitor.hold();
    }

    @Override
    @PreDestroy
    public void release() {
        inhibitor.release();
    }
}
