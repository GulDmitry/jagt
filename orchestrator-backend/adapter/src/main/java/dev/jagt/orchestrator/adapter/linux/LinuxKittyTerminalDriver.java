package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Both platform hooks are EMPTY by design: {@code kitty @ focus-window} already raises the window, and kitty's
 * own {@code ascii} shortcut fallback handles a non-Latin layout here.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.platform", havingValue = "linux", matchIfMissing = false)
public class LinuxKittyTerminalDriver extends AbstractKittyTerminalDriver {

    public LinuxKittyTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties,
                                    @Value("${orchestrator.kitty-command:kitty}") String kittyCommand,
                                    @Value("${orchestrator.kitty-font-size:}") String kittyFontSize) {
        super(processRunner, properties, kittyCommand, kittyFontSize);
    }

    @Override
    protected List<String> platformOptions() {
        return List.of();
    }

    @Override
    public void bringToFront() {
    }
}
