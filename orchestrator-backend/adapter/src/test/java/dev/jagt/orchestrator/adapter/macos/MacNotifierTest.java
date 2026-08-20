package dev.jagt.orchestrator.adapter.macos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Both come from ticket and comment text, and terminal-notifier reads a missing value as the next flag. */
    @Test
    void sendsATitleAndBodyEvenWhenTheCallerHadNeither() {
        assertThat(MacNotifier.command("terminal-notifier", null, null, null))
                .containsSequence("-title", "jagt").containsSequence("-message", "");
    }
}
