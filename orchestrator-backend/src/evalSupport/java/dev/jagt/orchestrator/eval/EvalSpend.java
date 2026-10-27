package dev.jagt.orchestrator.eval;

import java.util.OptionalDouble;

@FunctionalInterface
public interface EvalSpend {

    EvalSpend UNMETERED = OptionalDouble::empty;

    OptionalDouble costUsd();
}
