package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;

import java.util.List;
import java.util.Map;

/**
 * What makes one checker role the role it is: the id its jobs, records and region carry, its name to a human, when
 * its check opens, which outcomes may end it, what its read may call, and the shapes of what it answers to a check
 * and to a decline.
 */
public record RoleDefinition(String id, String name, String model, List<String> trigger, String oncePer,
                             List<String> outcomes, Tools tools, Schema answer, Schema decline) {

    /** {@code cli} is the tool set the CLI offers, {@code allow} and {@code refuse} its permission lists. */
    public record Tools(List<String> cli, List<String> allow, List<String> refuse, boolean git, boolean fetch) {
    }

    public static final RoleDefinition QA = new RoleDefinition("qa", "QA", "sonnet",
            List.of("deployed", "pipelines-green", "environment-named"), "deploy",
            List.of("round", "question", "wait", "dispute", "stop"),
            new Tools(List.of("Read", "Grep", "Glob", "Bash", "WebFetch"),
                    List.of("Read", "Grep", "Glob", "WebFetch", "Bash(curl -sS:*)", "Bash(git log:*)",
                            "Bash(git show:*)", "Bash(git diff:*)"),
                    List.of("Write", "Edit", "NotebookEdit", "Bash(git * --output*)", "Bash(git push:*)",
                            "Bash(git commit:*)", "Bash(git reset:*)", "Bash(git checkout:*)"), true, true),
            Schema.answer()
                    .required("failure", "string", null)
                    .records("rows", null, List.of("criterion", "status", "evidence"),
                            Map.of("status", List.of("met", "not met", "unprovable")))
                    .text("question", null)
                    .text("waitingOn", null),
            Schema.answer()
                    .required("failure", "string", null)
                    .text("dispute", null));
}
