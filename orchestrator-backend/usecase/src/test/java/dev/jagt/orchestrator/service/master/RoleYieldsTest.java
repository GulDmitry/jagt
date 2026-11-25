package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleYieldsTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final RoleYields yields = new RoleYields(configService);
  private final TaskState task =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

  @Test
  void yieldsWhileTheRoleIsOnAndTheProjectHasCodeHost() {
    when(configService.load()).thenReturn(ConfigFile.defaults().withCodeHost("github"));

    assertThat(yields.yields("poster", task)).isTrue();
  }

  @Test
  void doesNotYieldWhereTheRoleIsOff() {
    ConfigFile.RoleConfig off = ConfigFile.RoleConfig.defaults().withEnabled(false);
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withCodeHost("github").withRoles(Map.of("poster", off)));

    assertThat(yields.yields("poster", task)).isFalse();
  }

  @Test
  void doesNotYieldWhereTheProjectHasNoCodeHost() {
    when(configService.load()).thenReturn(ConfigFile.defaults().withCodeHost("none"));

    assertThat(yields.yields("poster", task)).isFalse();
  }

  @Test
  void doesNotYieldWhereTheRowNamesNoRole() {
    assertThat(yields.yields("", task)).isFalse();
  }
}
