package dev.jagt.orchestrator.service.role.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRolesTest {

  private final ModelRole master = mock(ModelRole.class);
  private final ModelRole acceptance = mock(ModelRole.class);
  private final ModelRole triage = mock(ModelRole.class);

  @Test
  void listsOnlyTheRolesThatRunInOrder() {
    when(master.id()).thenReturn("master");
    when(master.running()).thenReturn(true);
    when(acceptance.id()).thenReturn("acceptance");
    when(acceptance.running()).thenReturn(false);
    when(triage.id()).thenReturn("triage");
    when(triage.running()).thenReturn(true);

    ModelRoles roles = new ModelRoles(List.of(triage, acceptance, master));

    assertThat(roles.running()).containsExactly("master", "triage");
  }

  @Test
  void countsARoleWhoseConfigCannotBeReadAsNotRunning() {
    when(master.id()).thenReturn("master");
    when(master.running()).thenThrow(new IllegalStateException("jagt.yml unreadable"));
    when(triage.id()).thenReturn("triage");
    when(triage.running()).thenReturn(true);

    ModelRoles roles = new ModelRoles(List.of(master, triage));

    assertThat(roles.running()).containsExactly("triage");
    assertThat(roles.runs("master")).isFalse();
  }

  @Test
  void knowsNoRoleNoModuleDeclared() {
    when(master.id()).thenReturn("master");

    assertThat(new ModelRoles(List.of(master)).runs("ghost")).isFalse();
  }
}
