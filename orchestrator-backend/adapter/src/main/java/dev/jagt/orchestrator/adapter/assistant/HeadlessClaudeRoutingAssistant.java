package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoutingAssistant;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.protocol.RuleRead;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.RulePair;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaude.FAILURE_RULE;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeRoutingAssistant implements RoutingAssistant {

    private final HeadlessClaude claude;

    @Override
    public Answer<RoutingAnswer> routeProject(RoutingQuestion question) {
        if (question == null || !question.answerable()) {
            return Answer.unavailable();
        }
        TicketFacts item = question.item();
        String prompt = "<role>You place one work item in the repository whose code has to change.</role>\n"
                + "<task>The item is \"" + item.key() + " — " + item.title() + "\". OPEN it with the"
                + " matching tracker MCP tool and read it: its description, its components, its epic,"
                + " whatever says which system changes. Its board says nothing about which repository that"
                + " is.</task>\n"
                + "<rules>The repositories configured here, each with what it is:\n"
                + listed(question.projects())
                + "\nIts labels " + item.labels() + " matched " + question.suggested() + ". That is a"
                + " SUGGESTION and nothing more — whoever filed the item wrote those labels, and a label"
                + " naming a layer rather than a system places nothing. Confirm it against what the item"
                + " actually asks for, and answer a different key where the item says otherwise.\n"
                + written(question.rules())
                + precedents(question.precedents())
                + "Answer project with the ONE key whose repository the work belongs in, or "
                + ProjectRead.NONE + " where the item does not say clearly enough to be sure — a wrong"
                + " repository costs more than a human being asked. reason is one line.\n"
                + "Answer rule with the short phrase that would place the NEXT item like this one, and ONLY"
                + " where placing this one took something no label carried — leave it EMPTY otherwise, an"
                + " obvious placement being worth nothing to write down. Where a rule above already covers"
                + " this case, answer ITS phrase character for character rather than a rewording: two"
                + " wordings of one rule are two rules, and this file is read whole every time."
                + FAILURE_RULE
                + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, ProjectRead.schemaFor(question.projects().keySet()).json(), item.key(),
                        AssistantCallKind.ROUTE)
                .map(n -> new RoutingAnswer(n.path("project").asString(""), n.path("rule").asString("")));
    }

    private static String listed(Map<String, String> projects) {
        return projects.entrySet().stream().map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** What the install wrote down itself, which outranks a pattern because a human meant it. */
    private static String written(List<String> rules) {
        return rules.isEmpty() ? ""
                : "Rules this install has written, which outrank everything above:\n"
                        + String.join("\n", rules) + "\n";
    }

    /** Where items like this one were actually worked on, which is the only evidence here nobody wrote down. */
    private static String precedents(List<String> precedents) {
        return precedents.isEmpty() ? ""
                : "Items finished before, and the repository each was done in:\n"
                        + String.join("\n", precedents) + "\n";
    }

    @Override
    public Answer<String> staleRule(String ticketKey, String project, List<String> rules) {
        if (ticketKey == null || project == null || rules == null || rules.isEmpty()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You find the written rule a person has just contradicted.</role>\n"
                + "<task>A person placed the work item " + ticketKey + " in the repository '" + project
                + "' by hand. Read that item with the matching tracker MCP tool.</task>\n"
                + "<rules>These rules are written down here:\n" + String.join("\n", rules) + "\n"
                + "Answer rule with the ONE of them that would have sent this item somewhere else, or "
                + RuleRead.NONE + " where none of them covers it — which is the usual answer, and retiring a"
                + " rule that was never wrong costs more than leaving it. reason is one line."
                + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, RuleRead.schemaFor(rules).json(), ticketKey, AssistantCallKind.ROUTE)
                .map(n -> n.path("rule").asString(""));
    }

    @Override
    public Answer<RulePair> sameRules(List<String> rules) {
        if (rules == null || rules.size() < 2) {
            return Answer.unavailable();
        }
        String prompt = "<role>You find two written routing rules that say the same thing.</role>\n"
                + "<rules>These rules place work items in repositories:\n" + String.join("\n", rules) + "\n"
                + "Answer kept and duplicate with two of them that would place the same items in the same"
                + " repository, worded differently. kept is the clearer wording. Answer " + RuleRead.NONE
                + " for both where no two do, which is the usual answer." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, RuleRead.pairSchemaFor(rules).json(), "routing memory", AssistantCallKind.ROUTE)
                .map(n -> new RulePair(n.path("kept").asString(""), n.path("duplicate").asString("")));
    }
}
