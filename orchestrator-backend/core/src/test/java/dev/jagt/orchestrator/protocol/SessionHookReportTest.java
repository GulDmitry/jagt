package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHookReportTest {

    @Test
    void refusesToBelieveALogPathThatWouldResolveAgainstJagtsOwnDirectory() {
        var said = new SessionHookReport("logs/session.jsonl", "startup", null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactly("transcript_path");
        assertThat(said.sessionLog()).isEmpty();
    }

    @Test
    void followsTheLogTheHarnessNamedInFull() {
        var said = new SessionHookReport("/tmp/agent/session.jsonl", "startup", null);

        assertThat(said.sessionLog()).contains(Path.of("/tmp/agent/session.jsonl"));
    }

    @Test
    void readsTheErrorAndTheEndReasonFromTheNamesTheVendorGaveThem() {
        var said = new JsonMapper().readValue("{\"error\": \"overloaded\", \"reason\": \"clear\"}",
                SessionHookReport.class);

        assertThat(said.error()).isEqualTo("overloaded");
        assertThat(said.reason()).isEqualTo("clear");
    }

    @Test
    void readsAHookThatPostedNothingAtAllAsASessionThatNamedNoLog() {
        assertThat(SessionHookReport.none().sessionLog()).isEmpty();
        assertThat(SessionHookReport.none().violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void readsAHookThatSentItsTurnEndingOnAndOneStillHoldingBackgroundWork() {
        var said = new JsonMapper().readValue(
                "{\"stop_hook_active\": true, \"background_tasks\": [{\"id\": \"bash-1\"}]}",
                SessionHookReport.class);

        assertThat(said.sentOn()).isTrue();
        assertThat(said.pausedOnBackgroundWork()).isTrue();
    }

    @Test
    void readsAHookThatSaidNeitherAsAStopWithNothingInFlight() {
        var said = new JsonMapper().readValue("{\"stop_hook_active\": false}", SessionHookReport.class);

        assertThat(said.sentOn()).isFalse();
        assertThat(said.pausedOnBackgroundWork()).isFalse();
        assertThat(SessionHookReport.none().sentOn()).isFalse();
    }

    @Test
    void readsBlankFieldsAsLeftOutSoABlankLogPathNamesNoLog() {
        var said = new SessionHookReport("  ", "  ", "  ");

        assertThat(said.transcriptPath()).isNull();
        assertThat(said.source()).isNull();
        assertThat(said.message()).isNull();
        assertThat(said.sessionLog()).isEmpty();
    }
}
