package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KittyTerminalDriverTest {

    @Test
    void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {
        var driver = new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(),
                mock(OsaScript.class), "kitty", "");

        assertThat(driver.platformOptions())
                .containsSequence("-o", "map=cmd+м paste_from_clipboard")
                .containsSequence("-o", "map=cmd+с copy_to_clipboard");
    }

    @Test
    void keepsTheLatinDefaultsInsteadOfRemappingCmdVWhichWouldDropKittysAsciiFallback() {
        var driver = new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(),
                mock(OsaScript.class), "kitty", "");

        assertThat(driver.platformOptions()).doesNotContain("map=cmd+v paste_from_clipboard",
                "map=cmd+c copy_to_clipboard");
    }

    @Test
    void raisesTheAppWithAppleScriptBecauseKittyCannotPutItselfInFrontOnCocoa() {
        OsaScript osaScript = mock(OsaScript.class);

        new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(), osaScript,
                "kitty", "").bringToFront();

        verify(osaScript).run("tell application \"kitty\" to activate");
    }
}
