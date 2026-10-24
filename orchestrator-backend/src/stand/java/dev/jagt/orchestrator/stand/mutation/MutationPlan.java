package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.stand.WorldEvent;
import java.util.List;

public final class MutationPlan {

    public record Applied(String ticket, List<WorldEvent> events) {
    }

    private final TicketRewrite rewrite;

    public MutationPlan(TicketRewrite rewrite) {
        this.rewrite = rewrite;
    }

    public Applied apply(Mutation mutation, String ticket) {
        return switch (mutation.kind()) {
            case TICKET -> new Applied(rewrite.rewrite(mutation.ask(), ticket), List.of());
            case WORLD -> new Applied(ticket, List.of(world(mutation.name())));
            case ATTACK -> mutation.ask().isEmpty()
                    ? new Applied(ticket, List.of(WorldEvent.reviewerThread(mutation.event())))
                    : new Applied(ticket + "\n\n" + mutation.ask(), List.of());
        };
    }

    private static WorldEvent world(String name) {
        return switch (name) {
            case "wrong-review-thread" -> WorldEvent.reviewerThread(
                    "Make ./greet.sh print \"Hi, World\" without an argument; \"Hello, World\" reads too stiff.");
            case "red-then-green" -> WorldEvent.redThenGreen();
            case "deploy-conflict" -> WorldEvent.deployBranchCommit("greet.sh", "#!/bin/sh\necho \"Hello there, $1\"\n",
                    "Greet more warmly");
            case "human-types-mid-turn" -> WorldEvent.humanLine("Keep the change to greet.sh as small as you can.");
            case "restart-mid-turn" -> WorldEvent.restart();
            default -> throw new StandFailure("no stand event plays the world mutation " + name);
        };
    }
}
