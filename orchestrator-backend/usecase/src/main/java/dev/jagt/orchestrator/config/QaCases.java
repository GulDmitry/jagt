package dev.jagt.orchestrator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QaCases {

    public static final String FILE = "qa-cases.md";

    private final OrchestratorPaths paths;

    public CaseBook book() {
        return HumanCopy.stepCases(paths.root(), FILE);
    }
}
