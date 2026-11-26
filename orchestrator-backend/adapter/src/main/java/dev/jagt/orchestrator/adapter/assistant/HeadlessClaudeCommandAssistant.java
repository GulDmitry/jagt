package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.protocol.CommandRead;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeCommandAssistant implements CommandAssistant {

  /** Mapping text to a command reads nothing and must feel like typing. */
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private static final Duration NAMING_TIMEOUT = Duration.ofSeconds(60);
  private static final int PROMPT_MAX = 2000;

  private final ToolLessRead read;

  @Override
  public Answer<CommandProposal> mapCommand(@Nullable String text, Menu menu) {
    if (text == null || text.isBlank()) {
      return Answer.unavailable();
    }
    String prompt =
        """
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
        Respond directly.\
        """
            .formatted(text, menu.prompt());
    return read.ask(
            prompt,
            CommandRead.schemaFor(menu.commands(), menu.tasks()).json(),
            "command mapping",
            AssistantCallKind.COMMAND_MAP,
            TIMEOUT)
        .map(n ->
            new CommandProposal(
                n.path("command").asString(""),
                n.path("task").asString(""),
                n.path("ticket").asString(""),
                n.path("reason").asString("")));
  }

  @Override
  public Answer<String> nameBranch(@Nullable String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return Answer.unavailable();
    }
    String question =
        """
        <role>You name the git branch of one task from the request that opened it.</role>
        <request>%s</request>
        <rules>
        - words: at most four lowercase words saying what the work changes, never the request's \
        opening complaint or context.
        </rules>
        <examples>
        Request "the linter was updated and there are a lot of new warnings, please fix them": \
        words="upgrade linter".
        </examples>
        Respond directly.\
        """
            .formatted(prompt.length() <= PROMPT_MAX ? prompt : prompt.substring(0, PROMPT_MAX));
    return read.ask(
            question,
            Schema.answer().required("words", "string", null).json(),
            "branch name",
            AssistantCallKind.BRANCH_NAME,
            NAMING_TIMEOUT)
        .map(n -> n.path("words").asString(""));
  }
}
