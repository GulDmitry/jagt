package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * kitty on Linux. The whole driver is inherited — kitty's remote-control protocol and the temp-dir socket are
 * identical across platforms, which is what the macOS implementation had been quietly proving.
 *
 * <p>Both platform hooks come out EMPTY here, and that is the finding, not an omission:
 * <ul>
 *   <li>raising the window is already done by {@code kitty @ focus-window} in {@code reveal} — X11/Wayland
 *       window managers own stacking, and jagt is not going to shell out to wmctrl to fight a WM's
 *       focus-stealing policy. There is no macOS-style "activate the application" step to add;
 *   <li>the Cyrillic keymap workaround is macOS-only by construction: it exists because Cocoa matches a
 *       {@code cmd} key-equivalent by produced character. On Linux kitty's own {@code ascii} shortcut
 *       fallback handles a non-Latin layout, so injecting {@code ctrl+shift} aliases would add config the
 *       terminal does not need.
 * </ul>
 */
@Component
@ConditionalOnExpression("'${orchestrator.terminal:kitty}'.equals('kitty')"
        + " and '${orchestrator.platform:macos}'.equals('linux')")
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
        // Nothing to add: `reveal` already asked kitty to focus the window, and whether that raises it above
        // other applications is the window manager's decision, not ours. Deliberately a no-op rather than a
        // wmctrl/xdotool dependency that would work on one desktop and silently fail on the next.
    }
}
