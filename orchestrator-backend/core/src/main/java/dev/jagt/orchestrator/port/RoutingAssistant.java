package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.RulePair;

import java.util.List;

/** Which repository a work item belongs in, and what the written routing rules say. Every call is booked. */
public interface RoutingAssistant {

    /**
     * The answer is one of the question's project keys or {@code "none"}; anything else is a read that failed in
     * the shape of an answer.
     */
    Answer<RoutingAnswer> routeProject(RoutingQuestion question);

    /**
     * Which of {@code rules} the placing of {@code ticketKey} in {@code project} has just contradicted — asked
     * only where a human placed by hand what a written rule had placed elsewhere. One of those rules, or
     * {@code "none"}, which is the usual answer.
     */
    Answer<String> staleRule(String ticketKey, String project, List<String> rules);

    /** Two of {@code rules} that place the same items in other words, or {@code "none"} for both. */
    Answer<RulePair> sameRules(List<String> rules);
}
