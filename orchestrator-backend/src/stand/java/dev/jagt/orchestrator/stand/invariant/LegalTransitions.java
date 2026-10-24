package dev.jagt.orchestrator.stand.invariant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LegalTransitions implements Invariant {

    public static final String NAME = "legal-transitions";

    @Override
    public List<Violation> check(RunRecord run) {
        List<Violation> violations = new ArrayList<>();
        run.histories().forEach((task, steps) -> {
            for (int i = 1; i < steps.size(); i++) {
                RunRecord.Step from = steps.get(i - 1);
                RunRecord.Step to = steps.get(i);
                if (!LegalMoves.allows(from.status(), to.status())) {
                    violations.add(new Violation(NAME, to.at(), "state.json history of " + task + ": " + from.status()
                            + " -> " + to.status() + " by " + to.origin()));
                }
            }
        });
        Map<String, RunRecord.Sample> last = new HashMap<>();
        for (RunRecord.Sample sample : run.timeline().samples()) {
            RunRecord.Sample before = last.put(sample.task(), sample);
            if (before != null && !LegalMoves.reaches(before.status(), sample.status())) {
                violations.add(new Violation(NAME, sample.at(), before.line() + " | " + sample.line()));
            }
        }
        return List.copyOf(violations);
    }
}
