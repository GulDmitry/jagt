package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandMode;
import java.util.Arrays;

public record Mutation(String name, Kind kind, String ask, String event, String holds,
                       String expects, String relaysIn) {

    public enum Kind {
        TICKET, WORLD, ATTACK
    }

    public boolean relaysIn(StandMode mode) {
        return Arrays.stream(relaysIn.split(",")).map(String::strip).anyMatch(mode.id()::equals);
    }
}
