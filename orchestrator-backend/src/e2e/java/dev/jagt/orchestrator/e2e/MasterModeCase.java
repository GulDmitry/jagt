package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.List;
import java.util.stream.Stream;

record MasterModeCase(String name, String mode, List<String> mine, String verdict, TaskStatus expected,
                      boolean read, String told) {

    @Override
    public String toString() {
        return name;
    }

    static Stream<MasterModeCase> rounds() {
        return Stream.of(
                new MasterModeCase("act ships a ready round", "act", List.of(), "ready", TaskStatus.SHIPPING, true,
                        "reviewRequestUrl=<the url>"),
                new MasterModeCase("act with mine: [ship] leaves the ship yours", "act", List.of("ship"), "ready",
                        TaskStatus.REVIEW_PENDING, true, "Fix the widget"),
                new MasterModeCase("off reads nothing", "off", List.of(), "ready", TaskStatus.REVIEW_PENDING, false,
                        "Fix the widget"));
    }

    static Stream<MasterModeCase> plans() {
        return Stream.of(
                new MasterModeCase("act starts a plan that holds", "act", List.of(), "ready", TaskStatus.IN_PROGRESS,
                        true, "the plan holds"),
                new MasterModeCase("act returns a plan that misses the ticket", "act", List.of(), "not ready",
                        TaskStatus.IN_PROGRESS, true, "Rework the plan"),
                new MasterModeCase("judge holds a plan for the human", "judge", List.of(), "ready",
                        TaskStatus.PLAN_PENDING, true, "Fix the widget"),
                new MasterModeCase("act with mine: [plan] holds it for the human", "act", List.of("plan"), "ready",
                        TaskStatus.PLAN_PENDING, true, "Fix the widget"),
                new MasterModeCase("off reads no plan", "off", List.of(), "ready", TaskStatus.PLAN_PENDING, false,
                        "Fix the widget"));
    }

    static Stream<MasterModeCase> questions() {
        return Stream.of(
                new MasterModeCase("act answers the session", "act", List.of(), "", TaskStatus.IN_PROGRESS, true,
                        "The Master answered your question"),
                new MasterModeCase("act with mine: [answer] leaves it for the human", "act", List.of("answer"), "",
                        TaskStatus.IN_PROGRESS, false, "Fix the widget"));
    }

    static Stream<MasterModeCase> deploys() {
        return Stream.of(
                new MasterModeCase("act deploys a green request", "act", List.of(), "ready", TaskStatus.DEPLOYED, true,
                        ""),
                new MasterModeCase("act with mine: [deploy, revert] leaves the press yours", "act",
                        List.of("deploy", "revert"), "ready", TaskStatus.REVIEWED, true, ""));
    }
}
