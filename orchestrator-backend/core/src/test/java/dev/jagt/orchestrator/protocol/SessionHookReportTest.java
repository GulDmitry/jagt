package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionHookReportTest {

  @Test
  void refusesToBelieveALogPathThatWouldResolveAgainstJagtsOwnDirectory() {
    var said = new SessionHookReport("logs/session.jsonl", "startup", null);

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("transcriptPath");
    assertThat(said.sessionLog()).isEmpty();
  }

  @Test
  void followsTheLogTheHarnessNamedInFull() {
    var said = new SessionHookReport("/tmp/agent/session.jsonl", "startup", null);

    assertThat(said.sessionLog()).contains(Path.of("/tmp/agent/session.jsonl"));
  }

  @Test
  void readsAHookThatPostedNothingAtAllAsASessionThatNamedNoLog() {
    assertThat(SessionHookReport.none().sessionLog()).isEmpty();
    assertThat(SessionHookReport.none().violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void readsBlankFieldsAsLeftOutSoABlankLogPathNamesNoLog() {
    var said = new SessionHookReport("  ", "  ", "  ");

    assertThat(said.transcriptPath()).isNull();
    assertThat(said.source()).isNull();
    assertThat(said.message()).isNull();
    assertThat(said.sessionLog()).isEmpty();
  }

  @Test
  void readsAStopSentOnByARefusalAsOneThatCannotBeRefusedAgain() {
    var said =
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(true, null, null),
            new SessionHookReport.Turn(null, null, java.util.List.of()));

    assertThat(said.sentOn()).isTrue();
    assertThat(SessionHookReport.none().sentOn()).isFalse();
  }

  @Test
  void readsWhatTheHumanTypedAndBlankWhereNothingWasTyped() {
    var said =
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(null, null, null),
            new SessionHookReport.Turn("go on", null, java.util.List.of()));

    assertThat(said.typed()).isEqualTo("go on");
    assertThat(SessionHookReport.none().typed()).isEmpty();
  }

  @Test
  void readsBackgroundWorkInFlightAsAPauseNotAStop() {
    var said =
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(null, null, null),
            new SessionHookReport.Turn(null, null, java.util.List.of("bash-1")));

    assertThat(said.pausedOnBackgroundWork()).isTrue();
    assertThat(SessionHookReport.none().pausedOnBackgroundWork()).isFalse();
  }

  @Test
  void readsTheFlatHookJsonAndWritesItBackEqual() throws Exception {
    var mapper = JsonMapper.builder().build();
    var flat =
        """
        {"transcriptPath":"/tmp/s.jsonl","source":"startup","message":"hi","continuing":true,
         "error":"overloaded","reason":"clear","prompt":"go on","toolName":"Bash",
         "backgroundTasks":[{"id":"bash-1"}]}\
        """;

    var said = mapper.readValue(flat, SessionHookReport.class);

    assertThat(mapper.readTree(mapper.writeValueAsString(said))).isEqualTo(mapper.readTree(flat));
  }
}
