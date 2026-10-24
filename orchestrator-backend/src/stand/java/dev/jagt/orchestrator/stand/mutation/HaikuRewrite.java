package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.stand.StandShell;
import java.nio.file.Path;
import java.util.List;

public record HaikuRewrite(Path directory) implements TicketRewrite {

    @Override
    public String rewrite(String ask, String ticket) {
        String prompt = ask + "\n\nAnswer with the rewritten ticket description alone, no preamble.\n\nTicket:\n" + ticket;
        StandShell.Result result = StandShell.run(directory, List.of("claude", "-p", "--model", "haiku", prompt));
        if (result.exit() != 0 || result.output().isBlank()) {
            throw new StandFailure("the ticket rewrite failed with exit " + result.exit() + ": " + result.output());
        }
        return result.output().strip();
    }
}
