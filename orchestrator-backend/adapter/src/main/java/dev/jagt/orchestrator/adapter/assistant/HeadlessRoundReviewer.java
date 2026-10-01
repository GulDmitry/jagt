package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.protocol.RoundRead;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeadlessRoundReviewer implements RoundReviewer {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private static final List<String> READS = List.of("Read", "Grep", "Glob", "Bash");
    /** Writes nothing, moves no ref, and does not read the author's own account first. */
    private static final List<String> REFUSED = List.of("Edit", "Write", "NotebookEdit",
            "Bash(git push:*)", "Bash(git commit:*)", "Bash(git reset:*)", "Bash(git checkout:*)",
            "Bash(git stash:*)", "Bash(git restore:*)", "Read(**/review_replies.md)");
    /** The tests ran before the round reached review; every role building at once in one worktree is the cost. */
    private static final List<String> BUILDS = List.of("Bash(./gradlew:*)", "Bash(gradle:*)", "Bash(./mvnw:*)",
            "Bash(mvn:*)", "Bash(npm:*)", "Bash(npx:*)", "Bash(yarn:*)", "Bash(pnpm:*)", "Bash(composer:*)",
            "Bash(vendor/bin/phpunit:*)", "Bash(pytest:*)", "Bash(make:*)", "Bash(docker:*)", "Bash(timeout:*)");

    private final ProcessRunner processRunner;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Answer<Judgement> review(Round round) {
        List<String> cmd = new ArrayList<>(List.of(claude.command(), round.prompt(), "-p",
                "--json-schema", RoundRead.SCHEMA.json(), "--output-format", "json",
                "--setting-sources", assistant.settingSources()));
        if (!round.shared().isBlank()) {
            cmd.addAll(List.of("--append-system-prompt", round.shared()));
        }
        String pinned = assistant.mcpConfigFor(AssistantCallKind.MASTER_REVIEW);
        if (!pinned.isBlank()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", pinned));
        }
        round.worktrees().stream().skip(1).forEach(dir -> cmd.addAll(List.of("--add-dir", dir.toString())));
        if (!round.model().isBlank()) {
            cmd.addAll(List.of("--model", round.model()));
        }
        cmd.add("--allowedTools");
        cmd.addAll(READS);
        cmd.addAll(assistant.allowedTools());
        if (assistant.permissionMode() != null && !assistant.permissionMode().isBlank()) {
            cmd.addAll(List.of("--permission-mode", assistant.permissionMode()));
        }
        cmd.add("--disallowedTools");
        cmd.addAll(REFUSED);
        cmd.addAll(BUILDS);
        Processes.Result result;
        try {
            result = processRunner.run(round.worktrees().getFirst(), TIMEOUT, cmd);
        } catch (RuntimeException e) {
            log.atError().setMessage("master review did not return")
                    .addKeyValue("path", round.worktrees().getFirst())
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "token cost unmeasured")
                    .log();
            return new Answer<>(Optional.of(Judgement.failed("the review did not return: " + e.getMessage())),
                    TokenUsage.NONE);
        }
        JsonNode envelope = envelope(result.stdout());
        TokenUsage usage = HeadlessClaudeAssistant.usageOf(envelope);
        JsonNode answer = envelope == null ? null : envelope.path("structured_output");
        if (result.exitCode() != 0 || answer == null || !answer.isObject()) {
            String cause = result.stderr().isBlank() ? result.stdout() : result.stderr();
            log.atError().setMessage("master review failed")
                    .addKeyValue("path", round.worktrees().getFirst())
                    .addKeyValue("exit", result.exitCode())
                    .addKeyValue("cause", cause.strip())
                    .log();
            return new Answer<>(Optional.of(Judgement.failed("the review answered nothing readable, exit "
                    + result.exitCode())), usage);
        }
        return new Answer<>(Optional.of(judgement(answer)), usage);
    }

    private JsonNode envelope(String stdout) {
        try {
            return stdout == null || stdout.isBlank() ? null : mapper.readTree(stdout);
        } catch (RuntimeException unparseable) {
            return null;
        }
    }

    private static Judgement judgement(JsonNode answer) {
        List<Finding> findings = new ArrayList<>();
        answer.path("findings").forEach(f -> findings.add(new Finding(f.path("file").asString(""),
                f.path("issue").asString(""), f.path("pattern").asString(""),
                f.path("severity").asString("").strip().toLowerCase(java.util.Locale.ROOT))));
        List<Premise> premises = new ArrayList<>();
        answer.path("premises").forEach(p -> premises.add(new Premise(p.path("claim").asString(""),
                p.path("provenBy").asString(""))));
        return new Judgement(answer.path("failure").asString(""), answer.path("verdict").asString(""),
                findings, answer.path("question").asString(""), premises);
    }
}
