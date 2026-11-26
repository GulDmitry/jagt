package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.service.role.HeaderChips;
import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import dev.jagt.orchestrator.service.role.HeaderChips.Kind;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoardStopChips implements HeaderChips {

  private final BoardStop board;

  @Override
  public List<HeaderChip> chips() {
    return board
        .standing()
        .map(
            standing -> new HeaderChip("board-stopped", "unstop", Kind.PAUSED, standing.at(), null))
        .stream()
        .toList();
  }
}
