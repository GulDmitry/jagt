package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.CodeHostCommands;
import dev.jagt.orchestrator.task.Remote;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostCliLineTest {

  private record Cli(String command, Map<String, Set<String>> reads, Set<String> groups)
      implements CodeHostCommands {}

  private static final Cli FIRST =
      new Cli("hc", Map.of("change", Set.of("view", "list")), Set.of("change", "api"));
  private static final Cli SECOND =
      new Cli(
          "xc", Map.of("change", Set.of("show"), "pipe", Set.of("status")), Set.of("pipe", "api"));

  private final HostCliLine line = new HostCliLine(List.of(SECOND, FIRST));

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hc change merge 1",
        "xc pipe delete 1",
        "hc pipe status extra --show-token",
        "/usr/bin/hc change merge 1",
        "HC change merge 1",
        "env X=1 hc change merge 1",
        "nohup hc change merge 1",
        "hc -R owner/repo change merge 1",
        "hc --repo=owner/repo change merge 1",
        "hc api -X POST projects/1",
        "hc api --input body.json projects/1",
        "sh -c \"hc change merge 1\"",
        "bash -c 'echo ok; hc change merge 1'",
        "eval \"hc change merge 1\"",
      })
  void writesWhenAHostCliRunsAVerbThatIsNotAReadOfItsGroup(String segment) {
    assertThat(line.writes(segment)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hc change view 1",
        "hc change list",
        "xc change show 1",
        "hc change show 1",
        "xc pipe status",
        "hc -R owner/repo change view 1",
        "hc api projects/1",
        "hc api --method get projects/1",
        "grep -rn hc src",
        "echo hc",
        "sh -c \"hc change view 1\"",
        "echo 'hc change merge 1'",
      })
  void allowsAHostCliReadAndAWordThatIsNotACall(String segment) {
    assertThat(line.writes(segment)).isFalse();
  }

  @Test
  void listsEveryReadOfEveryHostCliOnceByGroup() {
    assertThat(line.readsAllowed()).isEqualTo("change list/show/view, pipe status, api GET");
  }

  @Test
  void namesTheHostCliWordsSorted() {
    assertThat(line.commands()).containsExactly("hc", "xc");
  }

  @Test
  void refusesAWriteNamingTheHostClisItJudges() {
    ToolGate gate = new ToolGate(List.of(SECOND, FIRST));

    assertThat(gate.refusal("hc change merge 1", "ABC-42", 8290, Remote.ORIGIN))
        .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains("hc and xc only read (change list/show/view, pipe status, api GET)");
  }

  @Test
  void refusesAnUnreadQuotingOnAHostCliLine() {
    ToolGate gate = new ToolGate(List.of(SECOND, FIRST));

    assertThat(gate.refusal("xc change show $'1'", "ABC-42", 8290, Remote.ORIGIN))
        .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains("on a hc, xc or git line");
  }

  @Test
  void allowsAnUnreadQuotingOnALineNamingNoHostCliOrGit() {
    ToolGate gate = new ToolGate(List.of(SECOND, FIRST));

    assertThat(gate.refusal("echo $'1'", "ABC-42", 8290, Remote.ORIGIN)).isEmpty();
  }
}
