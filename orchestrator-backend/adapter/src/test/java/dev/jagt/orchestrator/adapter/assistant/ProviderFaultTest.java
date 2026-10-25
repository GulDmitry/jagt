package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFaultTest {

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

    private static final String SERVER_ERROR_500 = """
{"duration_api_ms":0,"stop_reason":"stop_sequence",\
"session_id":"67958421-a540-4e67-b942-46e74e3c68c1","total_cost_usd":0,\
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
"is_error":true,"num_turns":1,"subtype":"success","api_error_status":500,\
"result":"API Error: 500 Internal server error. This is a server-side issue,\
 usually temporary — try again in a moment. If it persists,\
 check your inference gateway (127.0.0.1:18500).","type":"result","duration_ms":871,\
"uuid":"a5a4a924-58e7-4630-9273-cda539689f77","queued_turn_count":0,"result_index":0}""";

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

    private static final String CONNECTION_REFUSED = """
{"duration_api_ms":0,"stop_reason":"stop_sequence",\
"session_id":"996eef52-5470-4786-a832-e736fc423f4c","total_cost_usd":0,\
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
"is_error":true,"num_turns":1,"subtype":"success","api_error_status":null,\
"result":"API Error: Connection refused — a firewall or proxy may be blocking it (ECONNREFUSED)",\
"type":"result","duration_ms":721,"uuid":"57f64e41-c649-4b77-80f4-65802a3efb73",\
"queued_turn_count":0,"result_index":0}""";

    @Test
    void anOverloadedProviderFails() {
        var reading = ProviderFault.of(new Processes.Result(1, OVERLOADED_529, ""));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.FAILED);
        assertThat(reading.cause()).startsWith("API Error: 529 Overloaded");
    }

    @Test
    void aServerErrorFails() {
        var reading = ProviderFault.of(new Processes.Result(1, SERVER_ERROR_500, ""));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.FAILED);
    }

    @Test
    void aRefusedConnectionFailsWithTheCliOwnWords() {
        var reading = ProviderFault.of(new Processes.Result(1, CONNECTION_REFUSED, ""));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.FAILED);
        assertThat(reading.cause()).contains("ECONNREFUSED");
    }

    @Test
    void aRateLimitMeansTheProviderAnsweredAndNeverCounts() {
        var reading = ProviderFault.of(new Processes.Result(1, RATE_LIMITED_429, ""));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.REACHED);
    }

    @Test
    void anAnswerMeansTheProviderAnswered() {
        var reading = ProviderFault.of(new Processes.Result(0, "{\"type\":\"result\",\"is_error\":false}", ""));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.REACHED);
    }

    @Test
    void noEnvelopeSaysNothingAboutTheProvider() {
        var reading = ProviderFault.of(new Processes.Result(1, "", "Reached maximum budget"));

        assertThat(reading.verdict()).isEqualTo(ProviderFault.Verdict.UNKNOWN);
    }
}
