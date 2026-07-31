package dev.jagt.orchestrator.shell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextWrapTest {

    @Test
    void leavesAShortLineUntouched() {
        assertThat(MasterShell.wrap("Opened the project", 40)).containsExactly("Opened the project");
    }

    @Test
    void breaksAProseLineAtWordBoundariesWithinTheWidth() {
        List<String> lines = MasterShell.wrap("Opened /path as a project use Git for a live diff", 20);

        assertThat(lines).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(20));
        assertThat(String.join(" ", lines)).isEqualTo("Opened /path as a project use Git for a live diff");
    }

    @Test
    void hardBreaksALongTokenThatHasNoSpaces() {
        String url = "https://gitlab.example.com/group/project/-/merge_requests/12345";
        List<String> lines = MasterShell.wrap(url, 20);

        assertThat(lines).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(20));
        assertThat(String.join("", lines)).isEqualTo(url);   // no chars lost on a spaceless token
        assertThat(lines.size()).isGreaterThan(1);
    }
}
