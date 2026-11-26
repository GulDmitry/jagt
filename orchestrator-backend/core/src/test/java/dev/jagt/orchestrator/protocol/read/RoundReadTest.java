package dev.jagt.orchestrator.protocol.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.protocol.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoundReadTest {

  @ParameterizedTest
  @CsvSource({
    "ready,READY",
    "' Not Ready ',NOT_READY",
    "question,QUESTION",
    "changes,UNREAD",
    "'',UNREAD"
  })
  void resolvesVerdictWordToItsTypeOrUnread(String word, Verdict expected) {
    assertThat(RoundRead.verdictOf(word)).isEqualTo(expected);
  }

  @Test
  void readsBlankPressAsWordsAndAnyOtherAsTheMoveStripped() {
    assertThat(RoundRead.pressOf("  ")).isEqualTo(Press.NONE);
    assertThat(RoundRead.pressOf(" ship ")).isEqualTo(new Press.Move("ship"));
  }

  @Test
  void namesTheVerdictFieldWhereTheWordIsOutsideTheVocabulary() {
    Judgement unread = new Judgement("", Verdict.UNREAD, List.of(), "", List.of(), "", Press.NONE);

    assertThat(RoundRead.violations(unread))
        .extracting(Violation::field)
        .containsExactly("verdict");
  }

  @Test
  void closesUnreadJudgementAsFailedNamingTheCause() {
    Judgement unread = new Judgement("", Verdict.UNREAD, List.of(), "", List.of(), "", Press.NONE);

    Judgement closed = RoundRead.closed(unread);

    assertThat(closed.failure()).contains("verdict");
    assertThat(closed.verdict()).isEqualTo(Verdict.NOT_READY);
  }

  @Test
  void keepsReadJudgementAsItIs() {
    Judgement ready = new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE);

    assertThat(RoundRead.closed(ready)).isSameAs(ready);
  }
}
