package dev.jagt.orchestrator.command.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.git.WorktreeOrphans;
import org.junit.jupiter.api.Test;

class DeleteWorktreeCommandTest {

  private final WorktreeOrphans orphans = mock(WorktreeOrphans.class);

  @Test
  void deletesTheOrphanTheHumanNamed() {
    when(orphans.delete("ABC-40-demo")).thenReturn("deleted /w/ABC-40-demo");

    String said = new DeleteWorktreeCommand(orphans).run("  ABC-40-demo ");

    assertThat(said).isEqualTo("deleted /w/ABC-40-demo");
  }

  @Test
  void deletesNothingWhereNoNameIsGiven() {
    assertThatThrownBy(() -> new DeleteWorktreeCommand(orphans).run(" "))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(orphans);
  }

  @Test
  void isInTheMastersReach() {
    assertThat(new DeleteWorktreeCommand(orphans).whyNotTheArbiter()).isEmpty();
  }
}
