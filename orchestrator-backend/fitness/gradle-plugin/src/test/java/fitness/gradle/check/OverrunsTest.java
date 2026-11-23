package fitness.gradle.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OverrunsTest {

  @Test
  void hungRunNamesFunctionTaskGuardAndCeiling() {
    assertThat(Overruns.message("suite-minutes", ":test", "10"))
        .isEqualTo("fitness: suite-minutes: :test hung: no end within 3x its ceiling of 10 minutes");
  }
}
