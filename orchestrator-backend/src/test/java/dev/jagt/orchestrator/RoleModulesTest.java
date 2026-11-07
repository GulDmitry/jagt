package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RoleModulesTest {

  @Test
  void theDeclaredModulesNameNoPackageOrClassThatIsGone() {
    assertThat(
            Stream.concat(
                    RoleModules.ARBITER.stream(),
                    RoleModules.CHECKERS.values().stream().flatMap(java.util.List::stream))
                .filter(
                    prefix ->
                        MainClasses.CODE.stream()
                            .noneMatch(type -> type.getName().startsWith(prefix))))
        .isEmpty();
  }
}
