package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NewTaskMessageTest {

  @Test
  void refusesALaunchWithNothingToNameTheBranchOrTheRepositoryBy() {
    var said =
        new NewTaskMessage(
            null,
            new NewTaskMessage.Placement(null, List.of(), null, null),
            null,
            null,
            null,
            null,
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactlyInAnyOrder("taskId", "projectKey");
  }

  @Test
  void refusesABranchStrategyThisMachineDoesNotHave() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of(), null, "clobber"),
            null,
            null,
            null,
            null,
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("branchStrategy");
  }

  @Test
  void refusesATicketLinkNobodyCanOpen() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of(), null, null),
            null,
            null,
            "jira://ABC-42",
            null,
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("ticketUrl");
  }

  @Test
  void refusesTheSessionsOwnRepositoryRepeatedAmongTheFurtherOnes() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of("api", "web"), null, null),
            null,
            null,
            null,
            null,
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("alsoProjects");
  }

  @Test
  void acceptsTheOrdinaryLaunchWithNothingButATaskAProjectAndAReason() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of(), null, null),
            null,
            null,
            null,
            null,
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void acceptsALaunchNamingEveryOptionInAnyCase() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of("web"), "release", " Recreate "),
            "do it",
            "Widget layout is off",
            "https://tracker/ABC-42",
            " PLAN ",
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesASessionModeThisMachineDoesNotHave() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of(), null, null),
            null,
            null,
            null,
            "turbo",
            "user asked for the widget fix");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("mode");
  }

  @Test
  void readsBlankFieldsAsLeftOutSoALaunchOfBlanksIsRefusedForTheThreeItNeeds() {
    var said =
        new NewTaskMessage(
            " ", new NewTaskMessage.Placement(" ", null, " ", " "), " ", " ", " ", " ", " ");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactlyInAnyOrder("taskId", "projectKey", "why");
  }

  @Test
  void refusesALaunchGivingNoReason() {
    var said =
        new NewTaskMessage(
            "ABC-42",
            new NewTaskMessage.Placement("api", List.of(), null, null),
            null,
            null,
            null,
            null,
            null);

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("why");
  }

  @Test
  void readsTheFlatLaunchJsonAndWritesItBackEqual() throws Exception {
    var mapper = JsonMapper.builder().build();
    var flat =
        """
        {"taskId":"ABC-42","projectKey":"api","alsoProjects":["web"],"instructions":"do it",
         "title":"Widget layout is off","ticketUrl":"https://tracker/ABC-42","mode":"plan",
         "branchStrategy":"recreate","baseBranch":"release","why":"user asked for the widget fix"}\
        """;

    var said = mapper.readValue(flat, NewTaskMessage.class);

    assertThat(mapper.readTree(mapper.writeValueAsString(said))).isEqualTo(mapper.readTree(flat));
  }
}
