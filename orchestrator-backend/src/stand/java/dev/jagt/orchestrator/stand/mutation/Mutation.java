package dev.jagt.orchestrator.stand.mutation;

public record Mutation(String name, Kind kind, String ask, String event, String holds) {

    public enum Kind {
        TICKET, WORLD, ATTACK
    }
}
