package dev.jagt.orchestrator.platform.macos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KittyTerminalDriverTest {

    private static final List<String> CMD =
            KittyTerminalDriver.firstOpenCommand("kitty", "unix:/tmp/jagt-kitty-agents",
                    "agents", "/work/tree", "tmux", "agents");

    @Test
    void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {
        assertThat(CMD).containsSequence("-o", "map=cmd+м paste_from_clipboard")
                .containsSequence("-o", "map=cmd+с copy_to_clipboard");
    }

    @Test
    void keepsTheLatinDefaultsInsteadOfRemappingCmdVWhichWouldDropKittysAsciiFallback() {
        assertThat(CMD).doesNotContain("map=cmd+v paste_from_clipboard", "map=cmd+c copy_to_clipboard");
    }

    @Test
    void launchesADetachedRemoteControllableInstanceAttachedToTheSession() {
        assertThat(CMD).startsWith("kitty", "--detach")
                .containsSequence("-o", "allow_remote_control=yes")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }
}
