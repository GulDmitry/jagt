package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.AssistantUnavailable;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantProcessTest {

    private static final Path CWD = Path.of(".");
    private static final Duration TIMEOUT = Duration.ofMinutes(1);
    private static final String OVERLOADED_529 = """
{"duration_api_ms":0,"stop_reason":"stop_sequence",\
"session_id":"d638fd9d-2a7c-449d-8e68-a64542931b9c","total_cost_usd":0,\
"usage":{"output_tokens_details":{"thinking_tokens":0},"input_tokens":0,\
"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,\
"server_tool_use":{"web_search_requests":0,"web_fetch_requests":0},"service_tier":"standard",\
"cache_creation":{"ephemeral_1h_input_tokens":0,"ephemeral_5m_input_tokens":0},"inference_geo":"",\
"iterations":[],"speed":"standard","fallback_credit":null},"modelUsage":{},"permission_denials":[],\
"terminal_reason":"api_error","fast_mode_state":"off",\
"fast_mode_disabled_reason":"sdk_opt_in_required","subagent_stats":{"spawned":0,\
"requested":{"background":0,"foreground":0,"unset":0},"started_in_background":0,"max_depth":0,\
"spawned_by_subagents":0,"completed":0,"failed":0,"killed":{"parent":0,"user":0,"system":0},\
"refused":{"depth_limit":0,"concurrency_limit":0,"budget":0},"by_type":{}},"safety_stops":0,\
"is_error":true,"num_turns":1,"subtype":"success","api_error_status":529,\
"result":"API Error: 529 Overloaded. This is a server-side issue,\
 usually temporary — try again in a moment. If it persists,\
 check your inference gateway (127.0.0.1:18529).","type":"result","duration_ms":733,\
"uuid":"02368b97-942b-4e63-9855-74344bfb179e","queued_turn_count":0,"result_index":0}""";

    private static final String RATE_LIMITED_429 = """
{"duration_api_ms":0,"stop_reason":"stop_sequence",\
"session_id":"0520d56f-4a1b-426c-9622-310623e29203","total_cost_usd":0,\
"usage":{"output_tokens_details":{"thinking_tokens":0},"input_tokens":0,\
"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,\
"server_tool_use":{"web_search_requests":0,"web_fetch_requests":0},"service_tier":"standard",\
"cache_creation":{"ephemeral_1h_input_tokens":0,"ephemeral_5m_input_tokens":0},"inference_geo":"",\
"iterations":[],"speed":"standard","fallback_credit":null},"modelUsage":{},"permission_denials":[],\
"terminal_reason":"api_error","fast_mode_state":"off",\
"fast_mode_disabled_reason":"sdk_opt_in_required","subagent_stats":{"spawned":0,\
"requested":{"background":0,"foreground":0,"unset":0},"started_in_background":0,"max_depth":0,\
"spawned_by_subagents":0,"completed":0,"failed":0,"killed":{"parent":0,"user":0,"system":0},\
"refused":{"depth_limit":0,"concurrency_limit":0,"budget":0},"by_type":{}},"safety_stops":0,\
"is_error":true,"num_turns":1,"subtype":"success","api_error_status":429,\
"result":"API Error: Request rejected (429) · This request would exceed your organization's rate limit",\
"type":"result","duration_ms":807,"uuid":"2e52fbdc-0907-4aa3-a9c3-6855aad1e9f9",\
"queued_turn_count":0,"result_index":0}""";

    private static final Processes.Result OVERLOADED = new Processes.Result(1, OVERLOADED_529, "");
    private static final Processes.Result LIMIT = new Processes.Result(1, RATE_LIMITED_429, "");

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final AssistantProcess process = new AssistantProcess(runner,
            new AssistantBreaker(AssistantProperties.empty().withBreakerFailures(3), Clock.systemUTC()));

    @Test
    void startsNoProcessOnceThreeProviderFailuresInARowOpenedIt() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(OVERLOADED);
        process.run(CWD, TIMEOUT, List.of("claude"));
        process.run(CWD, TIMEOUT, List.of("claude"));
        process.run(CWD, TIMEOUT, List.of("claude"));

        assertThatThrownBy(() -> process.run(CWD, TIMEOUT, List.of("claude")))
                .isInstanceOf(AssistantUnavailable.class).hasMessageContaining("529");
        verify(runner, times(3)).run(any(Path.class), any(Duration.class), any());
    }

    @Test
    void aUsageLimitNeverOpensIt() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(LIMIT);
        for (int call = 0; call < 5; call++) {
            process.run(CWD, TIMEOUT, List.of("claude"));
        }

        assertThat(process.run(CWD, TIMEOUT, List.of("claude"))).isEqualTo(LIMIT);
        verify(runner, times(6)).run(any(Path.class), any(Duration.class), any());
    }
}
