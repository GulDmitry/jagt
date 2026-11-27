package dev.jagt.orchestrator.sessionrun;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalSpend;
import dev.jagt.orchestrator.eval.SessionTurnCase;
import dev.jagt.orchestrator.eval.SessionTurnReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("sessionEval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionTurnEvalTest {

  private final SessionTurnReader reader =
      new SessionTurnReader(
          Path.of(System.getProperty("sessionEval.brief", "../roles/session/brief.md.dist")),
          System.getProperty("sessionEval.model", "sonnet"));

  @ParameterizedTest
  @MethodSource("cases")
  void endsTheTurnWithTheReportTheStepAsks(SessionTurnCase turn) {
    EvalQuorum.configured()
        .hold(
            turn.name(),
            EvalSpend.UNMETERED,
            () ->
                assertThat(reader.read(turn))
                    .isEqualTo(new SessionTurnReader.Reported(turn.status(), turn.outcome())));
  }

  static List<SessionTurnCase> cases() {
    return SessionTurnCase.all();
  }
}
