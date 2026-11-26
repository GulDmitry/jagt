package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.protocol.debug.ScreenRead;
import dev.jagt.orchestrator.protocol.debug.TranscriptRead;
import dev.jagt.orchestrator.protocol.message.Message;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.service.log.ScreenReads;
import dev.jagt.orchestrator.surface.ui.log.LogReads;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/debug", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class DebugController {

  private final LogReads logs;
  private final DebugReads reads;
  private final ScreenReads screens;

  @GetMapping("/records/{record}")
  public String record(
      @PathVariable String record, @RequestParam(required = false) @Nullable Integer lines) {
    return logs.read(valid(new RecordTail(record, lines)));
  }

  @GetMapping("/transcript")
  public String transcript(
      @RequestParam String taskId, @RequestParam(required = false) @Nullable Integer maxChars) {
    TranscriptRead request = valid(new TranscriptRead(taskId, maxChars));
    return reads.transcript(request.taskId(), request.count());
  }

  @GetMapping("/screen")
  public String screen(
      @RequestParam String taskId, @RequestParam(required = false) @Nullable Integer lines) {
    return screens.screen(valid(new ScreenRead(taskId, lines)));
  }

  private static <T extends Message> T valid(T message) {
    var violations = message.violations(MessageContext.NONE);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(Message.refusal(violations));
    }
    return message;
  }
}
