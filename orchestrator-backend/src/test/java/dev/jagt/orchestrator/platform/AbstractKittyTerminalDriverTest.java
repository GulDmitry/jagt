package dev.jagt.orchestrator.platform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The OS-neutral half of driving kitty: the argv of the first-open window. */
class AbstractKittyTerminalDriverTest {

    private static final List<String> NO_PLATFORM_OPTIONS = List.of();
    private static final List<String> CMD =
            AbstractKittyTerminalDriver.firstOpenCommand("kitty", "", "unix:/tmp/jagt-kitty-agents",
                    "agents", "/work/tree", "tmux", "agents", NO_PLATFORM_OPTIONS);

    @Test
    void launchesADetachedRemoteControllableInstanceAttachedToTheSession() {
        assertThat(CMD).startsWith("kitty", "--detach")
                .containsSequence("-o", "allow_remote_control=yes")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }

    @Test
    void isolatesTheViewerFromTheUsersOwnKittyWindows() {
        assertThat(CMD).containsSequence("--single-instance", "--instance-group", "jagt-agents")
                .containsSequence("--listen-on", "unix:/tmp/jagt-kitty-agents");
    }

    @Test
    void appliesTheConfiguredFontSizeToTheViewerInstance() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "13",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", NO_PLATFORM_OPTIONS);

        assertThat(cmd).containsSequence("-o", "font_size=13");
    }

    @Test
    void leavesTheFontSizeToKittysOwnConfigWhenUnset() {
        assertThat(CMD).noneMatch(argument -> argument.startsWith("font_size="));
    }

    @Test
    void passesAPlatformsOwnLaunchOptionsThrough() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents",
                List.of("-o", "map=cmd+м paste_from_clipboard"));

        assertThat(cmd).containsSequence("-o", "map=cmd+м paste_from_clipboard");
    }
}
