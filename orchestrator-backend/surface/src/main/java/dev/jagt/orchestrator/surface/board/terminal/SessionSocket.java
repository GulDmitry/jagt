package dev.jagt.orchestrator.surface.board.terminal;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.session.SessionStreams;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A human's live terminal on one window: output goes out as binary frames; text frames come in,
 * {@code {"type":"data","data":"…"}} to type and {@code {"type":"resize","cols":n,"rows":n}}.
 * Nothing the Master can reach calls this: typing here is the human's.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionSocket extends TextWebSocketHandler {

  public static final String PATH = "/api/windows/stream";
  public static final CloseStatus NO_WINDOW = new CloseStatus(4404, "no window holds this session");

  private static final String VIEW = "view";
  private static final int SEND_MILLIS = 10_000;
  private static final int BUFFERED_BYTES = 4 * 1024 * 1024;
  private static final int FALLBACK_COLUMNS = 80;
  private static final int FALLBACK_ROWS = 24;
  private static final int REASON = 100;
  private static final int LEAST = 10;
  private static final int MOST = 1000;

  private final SessionStreams streams;
  private final JsonMapper json;

  @Override
  public void afterConnectionEstablished(WebSocketSession socket) throws IOException {
    MultiValueMap<String, String> query =
        UriComponentsBuilder.fromUri(socket.getUri() == null ? URI.create(PATH) : socket.getUri())
            .build()
            .getQueryParams();
    WebSocketSession sending =
        new ConcurrentWebSocketSessionDecorator(socket, SEND_MILLIS, BUFFERED_BYTES);
    int columns = queried(query.getFirst("cols"), FALLBACK_COLUMNS);
    int rows = queried(query.getFirst("rows"), FALLBACK_ROWS);
    try {
      Optional<WindowStream.View> view = open(query, columns, rows, sinkOf(sending));
      if (view.isPresent()) {
        socket.getAttributes().put(VIEW, view.get());
      } else {
        socket.close(NO_WINDOW);
      }
    } catch (RuntimeException unavailable) {
      log.atWarn()
          .setMessage("session stream refused")
          .addKeyValue("cause", OneLine.clipped(unavailable.toString()))
          .addKeyValue("effect", "the Session tab shows no live terminal")
          .log();
      socket.close(NO_WINDOW);
    }
  }

  private Optional<WindowStream.View> open(
      MultiValueMap<String, String> query, int columns, int rows, WindowStream.Sink sink) {
    String task = query.getFirst("task");
    return task != null
        ? streams.ofTask(task, columns, rows, sink)
        : streams.ofWindow(String.valueOf(query.getFirst("name")), columns, rows, sink);
  }

  private static WindowStream.Sink sinkOf(WebSocketSession sending) {
    return new WindowStream.Sink() {
      @Override
      public void output(byte[] bytes) {
        try {
          sending.sendMessage(new BinaryMessage(bytes));
        } catch (IOException | RuntimeException gone) {
          ended("the browser's connection is gone");
        }
      }

      @Override
      public void ended(String cause) {
        try {
          sending.close(
              CloseStatus.GOING_AWAY.withReason(
                  cause.substring(0, Math.min(cause.length(), REASON))));
        } catch (IOException | RuntimeException alreadyClosed) {
          log.atDebug()
              .setMessage("session stream already closed")
              .addKeyValue("cause", cause)
              .log();
        }
      }
    };
  }

  @Override
  protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
    Object held = socket.getAttributes().get(VIEW);
    if (!(held instanceof WindowStream.View view)) {
      return;
    }
    JsonNode frame = json.readTree(message.getPayload());
    switch (frame.path("type").asString("")) {
      case "data" -> view.type(frame.path("data").asString(""));
      case "resize" ->
          view.resize(
              size(frame.path("cols").asInt(0), FALLBACK_COLUMNS),
              size(frame.path("rows").asInt(0), FALLBACK_ROWS));
      default ->
          log.atWarn()
              .setMessage("session stream frame ignored")
              .addKeyValue("cause", "unknown type")
              .log();
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    if (socket.getAttributes().remove(VIEW) instanceof WindowStream.View view) {
      view.close();
    }
  }

  static int queried(@Nullable String asked, int fallback) {
    try {
      return size(Integer.parseInt(asked), fallback);
    } catch (NumberFormatException unreadable) {
      return fallback;
    }
  }

  static int size(int asked, int fallback) {
    return asked <= 0 ? fallback : Math.clamp(asked, LEAST, MOST);
  }
}
