package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CodeHostAssistant;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.port.assistant.RoutingAssistant;
import dev.jagt.orchestrator.port.assistant.TrackerAssistant;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Headless models that answer nothing, but for a reviewer that finds a round ready. */
public final class FakeAssistants {

  private final List<String> reviewed = new CopyOnWriteArrayList<>();

  public List<String> reviewed() {
    return List.copyOf(reviewed);
  }

  public Assistants assistants() {
    return new Assistants(
        new CodeHostAssistant() {
          @Override
          public Answer<dev.jagt.orchestrator.task.review.MergeRequestFacts> readMergeRequest(
              String mrUrl) {
            return Answer.unavailable();
          }

          @Override
          public Answer<dev.jagt.orchestrator.task.review.ReviewFacts> readReview(String mrUrl) {
            return Answer.unavailable();
          }
        },
        new TrackerAssistant() {
          @Override
          public Answer<dev.jagt.orchestrator.task.ticket.TicketFacts> readTicket(
              String ticketRef, List<String> corrections) {
            return Answer.unavailable();
          }

          @Override
          public Answer<String> readTicketText(String ticketRef) {
            return Answer.unavailable();
          }

          @Override
          public Answer<List<String>> findCandidates(String query) {
            return Answer.unavailable();
          }
        },
        new RoutingAssistant() {
          @Override
          public Answer<dev.jagt.orchestrator.task.launch.RoutingAnswer> routeProject(
              dev.jagt.orchestrator.task.launch.RoutingQuestion question) {
            return Answer.unavailable();
          }

          @Override
          public Answer<String> staleRule(String ticketKey, String project, List<String> rules) {
            return Answer.unavailable();
          }

          @Override
          public Answer<dev.jagt.orchestrator.task.launch.RulePair> sameRules(List<String> rules) {
            return Answer.unavailable();
          }
        },
        new RoundReviewer() {
          @Override
          public Answer<Judgement> review(Round round) {
            reviewed.add(round.worktrees().getFirst().getFileName().toString());
            return new Answer<>(
                Optional.of(
                    new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE)),
                TokenUsage.NONE);
          }

          @Override
          public boolean loadsMcpServer() {
            return false;
          }
        });
  }
}
