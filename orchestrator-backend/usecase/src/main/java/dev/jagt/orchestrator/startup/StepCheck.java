package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** A step jagt can send with no brief section or no case is a miss at the first relay. */
@Component
@RequiredArgsConstructor
public class StepCheck implements StartupCheck {

    private final SessionCases cases;
    private final SessionBrief brief;

    @Override
    public List<Problem> problems() {
        Set<String> sections = brief.steps();
        return Arrays.stream(Step.values()).map(Step::id).flatMap(step -> Stream.of(
                        sections.contains(step) ? null : new Problem(SessionBrief.FILE, SessionBrief.FILE + ": no section for the step '" + step
                                + "' — add one, or the session is told nothing of it"),
                        cases.book().names(step) ? null : new Problem(SessionCases.FILE, SessionCases.FILE
                                + ": no case sends the step '" + step + "' — add one, or it never reaches a session")))
                .filter(Objects::nonNull).toList();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
