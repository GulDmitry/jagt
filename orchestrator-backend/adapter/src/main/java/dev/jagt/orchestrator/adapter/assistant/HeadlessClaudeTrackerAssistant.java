package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.protocol.TicketSearch;
import dev.jagt.orchestrator.protocol.TicketText;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaude.FAILURE_RULE;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeTrackerAssistant implements TrackerAssistant {

    private final HeadlessClaude claude;

    @Override
    public Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You read one work item from whichever tracker holds it.</role>\n"
                + "<task>Read the work item identified by \"" + ticketRef + "\" — this is EITHER an issue"
                + " key (e.g. ABC-123) OR a URL to it in some tracker (Jira, GitHub, GitLab, …). Open it"
                + " with the matching MCP tool: if it is a URL, follow the URL — do NOT try to parse a key"
                + " out of it.</task>\n"
                + "<rules>Return exists=true with its canonical issue key as key, its summary as"
                + " title, its project key as trackerProject, its labels, its workflow status as"
                + " trackerStatus and the login or display name it is assigned to as assignee — empty where"
                + " nobody holds it — and its canonical web URL as url"
                + " — the link the item itself reports, never one you assemble. Where the item carries no"
                + " summary of its own, WRITE the title yourself: at most eight words naming what the item"
                + " asks for, from its description. Never answer exists=true with an empty title or an"
                + " empty url." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt + correcting(corrections), TicketRead.SCHEMA.json(), ticketRef,
                AssistantCallKind.TICKET_READ).map(n -> {
            List<String> labels = new ArrayList<>();
            n.path("labels").forEach(l -> labels.add(l.asString("")));
            return TicketFacts.defaults()
                    .withExists(n.path("exists").asBoolean(false))
                    .withKey(n.path("key").asString(""))
                    .withTitle(n.path("title").asString(""))
                    .withTrackerProject(n.path("trackerProject").asString(""))
                    .withLabels(labels)
                    .withUrl(n.path("url").asString(""))
                    .withTrackerStatus(n.path("trackerStatus").asString(""))
                    .withAssignee(n.path("assignee").asString(""));
        });
    }

    @Override
    public Answer<String> readTicketText(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You copy one work item out of whichever tracker holds it.</role>\n"
                + "<task>Read the work item \"" + ticketRef + "\" with the matching MCP tool; if it is a URL,"
                + " follow it.</task>\n"
                + "<rules>Answer text with its summary, description, acceptance criteria and every comment, each"
                + " under its own heading, VERBATIM: never summarise, shorten or add." + FAILURE_RULE
                + "</rules>\n"
                + "Respond directly, no preamble.";
        Answer<String> read = claude.read(prompt, TicketText.SCHEMA.json(), ticketRef,
                AssistantCallKind.MASTER_REVIEW).map(n -> n.path("text").asString("").strip());
        return new Answer<>(read.facts().filter(text -> !text.isEmpty()), read.usage());
    }

    @Override
    public Answer<List<String>> findCandidates(String query) {
        if (query == null || query.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You list work items from whichever tracker holds them.</role>\n"
                + "<task>Find every work item matching: " + query + ". Use the matching tracker MCP tool's own"
                + " search.</task>\n"
                + "<rules>Return keys: the canonical issue key of each item the search answered with, and"
                + " NOTHING else — no item you were not shown, none you think belongs there. An empty list is"
                + " the right answer where the search matched nothing." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, TicketSearch.SCHEMA.json(), query, AssistantCallKind.INTAKE)
                .map(n -> {
                    List<String> keys = new ArrayList<>();
                    n.path("keys").forEach(key -> keys.add(key.asString("").strip()));
                    keys.removeIf(String::isEmpty);
                    return List.copyOf(keys);
                });
    }

    /** What the last answer got wrong, appended so the next one is not the same answer. */
    private static String correcting(List<String> corrections) {
        return corrections.isEmpty() ? ""
                : "\n\nYour last answer was refused:\n- " + String.join("\n- ", corrections)
                + "\nAnswer again, fixing every one of those.";
    }
}
