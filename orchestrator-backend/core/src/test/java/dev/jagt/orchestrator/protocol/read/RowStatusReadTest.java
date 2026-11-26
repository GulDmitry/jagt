package dev.jagt.orchestrator.protocol.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Row;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class RowStatusReadTest {

  private final AcceptanceVerdict unread =
      new AcceptanceVerdict(
          "abc@1",
          "",
          List.of(new Row("it greets", Status.UNREAD, "")),
          "",
          "",
          AcceptanceVerdict.Flows.none());

  @Test
  void resolvesStatusWordToItsTypeOrUnread() {
    assertThat(RowStatusRead.statusOf("not met")).isEqualTo(Status.NOT_MET);
    assertThat(RowStatusRead.statusOf("passed")).isEqualTo(Status.UNREAD);
  }

  @Test
  void namesTheRowWhoseStatusIsOutsideTheVocabulary() {
    assertThat(RowStatusRead.violations(unread))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).contains("it greets"));
  }

  @Test
  void closesUnreadVerdictAsFailedNamingTheCause() {
    AcceptanceVerdict closed = RowStatusRead.closed(unread);

    assertThat(closed.failure()).contains("it greets");
    assertThat(closed.rows()).isEmpty();
  }

  @Test
  void keepsReadVerdictAsItIs() {
    AcceptanceVerdict met =
        new AcceptanceVerdict(
            "abc@1",
            "",
            List.of(new Row("it greets", Status.MET, "")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    assertThat(RowStatusRead.closed(met)).isSameAs(met);
  }
}
