package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionHooksTest {

  @Test
  void mapsNothingForARuntimeThatDeclaresNoEvents() {
    assertThat(SessionHooks.of("a-cli-that-ships-no-hooks")).isEmpty();
  }

  @Test
  void declaresTheEventAPermissionPromptOpensOnWithoutMappingItToAState() {
    assertThat(SessionHooks.permissionRequest("claude")).contains("PermissionRequest");
    assertThat(SessionHooks.of("claude")).doesNotContainKey("PermissionRequest");
  }

  @Test
  void declaresOnlyStatesTheOrchestratorAnswersTo() {
    assertThat(SessionHooks.of("claude").values()).isSubsetOf("waiting", "gone", "idle", "working");
  }

  @Test
  void namesTheErrorWordThatMeansAUsageLimit() {
    assertThat(SessionHooks.rateLimitError("claude")).isEqualTo("rate_limit");
  }

  @Test
  void namesTheEndReasonsANewStartFollowsAtOnce() {
    assertThat(SessionHooks.continuingEnds("claude")).containsExactlyInAnyOrder("clear", "resume");
  }

  @Test
  void namesTheStartThatFollowsACompaction() {
    assertThat(SessionHooks.compactedStart("claude")).isEqualTo("compact");
  }

  @Test
  void saysNothingForARuntimeThatShipsNoHooks() {
    assertThat(SessionHooks.rateLimitError("a-cli-that-ships-no-hooks")).isEmpty();
    assertThat(SessionHooks.continuingEnds("a-cli-that-ships-no-hooks")).isEmpty();
    assertThat(SessionHooks.compactedStart("a-cli-that-ships-no-hooks")).isEmpty();
  }

  @Test
  void declaresTheEventAPromptIsAnsweredOnWithoutMappingItToAState() {
    assertThat(SessionHooks.promptGate("claude")).contains("UserPromptSubmit");
    assertThat(SessionHooks.of("claude")).doesNotContainKey("UserPromptSubmit");
  }

  @Test
  void declaresNoTrustDialogPhraseForARuntimeThatShipsNoHooks() {
    assertThat(SessionHooks.trustDialog("a-cli-that-ships-no-hooks")).isEmpty();
  }
}
