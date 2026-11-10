package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionHooksTest {

  @Test
  void mapsNothingForRuntimeThatDeclaresNoEvents() {
    assertThat(SessionHooks.of("a-cli-that-ships-no-hooks")).isEmpty();
  }

  @Test
  void declaresTheEventPermissionPromptOpensOnWithoutMappingItToState() {
    assertThat(SessionHooks.permissionRequest("claude")).contains("PermissionRequest");
    assertThat(SessionHooks.of("claude")).doesNotContainKey("PermissionRequest");
  }

  @Test
  void declaresOnlyStatesTheOrchestratorAnswersTo() {
    assertThat(SessionHooks.of("claude").values()).isSubsetOf("waiting", "gone", "idle", "working");
  }

  @Test
  void namesTheErrorWordThatMeansUsageLimit() {
    assertThat(SessionHooks.rateLimitError("claude")).isEqualTo("rate_limit");
  }

  @Test
  void namesTheEndReasonsNewStartFollowsAtOnce() {
    assertThat(SessionHooks.continuingEnds("claude")).containsExactlyInAnyOrder("clear", "resume");
  }

  @Test
  void namesTheStartThatFollowsCompaction() {
    assertThat(SessionHooks.compactedStart("claude")).isEqualTo("compact");
  }

  @Test
  void saysNothingForRuntimeThatShipsNoHooks() {
    assertThat(SessionHooks.rateLimitError("a-cli-that-ships-no-hooks")).isEmpty();
    assertThat(SessionHooks.continuingEnds("a-cli-that-ships-no-hooks")).isEmpty();
    assertThat(SessionHooks.compactedStart("a-cli-that-ships-no-hooks")).isEmpty();
  }

  @Test
  void declaresTheEventPromptIsAnsweredOnWithoutMappingItToState() {
    assertThat(SessionHooks.promptGate("claude")).contains("UserPromptSubmit");
    assertThat(SessionHooks.of("claude")).doesNotContainKey("UserPromptSubmit");
  }

  @Test
  void declaresNoTrustDialogPhraseForRuntimeThatShipsNoHooks() {
    assertThat(SessionHooks.trustDialog("a-cli-that-ships-no-hooks")).isEmpty();
  }
}
