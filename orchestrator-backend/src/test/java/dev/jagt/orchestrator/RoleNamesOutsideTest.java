package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RoleNamesOutsideTest {

  @Test
  void noRoleIsNamedInSurfaceOrBoardOutsideItsModules() {
    assertThat(
            Stream.concat(
                RoleWords.inSurface(RoleFolders.ids()).stream(),
                RoleWords.inBoard(RoleFolders.ids()).stream()))
        .isEmpty();
  }

  @Test
  void noRoleIsNamedInCoreOrUsecaseOutsideItsModulesAndThePinnedKinds() {
    assertThat(RoleWords.inCoreAndUsecase(RoleFolders.ids())).isEmpty();
  }
}
