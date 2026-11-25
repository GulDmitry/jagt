package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.surface.mcp.refusal.MasterToken;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class LoopbackOrRootTokenTest {

  @ParameterizedTest
  @CsvSource({
    "192.168.1.20, X-Working-Directory, /work/task-1",
    "127.0.0.1, X-Forwarded-For, 127.0.0.1",
    "127.0.0.1, Forwarded, for=127.0.0.1",
    "peer.invalid, X-Working-Directory, /work/task-1"
  })
  void refusesPeerOffLoopbackOrForwardedOrUnparsableThatNamesNoToken(
      String peer, String header, String value, @TempDir Path root) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr(peer);
    request.addHeader(header, value);
    LoopbackOrRootToken door =
        new LoopbackOrRootToken(
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()),
                    FakeHost.machine())));

    assertThat(door.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted())
        .isFalse();
  }

  @Test
  void admitsPeerOffLoopbackPresentingTheMasterToken(@TempDir Path root) throws Exception {
    MasterToken token =
        new MasterToken(
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString()),
                FakeHost.machine()));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr("192.168.1.20");
    request.addHeader(MasterToken.HEADER, Files.readString(root.resolve("jagt/master-token")));

    assertThat(
            new LoopbackOrRootToken(token)
                .authorize(() -> null, new RequestAuthorizationContext(request))
                .isGranted())
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"})
  void admitsSessionOnTheSameMachineByItsWorktreeHeader(String peer, @TempDir Path root) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr(peer);
    request.addHeader("X-Working-Directory", "/work/task-1");
    LoopbackOrRootToken door =
        new LoopbackOrRootToken(
            new MasterToken(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString()),
                    FakeHost.machine())));

    assertThat(door.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted())
        .isTrue();
  }
}
