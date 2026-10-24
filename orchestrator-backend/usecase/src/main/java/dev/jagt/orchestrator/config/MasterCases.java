package dev.jagt.orchestrator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A case the human's copy lacks is read from the shipped file. */
@Component
@RequiredArgsConstructor
public class MasterCases {

    public static final String FILE = "master-cases.md";

    private final OrchestratorPaths paths;

    public CaseBook book() {
        return HumanCopy.read(paths.root(), FILE).stream()
                .map(file -> CaseBook.parseMoves(file.file(), file.text()))
                .reduce(CaseBook::over)
                .orElseGet(() -> CaseBook.empty(CaseBook.Shape.MOVES));
    }
}
