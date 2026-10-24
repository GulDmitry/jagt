package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** A step no case sends never reaches a session, and a case naming no step sends nothing. */
@Component
@RequiredArgsConstructor
public class CaseCheck implements StartupCheck {

    private final SessionCases cases;
    private final SessionBrief brief;

    @Override
    public List<Problem> problems() {
        return cases.book().problems(brief.steps(), SessionCases.FILE);
    }
}
