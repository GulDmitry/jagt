package dev.jagt.orchestrator.eval;

import org.junit.jupiter.api.function.Executable;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public record EvalQuorum(int runs, int passes) {

    public EvalQuorum {
        if (passes < 1 || passes > runs) {
            throw new IllegalArgumentException("eval.passes " + passes + " must be between 1 and eval.runs " + runs);
        }
    }

    public static EvalQuorum configured() {
        int runs = Integer.getInteger("eval.runs", 3);
        return new EvalQuorum(runs, Integer.getInteger("eval.passes", runs / 2 + 1));
    }

    public void hold(String name, EvalSpend spend, Executable run) {
        long started = System.nanoTime();
        OptionalDouble spentBefore = spend.costUsd();
        List<String> failures = new ArrayList<>();
        int made = 0;
        int stalled = 0;
        while (made - failures.size() < passes && failures.size() <= runs - passes) {
            made++;
            try {
                run.execute();
            } catch (Throwable failure) {
                failures.add("run " + made + ": " + failure.getMessage());
                if (failure instanceof EvalStalled) {
                    stalled++;
                }
            }
        }
        boolean held = made - failures.size() >= passes;
        LoggerFactory.getLogger(EvalQuorum.class).atInfo()
                .setMessage(held ? "eval case held" : "eval case failed")
                .addKeyValue("case", name)
                .addKeyValue("passes", made - failures.size())
                .addKeyValue("runs", made)
                .log();
        EvalStats.record(name, held, made, (System.nanoTime() - started) / 1e9, costSince(spentBefore, spend.costUsd()), stalled);
        if (!held) {
            throw new AssertionError(name + " passed " + (made - failures.size()) + " of " + made
                    + " runs, " + passes + " needed:\n" + String.join("\n", failures));
        }
    }

    private static OptionalDouble costSince(OptionalDouble before, OptionalDouble after) {
        if (before.isEmpty() || after.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(after.getAsDouble() - before.getAsDouble());
    }
}
