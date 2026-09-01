package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MacNotifierTest {

    @Test
    void makesTheBannerClickThroughToWhereverTheNotificationPointed() {
        assertThat(MacNotifier.command("/opt/homebrew/bin/terminal-notifier", "jagt · ABC-1 · needs input",
                "answer the question", "http://localhost:8290/?task=ABC-1"))
                .containsSequence("-open", "http://localhost:8290/?task=ABC-1");
    }

    @Test
    void sendsThePlainBannerWhenThereIsNowhereToSendTheHuman() {
        assertThat(MacNotifier.command("/opt/homebrew/bin/terminal-notifier", "jagt · restart needed",
                "the running jar was rebuilt", null)).doesNotContain("-open");
    }

    @Test
    void sendsATitleAndBodyEvenWhenTheCallerHadNeither() {
        assertThat(MacNotifier.command("terminal-notifier", null, null, null))
                .containsSequence("-title", "jagt").containsSequence("-message", "");
    }

    @Test
    void reachesTheHumanThroughOsaScriptWhenTheInstalledTerminalNotifierRefusesTheBanner() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), anyList()))
                .thenReturn(new Processes.Result(3, "", "Could not request notification permission"));
        OsaScript osaScript = mock(OsaScript.class);

        new MacNotifier(osaScript, processRunner, "/opt/homebrew/bin/terminal-notifier")
                .notify("jagt · ABC-42", "needs input", null);

        verify(osaScript).run("display notification \"needs input\" with title \"jagt · ABC-42\"");
    }

    @Test
    void raisesOneBannerOnlyWhenTerminalNotifierDeliveredIt() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));
        OsaScript osaScript = mock(OsaScript.class);

        new MacNotifier(osaScript, processRunner, "/opt/homebrew/bin/terminal-notifier")
                .notify("jagt · ABC-42", "needs input", null);

        verifyNoInteractions(osaScript);
    }

    @Test
    void speaksThroughOsaScriptEvenWhenTheCallerHadNeitherTitleNorBody() {
        OsaScript osaScript = mock(OsaScript.class);

        new MacNotifier(osaScript, mock(ProcessRunner.class), "").notify(null, null, null);

        verify(osaScript).run("display notification \"\" with title \"jagt\"");
    }

    @Test
    void spawnsNothingWhenTheHumanEmptiedTheTerminalNotifierSetting() {
        ProcessRunner processRunner = mock(ProcessRunner.class);

        new MacNotifier(mock(OsaScript.class), processRunner, "").notify("jagt · ABC-42", "needs input", null);

        verifyNoInteractions(processRunner);
    }
}
