package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Both platform hooks come out EMPTY here, and that is the finding, not an omission:
 * <ul>
 *   <li>raising the window is already done by {@code kitty @ focus-window} in {@code reveal} — X11/Wayland
 *       window managers own stacking, and jagt is not going to shell out to wmctrl or xdotool to fight a WM's
 *       focus-stealing policy on one desktop and fail silently on the next;
 *   <li>the Cyrillic keymap workaround is macOS-only by construction: it exists because Cocoa matches a
 *       {@code cmd} key-equivalent by produced character. Here kitty's own {@code ascii} shortcut fallback
 *       handles a non-Latin layout, so aliases would add config the terminal does not need.
 * </ul>
 */
@Component
@ConditionalOnExpression("'${orchestrator.terminal:kitty}'.equalsIgnoreCase('kitty')"
        + " and '${orchestrator.platform:macos}'.equalsIgnoreCase('linux')")
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
