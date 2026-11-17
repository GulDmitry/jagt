package dev.jagt.orchestrator.surface.board.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.session.SessionStreams;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class SessionSocketTest {

  private final SessionStreams streams = mock(SessionStreams.class);
  private final WebSocketSession socket = mock(WebSocketSession.class);
  private final WindowStream.View view = mock(WindowStream.View.class);
  private final SessionSocket handler = new SessionSocket(streams, JsonMapper.builder().build());

  @BeforeEach
  void theConnectionHoldsAttributes() {
    when(socket.getAttributes()).thenReturn(new HashMap<>());
  }

  @Test
  void opensTheWindowOfTheTaskAtTheSizeOfTheBrowsersScreen() throws Exception {
    when(streams.ofTask(eq("ABC-1"), eq(120), eq(40), any())).thenReturn(Optional.of(view));

    when(socket.getUri())
        .thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-1&cols=120&rows=40"));
    handler.afterConnectionEstablished(socket);

    verify(streams).ofTask(eq("ABC-1"), eq(120), eq(40), any());
  }

  @Test
  void opensWindowJagtOpensWhenItIsNamed() throws Exception {
    when(streams.ofWindow(eq("login"), eq(80), eq(24), any())).thenReturn(Optional.of(view));

    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?name=login"));
    handler.afterConnectionEstablished(socket);

    verify(streams).ofWindow(eq("login"), eq(80), eq(24), any());
  }

  @Test
  void closesWithNoWindowWhereNoWindowHoldsTheSession() throws Exception {
    when(streams.ofTask(eq("ABC-1"), eq(80), eq(24), any())).thenReturn(Optional.empty());

    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-1"));
    handler.afterConnectionEstablished(socket);

    verify(socket).close(SessionSocket.NO_WINDOW);
  }

  @Test
  void closesWithNoWindowWhereTheTaskIsUnknown() throws Exception {
    when(streams.ofTask(eq("ABC-9"), eq(80), eq(24), any()))
        .thenThrow(new IllegalStateException("No such task"));

    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-9"));
    handler.afterConnectionEstablished(socket);

    verify(socket).close(SessionSocket.NO_WINDOW);
  }

  @Test
  void typesWhatDataFrameCarries() throws Exception {
    when(streams.ofTask(eq("ABC-1"), eq(80), eq(24), any())).thenReturn(Optional.of(view));
    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-1"));
    handler.afterConnectionEstablished(socket);

    handler.handleMessage(socket, new TextMessage("{\"type\":\"data\",\"data\":\"ls\\r\"}"));

    verify(view).type("ls\r");
  }

  @Test
  void resizesTheWindowWithinTheLimits() throws Exception {
    when(streams.ofTask(eq("ABC-1"), eq(80), eq(24), any())).thenReturn(Optional.of(view));
    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-1"));
    handler.afterConnectionEstablished(socket);

    handler.handleMessage(
        socket, new TextMessage("{\"type\":\"resize\",\"cols\":5000,\"rows\":2}"));

    verify(view).resize(1000, 10);
  }

  @Test
  void closesTheViewWhenTheConnectionCloses() throws Exception {
    when(streams.ofTask(eq("ABC-1"), eq(80), eq(24), any())).thenReturn(Optional.of(view));
    when(socket.getUri()).thenReturn(URI.create("ws://localhost/api/windows/stream?task=ABC-1"));
    handler.afterConnectionEstablished(socket);

    handler.afterConnectionClosed(socket, CloseStatus.NORMAL);

    verify(view).close();
  }

  @ParameterizedTest
  @CsvSource({"abc,80", "0,80", "5,10", "120,120", "9999,1000"})
  void keepsAskedSizeWithinLimitsAndFallsBackWhereItIsNoSize(String asked, int size) {
    assertThat(SessionSocket.queried(asked, 80)).isEqualTo(size);
  }

  @Test
  void fallsBackWhereNoSizeWasAsked() {
    assertThat(SessionSocket.queried(null, 80)).isEqualTo(80);
  }
}
