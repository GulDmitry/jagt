package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpControllerTest {

    @Test
    void refusesACallAFormCouldSendWithoutAPreflight() throws Exception {
        McpProtocolService protocol = mock(McpProtocolService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new McpController(protocol, mock(StateService.class), new JsonMapper())).build();

        mvc.perform(post("/mcp").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(protocol);
    }
}
