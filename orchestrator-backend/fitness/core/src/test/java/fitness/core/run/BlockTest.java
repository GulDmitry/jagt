package fitness.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import org.junit.jupiter.api.Test;

class BlockTest {

  @Test
  void aCharacteristicCoversItsSubcharacteristicsAndNoOtherCharacteristic() {
    Block block = Block.of("technical.maintainability");

    assertThat(block.covers("technical.maintainability.testability")).isTrue();
    assertThat(block.covers("technical.usability.learnability")).isFalse();
  }

  @Test
  void aNamePrefixIsNotABlock() {
    assertThat(Block.of("technical").covers("technical.maintainability.modularity")).isTrue();
    assertThat(new Block("tech").covers("technical.maintainability.modularity")).isFalse();
  }

  @Test
  void theWholeTreeIsTheBlockOfNoPath() {
    assertThat(Block.ALL.covers("operational.reliability.maturity")).isTrue();
  }

  @Test
  void aPathOutsideTheReservedTreeFailsNamingIt() {
    assertThatThrownBy(() -> Block.of("technical.speed"))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(Reason.UNKNOWN_SECTION);
              assertThat(e.key()).isEqualTo("technical.speed");
            });
  }
}
