package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalShardsTest {

  static class AcceptanceVerdictEvalShard3Test {}

  static class MasterVerdictEvalShard0Test {}

  @Test
  void aLateShardClassClaimsItsOwnNumberWhileShardZeroIsFree(@TempDir Path base)
      throws IOException {
    EvalShards shards = EvalShards.under(base, 6);

    int claimed = shards.claim(AcceptanceVerdictEvalShard3Test.class);

    assertThat(claimed).isEqualTo(3);
    shards.release();
  }

  @Test
  void aShardClassReadsItsNumberFromItsName() {
    assertThat(EvalShards.indexOf(MasterVerdictEvalShard0Test.class)).isZero();
  }

  @Test
  void aClassWithoutAShardNumberClaimsNothing(@TempDir Path base) {
    assertThatThrownBy(() -> EvalShards.under(base, 6).claim(EvalShardsTest.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aShardNumberPastTheRunsShardsClaimsNothing(@TempDir Path base) {
    assertThatThrownBy(() -> EvalShards.under(base, 3).claim(AcceptanceVerdictEvalShard3Test.class))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aShardAnotherRunHoldsIsRefusedNotReplacedByAFreeOne(@TempDir Path base) throws IOException {
    EvalShards other = EvalShards.under(base, 6);
    other.claim(AcceptanceVerdictEvalShard3Test.class);

    assertThatThrownBy(() -> EvalShards.under(base, 6).claim(AcceptanceVerdictEvalShard3Test.class))
        .isInstanceOf(IllegalStateException.class);
    other.release();
  }
}
