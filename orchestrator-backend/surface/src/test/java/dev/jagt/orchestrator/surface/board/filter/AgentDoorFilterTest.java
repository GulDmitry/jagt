package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import dev.jagt.orchestrator.surface.mcp.refusal.MasterToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AgentDoorFilterTest {

  private static final AuthProperties PASSWORD =
      new AuthProperties(AuthProperties.Mode.PASSWORD, "hash", List.of());

  @ParameterizedTest
  @CsvSource({"/mcp", "/api/agent/hooks", "/mcp;x=1"})
  void refusesPeerOffLoopbackNamingTaskByWorktreeHeaderOnly(String path, @TempDir Path root)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr("192.168.1.20");
    request.addHeader("X-Working-Directory", "/work/task-1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new AgentDoorFilter(
            PASSWORD,
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"X-Forwarded-For, 127.0.0.1", "Forwarded, for=127.0.0.1"})
  void refusesForwardedRequestEvenWhenItsPeerReadsAsLoopback(
      String header, String value, @TempDir Path root) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr("127.0.0.1");
    request.addHeader(header, value);
    request.addHeader("X-Working-Directory", "/work/task-1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new AgentDoorFilter(
            PASSWORD,
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void refusesPeerWhoseAddressIsNoLiteral(@TempDir Path root) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr("peer.invalid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new AgentDoorFilter(
            PASSWORD,
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void admitsPeerOffLoopbackPresentingTheMasterToken(@TempDir Path root) throws Exception {
    MasterToken token =
        new MasterToken(
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString())));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr("192.168.1.20");
    request.addHeader(MasterToken.HEADER, Files.readString(root.resolve("jagt/master-token")));
    MockFilterChain chain = new MockFilterChain();

    new AgentDoorFilter(PASSWORD, token).doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @ParameterizedTest
  @CsvSource({"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"})
  void admitsSessionOnTheSameMachineByItsWorktreeHeader(String peer, @TempDir Path root)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr(peer);
    request.addHeader("X-Working-Directory", "/work/task-1");
    MockFilterChain chain = new MockFilterChain();

    new AgentDoorFilter(
            PASSWORD,
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void leavesEveryOtherPathToTheLogin(@TempDir Path root) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/state");
    request.setRemoteAddr("192.168.1.20");
    MockFilterChain chain = new MockFilterChain();

    new AgentDoorFilter(
            PASSWORD,
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void changesNothingWithoutLogin(@TempDir Path root) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr("192.168.1.20");
    MockFilterChain chain = new MockFilterChain();

    new AgentDoorFilter(
            new AuthProperties(AuthProperties.Mode.NONE, null, List.of()),
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()))))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }
}
