package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordReadersTest {

  @Test
  void roleLogRecordIsOneRecordTailNames() {
    assertThat(RecordReaders.roleLogRecords()).isNotEmpty();
    assertThat(RecordReaders.roleLogRecordsOutsideRecordTail()).isEmpty();
  }

  @Test
  void everyRecordTailNameHasOneReader() {
    assertThat(RecordReaders.recordsWithoutExactlyOneReader()).isEmpty();
  }
}
