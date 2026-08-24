package dev.jagt.orchestrator.surface.mcp.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgsTest {

    @Test
    void keepsTheOrderTheCallerNamedThePairsIn() {
        var args = new tools.jackson.databind.json.JsonMapper().readTree(
                "{\"reviewRequests\": {\"zzz\": \"1\", \"aaa\": \"2\", \"mmm\": \"3\", \"bbb\": \"4\"}}");

        assertThat(ToolArgs.pairs(args, "reviewRequests").keySet())
                .containsExactly("zzz", "aaa", "mmm", "bbb");
    }
}
