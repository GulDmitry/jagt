package dev.jagt.orchestrator.adapter.assistant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeConfigTest {

    @Test
    void switchesEveryToolOffBeforeNamingTheReadOnesSoNoneIsMasked() {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(Path.of("/wt")),
                List.of());

        JsonNode tools = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", scope, Path.of("/state/gate")))
                .path("agent").path("jagt-read").path("tools");

        assertThat(tools.propertyNames()).containsExactly("*", "read", "grep", "glob", "bash");
        assertThat(tools.path("*").asBoolean(true)).isFalse();
    }

    @Test
    void deniesEveryCommandFirstThenCurlWritesAndAnyRedirectLastSoTheLastMatchingRuleHoldsTheLine() {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)", "Bash(git show:*)"),
                List.of("Bash(git * --ou*)"), false, true, List.of(Path.of("/wt")), List.of());

        JsonNode bash = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", scope, Path.of("/state/gate")))
                .path("agent").path("jagt-read").path("permission").path("bash");

        assertThat(bash.propertyNames()).containsExactly("*", "git log*", "git show*", "git * --ou*", "* -o *", "* -O*",
                "* --output*", "* -T *", "* --upload-file*", "* -d *", "* --data*", "* -F *", "* --form*", "* -X *",
                "* --request*", "*>*");
        assertThat(bash.path("*").asString()).isEqualTo("deny");
        assertThat(bash.path("git log*").asString()).isEqualTo("allow");
        assertThat(bash.path("git * --ou*").asString()).isEqualTo("deny");
        assertThat(bash.path("* --upload-file*").asString()).isEqualTo("deny");
        assertThat(bash.path("*>*").asString()).isEqualTo("deny");
    }

    @Test
    void opensEveryWorktreeOfTheReadAndNoOtherDirectory() {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, false, List.of(Path.of("/wt1"), Path.of("/wt2")),
                List.of());

        JsonNode outside = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", scope, Path.of("/state/gate")))
                .path("agent").path("jagt-read").path("permission").path("external_directory");

        assertThat(outside.propertyNames()).containsExactly("*", "/wt1/**", "/wt2/**");
        assertThat(outside.path("*").asString()).isEqualTo("deny");
        assertThat(outside.path("/wt2/**").asString()).isEqualTo("allow");
    }

    @Test
    void offersAWebFetchAndAllowsItOnlyWhereTheRoleFetches() {
        OpenCodeScope fetching = OpenCodeScope.of(List.of(), List.of(), true, false, List.of(Path.of("/wt")),
                List.of("https://dev.example.test"));
        OpenCodeScope silent = OpenCodeScope.of(List.of(), List.of(), false, false, List.of(Path.of("/wt")), List.of());

        JsonNode allowed = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", fetching, Path.of("/state/gate")));
        JsonNode denied = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", silent, Path.of("/state/gate")));

        assertThat(allowed.path("agent").path("jagt-read").path("tools").path("webfetch").asBoolean(false)).isTrue();
        assertThat(allowed.path("agent").path("jagt-read").path("permission").path("webfetch").asString()).isEqualTo("allow");
        assertThat(denied.path("agent").path("jagt-read").path("tools").has("webfetch")).isFalse();
        assertThat(denied.path("agent").path("jagt-read").path("permission").path("webfetch").asString()).isEqualTo("deny");
    }

    @Test
    void runsTheReadOnItsOwnAgentOnTheRolesModelWithNoMcpServerAndNoEdit() {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, false, List.of(Path.of("/wt")), List.of());

        JsonNode config = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", scope, Path.of("/state/gate")));

        assertThat(config.path("default_agent").asString()).isEqualTo("jagt-read");
        assertThat(config.path("model").asString()).isEqualTo("local/qwen-4b");
        assertThat(config.path("provider").path("local").path("options").path("baseURL").asString())
                .isEqualTo("http://127.0.0.1:8080/v1");
        assertThat(config.has("mcp")).isFalse();
        assertThat(config.path("agent").path("jagt-read").path("permission").path("edit").asString()).isEqualTo("deny");
        assertThat(config.path("autoupdate").asBoolean(true)).isFalse();
        assertThat(config.path("share").asString()).isEqualTo("disabled");
    }

    @Test
    void loadsTheGatePluginFromItsDirectoryAndNoOther() {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, false, List.of(Path.of("/wt")), List.of());

        JsonNode plugin = new JsonMapper().readTree(OpenCodeConfig.json("http://127.0.0.1:8080/v1", "qwen-4b", scope,
                Path.of("/state/opencode-gate"))).path("plugin");

        assertThat(plugin).extracting(JsonNode::asString).containsExactly("/state/opencode-gate");
    }
}
