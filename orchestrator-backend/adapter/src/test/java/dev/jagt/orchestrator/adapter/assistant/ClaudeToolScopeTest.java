package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeToolScopeTest {

  @Test
  void aGlobWithOnlyATrailingStarIsAPrefixRuleAndAnyOtherGlobStaysVerbatim() {
    ClaudeToolScope scope =
        ClaudeToolScope.of(List.of("git log*"), List.of("git * --output*"), List.of(), false);

    assertThat(scope.allow()).containsExactly("Read", "Grep", "Glob", "Bash(git log:*)");
    assertThat(scope.refuse())
        .containsExactly("Write", "Edit", "NotebookEdit", "Bash(git * --output*)");
  }

  @Test
  void offersBashOnlyWhereAShellGlobIsNamedAndWebFetchOnlyWhereTheRoleFetches() {
    assertThat(ClaudeToolScope.of(List.of(), List.of(), List.of(), false).tools())
        .isEqualTo("Read,Grep,Glob");
    assertThat(ClaudeToolScope.of(List.of("git log*"), List.of(), List.of(), true).tools())
        .isEqualTo("Read,Grep,Glob,Bash,WebFetch");
  }

  @Test
  void refusesAPathGlobAsAReadRule() {
    ClaudeToolScope scope = ClaudeToolScope.of(List.of(), List.of(), List.of("**/notes.md"), false);

    assertThat(scope.refuse()).contains("Read(**/notes.md)");
  }

  @Test
  void allowsWebFetchBesideTheReadsWhereTheRoleFetches() {
    ClaudeToolScope scope = ClaudeToolScope.of(List.of(), List.of(), List.of(), true);

    assertThat(scope.allow()).containsExactly("Read", "Grep", "Glob", "WebFetch");
  }
}
