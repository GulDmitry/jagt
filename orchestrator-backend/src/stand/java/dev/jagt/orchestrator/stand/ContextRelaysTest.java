package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContextRelaysTest {

    @TempDir
    Path root;

    @Test
    void namesEveryBriefStepARelayedTextCarries() throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), """
                ## ship
                Commit and push.

                ## round-threads
                Answer each thread.

                ## round-threads-done
                Hand the round back.
                """);
        Files.createDirectories(root.resolve("abc-42"));
        Files.writeString(root.resolve("abc-42/task_context.md"), "Review round.\nAnswer each thread.\nHand the round back.");
        JsonNode task = JsonMapper.builder().build().readTree("{\"repos\":[{\"worktreePath\":\"" + root.resolve("abc-42") + "\"}]}");

        Optional<List<String>> steps = new ContextRelays(new StandBooks(root).brief()).changed("ABC-42", task);

        assertThat(steps).contains(List.of("round-threads", "round-threads-done"));
    }

    @Test
    void reportsNothingWhileTheContextFileHoldsWhatItHeldBefore() throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## ship\nCommit and push.\n");
        Files.createDirectories(root.resolve("abc-42"));
        Files.writeString(root.resolve("abc-42/task_context.md"), "Shipped.\n\nCommit and push.");
        JsonNode task = JsonMapper.builder().build().readTree("{\"repos\":[{\"worktreePath\":\"" + root.resolve("abc-42") + "\"}]}");
        ContextRelays relays = new ContextRelays(new StandBooks(root).brief());
        relays.changed("ABC-42", task);

        Optional<List<String>> again = relays.changed("ABC-42", task);

        assertThat(again).isEmpty();
    }
}
