package dev.jagt.orchestrator.stand.mutation;

public record Mutation(String name, Kind kind, String ask, String event, String holds,
                       String expects) {

    public enum Kind {
        TICKET, WORLD, ATTACK
    }
}
