package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHookReportTest {

    @Test
    void refusesToBelieveALogPathThatWouldResolveAgainstJagtsOwnDirectory() {
        var said = new SessionHookReport("logs/session.jsonl", "startup", null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
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
}
