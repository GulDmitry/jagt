package dev.jagt.orchestrator.config.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RenamedKeysTest {

  static List<Map<String, Object>> oldSpellings() {
    return List.of(
        Map.of("sessionHost", "herdr"),
        Map.of("tmuxCommand", "herdr"),
        Map.of("tmuxSocket", "herdr"),
        Map.of("viewer", Map.of("tmuxSession", "herdr")));
  }

  @Test
  void movesSessionHostToMultiplexer() {
    Object applied = RenamedKeys.applied(Map.of("sessionHost", "herdr", "codeHost", "gitlab"));

    assertThat(applied).isEqualTo(Map.of("multiplexer", "herdr", "codeHost", "gitlab"));
  }

  @Test
  void movesTmuxCommandAndSocketIntoTheTmuxBlock() {
    Object applied =
        RenamedKeys.applied(Map.of("tmuxCommand", "/opt/tmux", "tmuxSocket", "jagt-sock"));

    assertThat(applied)
        .isEqualTo(Map.of("tmux", Map.of("command", "/opt/tmux", "socket", "jagt-sock")));
  }

  @Test
  void movesViewerTmuxSessionToViewerSessionBesideItsSiblings() {
    Object applied =
        RenamedKeys.applied(
            Map.of("viewer", Map.of("tmuxSession", "agents", "viewMode", "shared")));

    assertThat(applied)
        .isEqualTo(Map.of("viewer", Map.of("session", "agents", "viewMode", "shared")));
  }

  @Test
  void keepsTheNewKeyWhereBothAreWritten() {
    Object applied = RenamedKeys.applied(Map.of("sessionHost", "tmux", "multiplexer", "herdr"));

    assertThat(applied).isEqualTo(Map.of("multiplexer", "herdr"));
  }

  @Test
  void leavesSectionWithoutOldKeysAsItIs() {
    Map<String, Object> section = Map.of("multiplexer", "herdr", "viewer", Map.of("session", "a"));

    assertThat(RenamedKeys.applied(section)).isEqualTo(section);
  }

  @ParameterizedTest
  @MethodSource("oldSpellings")
  void namesEachOldKeyWrittenWithValue(Map<String, Object> section) {
    assertThat(RenamedKeys.written(section)).hasSize(1);
  }

  @Test
  void namesNoRenameWhereTheOldKeyIsAbsent() {
    assertThat(RenamedKeys.written(Map.of("multiplexer", "herdr"))).isEmpty();
  }

  @Test
  void spellsPropertyInTheDashedFormSpringBinds() {
    RenamedKeys.Rename rename = new RenamedKeys.Rename("viewer.tmuxSession", "viewer.session");

    assertThat(rename.oldProperty()).isEqualTo("orchestrator.viewer.tmux-session");
    assertThat(rename.newProperty()).isEqualTo("orchestrator.viewer.session");
  }
}
