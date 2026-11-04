package dev.jagt.orchestrator.stand.mutation;

public interface TicketRewrite {

    String rewrite(String ask, String ticket);

    static String prompt(String ask, String ticket) {
        return ask + "\n\nAnswer with the rewritten ticket description alone, no preamble.\n\nTicket:\n" + ticket;
    }
}
