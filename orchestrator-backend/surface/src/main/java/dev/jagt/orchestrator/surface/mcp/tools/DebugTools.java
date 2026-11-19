package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.protocol.debug.TranscriptRead;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.ui.log.LogReads;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DebugTools implements McpTools {

  private final LogReads logs;
  private final DebugReads reads;

  @Override
  public void declare(McpToolRegistry tools) {
    tools.tool(
        "read_record",
        Audience.ROOT,
        Effect.REPORTS,
        RecordTail.SCHEMA,
        RecordTail.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> logs.read(said));

    tools.tool(
        "read_transcript",
        Audience.ROOT,
        Effect.REPORTS,
        TranscriptRead.SCHEMA,
        TranscriptRead.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> reads.transcript(said.taskId(), said.count()));
  }
}
