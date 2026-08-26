package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Raising the app needs an AppleScript activate: kitty's own {@code focus-window} moves focus WITHIN the
 * application but does not bring it in front of other ones.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.platform", havingValue = "macos", matchIfMissing = true)
public class KittyTerminalDriver extends AbstractKittyTerminalDriver {

    /**
     * kitty matches keyboard shortcuts by the character the key produces, so on a non-Latin input layout
     * (Russian/Ukrainian ЙЦУКЕН) the physical V key emits {@code м} and physical C emits {@code с} — the
     * default {@code cmd+v}/{@code cmd+c} no longer match, and NOTHING pastes/copies until the user flips
     * the OS layout back to Latin. kitty ≥0.36 has an {@code ascii} shortcut fallback, but it does not fire
     * reliably for {@code cmd} shortcuts on macOS (Cocoa matches the key-equivalent by character), which is
     * why this is a macOS-only workaround. Additive: it does NOT replace the Latin defaults. Bound on the
     * initial instance; every later tab inherits this instance's config.
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
