package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The OS-neutral half of driving kitty: the argv of the first-open window. */
class AbstractKittyTerminalDriverTest {

    @Test
    void launchesADetachedRemoteControllableInstanceAttachedToTheSession() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).startsWith("kitty", "--detach")
                .containsSequence("-o", "allow_remote_control=yes")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }

    @Test
    void isolatesTheViewerFromTheUsersOwnKittyWindows() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).containsSequence("--single-instance", "--instance-group", "jagt-agents")
                .containsSequence("--listen-on", "unix:/tmp/jagt-kitty-agents");
    }

    @Test
    void appliesTheConfiguredFontSizeToTheViewerInstance() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "13",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).containsSequence("-o", "font_size=13");
    }

    @Test
    void leavesTheFontSizeToKittysOwnConfigWhenUnset() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).noneMatch(argument -> argument.startsWith("font_size="));
    }

    @Test
    void passesAPlatformsOwnLaunchOptionsThrough() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents",
                List.of("-o", "map=cmd+м paste_from_clipboard"));

        assertThat(cmd).containsSequence("-o", "map=cmd+м paste_from_clipboard");
    }
}
