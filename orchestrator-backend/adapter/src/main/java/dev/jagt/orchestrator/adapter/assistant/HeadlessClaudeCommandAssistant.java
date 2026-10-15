package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.protocol.CommandRead;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeCommandAssistant implements CommandAssistant {

    /** Mapping text to a command reads nothing and must feel like typing. */
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HeadlessClaude claude;

    @Override
    public Answer<CommandProposal> mapCommand(String text, String context) {
        if (text == null || text.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = """
                <role>You map one operator request onto EXACTLY ONE command of the tool below.</role>
                <request>%s</request>
                <tool>
                %s
                </tool>
                <rules>
                - command: the command word. task: the task it applies to, its id or alias copied verbatim from the \
                list, never invented. ticket: the ticket reference for `do`, the request URL for `resume`. reason: \
                why, in a few words.
                - Leave a field as an empty string when it does not apply. `do` and `resume` name no task.
                - If the request does not clearly match one command and, where it acts on one, one task, answer \
                command="none" and put the ambiguity in reason. Do NOT guess between two tasks: ambiguity is a `none`.
                </rules>
                <examples>
                Tasks ABC-5 (alias s1, search filters) and ABC-6 (alias s2, search ranking); request "the search \
                filters work can go out for review": command=ship, task=ABC-5.
                Same tasks; request "restart the search agent": command=none, reason="two search tasks fit".
                </examples>
                Respond directly.""".formatted(text, context);
        return claude.ask(prompt, CommandRead.SCHEMA.json(), "command mapping", AssistantCallKind.COMMAND_MAP, TIMEOUT)
                .map(n -> new CommandProposal(n.path("command").asString(""), n.path("task").asString(""),
                        n.path("ticket").asString(""), n.path("reason").asString("")));
    }
}
