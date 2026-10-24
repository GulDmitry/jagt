package dev.jagt.orchestrator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A case the human's copy lacks is read from the shipped file. */
@Component
@RequiredArgsConstructor
public class SessionCases {

    public static final String FILE = "session-cases.md";

    private final OrchestratorPaths paths;

    public CaseBook book() {
        return HumanCopy.read(paths.root(), FILE).stream()
                .map(file -> CaseBook.parse(file.file(), file.text()))
                .reduce(CaseBook::over)
                .orElseGet(CaseBook::empty);
    }
}
