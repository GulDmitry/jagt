package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketRewriteTest {

  @Test
  void thePromptCarriesTheAskThenTheTicket() {
    String prompt = TicketRewrite.prompt("be terse", "ticket body");

    assertThat(prompt).startsWith("be terse").endsWith("Ticket:\nticket body");
  }
}
