package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpControllerTest {

    @Test
    void refusesACallAFormCouldSendWithoutAPreflight() throws Exception {
        McpProtocolService protocol = mock(McpProtocolService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(protocol, mock(StateService.class),
                new JsonMapper(), mock(MasterToken.class))).build();

        mvc.perform(post("/mcp").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(protocol);
    }

    @Test
    void answersAnUnknownCallerUnauthorizedSoItsClientMintsItsHeaderAgain() throws Exception {
        JsonMapper mapper = new JsonMapper();
        McpProtocolService protocol = mock(McpProtocolService.class);
        when(protocol.handle(any(), any(), anyBoolean())).thenReturn(Optional.of(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32001,\"message\":\"Unknown caller\"}}")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(protocol, mock(StateService.class),
                mapper, mock(MasterToken.class))).build();

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAWrongMasterTokenUnauthorized(@TempDir Path root) throws Exception {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(new McpProtocolService(mapper, state,
                List.of()), state, mapper, new MasterToken(paths))).build();

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).header(MasterToken.HEADER, "0123abcd")
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesACallPresentingNoTokenUnauthorized(@TempDir Path root) throws Exception {
        JsonMapper mapper = new JsonMapper();
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(mapper, paths);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(new McpProtocolService(mapper, state,
                List.of()), state, mapper, new MasterToken(paths))).build();

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passesTheMasterThatPresentsItsToken() throws Exception {
        McpProtocolService protocol = mock(McpProtocolService.class);
        when(protocol.handle(any(), any(), anyBoolean())).thenReturn(Optional.empty());
        MasterToken token = mock(MasterToken.class);
        when(token.matches("s3cret")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(protocol, mock(StateService.class),
                new JsonMapper(), token)).build();

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).header(MasterToken.HEADER, "s3cret")
                .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));

        verify(protocol).handle(any(), eq(null), eq(true));
    }
}
