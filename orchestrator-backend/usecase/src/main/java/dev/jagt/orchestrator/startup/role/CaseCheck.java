package dev.jagt.orchestrator.startup.role;

import dev.jagt.orchestrator.config.role.CaseBook;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.config.role.SessionCases;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.task.turn.Step;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A step no case sends never reaches a session, and a case naming no step sends nothing. */
@Component
@RequiredArgsConstructor
public class CaseCheck implements StartupCheck {

  private final SessionCases cases;
  private final SessionBrief brief;
  private final Checkers checkers;

  @Override
  public List<Problem> problems() {
    return Stream.of(
            cases
                .book()
                .problems(brief.steps(), SessionCases.FILE, checkers.installed(), Checkers.SHIPPED)
                .stream(),
            roundsUnsent(),
            brief.unreadable().stream())
        .flatMap(problems -> problems)
        .toList();
  }

  private Stream<Problem> roundsUnsent() {
    String step = Step.ACCEPTANCE_ROUND.id();
    if (!brief.steps().contains(step)) {
      return Stream.empty();
    }
    return checkers.installed().stream()
        .filter(id -> !cases.book().unnamed(Set.of(step), new CaseBook.By.Role(id)).isEmpty())
        .map(id ->
            new Problem(
                SessionCases.FILE,
                SessionCases.FILE
                    + ": installed checker "
                    + id
                    + " has no case sending the step '"
                    + step
                    + "' by: "
                    + id
                    + " — add acceptance-round-by-"
                    + id
                    + ", or its rows never reach a session"));
  }

  @Override
  public boolean machine() {
    return false;
  }
}
