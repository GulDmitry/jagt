package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.stop.ActStop.Cause;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The stop that demotes `act` to judge: three bad endings, or the whole board stopped. */
@Service
@RequiredArgsConstructor
public class ActStops {

  private final MasterBrake brake;
  private final BoardStop board;

  public Optional<ActStop> standing() {
    Optional<ActStop> halted =
        board.standing().map(standing -> new ActStop(Cause.BOARD, standing.at()));
    return halted.isPresent() || !brake.stopped()
        ? halted
        : Optional.of(new ActStop(Cause.STRIKES, null));
  }

  /** True where it stood. */
  public boolean lift() {
    return brake.restore();
  }
}
