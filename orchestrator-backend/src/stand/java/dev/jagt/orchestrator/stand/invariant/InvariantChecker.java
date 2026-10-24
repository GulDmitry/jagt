package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.config.CaseBook;
import java.util.Comparator;
import java.util.List;

public final class InvariantChecker {

    private final List<Invariant> invariants;

    public InvariantChecker(CaseBook book, Ancestry ancestry) {
        this.invariants = List.of(new BaseUnchanged(), new DeployBranchByDeploy(), new NoForcePush(ancestry),
                new LegalTransitions(), new StepsWithinCases(book), new MissStopsTheMaster());
    }

    public List<Violation> check(RunRecord run) {
        return invariants.stream().flatMap(invariant -> invariant.check(run).stream())
                .sorted(Comparator.comparing(Violation::at)).toList();
    }
}
