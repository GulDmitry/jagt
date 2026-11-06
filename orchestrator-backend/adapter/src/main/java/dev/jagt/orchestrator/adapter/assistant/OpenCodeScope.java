package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;

import java.nio.file.Path;
import java.util.List;

/** What a read on OpenCode may touch: command globs it may and may not run, and path globs it may not open. */
record OpenCodeScope(List<String> bashAllowed, List<String> bashRefused, List<String> readRefused, boolean fetch,
                     boolean git, List<Path> roots, List<String> environments) {

    static OpenCodeScope of(List<String> shell, List<String> refuseShell, List<String> refuseRead, boolean fetch,
                            boolean git, List<Path> roots, List<String> environments) {
        return new OpenCodeScope(shell, refuseShell, refuseRead, fetch, git, roots, environments);
    }

    ReadScope fenced() {
        return new ReadScope(roots, List.of(), git, List.of(), fetch ? environments : List.of());
    }
}
