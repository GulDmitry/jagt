package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Raising the app needs an AppleScript activate: kitty's own {@code focus-window} moves focus WITHIN the
 *  application only. */
@Component
@ConditionalOnProperty(name = "orchestrator.platform", havingValue = "macos", matchIfMissing = true)
public class KittyTerminalDriver extends AbstractKittyTerminalDriver {

    /**
     * kitty matches shortcuts by the character a key produces, so on a ЙЦУКЕН layout physical V emits {@code м}
     * and C emits {@code с}, and {@code cmd+v}/{@code cmd+c} stop matching. kitty's {@code ascii} fallback does
     * not fire reliably for {@code cmd} on macOS, where Cocoa matches the key-equivalent by character. Additive,
     * and bound on the initial instance: every later tab inherits its config.
     */
    static final List<String> CYRILLIC_SHORTCUT_FIXES = List.of(
            "-o", "map=cmd+м paste_from_clipboard",
            "-o", "map=cmd+с copy_to_clipboard");

    private final OsaScript osaScript;

    public KittyTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties, OsaScript osaScript,
                               @Value("${orchestrator.kitty-command:kitty}") String kittyCommand,
                               @Value("${orchestrator.kitty-font-size:}") String kittyFontSize) {
        super(processRunner, properties, kittyCommand, kittyFontSize);
        this.osaScript = osaScript;
    }

    @Override
    protected List<String> platformOptions() {
        return CYRILLIC_SHORTCUT_FIXES;
    }

    @Override
    public void bringToFront() {
        osaScript.run("tell application \"kitty\" to activate");
    }
}
