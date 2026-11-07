package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {

  private static class Minimal implements AgentRuntime {

    @Override
    public String displayName() {
      return "minimal";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
      return "run in " + worktree + (planMode ? " planning" : "");
    }

    @Override
    public Path systemKnowledgeFile(Path worktree) {
      return worktree.resolve("AGENTS.md");
    }

    @Override
    public void provisionWorktree(AgentWorktree worktree) {}

    @Override
    public OptionalLong lastSessionActivity(Path worktree) {
      return OptionalLong.empty();
    }
  }

  @Test
  void startsTheSameSessionWhateverModelOrPromptARuntimeCannotBeTold() {
    AgentRuntime runtime = new Minimal();

    assertThat(runtime.launchCommand(Path.of("/wt"), true, "fast"))
        .isEqualTo("run in /wt planning");
    assertThat(runtime.launchCommand(Path.of("/wt"), false, "fast", "a brief"))
        .isEqualTo("run in /wt");
  }

  @Test
  void revivesASessionByStartingAFreshOneWhereTheRuntimeKeepsNothingToReEnter() {
    assertThat(new Minimal().reviveCommand(Path.of("/wt"))).isEqualTo("run in /wt");
  }

  @Test
  void offersNoModelByDefault() {
    assertThat(new Minimal().models()).isEmpty();
  }

  @Test
  void leavesTheRepositorysAgentFileForTheNextTaskToReadByDefault() {
    assertThat(new Minimal().projectAgentFiles()).containsExactly("AGENTS.md");
  }

  @Test
  void readsNothingTheHumanTypedAndHoldsNoDraftWhereItCannot() {
    AgentRuntime runtime = new Minimal();

    assertThat(runtime.readsWhatTheHumanTyped()).isFalse();
    assertThat(runtime.humanSaid(Path.of("/wt"), java.util.Set.of())).isEmpty();
    assertThat(runtime.holdsDraft("> half a sent")).isFalse();
  }

  @Test
  void holdsNoPromptToBeTheHumansWhereItCannotReadTheRecord() {
    assertThat(new Minimal().humanExchangeOpen(Path.of("/wt"), java.util.Set.of())).isFalse();
  }
}
