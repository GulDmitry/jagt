package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AgentStatusMessageTest {

  @Test
  void refusesAnOutcomeItDoesNotNameInsteadOfReadingTheMessageInstead() {
    var said = AgentStatusMessage.of("IN_PROGRESS", "still going").withOutcome("done");

    assertThat(said.reportViolations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("outcome");
  }

  @Test
  void refusesAStatusThisMachineDoesNotHave() {
    var said = AgentStatusMessage.of("ALMOST_DONE", "still going");

    assertThat(said.reportViolations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("status");
  }

  @Test
  void refusesAStatusOnlyJagtSetsNamingOnlyTheReportableOnes() {
    var said = AgentStatusMessage.of("DEPLOYED", "landed");

    assertThat(said.reportViolations(MessageContext.NONE))
        .singleElement()
        .satisfies(
            violation ->
                assertThat(violation.expected())
                    .isEqualTo(
                        "one of [PLAN_PENDING, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING,"
                            + " CI_FAILED]"));
  }

  @Test
  void refusesARoundThatIsOutForReviewWithNoRequestAnywhereInIt() {
    var said = AgentStatusMessage.of("CI_POLLING", "shipped it");

    assertThat(said.reportViolations(MessageContext.NONE))
        .extracting(Violation::expected)
        .allSatisfy(expected -> assertThat(expected).contains("required with CI_POLLING"));
  }

  @Test
  void acceptsARoundWhoseRequestWasWrittenIntoTheMessage() {
    var said = AgentStatusMessage.of("CI_POLLING", "review request: https://host/mr/9");

    assertThat(said.reportViolations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesBothLinkFormsAtOnceBecauseNeitherWinsWithoutGuessing() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(
                new AgentStatusMessage.Request(
                    "https://host/mr/9", Map.of("api", "https://host/api/mr/1")));

    assertThat(
            said.reportViolations(new MessageContext(List.of("api"), false, java.util.List.of())))
        .extracting(Violation::field)
        .containsExactly("reviewRequests");
  }

  @Test
  void refusesALinkNobodyCanOpen() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(new AgentStatusMessage.Request("git@host:proj.git", null));

    assertThat(said.reportViolations(MessageContext.NONE))
        .extracting(Violation::field)
        .contains("reviewRequestUrl");
  }

  @Test
  void refusesARequestFiledUnderAProjectTheTaskDoesNotHave() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(new AgentStatusMessage.Request(null, Map.of("nope", "https://host/mr/9")));

    assertThat(
            said.reportViolations(
                new MessageContext(List.of("api", "web"), false, java.util.List.of())))
        .extracting(Violation::expected)
        .allSatisfy(expected -> assertThat(expected).contains("not on this task"));
  }

  @Test
  void namesEveryBrokenRuleAtOnceSoASenderFixesThemInOneGo() {
    var said =
        new AgentStatusMessage("ALMOST_DONE", "shipped", "done", "git@host:proj.git", Map.of());

    assertThat(said.reportViolations(MessageContext.NONE)).hasSize(3);
  }

  @Test
  void readsTheSessionsOwnRepositoryAsTheLinkAHumanFollowsFirst() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(
                new AgentStatusMessage.Request(
                    null, Map.of("api", "https://host/api/mr/1", "web", "https://host/web/mr/2")));

    assertThat(
            said.accepted(new MessageContext(List.of("web", "api"), false, java.util.List.of()))
                .orElseThrow()
                .link())
        .isEqualTo("https://host/web/mr/2");
  }

  @Test
  void readsAnOutcomeTypedIntoTheMessageAsWordsWhenNoOutcomeFieldWasSent() {
    var said = AgentStatusMessage.of("REVIEW_PENDING", "outcome=question: which branch?");

    Reported accepted = said.accepted(MessageContext.NONE).orElseThrow();
    assertThat(accepted.claimed()).isEqualTo(AgentReport.PLAIN);
    assertThat(accepted.detail()).isEqualTo("outcome=question: which branch?");
  }

  @Test
  void handsBackTheStatusAndTheHumansHalfOfTheMessageSeparately() {
    var said = AgentStatusMessage.of("REVIEW_PENDING", "all handled").withOutcome("no_changes");

    Reported accepted = said.accepted(MessageContext.NONE).orElseThrow();
    assertThat(accepted.status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    assertThat(accepted.detail()).isEqualTo("all handled");
  }

  @Test
  void refusesEveryRequestUnderAProjectTheTaskDoesNotHaveByNamingIt() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(new AgentStatusMessage.Request(null, Map.of("nope", "https://host/mr/9")));

    assertThat(
            said.reportViolations(
                new MessageContext(List.of("api", "web"), false, java.util.List.of())))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("reviewRequests"))
        .satisfies(
            violation ->
                assertThat(violation.expected())
                    .isEqualTo("project 'nope' is not on this task; its projects are [api, web]"));
  }

  @Test
  void acceptsARequestFiledUnderAProjectOfTheTask() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(new AgentStatusMessage.Request(null, Map.of("web", "https://host/mr/9")));

    assertThat(
            said.reportViolations(
                new MessageContext(List.of("api", "web"), false, java.util.List.of())))
        .isEmpty();
  }

  @Test
  void leavesTheProjectsAloneWhenTheTaskIsNotKnown() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(new AgentStatusMessage.Request(null, Map.of("nope", "https://host/mr/9")));

    assertThat(said.reportViolations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void readsTheLinkFromTheFieldTheMapAndTheMessageInThatOrder() {
    var field =
        AgentStatusMessage.of("CI_POLLING", "see https://host/other")
            .withRequest(new AgentStatusMessage.Request("  https://host/mr/1 ", null));
    var message = AgentStatusMessage.of("CI_POLLING", "request https://host/mr/2 opened");
    var neither = AgentStatusMessage.of("REVIEW_PENDING", "all handled");

    assertThat(field.accepted(MessageContext.NONE).orElseThrow().link())
        .isEqualTo("https://host/mr/1");
    assertThat(message.accepted(MessageContext.NONE).orElseThrow().link())
        .isEqualTo("https://host/mr/2");
    assertThat(neither.accepted(MessageContext.NONE).orElseThrow().link()).isNull();
  }

  @Test
  void readsTheOnlyRequestAsTheLinkWhenTheSessionsOwnRepositoryHasNone() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(
                new AgentStatusMessage.Request(null, Map.of("web", "https://host/web/mr/2")));

    assertThat(
            said.accepted(new MessageContext(List.of("api", "web"), false, java.util.List.of()))
                .orElseThrow()
                .link())
        .isEqualTo("https://host/web/mr/2");
  }

  @Test
  void readsTheOnlyRequestAsTheLinkWhenTheTaskIsNotKnown() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(
                new AgentStatusMessage.Request(null, Map.of("web", "https://host/web/mr/2")));

    assertThat(said.accepted(MessageContext.NONE).orElseThrow().link())
        .isEqualTo("https://host/web/mr/2");
  }

  @Test
  void keepsEveryRequestTheReportNamedForTheRoundToRecord() {
    var said =
        AgentStatusMessage.of("CI_POLLING", "shipped")
            .withRequest(
                new AgentStatusMessage.Request(
                    null, Map.of("api", "https://host/api/mr/1", "web", "https://host/web/mr/2")));

    assertThat(
            said.accepted(new MessageContext(List.of("api", "web"), false, java.util.List.of()))
                .orElseThrow()
                .requests())
        .containsOnlyKeys("api", "web");
  }

  @Test
  void takesWhatTheOutcomeFieldClaimsOverWhatTheMessageOpensWith() {
    var question = AgentStatusMessage.of("REVIEW_PENDING", "which one?").withOutcome(" Question ");
    var none = AgentStatusMessage.of("REVIEW_PENDING", "all handled").withOutcome("no-changes");
    var progress =
        AgentStatusMessage.of("REVIEW_PENDING", "outcome=question: x").withOutcome("progress");

    assertThat(question.accepted(MessageContext.NONE).orElseThrow().claimed())
        .isEqualTo(AgentReport.QUESTION);
    assertThat(none.accepted(MessageContext.NONE).orElseThrow().claimed())
        .isEqualTo(AgentReport.NO_CHANGES);
    assertThat(progress.accepted(MessageContext.NONE).orElseThrow().claimed())
        .isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void acceptsNothingThatBrokeARule() {
    assertThat(AgentStatusMessage.of("ALMOST_DONE", "x").accepted(MessageContext.NONE)).isEmpty();
  }

  @Test
  void acceptsNoPlanFromAnAutoplanTaskAndReportsIt() {
    var said = new AgentStatusMessage("IN_PROGRESS", "touches one file", "no_plan", null, Map.of());

    assertThat(
            said.reportViolations(new MessageContext(List.of("api"), false, java.util.List.of())))
        .isEmpty();
    assertThat(said.accepted(MessageContext.NONE).orElseThrow().noPlan()).isTrue();
  }

  @Test
  void refusesNoPlanForATaskOpenedInPlanMode() {
    var said = new AgentStatusMessage("IN_PROGRESS", "touches one file", "no_plan", null, Map.of());

    assertThat(said.reportViolations(new MessageContext(List.of("api"), true, java.util.List.of())))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("outcome"));
  }

  @Test
  void refusesNoPlanFromAnyStatusButInProgress() {
    var said =
        new AgentStatusMessage("REVIEW_PENDING", "touches one file", "no_plan", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("status"));
  }

  @Test
  void carriesTheProjectAQuestionNeedsWhenItIsConfiguredAndNotOnTheTask() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "needs the registry",
            "question",
            new AgentStatusMessage.Request(null, null),
            null,
            "registry",
            null,
            Map.of());
    var context = new MessageContext(List.of("api"), false, List.of("api", "registry"));

    assertThat(said.reportViolations(context)).isEmpty();
    assertThat(said.accepted(context).orElseThrow().needsProject()).isEqualTo("registry");
  }

  @Test
  void refusesAProjectNoConfigurationHasNamingTheOnesThereAre() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "needs it",
            "question",
            new AgentStatusMessage.Request(null, null),
            null,
            "ghost",
            null,
            Map.of());
    var context = new MessageContext(List.of("api"), false, List.of("api", "registry"));

    assertThat(said.reportViolations(context))
        .singleElement()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("needsProject");
              assertThat(violation.expected())
                  .contains("not configured")
                  .contains("[api, registry]");
            });
  }

  @Test
  void refusesAProjectTheTaskAlreadyHoldsNamingItsProjects() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "needs it",
            "question",
            new AgentStatusMessage.Request(null, null),
            null,
            "api",
            null,
            Map.of());
    var context = new MessageContext(List.of("api"), false, List.of("api", "registry"));

    assertThat(said.reportViolations(context))
        .singleElement()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("needsProject");
              assertThat(violation.expected()).contains("already on this task").contains("[api]");
            });
  }

  @Test
  void refusesAProjectNeededOutsideAQuestion() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            "progress",
            new AgentStatusMessage.Request(null, null),
            null,
            "registry",
            null,
            Map.of());

    assertThat(
            said.reportViolations(
                new MessageContext(List.of("api"), false, List.of("api", "registry"))))
        .singleElement()
        .satisfies(
            violation -> assertThat(violation.expected()).isEqualTo("only with outcome=question"));
  }

  @Test
  void refusesAReportGivingNoReason() {
    var said = AgentStatusMessage.of("IN_PROGRESS", "working");

    assertThat(said.violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("why");
  }

  @Test
  void refusesAReasonLongerThanOneSentenceNamingTheLimit() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "the human asked for the billing repository because the invoice export reads it, and"
                + " the change crosses both sides of the contract",
            Map.of());

    assertThat(said.violations(MessageContext.NONE))
        .singleElement()
        .satisfies(
            violation -> assertThat(violation).hasToString("why: at most 120 characters, not 129"));
  }

  @Test
  void acceptsAReportWithItsReason() {
    var said =
        new AgentStatusMessage(
            "REVIEW_PENDING",
            "done",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "work done, tests green, ready for review",
            Map.of());

    assertThat(said.violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesTheFirstReportThatLeavesOutASwitchThatIsOn() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "starting work",
            Map.of());

    assertThat(
            said.violations(
                MessageContext.NONE.withSwitchesOn(List.of("visualize")).withFirstReport(true)))
        .extracting(Violation::field)
        .containsExactly("switches");
  }

  @Test
  void refusesAPlanHandBackThatLeavesOutASwitchThatIsOn() {
    var said =
        new AgentStatusMessage(
            "PLAN_PENDING",
            "plan ready",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "plan written for approval",
            Map.of());

    assertThat(said.violations(MessageContext.NONE.withSwitchesOn(List.of("visualize"))))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("switches"))
        .satisfies(violation -> assertThat(violation.expected()).contains("visualize"));
  }

  @Test
  void refusesAPlanHandBackJudgingVisualizeYesWhileThePictureIsMissing() {
    var said =
        new AgentStatusMessage(
            "PLAN_PENDING",
            "plan ready",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "plan written for approval",
            Map.of("visualize", "yes — a new table"));

    assertThat(
            said.violations(
                MessageContext.NONE.withSwitchesOn(List.of("visualize")).withVisualMissing(true)))
        .singleElement()
        .satisfies(violation -> assertThat(violation.expected()).contains("task_visual.md"));
  }

  @Test
  void acceptsAPlanHandBackJudgingVisualizeYesOnceThePictureIsThere() {
    var said =
        new AgentStatusMessage(
            "PLAN_PENDING",
            "plan ready",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "plan written for approval",
            Map.of("visualize", "yes — a new table"));

    assertThat(said.violations(MessageContext.NONE.withSwitchesOn(List.of("visualize")))).isEmpty();
  }

  @Test
  void acceptsAPlanHandBackJudgingVisualizeNoWhileThePictureIsMissing() {
    var said =
        new AgentStatusMessage(
            "PLAN_PENDING",
            "plan ready",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "plan written for approval",
            Map.of("visualize", "no — a two-line fix"));

    assertThat(
            said.violations(
                MessageContext.NONE.withSwitchesOn(List.of("visualize")).withVisualMissing(true)))
        .isEmpty();
  }

  @Test
  void owesNoSwitchOnALaterReportThatHandsBackNoPlan() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "back to work",
            Map.of());

    assertThat(said.violations(MessageContext.NONE.withSwitchesOn(List.of("visualize")))).isEmpty();
  }

  @Test
  void acceptsTheFirstReportWhenNoSwitchIsOwed() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "starting work",
            Map.of());

    assertThat(said.violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void carriesTheJudgementsOfAnAcceptedReport() {
    var said =
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, null),
            null,
            null,
            "starting work",
            Map.of("visualize", "no — config-only change"));

    assertThat(said.accepted(MessageContext.NONE).orElseThrow().switches())
        .containsExactly(Map.entry("visualize", "no — config-only change"));
  }

  @Test
  void acceptsAWaitFromAWorkingSessionNamingWhatIsDown() {
    var said =
        new AgentStatusMessage("IN_PROGRESS", "db down: ping db.internal", "wait", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE)).isEmpty();
    assertThat(said.accepted(MessageContext.NONE).orElseThrow().claimed())
        .isEqualTo(AgentReport.WAIT);
  }

  @Test
  void refusesAWaitThatNamesNothing() {
    var said = new AgentStatusMessage("IN_PROGRESS", null, "wait", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("message"));
  }

  @Test
  void refusesAQuestionThatAsksNothing() {
    var said = new AgentStatusMessage("REVIEW_PENDING", null, "question", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("message"));
  }

  @Test
  void refusesAWaitFromAnyStatusButInProgress() {
    var said =
        new AgentStatusMessage(
            "REVIEW_PENDING", "db down: ping db.internal", "wait", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("status"));
  }

  @Test
  void refusesAWaitWhereNothingWaits() {
    var said =
        new AgentStatusMessage("IN_PROGRESS", "db down: ping db.internal", "wait", null, Map.of());

    assertThat(said.reportViolations(MessageContext.NONE.refusingWaits()))
        .singleElement()
        .satisfies(violation -> assertThat(violation.field()).isEqualTo("outcome"));
  }

  @Test
  void readsTheFlatReportJsonAndWritesItBackEqual() throws Exception {
    var mapper = JsonMapper.builder().build();
    var flat =
        """
        {"status":"CI_POLLING","message":"shipped","outcome":"progress",
         "reviewRequestUrl":"https://host/mr/9","reviewRequests":{"api":"https://host/api/mr/1"},
         "taskId":"ABC-9","needsProject":"web","why":"request opened","switches":{"visualize":"no"}}\
        """;

    var said = mapper.readValue(flat, AgentStatusMessage.class);

    assertThat(mapper.readTree(mapper.writeValueAsString(said))).isEqualTo(mapper.readTree(flat));
  }
}
