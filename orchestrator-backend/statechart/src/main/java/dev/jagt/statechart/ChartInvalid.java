package dev.jagt.statechart;

import java.util.List;

public final class ChartInvalid extends RuntimeException {

    private final transient List<String> problems;

    ChartInvalid(List<String> problems) {
        super("chart is invalid: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
